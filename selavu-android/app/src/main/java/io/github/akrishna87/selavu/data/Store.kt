package io.github.akrishna87.selavu.data

import android.content.Context
import android.util.Log
import io.github.akrishna87.selavu.core.BudgetPeriod
import io.github.akrishna87.selavu.core.Category
import io.github.akrishna87.selavu.core.Ledger
import io.github.akrishna87.selavu.core.Recurring
import io.github.akrishna87.selavu.core.Repeat
import io.github.akrishna87.selavu.core.Settings
import io.github.akrishna87.selavu.core.Transaction
import io.github.akrishna87.selavu.core.TxType
import java.io.File
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject

/**
 * Keeps the whole ledger in one JSON file in the app's private storage. Writes go to a
 * temporary file that then replaces the real one, so a crash mid-save can't corrupt it.
 * The previous version is kept as a backup and used if the main file can't be read.
 */
class Store(context: Context) {
    private val file = File(context.filesDir, "ledger.json")
    private val backup = File(context.filesDir, "ledger.backup.json")

    fun load(): Ledger {
        for (f in listOf(file, backup)) {
            if (!f.exists()) continue
            try {
                return decode(JSONObject(f.readText()))
            } catch (e: Exception) {
                Log.w("Selavu", "Couldn't read ${f.name}", e)
            }
        }
        return Ledger()
    }

    @Synchronized
    fun save(ledger: Ledger) {
        val tmp = File(file.parentFile, "ledger.json.tmp")
        tmp.writeText(encode(ledger).toString())
        if (file.exists()) file.copyTo(backup, overwrite = true)
        if (!tmp.renameTo(file)) {
            tmp.copyTo(file, overwrite = true)
            tmp.delete()
        }
    }

    companion object {
        private const val VERSION = 1

        fun encode(l: Ledger): JSONObject = JSONObject().apply {
            put("version", VERSION)
            put("settings", JSONObject().apply {
                put("currency", l.settings.currency)
                put("budget", l.settings.budget)
                put("budgetPeriod", l.settings.budgetPeriod.name)
                put("weekStartsMonday", l.settings.weekStartsMonday)
            })
            put("categories", JSONArray().apply {
                l.categories.forEach { c ->
                    put(JSONObject().apply {
                        put("id", c.id); put("name", c.name); put("emoji", c.emoji)
                        put("type", c.type.name); put("color", c.color)
                    })
                }
            })
            put("transactions", JSONArray().apply {
                l.transactions.forEach { t ->
                    put(JSONObject().apply {
                        put("id", t.id); put("type", t.type.name); put("amount", t.amount)
                        put("category", t.categoryId); put("date", t.date.toString())
                        if (t.note.isNotEmpty()) put("note", t.note)
                        t.recurringId?.let { put("recurring", it) }
                    })
                }
            })
            put("recurring", JSONArray().apply {
                l.recurring.forEach { r ->
                    put(JSONObject().apply {
                        put("id", r.id); put("type", r.type.name); put("amount", r.amount)
                        put("category", r.categoryId); put("note", r.note); put("repeat", r.repeat.name)
                        put("start", r.start.toString()); put("logged", r.logged)
                    })
                }
            })
        }

        fun decode(o: JSONObject): Ledger {
            val s = o.optJSONObject("settings")
            val settings = if (s == null) Settings() else Settings(
                currency = s.optString("currency", "₹"),
                budget = s.optLong("budget", 0),
                budgetPeriod = enumOr(s.optString("budgetPeriod"), BudgetPeriod.MONTHLY),
                weekStartsMonday = s.optBoolean("weekStartsMonday", true),
            )
            val categories = o.optJSONArray("categories").objects().map { c ->
                Category(
                    c.getString("id"), c.getString("name"), c.optString("emoji", "🏷️"),
                    enumOr(c.optString("type"), TxType.EXPENSE), c.optLong("color", 0xFF768390),
                )
            }
            val transactions = o.optJSONArray("transactions").objects().map { t ->
                Transaction(
                    t.getString("id"), enumOr(t.optString("type"), TxType.EXPENSE), t.getLong("amount"),
                    t.getString("category"), LocalDate.parse(t.getString("date")), t.optString("note", ""),
                    if (t.has("recurring")) t.getString("recurring") else null,
                )
            }
            val recurring = o.optJSONArray("recurring").objects().map { r ->
                Recurring(
                    r.getString("id"), enumOr(r.optString("type"), TxType.EXPENSE), r.getLong("amount"),
                    r.getString("category"), r.optString("note", ""), enumOr(r.optString("repeat"), Repeat.MONTHLY),
                    LocalDate.parse(r.getString("start")), r.optInt("logged", 0),
                )
            }
            return Ledger(categories.ifEmpty { Ledger().categories }, transactions, recurring, settings)
        }

        private fun JSONArray?.objects(): List<JSONObject> =
            if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }

        private inline fun <reified E : Enum<E>> enumOr(name: String?, fallback: E): E =
            enumValues<E>().firstOrNull { it.name == name } ?: fallback
    }
}
