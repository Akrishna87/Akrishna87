package io.github.akrishna87.pakkam.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import io.github.akrishna87.pakkam.data.Files
import io.github.akrishna87.pakkam.data.RecentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Saves the open document: back into its own file when the app may write there, otherwise as a copy. */
class DocSaver(val save: (then: (() -> Unit)?) -> Unit, val saveAs: (then: (() -> Unit)?) -> Unit)

/** [currentPage] is remembered with the copy in Recent. */
@Composable
fun rememberDocSaver(vm: AppViewModel, session: DocSession, currentPage: () -> Int): DocSaver {
    val context = LocalContext.current
    var afterSave by remember { mutableStateOf<(() -> Unit)?>(null) }
    val saveCopy = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri: Uri? ->
        val then = afterSave
        afterSave = null
        if (uri == null) return@rememberLauncherForActivityResult
        vm.task("Saving a copy…", {
            session.applyEdits()
            withContext(Dispatchers.IO) { Files.writeTo(context, session.workFile, uri) }
            Files.keepAccess(context, uri)
            val name = withContext(Dispatchers.IO) { Files.displayName(context, uri) }
            session.saved(uri, name)
            session.canWriteBack = withContext(Dispatchers.IO) { Files.canWrite(context, uri) }
            vm.recentsStore.put(RecentFile(uri.toString(), name, session.workFile.length(), session.doc.pageCount, currentPage(), System.currentTimeMillis(), false))
            vm.refreshRecents()
        }) {
            vm.say("Saved as ${session.name}")
            then?.invoke()
        }
    }
    return remember(session, saveCopy) {
        val saveAs: (then: (() -> Unit)?) -> Unit = { then ->
            afterSave = then
            saveCopy.launch(Files.baseName(session.name) + " (edited).pdf")
        }
        val save: (then: (() -> Unit)?) -> Unit = { then ->
            if (!session.canWriteBack) {
                saveAs(then)
            } else {
                vm.task("Saving…", {
                    session.applyEdits()
                    withContext(Dispatchers.IO) { Files.writeTo(context, session.workFile, session.uri) }
                    session.saved()
                }) {
                    vm.say("Saved")
                    then?.invoke()
                }
            }
        }
        DocSaver(save, saveAs)
    }
}
