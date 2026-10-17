@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.akrishna87.selavu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.akrishna87.selavu.core.Category
import io.github.akrishna87.selavu.core.Money
import io.github.akrishna87.selavu.core.Period
import io.github.akrishna87.selavu.core.PeriodType
import io.github.akrishna87.selavu.core.Transaction
import io.github.akrishna87.selavu.core.TxType
import java.time.LocalDate

/** Day / Week / Month / Year, and ‹ the period › underneath. Tapping the label goes back to today. */
@Composable
fun PeriodPicker(
    type: PeriodType,
    period: Period,
    onType: (PeriodType) -> Unit,
    onShift: (Long) -> Unit,
    onToday: () -> Unit,
) {
    val today = LocalDate.now()
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            PeriodType.entries.forEachIndexed { i, t ->
                SegmentedButton(
                    selected = t == type,
                    onClick = { onType(t) },
                    shape = SegmentedButtonDefaults.itemShape(i, PeriodType.entries.size),
                    icon = {},
                ) { Text(t.label) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onShift(-1) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous")
            }
            Column(
                Modifier.weight(1f).clip(MaterialTheme.shapes.medium).clickable(onClick = onToday).padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(period.label(today), style = MaterialTheme.typography.titleMedium)
                period.subLabel(today)?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = { onShift(1) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next")
            }
        }
    }
}

@Composable
fun CategoryIcon(category: Category?, size: Dp = 40.dp) {
    val color = Color(category?.color ?: 0xFF768390)
    Box(
        Modifier.size(size).clip(CircleShape).background(color.copy(alpha = 0.22f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(category?.emoji ?: "❔", fontSize = (size.value * 0.5f).sp, textAlign = TextAlign.Center)
    }
}

@Composable
fun amountColor(type: TxType): Color =
    if (type == TxType.EXPENSE) LocalMoneyColors.current.expense else LocalMoneyColors.current.income

/** "−₹250" for spending, "+₹250" for income. */
fun signed(t: Transaction, currency: String) =
    Money.format(if (t.type == TxType.EXPENSE) -t.amount else t.amount, currency, sign = true)

@Composable
fun TransactionRow(t: Transaction, category: Category?, currency: String, subtitle: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryIcon(category)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(category?.name ?: "Deleted category", style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val second = listOfNotNull(subtitle, t.note.takeIf { it.isNotBlank() }).joinToString(" · ")
            if (second.isNotEmpty()) {
                Text(
                    second, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (t.recurringId != null) {
            Icon(
                Icons.Default.Repeat, contentDescription = "Repeats", Modifier.size(16.dp).padding(end = 2.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(signed(t, currency), color = amountColor(t.type), fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun SectionHeader(title: String, trailing: String? = null) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        trailing?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
fun EmptyState(emoji: String, title: String, body: String) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(emoji, fontSize = 44.sp)
        Spacer(Modifier.size(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}
