package io.github.akrishna87.pakkam.ui

import android.app.Application
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.akrishna87.pakkam.data.Files
import io.github.akrishna87.pakkam.data.RecentFile
import io.github.akrishna87.pakkam.data.Recents
import io.github.akrishna87.pakkam.data.Settings
import io.github.akrishna87.pakkam.data.Signatures
import io.github.akrishna87.pakkam.pdf.PdfDoc
import io.github.akrishna87.pakkam.pdf.WrongPasswordException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Where the app is. The last entry of [AppViewModel.stack] is on screen. */
sealed interface Screen {
    data object Home : Screen
    data object Viewer : Screen
    data class Tool(val tool: ToolKind) : Screen
    data object Merge : Screen
    data class Images(val camera: Boolean) : Screen
}

/** The document tools, each with its own screen. */
enum class ToolKind(val title: String, val blurb: String) {
    ORGANIZE("Organize pages", "Reorder, turn, delete, insert or extract pages"),
    COMPRESS("Compress PDF", "Make the file smaller to share"),
    PROTECT("Protect with password", "Lock the PDF, or remove a password"),
    SPLIT("Split PDF", "Save page ranges as separate files"),
    EXPORT("Export PDF", "Save as Word, text or pictures"),
    WATERMARK("Add watermark", "Stamp text across every page"),
    PAGE_NUMBERS("Add page numbers", "Number the pages in a corner"),
    FILL_FORM("Fill in form", "Type into the form's fields"),
    FLATTEN("Flatten", "Make comments and form entries permanent"),
    INFO("Document properties", "Title, author and file details"),
}

data class PasswordRequest(val uri: Uri, val name: String, val tool: ToolKind?, val wrong: Boolean)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val context: Application get() = getApplication()
    val recentsStore = Recents(app)
    val settings = Settings(app)
    val signatures = Signatures(app)

    val stack = mutableStateListOf<Screen>(Screen.Home)
    var recents by mutableStateOf(recentsStore.all())
        private set
    var session by mutableStateOf<DocSession?>(null)
        private set
    /** When set, a blocking progress card is shown with this text. */
    var busy by mutableStateOf<String?>(null)
    var passwordRequest by mutableStateOf<PasswordRequest?>(null)
    /** The toolbar the next opened document starts with (Fill & Sign from Home). */
    var startMode by mutableStateOf<ViewerMode?>(null)

    private val messages = Channel<String>(Channel.BUFFERED)
    val messageFlow = messages.receiveAsFlow()

    fun say(text: String) {
        messages.trySend(text)
    }

    fun go(screen: Screen) {
        stack.add(screen)
    }

    fun back() {
        if (stack.size > 1) {
            val top = stack.removeAt(stack.lastIndex)
            if (top == Screen.Viewer) closeSession()
        }
    }

    fun refreshRecents() {
        recents = recentsStore.all()
    }

    /** Opens a document, asking for its password when needed, then shows it (or a tool for it). */
    fun open(uri: Uri, tool: ToolKind? = null, password: String? = null, picked: Boolean = false) {
        viewModelScope.launch {
            busy = "Opening…"
            var work: File? = null
            try {
                if (picked) Files.keepAccess(context, uri)
                val name = withContext(Dispatchers.IO) { Files.displayName(context, uri) }
                work = withContext(Dispatchers.IO) { Files.copyToWork(context, uri) }
                val doc = try {
                    PdfDoc.open(work, password)
                } catch (e: WrongPasswordException) {
                    work.delete()
                    passwordRequest = PasswordRequest(uri, name, tool, wrong = password != null)
                    return@launch
                }
                closeSession()
                val s = DocSession(context, uri, name, password, work, doc)
                s.canWriteBack = withContext(Dispatchers.IO) { Files.canWrite(context, uri) }
                session = s
                val old = recentsStore.get(uri.toString())
                recentsStore.put(
                    RecentFile(
                        uri = uri.toString(),
                        name = name,
                        size = work.length(),
                        pages = doc.pageCount,
                        lastPage = old?.lastPage?.coerceIn(0, doc.pageCount - 1) ?: 0,
                        opened = System.currentTimeMillis(),
                        starred = old?.starred ?: false,
                        bookmarks = old?.bookmarks ?: emptyList(),
                    ),
                )
                refreshRecents()
                // One document at a time: drop any previous viewer or tool screens.
                while (stack.size > 1) stack.removeAt(stack.lastIndex)
                stack.add(Screen.Viewer)
                if (tool != null) stack.add(Screen.Tool(tool))
                launch { s.refreshAnnotations() }
            } catch (e: Exception) {
                work?.delete()
                say(e.message ?: "This file couldn't be opened")
            } finally {
                busy = null
            }
        }
    }

    fun closeSession() {
        val s = session ?: return
        session = null
        s.close()
    }

    fun rememberPage(page: Int) {
        val s = session ?: return
        recentsStore.update(s.uri.toString()) { it.copy(lastPage = page) }
    }

    fun toggleBookmark(page: Int) {
        val s = session ?: return
        recentsStore.update(s.uri.toString()) {
            it.copy(bookmarks = if (page in it.bookmarks) it.bookmarks - page else (it.bookmarks + page).sorted())
        }
        refreshRecents()
    }

    fun bookmarks(): List<Int> = session?.let { s -> recents.firstOrNull { it.uri == s.uri.toString() }?.bookmarks } ?: emptyList()

    /** Runs [work] with a progress card, reporting failures as a message. */
    fun <T> task(label: String, work: suspend () -> T, done: (T) -> Unit = {}) {
        viewModelScope.launch {
            busy = label
            try {
                val r = work()
                done(r)
            } catch (e: OutOfMemoryError) {
                say("Not enough memory for this file")
            } catch (e: Exception) {
                say(e.message?.takeIf { it.isNotBlank() } ?: "Something went wrong")
            } finally {
                busy = null
            }
        }
    }

    override fun onCleared() {
        closeSession()
    }
}

@Composable
fun PakkamRoot(vm: AppViewModel) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        vm.messageFlow.collect { snackbar.showSnackbar(it) }
    }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val screen = vm.stack.last()
        val session = vm.session
        BackHandler(enabled = vm.stack.size > 1 && screen != Screen.Viewer) { vm.back() }
        when (screen) {
            Screen.Home -> HomeScreen(vm)
            Screen.Viewer -> if (session != null) ViewerScreen(vm, session) else LaunchedEffect(Unit) { vm.back() }
            is Screen.Tool -> if (session != null) ToolScreen(vm, session, screen.tool) else LaunchedEffect(Unit) { vm.back() }
            Screen.Merge -> MergeScreen(vm)
            is Screen.Images -> ImagesToPdfScreen(vm, screen.camera)
        }

        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 72.dp))

        vm.busy?.let { BusyCard(it) }
        vm.passwordRequest?.let { req ->
            PasswordDialog(
                name = req.name,
                wrong = req.wrong,
                onDismiss = { vm.passwordRequest = null },
                onSubmit = { pw ->
                    vm.passwordRequest = null
                    vm.open(req.uri, req.tool, pw)
                },
            )
        }
    }
}

@Composable
fun BusyCard(label: String) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            // Swallow touches while working.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Card(Modifier.widthIn(max = 320.dp).padding(24.dp)) {
            Column(Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                CircularProgressIndicator()
                Text(label, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
fun PasswordDialog(name: String, wrong: Boolean, onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var pw by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Password needed") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (wrong) "That password didn't open “$name”. Try again." else "“$name” is protected. Type its password to open it.")
                OutlinedTextField(
                    value = pw,
                    onValueChange = { pw = it },
                    label = { Text("Password") },
                    singleLine = true,
                    isError = wrong,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSubmit(pw) }, enabled = pw.isNotEmpty()) { Text("Open") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
