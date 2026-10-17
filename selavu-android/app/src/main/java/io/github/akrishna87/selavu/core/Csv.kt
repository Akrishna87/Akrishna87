package io.github.akrishna87.selavu.core

import java.time.LocalDate

/**
 * Spreadsheet export and import. The columns are Date, Type, Category, Amount, Note, which opens
 * cleanly in Excel or Google Sheets and can be read back in on another phone.
 */
object Csv {
    const val HEADER = "Date,Type,Category,Amount,Note"

    fun export(ledger: Ledger): String = buildString {
        append(HEADER).append("\r\n")
        ledger.transactions.sortedBy { it.date }.forEach { t ->
            val cat = ledger.category(t.categoryId)?.name ?: ""
            append(t.date).append(',')
            append(if (t.type == TxType.INCOME) "Income" else "Expense").append(',')
            append(quote(cat)).append(',')
            append(Money.plain(t.amount)).append(',')
            append(quote(t.note)).append("\r\n")
        }
    }

    data class Imported(val ledger: Ledger, val added: Int, val skipped: Int, val failed: Int)

    /**
     * Adds the rows of a CSV in the format [export] writes. Categories are matched by name and
     * created when missing; rows already in the ledger (same date, type, category, amount and
     * note) are skipped, so importing the same file twice adds nothing.
     */
    fun import(ledger: Ledger, text: String, id: () -> String = ::newId): Imported {
        val rows = parse(text.removePrefix("﻿"))
        if (rows.isEmpty()) return Imported(ledger, 0, 0, 0)
        val header = rows.first().map { it.trim().lowercase() }
        fun col(name: String, fallback: Int) = header.indexOf(name).takeIf { it >= 0 } ?: fallback
        val hasHeader = "date" in header
        val iDate = if (hasHeader) col("date", 0) else 0
        val iType = if (hasHeader) col("type", 1) else 1
        val iCat = if (hasHeader) col("category", 2) else 2
        val iAmount = if (hasHeader) col("amount", 3) else 3
        val iNote = if (hasHeader) col("note", 4) else 4

        var categories = ledger.categories
        val seen = ledger.transactions.map { key(it, ledger.category(it.categoryId)?.name ?: "") }.toMutableSet()
        val added = mutableListOf<Transaction>()
        var skipped = 0
        var failed = 0
        for (row in if (hasHeader) rows.drop(1) else rows) {
            if (row.all { it.isBlank() }) continue
            val date = runCatching { LocalDate.parse(row.getOrNull(iDate)?.trim()) }.getOrNull()
            val rawAmount = row.getOrNull(iAmount)?.trim().orEmpty()
            val amount = Money.parse(rawAmount)
            if (date == null || amount == null) { failed++; continue }
            val type = when {
                row.getOrNull(iType)?.trim()?.lowercase()?.startsWith("inc") == true -> TxType.INCOME
                rawAmount.startsWith("+") -> TxType.INCOME
                else -> TxType.EXPENSE
            }
            val catName = row.getOrNull(iCat)?.trim().orEmpty().ifEmpty { "Other" }
            val note = row.getOrNull(iNote)?.trim().orEmpty()
            val cat = categories.firstOrNull { it.type == type && it.name.equals(catName, ignoreCase = true) }
                ?: Category(id(), catName, "🏷️", type, CategoryColors.all[categories.size % CategoryColors.all.size])
                    .also { categories = categories + it }
            val tx = Transaction(id(), type, amount, cat.id, date, note)
            if (!seen.add(key(tx, cat.name))) { skipped++; continue }
            added += tx
        }
        return Imported(ledger.copy(categories = categories, transactions = ledger.transactions + added), added.size, skipped, failed)
    }

    private fun key(t: Transaction, categoryName: String) =
        listOf(t.date.toString(), t.type.name, categoryName.lowercase(), t.amount.toString(), t.note.trim()).joinToString("\u0000")

    private fun quote(s: String): String =
        if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' } || s.startsWith(" ") || s.endsWith(" "))
            "\"" + s.replace("\"", "\"\"") + "\""
        else s

    /** RFC 4180: quoted fields may hold commas, doubled quotes and line breaks. */
    fun parse(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') { field.append('"'); i++ } else quoted = false
                } else field.append(c)
            } else when (c) {
                '"' -> quoted = true
                ',' -> { row.add(field.toString()); field.clear() }
                '\r' -> {}
                '\n' -> { row.add(field.toString()); field.clear(); rows.add(row); row = mutableListOf() }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) { row.add(field.toString()); rows.add(row) }
        return rows
    }
}
