package io.github.akrishna87.semippu.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.akrishna87.semippu.Rates
import io.github.akrishna87.semippu.Result
import io.github.akrishna87.semippu.Scheme
import io.github.akrishna87.semippu.inWords
import io.github.akrishna87.semippu.plain
import io.github.akrishna87.semippu.rupees
import io.github.akrishna87.semippu.schemes
import io.github.akrishna87.semippu.term

@Composable
fun SemippuRoot() {
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    val open = schemes.firstOrNull { it.id == openId }
    BackHandler(enabled = open != null) { openId = null }
    if (open == null) HomeScreen { openId = it.id } else CalculatorScreen(open) { openId = null }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(onOpen: (Scheme) -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("Semippu") }) }) { padding ->
        LazyColumn(contentPadding = padding) {
            item {
                Text(
                    "Government rates for ${Rates.QUARTER}",
                    Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            for ((group, list) in schemes.groupBy { it.group }) {
                item {
                    Text(
                        group,
                        Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                items(list, key = { it.id }) { s ->
                    ListItem(
                        headlineContent = { Text(s.name) },
                        supportingContent = { Text(s.fullName) },
                        trailingContent = {
                            val rate = s.fields.first { it.key == "rate" }.default
                            Text("${plain(rate)}%", style = MaterialTheme.typography.titleMedium)
                        },
                        modifier = Modifier.clickable { onOpen(s) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalculatorScreen(scheme: Scheme, onBack: () -> Unit) {
    var text by rememberSaveable(scheme.id) { mutableStateOf(scheme.fields.associate { it.key to plain(it.default) }) }
    val values = scheme.fields.associate { it.key to text[it.key]?.replace(",", "")?.trim()?.toDoubleOrNull() }
    val errors = scheme.fields.associate { it.key to it.error(values[it.key]) }
    val result = if (errors.values.all { it == null }) scheme.calc(values.mapValues { it.value!! }) else null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(scheme.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Text(scheme.fullName, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(scheme.fields, key = { it.key }) { f ->
                val error = errors[f.key]
                OutlinedTextField(
                    value = text[f.key].orEmpty(),
                    onValueChange = { v -> text = text + (f.key to v) },
                    label = { Text(f.label) },
                    supportingText = { Text(error ?: f.hint) },
                    isError = error != null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (result != null) {
                item { Summary(result) }
                item { Text(scheme.notes, style = MaterialTheme.typography.bodyMedium) }
                item { Table(result) }
            }
        }
    }
}

@Composable
private fun Summary(r: Result) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (r.payout != null) {
                Text("You receive", style = MaterialTheme.typography.labelLarge)
                Text("${rupees(r.payout)} ${r.payoutEvery}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("and your ${rupees(r.maturity)} back after ${term(r.months)}")
            } else {
                Text("Maturity value after ${term(r.months)}", style = MaterialTheme.typography.labelLarge)
                Text(rupees(r.maturity), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                inWords(r.maturity)?.let { Text(it) }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Line("You invest", rupees(r.invested))
            Line(if (r.payout != null) "Total interest paid to you" else "Interest earned", rupees(r.interest))
        }
    }
}

@Composable
private fun Line(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun Table(r: Result) {
    Column {
        Text("Year by year", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
        TableRow(
            "", "Deposited", if (r.payout != null) "Paid out" else "Interest", "Balance", FontWeight.Medium,
        )
        HorizontalDivider()
        for (row in r.rows) {
            TableRow(row.label, rupees(row.deposited), rupees(row.interest), rupees(row.balance))
        }
    }
}

@Composable
private fun TableRow(a: String, b: String, c: String, d: String, weight: FontWeight = FontWeight.Normal) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Cell(a, 0.9f, weight, TextAlign.Start)
        Cell(b, 1.1f, weight)
        Cell(c, 1.1f, weight)
        Cell(d, 1.3f, weight)
    }
}

@Composable
private fun RowScope.Cell(s: String, w: Float, weight: FontWeight, align: TextAlign = TextAlign.End) {
    Text(s, Modifier.weight(w), style = MaterialTheme.typography.bodySmall, fontWeight = weight, textAlign = align)
}
