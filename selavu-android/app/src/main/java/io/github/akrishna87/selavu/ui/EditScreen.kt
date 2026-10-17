@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package io.github.akrishna87.selavu.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.akrishna87.selavu.AppViewModel
import io.github.akrishna87.selavu.core.Ledger
import io.github.akrishna87.selavu.core.Money
import io.github.akrishna87.selavu.core.Repeat
import io.github.akrishna87.selavu.core.Transaction
import io.github.akrishna87.selavu.core.TxType
import io.github.akrishna87.selavu.core.lastCategory
import io.github.akrishna87.selavu.core.newId
import io.github.akrishna87.selavu.core.shortDate
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Adding a new expense or income, or changing one ([txId] set). */
@Composable
fun EditScreen(vm: AppViewModel, ledger: Ledger, startType: TxType, txId: String?, padding: PaddingValues) {
    val existing = remember(txId) { txId?.let { id -> ledger.transactions.firstOrNull { it.id == id } } }
    val today = LocalDate.now()
    var type by rememberSaveable { mutableStateOf(existing?.type ?: startType) }
    var amountText by rememberSaveable { mutableStateOf(existing?.let { Money.plain(it.amount) } ?: "") }
    var categoryId by rememberSaveable { mutableStateOf(existing?.categoryId ?: ledger.lastCategory(startType)) }
    var dateEpoch by rememberSaveable { mutableStateOf((existing?.date ?: vm.defaultDate()).toEpochDay()) }
    var note by rememberSaveable { mutableStateOf(existing?.note ?: "") }
    var repeat by rememberSaveable { mutableStateOf(Repeat.NONE) }
    var showDate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var newCategory by remember { mutableStateOf(false) }
    var triedSave by remember { mutableStateOf(false) }
    val date = LocalDate.ofEpochDay(dateEpoch)
    val amount = Money.parse(amountText)
    val recurring = existing?.recurringId?.let { id -> ledger.recurring.firstOrNull { it.id == id } }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (existing == null) runCatching { focus.requestFocus() } }

    fun save() {
        triedSave = true
        val cat = categoryId?.takeIf { id -> ledger.category(id)?.type == type } ?: return
        if (amount == null) return
        val tx = existing?.copy(type = type, amount = amount, categoryId = cat, date = date, note = note.trim())
            ?: Transaction(newId(), type, amount, cat, date, note.trim())
        vm.saveTransaction(tx, repeat)
        vm.back()
    }

    Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
        TopAppBar(
            windowInsets = WindowInsets(0),
            title = {
                Text(
                    (if (existing == null) "New " else "Edit ") + if (type == TxType.EXPENSE) "expense" else "income",
                )
            },
            navigationIcon = {
                IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            },
            actions = {
                if (existing != null) {
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
                }
            },
        )
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(TxType.EXPENSE to "Expense", TxType.INCOME to "Income").forEachIndexed { i, (t, label) ->
                    SegmentedButton(
                        selected = type == t,
                        onClick = {
                            if (type != t) {
                                type = t
                                categoryId = ledger.lastCategory(t)
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(i, 2),
                    ) { Text(label) }
                }
            }

            OutlinedTextField(
                value = amountText,
                onValueChange = { v -> amountText = v.filter { it.isDigit() || it == '.' || it == ',' }.take(16) },
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                label = { Text("Amount") },
                prefix = { Text(ledger.settings.currency, style = MaterialTheme.typography.headlineMedium) },
                textStyle = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold, color = amountColor(type)),
                singleLine = true,
                isError = triedSave && amount == null,
                supportingText = { if (triedSave && amount == null) Text("Enter an amount") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() }),
            )

            Column {
                Text("Category", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ledger.categories(type).forEach { c ->
                        FilterChip(
                            selected = c.id == categoryId,
                            onClick = { categoryId = c.id },
                            label = { Text("${c.emoji}  ${c.name}") },
                        )
                    }
                    AssistChip(onClick = { newCategory = true }, label = { Text("＋ New") })
                }
            }

            Column {
                Text("Date", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = date == today, onClick = { dateEpoch = today.toEpochDay() }, label = { Text("Today") })
                    FilterChip(
                        selected = date == today.minusDays(1),
                        onClick = { dateEpoch = today.minusDays(1).toEpochDay() },
                        label = { Text("Yesterday") },
                    )
                    val other = date != today && date != today.minusDays(1)
                    FilterChip(
                        selected = other,
                        onClick = { showDate = true },
                        label = { Text(if (other) shortDate(date, today) else "Pick a date") },
                        leadingIcon = { Icon(Icons.Default.CalendarMonth, contentDescription = null) },
                    )
                }
            }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it.take(200) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Note (optional)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() }),
            )

            Column {
                Text("Repeat", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                if (existing == null) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Repeat.entries.forEach { r ->
                            FilterChip(selected = repeat == r, onClick = { repeat = r }, label = { Text(r.label) })
                        }
                    }
                    if (repeat != Repeat.NONE) {
                        Text(
                            "It will be logged ${repeat.label.lowercase()} on its own, starting " + when (date) {
                                today -> "today."
                                today.minusDays(1) -> "yesterday."
                                else -> "on ${shortDate(date, today)}."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else if (recurring != null) {
                    Row {
                        Icon(Icons.Default.Repeat, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.padding(4.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${recurring.repeat.label}, next on ${shortDate(recurring.nextDate, today)}")
                            Text(
                                "Changes here apply to this entry only.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    TextButton(onClick = { vm.stopRecurring(recurring.id) }) { Text("Stop repeating") }
                } else {
                    Text(
                        if (existing.recurringId != null) "Logged by a repeat that has been stopped." else "Doesn't repeat.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Button(
            onClick = { save() },
            modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp),
        ) { Text("Save", style = MaterialTheme.typography.titleMedium) }
    }

    if (showDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        dateEpoch = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                    }
                    showDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }

    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this entry?") },
            text = {
                Text(
                    if (recurring != null) "Only this entry is deleted. It will keep repeating; use Stop repeating for that."
                    else "This can't be undone.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteTransaction(existing.id)
                    confirmDelete = false
                    vm.back()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }

    if (newCategory) {
        CategoryDialog(null, type, onDismiss = { newCategory = false }) { c ->
            vm.saveCategory(c)
            categoryId = c.id
            newCategory = false
        }
    }
}
