package io.github.akrishna87.selavu.core

import java.time.LocalDate

enum class TxType { EXPENSE, INCOME }

data class Category(
    val id: String,
    val name: String,
    val emoji: String,
    val type: TxType,
    /** ARGB colour, used for the chart slice and the icon's background. */
    val color: Long,
)

/** One expense or income. [amount] is in the currency's smallest unit (paise, cents). */
data class Transaction(
    val id: String,
    val type: TxType,
    val amount: Long,
    val categoryId: String,
    val date: LocalDate,
    val note: String = "",
    /** Set when this was logged by a [Recurring] item. */
    val recurringId: String? = null,
)

enum class Repeat(val label: String) {
    NONE("Never"),
    DAILY("Every day"),
    WEEKLY("Every week"),
    MONTHLY("Every month"),
    YEARLY("Every year"),
}

/**
 * A transaction that logs itself on a schedule. Occurrence n falls on [start] plus n periods,
 * worked out from [start] every time so a monthly item on the 31st lands on the last day of
 * short months without drifting to the 28th for good.
 */
data class Recurring(
    val id: String,
    val type: TxType,
    val amount: Long,
    val categoryId: String,
    val note: String,
    val repeat: Repeat,
    val start: LocalDate,
    /** How many occurrences have been logged so far. */
    val logged: Int = 0,
) {
    fun occurrence(n: Int): LocalDate = when (repeat) {
        Repeat.NONE -> start
        Repeat.DAILY -> start.plusDays(n.toLong())
        Repeat.WEEKLY -> start.plusWeeks(n.toLong())
        Repeat.MONTHLY -> start.plusMonths(n.toLong())
        Repeat.YEARLY -> start.plusYears(n.toLong())
    }

    val nextDate: LocalDate get() = occurrence(logged)
}

enum class BudgetPeriod { WEEKLY, MONTHLY }

data class Settings(
    val currency: String = "₹",
    /** 0 means no budget. */
    val budget: Long = 0,
    val budgetPeriod: BudgetPeriod = BudgetPeriod.MONTHLY,
    val weekStartsMonday: Boolean = true,
)

data class Ledger(
    val categories: List<Category> = DefaultCategories.all,
    val transactions: List<Transaction> = emptyList(),
    val recurring: List<Recurring> = emptyList(),
    val settings: Settings = Settings(),
) {
    fun category(id: String): Category? = categories.firstOrNull { it.id == id }
    fun categories(type: TxType): List<Category> = categories.filter { it.type == type }
}

object CategoryColors {
    val all = listOf(
        0xFFE5534BL, 0xFFF0883EL, 0xFFE3B341L, 0xFF57AB5AL, 0xFF39C5BBL, 0xFF4493F8L,
        0xFF8E6BFFL, 0xFFDB61A2L, 0xFFA0785AL, 0xFF768390L,
    )
}

object DefaultCategories {
    private fun c(id: String, name: String, emoji: String, type: TxType, color: Long) =
        Category(id, name, emoji, type, color)

    val all = listOf(
        c("food", "Food & dining", "🍔", TxType.EXPENSE, 0xFFF0883E),
        c("groceries", "Groceries", "🛒", TxType.EXPENSE, 0xFF57AB5A),
        c("transport", "Transport", "🚌", TxType.EXPENSE, 0xFF4493F8),
        c("fuel", "Fuel", "⛽", TxType.EXPENSE, 0xFF768390),
        c("shopping", "Shopping", "🛍️", TxType.EXPENSE, 0xFFDB61A2),
        c("bills", "Bills & utilities", "💡", TxType.EXPENSE, 0xFFE3B341),
        c("rent", "Rent & home", "🏠", TxType.EXPENSE, 0xFFA0785A),
        c("health", "Health", "💊", TxType.EXPENSE, 0xFFE5534B),
        c("fun", "Entertainment", "🎬", TxType.EXPENSE, 0xFF8E6BFF),
        c("education", "Education", "📚", TxType.EXPENSE, 0xFF39C5BB),
        c("travel", "Travel", "✈️", TxType.EXPENSE, 0xFF4493F8),
        c("gifts", "Gifts", "🎁", TxType.EXPENSE, 0xFFDB61A2),
        c("personal", "Personal care", "💇", TxType.EXPENSE, 0xFF8E6BFF),
        c("other", "Other", "📦", TxType.EXPENSE, 0xFF768390),
        c("salary", "Salary", "💼", TxType.INCOME, 0xFF57AB5A),
        c("business", "Business", "🏪", TxType.INCOME, 0xFF39C5BB),
        c("interest", "Interest", "🏦", TxType.INCOME, 0xFF4493F8),
        c("gift-in", "Gifts received", "🎁", TxType.INCOME, 0xFFDB61A2),
        c("other-in", "Other income", "💰", TxType.INCOME, 0xFFE3B341),
    )
}
