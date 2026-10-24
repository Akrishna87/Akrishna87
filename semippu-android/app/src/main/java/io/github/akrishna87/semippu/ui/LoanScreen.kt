package io.github.akrishna87.semippu.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.akrishna87.semippu.Every
import io.github.akrishna87.semippu.Field
import io.github.akrishna87.semippu.LoanResult
import io.github.akrishna87.semippu.LoanType
import io.github.akrishna87.semippu.PartPayment
import io.github.akrishna87.semippu.Payoff
import io.github.akrishna87.semippu.loan
import io.github.akrishna87.semippu.plain
import io.github.akrishna87.semippu.rupees
import io.github.akrishna87.semippu.term
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun loanFields(t: LoanType) = listOf(
    Field("amount", "Loan amount (₹)", t.amount, 1000.0, 1e10),
    Field("rate", "Interest rate (% a year)", t.rate, 0.1, 40.0, hint = "An example; enter your bank's rate"),
    Field("years", "Years", t.years.toDouble(), 1.0, 30.0, step = 1.0),
    Field("paid", "EMIs already paid", 0.0, 0.0, 359.0, step = 1.0, hint = "For a loan that's already running"),
    Field("fee", "Processing fee (₹)", 0.0, 0.0, 1e9, hint = "From your loan offer"),
)

private val partFields = listOf(
    Field("part", "Part payment (₹)", 0.0, 0.0, 1e10, hint = "0 for none"),
    Field("start", "Paid after EMI number", 1.0, 1.0, 360.0, step = 1.0, hint = "Counted from now: 1 is next month's EMI"),
)

/** The month the loan ends, if the next EMI is next month. */
private fun endMonth(months: Int): String =
    YearMonth.now().plusMonths(months.toLong()).format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoanScreen(type: LoanType, onBack: () -> Unit) {
    val fields = loanFields(type)
    var text by rememberSaveable(type.id) {
        mutableStateOf((fields + partFields).associate { it.key to plain(it.default) })
    }
    var every by rememberSaveable(type.id) { mutableStateOf(Every.ONCE) }
    var lowerEmi by rememberSaveable(type.id) { mutableStateOf(false) }

    val v = (fields + partFields).associate { it.key to parse(text[it.key]) }
    val errors = (fields + partFields).associate { it.key to it.error(v[it.key]) }.toMutableMap()
    val months = v["years"]?.let { (it * 12).toInt() }
    val paid = v["paid"]?.toInt()
    if (errors["paid"] == null && months != null && paid != null && paid >= months) errors["paid"] = "Fewer than the loan's ${months} EMIs"
    if (errors["fee"] == null && v["amount"] != null && v["fee"]!! >= v["amount"]!!) errors["fee"] = "Less than the loan amount"
    if (errors["start"] == null && months != null && paid != null && v["start"]!! > months - paid) {
        errors["start"] = "Only ${months - paid} EMIs are left"
    }
    val loanOk = fields.all { errors[it.key] == null }
    val result = if (loanOk && partFields.all { errors[it.key] == null }) {
        loan(
            v["amount"]!!, v["rate"]!!, months!!, paid!!, v["fee"]!!,
            PartPayment(v["part"]!!, v["start"]!!.toInt(), every), lowerEmi,
        )
    } else if (loanOk) {
        loan(v["amount"]!!, v["rate"]!!, months!!, paid!!, v["fee"]!!, null, false)
    } else {
        null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(type.name) },
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
            items(fields, key = { it.key }) { f ->
                NumberField(f, text[f.key].orEmpty(), errors[f.key]) { s -> text = text + (f.key to s) }
            }
            if (result != null) item { EmiCard(result, months!!, paid!!) }

            item { Text("Part payment", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
            items(partFields, key = { it.key }) { f ->
                NumberField(f, text[f.key].orEmpty(), errors[f.key]) { s -> text = text + (f.key to s) }
            }
            item {
                Choice(Every.entries.map { it.label }, every.ordinal) { every = Every.entries[it] }
            }
            item {
                Choice(listOf("Shorter loan", "Lower EMI"), if (lowerEmi) 1 else 0) { lowerEmi = it == 1 }
            }
            val with = result?.withPartPayments
            if (result != null) {
                item {
                    if (with != null) SavedCard(result.asIs, with, lowerEmi)
                    else Text("Enter a part payment to see how many months and how much interest it saves.")
                }
                item { Text(type.notes, style = MaterialTheme.typography.bodyMedium) }
                item { Schedule(with ?: result.asIs) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Choice(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        labels.forEachIndexed { i, label ->
            SegmentedButton(
                selected = i == selected,
                onClick = { onSelect(i) },
                shape = SegmentedButtonDefaults.itemShape(i, labels.size),
            ) { Text(label) }
        }
    }
}

@Composable
private fun EmiCard(r: LoanResult, months: Int, paid: Int) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("EMI", style = MaterialTheme.typography.labelLarge)
            Text(rupees(r.emi), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("a month for ${term(months)}")
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            val amount = r.emi * months - r.totalInterest
            Line("Loan amount", rupees(amount))
            Line("Total interest", rupees(r.totalInterest))
            if (r.fee > 0) Line("Processing fee", rupees(r.fee))
            Line("Total you pay", rupees(amount + r.totalInterest + r.fee))
            r.effectiveRate?.let { Line("Real rate with the fee", String.format(Locale.ROOT, "%.2f%%", it)) }
            if (paid > 0) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Line("Still owed today", rupees(r.outstanding))
                Line("EMIs left", "${r.asIs.months} (${term(r.asIs.months)})")
                Line("Interest still to pay", rupees(r.asIs.interest))
            }
        }
    }
}

@Composable
private fun SavedCard(asIs: Payoff, with: Payoff, lowerEmi: Boolean) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val saved = asIs.interest - with.interest
            if (lowerEmi) {
                Text("Your EMI drops to", style = MaterialTheme.typography.labelLarge)
                Text(rupees(with.lastEmi), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("from ${rupees(with.firstEmi)}, ending ${endMonth(with.months)} as before")
            } else {
                val sooner = asIs.months - with.months
                Text("The loan ends", style = MaterialTheme.typography.labelLarge)
                Text(
                    if (sooner > 0) "${term(sooner)} sooner" else "at the same time",
                    style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                )
                Text("in ${endMonth(with.months)} instead of ${endMonth(asIs.months)}: ${with.months} EMIs instead of ${asIs.months}")
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Line("Interest saved", rupees(saved))
            Line("Part payments in all", rupees(with.partPaid))
        }
    }
}

@Composable
private fun Schedule(p: Payoff) {
    Column {
        Text("Year by year, from now", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
        TableRow("", "Principal", "Interest", "Balance", FontWeight.Medium)
        HorizontalDivider()
        for (row in p.rows) {
            TableRow(row.label, rupees(row.principal + row.partPaid), rupees(row.interest), rupees(row.balance))
        }
        if (p.partPaid > 0) {
            Text(
                "Principal includes the part payments.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
