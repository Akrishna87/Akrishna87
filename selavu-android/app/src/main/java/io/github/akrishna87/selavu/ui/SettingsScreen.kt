@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package io.github.akrishna87.selavu.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.akrishna87.selavu.AppViewModel
import io.github.akrishna87.selavu.BuildConfig
import io.github.akrishna87.selavu.Screen
import io.github.akrishna87.selavu.core.BudgetPeriod
import io.github.akrishna87.selavu.core.Category
import io.github.akrishna87.selavu.core.CategoryColors
import io.github.akrishna87.selavu.core.Ledger
import io.github.akrishna87.selavu.core.Money
import io.github.akrishna87.selavu.core.TxType
import io.github.akrishna87.selavu.core.newId
import io.github.akrishna87.selavu.core.shortDate
import io.github.akrishna87.selavu.core.usage
import java.time.LocalDate

@Composable
fun SettingsScreen(vm: AppViewModel, ledger: Ledger, padding: PaddingValues) {
    val s = ledger.settings
    var editBudget by remember { mutableStateOf(false) }
    var editCurrency by remember { mutableStateOf(false) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let(vm::exportCsv)
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::importCsv)
    }

    LazyColumn(Modifier.fillMaxSize().padding(padding)) {
        item {
            Text("Settings", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(16.dp))
        }
        item { SectionHeader("Budget") }
        item {
            ListItem(
                headlineContent = { Text("Spending budget") },
                supportingContent = {
                    Text(
                        if (s.budget <= 0) "Off. Set one to see how much is left."
                        else "${Money.format(s.budget, s.currency)} ${if (s.budgetPeriod == BudgetPeriod.WEEKLY) "a week" else "a month"}",
                    )
                },
                modifier = Modifier.clickable { editBudget = true },
            )
        }
        item { SectionHeader("Your money") }
        item {
            ListItem(
                headlineContent = { Text("Categories") },
                supportingContent = { Text("${ledger.categories(TxType.EXPENSE).size} for spending, ${ledger.categories(TxType.INCOME).size} for income") },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                modifier = Modifier.clickable { vm.open(Screen.Categories) },
            )
        }
        item {
            ListItem(
                headlineContent = { Text("Repeating entries") },
                supportingContent = {
                    Text(if (ledger.recurring.isEmpty()) "None yet. Choose Repeat when adding one." else "${ledger.recurring.size} active")
                },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                modifier = Modifier.clickable { vm.open(Screen.Repeating) },
            )
        }
        item {
            ListItem(
                headlineContent = { Text("Currency") },
                supportingContent = { Text(s.currency) },
                modifier = Modifier.clickable { editCurrency = true },
            )
        }
        item {
            ListItem(
                headlineContent = { Text("Weeks start on Monday") },
                supportingContent = { Text(if (s.weekStartsMonday) "Monday to Sunday" else "Sunday to Saturday") },
                trailingContent = {
                    Switch(checked = s.weekStartsMonday, onCheckedChange = { on -> vm.updateSettings { it.copy(weekStartsMonday = on) } })
                },
            )
        }
        item { SectionHeader("Spreadsheet (CSV)") }
        item {
            ListItem(
                headlineContent = { Text("Export to a file") },
                supportingContent = { Text("All ${ledger.transactions.size} entries, for Excel, Google Sheets or a backup") },
                modifier = Modifier.clickable { export.launch("Selavu ${LocalDate.now()}.csv") },
            )
        }
        item {
            ListItem(
                headlineContent = { Text("Import from a file") },
                supportingContent = { Text("Columns Date, Type, Category, Amount, Note. Entries already here are skipped.") },
                modifier = Modifier.clickable { importer.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel", "application/octet-stream")) },
            )
        }
        item {
            Column {
                HorizontalDivider(Modifier.padding(top = 12.dp))
                Text(
                    "Selavu ${BuildConfig.VERSION_NAME}. Everything stays on this phone: the app has no internet access.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }

    if (editBudget) BudgetDialog(ledger, onDismiss = { editBudget = false }) { amount, period ->
        vm.updateSettings { it.copy(budget = amount, budgetPeriod = period) }
        editBudget = false
    }
    if (editCurrency) CurrencyDialog(s.currency, onDismiss = { editCurrency = false }) { c ->
        vm.updateSettings { it.copy(currency = c) }
        editCurrency = false
    }
}

@Composable
private fun BudgetDialog(ledger: Ledger, onDismiss: () -> Unit, onSave: (Long, BudgetPeriod) -> Unit) {
    val s = ledger.settings
    var text by rememberSaveable { mutableStateOf(if (s.budget > 0) Money.plain(s.budget) else "") }
    var period by rememberSaveable { mutableStateOf(s.budgetPeriod) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Spending budget") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(BudgetPeriod.WEEKLY to "Weekly", BudgetPeriod.MONTHLY to "Monthly").forEachIndexed { i, (p, label) ->
                        SegmentedButton(selected = period == p, onClick = { period = p }, shape = SegmentedButtonDefaults.itemShape(i, 2)) {
                            Text(label)
                        }
                    }
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { v -> text = v.filter { it.isDigit() || it == '.' }.take(14) },
                    label = { Text("Budget amount") },
                    prefix = { Text(s.currency) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                Text(
                    "Leave it empty to turn the budget off.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(Money.parse(text) ?: 0, period) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun CurrencyDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Currency") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("₹", "$", "€", "£", "¥", "AED ", "SGD ", "RM ").forEach { c ->
                        FilterChip(selected = text == c, onClick = { text = c }, label = { Text(c.trim()) })
                    }
                }
                OutlinedTextField(
                    value = text, onValueChange = { text = it.take(5) }, singleLine = true,
                    label = { Text("Symbol shown before amounts") },
                )
                Text("Only the symbol changes; amounts aren't converted.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text.ifBlank { "₹" }) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun CategoriesScreen(vm: AppViewModel, ledger: Ledger, padding: PaddingValues) {
    var type by rememberSaveable { mutableStateOf(TxType.EXPENSE) }
    var editing by remember { mutableStateOf<Category?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Category?>(null) }
    Box(Modifier.fillMaxSize().padding(padding)) {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                windowInsets = WindowInsets(0),
                title = { Text("Categories") },
                navigationIcon = {
                    IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                listOf(TxType.EXPENSE to "Spending", TxType.INCOME to "Income").forEachIndexed { i, (t, label) ->
                    SegmentedButton(selected = type == t, onClick = { type = t }, shape = SegmentedButtonDefaults.itemShape(i, 2)) {
                        Text(label)
                    }
                }
            }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 88.dp)) {
                items(ledger.categories(type), key = { it.id }) { c ->
                    val used = ledger.usage(c.id)
                    ListItem(
                        leadingContent = { CategoryIcon(c) },
                        headlineContent = { Text(c.name) },
                        supportingContent = { Text(if (used == 0) "Not used yet" else "$used ${if (used == 1) "entry" else "entries"}") },
                        trailingContent = {
                            if (ledger.categories(type).size > 1) {
                                TextButton(onClick = { deleting = c }) { Text("Remove") }
                            }
                        },
                        modifier = Modifier.clickable { editing = c },
                    )
                }
            }
        }
        FloatingActionButton(
            onClick = { adding = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) { Icon(Icons.Default.Add, contentDescription = "New category") }
    }

    if (adding || editing != null) {
        CategoryDialog(editing, type, onDismiss = { adding = false; editing = null }) { c ->
            vm.saveCategory(c)
            adding = false
            editing = null
        }
    }
    deleting?.let { c ->
        val others = ledger.categories(c.type).filter { it.id != c.id }
        var moveTo by remember(c.id) { mutableStateOf(others.firstOrNull { it.name.startsWith("Other") }?.id ?: others.first().id) }
        val used = ledger.usage(c.id)
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Remove ${c.name}?") },
            text = {
                if (used == 0) Text("It isn't used by any entry.")
                else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Its $used ${if (used == 1) "entry moves" else "entries move"} to:")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        others.forEach { o ->
                            FilterChip(selected = moveTo == o.id, onClick = { moveTo = o.id }, label = { Text("${o.emoji} ${o.name}") })
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.deleteCategory(c.id, moveTo); deleting = null }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

/** Adding ([existing] null) or renaming a category: name, emoji and colour. */
@Composable
fun CategoryDialog(existing: Category?, type: TxType, onDismiss: () -> Unit, onSave: (Category) -> Unit) {
    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var emoji by rememberSaveable { mutableStateOf(existing?.emoji ?: "🏷️") }
    var color by rememberSaveable { mutableStateOf(existing?.color ?: CategoryColors.all.random()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New category" else "Edit category") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it.take(30) }, singleLine = true, label = { Text("Name") })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = emoji, onValueChange = { emoji = it.take(8) }, singleLine = true,
                        label = { Text("Emoji") }, modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.size(12.dp))
                    CategoryIcon(Category("", name, emoji.ifBlank { "🏷️" }, type, color), 48.dp)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CategoryColors.all.forEach { c ->
                        Box(
                            Modifier.size(32.dp).background(Color(c), CircleShape)
                                .then(if (c == color) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                                .clickable { color = c },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(
                        (existing ?: Category(newId(), "", "", type, color))
                            .copy(name = name.trim(), emoji = emoji.trim().ifEmpty { "🏷️" }, color = color),
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun RepeatingScreen(vm: AppViewModel, ledger: Ledger, padding: PaddingValues) {
    var stopping by remember { mutableStateOf<String?>(null) }
    val today = LocalDate.now()
    Column(Modifier.fillMaxSize().padding(padding)) {
        TopAppBar(
            windowInsets = WindowInsets(0),
            title = { Text("Repeating entries") },
            navigationIcon = {
                IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            },
        )
        if (ledger.recurring.isEmpty()) {
            EmptyState("🔁", "Nothing repeats yet", "When adding an expense or income, choose how often it repeats and it will be logged on its own.")
        }
        LazyColumn(Modifier.weight(1f)) {
            items(ledger.recurring.sortedBy { it.nextDate }, key = { it.id }) { r ->
                val c = ledger.category(r.categoryId)
                ListItem(
                    leadingContent = { CategoryIcon(c) },
                    headlineContent = {
                        Text(listOfNotNull(c?.name, r.note.takeIf { it.isNotBlank() }).joinToString(" · "))
                    },
                    supportingContent = { Text("${Money.format(r.amount, ledger.settings.currency)}, ${r.repeat.label.lowercase()}. Next on ${shortDate(r.nextDate, today)}") },
                    trailingContent = { TextButton(onClick = { stopping = r.id }) { Text("Stop") } },
                )
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
    stopping?.let { id ->
        AlertDialog(
            onDismissRequest = { stopping = null },
            title = { Text("Stop repeating?") },
            text = { Text("Nothing more will be logged for it. What it already logged stays in your history.") },
            confirmButton = { TextButton(onClick = { vm.stopRecurring(id); stopping = null }) { Text("Stop") } },
            dismissButton = { TextButton(onClick = { stopping = null }) { Text("Cancel") } },
        )
    }
}
