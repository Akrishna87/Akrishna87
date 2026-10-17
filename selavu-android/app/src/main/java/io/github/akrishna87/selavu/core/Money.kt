package io.github.akrishna87.selavu.core

import kotlin.math.abs

object Money {
    /**
     * Reads what was typed into an amount box ("250", "1,200.5", "₹ 99.99") as paise/cents.
     * Returns null for anything that isn't a positive amount with at most two decimals.
     */
    fun parse(text: String): Long? {
        val cleaned = text.filter { it.isDigit() || it == '.' }
        if (cleaned.isEmpty() || cleaned.count { it == '.' } > 1) return null
        val whole = cleaned.substringBefore('.')
        val fraction = cleaned.substringAfter('.', "")
        if (fraction.length > 2 || whole.length > 13) return null
        val units = (whole.ifEmpty { "0" }).toLong() * 100 + fraction.padEnd(2, '0').ifEmpty { "0" }.toLong()
        return units.takeIf { it > 0 }
    }

    /** The amount as it would be typed back into the amount box, e.g. "250" or "99.5". */
    fun plain(amount: Long): String {
        val whole = amount / 100
        val fraction = amount % 100
        return when {
            fraction == 0L -> "$whole"
            fraction % 10 == 0L -> "$whole.${fraction / 10}"
            else -> "$whole.${fraction.toString().padStart(2, '0')}"
        }
    }

    /**
     * "₹1,25,000" (Indian grouping for rupees), "$125,000.50" for other currencies.
     * Paise are shown only when there are some.
     */
    fun format(amount: Long, currency: String, sign: Boolean = false): String {
        val a = abs(amount)
        val whole = group((a / 100).toString(), indian = currency == "₹")
        val fraction = a % 100
        val number = if (fraction == 0L) whole else "$whole.${fraction.toString().padStart(2, '0')}"
        val prefix = when {
            amount < 0 -> "−"
            sign && amount > 0 -> "+"
            else -> ""
        }
        return "$prefix$currency$number"
    }

    /** Short form for chart axes: "₹1.2L", "₹45k", "$3.4M". */
    fun compact(amount: Long, currency: String): String {
        val whole = abs(amount) / 100.0
        fun one(v: Double) = if (v >= 10) "${v.toLong()}" else String.format(java.util.Locale.US, "%.1f", v).removeSuffix(".0")
        val body = if (currency == "₹") when {
            whole >= 1_00_00_000 -> one(whole / 1_00_00_000) + "Cr"
            whole >= 1_00_000 -> one(whole / 1_00_000) + "L"
            whole >= 1_000 -> one(whole / 1_000) + "k"
            else -> "${whole.toLong()}"
        } else when {
            whole >= 1_000_000 -> one(whole / 1_000_000) + "M"
            whole >= 1_000 -> one(whole / 1_000) + "k"
            else -> "${whole.toLong()}"
        }
        return (if (amount < 0) "−" else "") + currency + body
    }

    private fun group(digits: String, indian: Boolean): String {
        if (digits.length <= 3) return digits
        val last3 = digits.takeLast(3)
        val rest = digits.dropLast(3)
        val size = if (indian) 2 else 3
        val groups = rest.reversed().chunked(size).map { it.reversed() }.reversed()
        return groups.joinToString(",") + "," + last3
    }
}
