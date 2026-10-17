@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package io.github.akrishna87.pakkam.ui

import android.app.SearchManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.akrishna87.pakkam.data.Definition
import io.github.akrishna87.pakkam.data.Dictionary
import io.github.akrishna87.pakkam.pdf.WordForms
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val FIRST_MEANINGS = 8

/**
 * What a long-pressed word means, from the built-in dictionary, with ways to look further.
 * [actions] go above those, for what else can be done with the word where it was pressed.
 */
@Composable
fun DefinitionSheet(vm: AppViewModel, word: String, actions: (@Composable () -> Unit)? = null, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val shownWord = remember(word) { WordForms.clean(word) }
    // null while looking; then (dictionary present, definition or null).
    val result by produceState<Pair<Boolean, Definition?>?>(null, word) {
        value = withContext(Dispatchers.IO) {
            try {
                vm.dictionary.available to vm.dictionary.lookup(word)
            } catch (e: Exception) {
                false to null
            }
        }
    }
    var showAll by remember(word) { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val r = result
            val def = r?.second
            Row(verticalAlignment = Alignment.Bottom) {
                Text(def?.word ?: shownWord, style = MaterialTheme.typography.headlineMedium, fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold)
                if (!def?.pronunciation.isNullOrBlank()) {
                    Text(
                        "  /${def!!.pronunciation}/",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 3.dp),
                    )
                }
            }
            if (def != null && !def.word.equals(def.looked, ignoreCase = true)) {
                Text("from “${def.looked}”", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // The ways to look further sit up top, where they show without scrolling past a long entry.
            actions?.invoke()
            LookFurther(context, def?.word ?: shownWord)
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            when {
                r == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("Looking it up…", Modifier.padding(start = 12.dp))
                }
                !r.first -> Text("This build of Pakkam doesn't include the dictionary. You can still look the word up with the buttons below.")
                def == null -> Text("“$shownWord” isn't in the built-in English dictionary.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> {
                    val list = if (showAll) def.meanings else def.meanings.take(FIRST_MEANINGS)
                    var lastPos = ""
                    for ((i, m) in list.withIndex()) {
                        if (m.pos != lastPos) {
                            Text(m.pos, style = MaterialTheme.typography.titleSmall, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp))
                            lastPos = m.pos
                        }
                        Column(Modifier.fillMaxWidth()) {
                            Text("${i + 1}. ${m.definition}", style = MaterialTheme.typography.bodyLarge)
                            for (ex in m.examples) {
                                Text("“$ex”", style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp, top = 2.dp))
                            }
                            if (m.synonyms.isNotEmpty()) {
                                Text("Also: " + m.synonyms.take(6).joinToString(", "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp, top = 2.dp))
                            }
                        }
                    }
                    if (!showAll && def.meanings.size > FIRST_MEANINGS) {
                        TextButton(onClick = { showAll = true }) { Text("Show all ${def.meanings.size} meanings") }
                    }
                }
            }
            if (def != null) {
                Text(Dictionary.CREDIT, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Copy, search the web, and any dictionary or translation app on the phone that takes words. */
@Composable
private fun LookFurther(context: Context, word: String) {
    val apps = remember {
        try {
            val pm = context.packageManager
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain"), 0)
                .filter { it.activityInfo.packageName != context.packageName }
                .map { it to it.loadLabel(pm).toString() }
                .distinctBy { it.second }
                .take(4)
        } catch (e: Exception) {
            emptyList()
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AssistChip(
            onClick = {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("Word", word))
            },
            label = { Text("Copy") },
            leadingIcon = { Icon(Icons.Filled.ContentCopy, null, Modifier.size(18.dp)) },
        )
        AssistChip(
            onClick = { searchWeb(context, word) },
            label = { Text("Search the web") },
            leadingIcon = { Icon(Icons.Filled.Search, null, Modifier.size(18.dp)) },
        )
        for ((info, label) in apps) {
            AssistChip(
                onClick = {
                    val i = Intent(Intent.ACTION_PROCESS_TEXT)
                        .setType("text/plain")
                        .setClassName(info.activityInfo.packageName, info.activityInfo.name)
                        .putExtra(Intent.EXTRA_PROCESS_TEXT, word)
                        .putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
                    try {
                        context.startActivity(i)
                    } catch (_: Exception) {
                    }
                },
                label = { Text(label) },
                leadingIcon = { Icon(Icons.Filled.Translate, null, Modifier.size(18.dp)) },
            )
        }
    }
}

private fun searchWeb(context: Context, word: String) {
    val query = "define $word"
    try {
        context.startActivity(Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, query))
    } catch (e: Exception) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(query))))
        } catch (_: Exception) {
        }
    }
}
