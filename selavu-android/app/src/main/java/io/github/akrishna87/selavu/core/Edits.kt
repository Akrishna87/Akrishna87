package io.github.akrishna87.selavu.core

import java.time.LocalDate
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

/** Most occurrences a recurring item logs in one go, so a years-old daily item can't freeze the app. */
const val MAX_CATCH_UP = 400

/**
 * Logs every occurrence of every recurring item that has come due by [today].
 */
fun Ledger.logDueRecurring(today: LocalDate, id: () -> String = ::newId): Ledger {
    if (recurring.none { !it.nextDate.isAfter(today) }) return this
    val added = mutableListOf<Transaction>()
    val updated = recurring.map { r ->
        var n = r.logged
        var count = 0
        while (count < MAX_CATCH_UP && !r.occurrence(n).isAfter(today)) {
            added += Transaction(id(), r.type, r.amount, r.categoryId, r.occurrence(n), r.note, r.id)
            n++
            count++
        }
        r.copy(logged = n)
    }
    return copy(transactions = transactions + added, recurring = updated)
}

/**
 * Saves a new or edited transaction. For a new one with a [repeat], a recurring item is set up
 * starting on its date, and its first occurrence is logged now (if that date has come).
 */
fun Ledger.save(tx: Transaction, repeat: Repeat, today: LocalDate, id: () -> String = ::newId): Ledger {
    val exists = transactions.any { it.id == tx.id }
    if (exists) return copy(transactions = transactions.map { if (it.id == tx.id) tx else it })
    if (repeat == Repeat.NONE) return copy(transactions = transactions + tx)
    val r = Recurring(id(), tx.type, tx.amount, tx.categoryId, tx.note, repeat, tx.date)
    return if (tx.date.isAfter(today)) copy(recurring = recurring + r)
    else copy(
        transactions = transactions + tx.copy(recurringId = r.id),
        recurring = recurring + r.copy(logged = 1),
    ).logDueRecurring(today, id)
}

fun Ledger.delete(txId: String): Ledger = copy(transactions = transactions.filterNot { it.id == txId })

/** Stops a recurring item. What it already logged stays in the history. */
fun Ledger.stopRecurring(recurringId: String): Ledger = copy(recurring = recurring.filterNot { it.id == recurringId })

fun Ledger.saveCategory(category: Category): Ledger =
    if (categories.any { it.id == category.id }) copy(categories = categories.map { if (it.id == category.id) category else it })
    else copy(categories = categories + category)

/**
 * Removes a category, moving its transactions (and recurring items) to [moveTo].
 * The last category of a type can't be removed.
 */
fun Ledger.deleteCategory(categoryId: String, moveTo: String): Ledger {
    val cat = category(categoryId) ?: return this
    if (categories(cat.type).size <= 1 || moveTo == categoryId) return this
    return copy(
        categories = categories.filterNot { it.id == categoryId },
        transactions = transactions.map { if (it.categoryId == categoryId) it.copy(categoryId = moveTo) else it },
        recurring = recurring.map { if (it.categoryId == categoryId) it.copy(categoryId = moveTo) else it },
    )
}

fun Ledger.usage(categoryId: String): Int = transactions.count { it.categoryId == categoryId }
