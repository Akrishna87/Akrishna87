package io.github.akrishna87.selavu

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.akrishna87.selavu.core.Category
import io.github.akrishna87.selavu.core.Csv
import io.github.akrishna87.selavu.core.Ledger
import io.github.akrishna87.selavu.core.Period
import io.github.akrishna87.selavu.core.PeriodType
import io.github.akrishna87.selavu.core.Repeat
import io.github.akrishna87.selavu.core.Settings
import io.github.akrishna87.selavu.core.Transaction
import io.github.akrishna87.selavu.core.TxType
import io.github.akrishna87.selavu.core.delete
import io.github.akrishna87.selavu.core.deleteCategory
import io.github.akrishna87.selavu.core.logDueRecurring
import io.github.akrishna87.selavu.core.save
import io.github.akrishna87.selavu.core.saveCategory
import io.github.akrishna87.selavu.core.stopRecurring
import io.github.akrishna87.selavu.core.weekStart
import io.github.akrishna87.selavu.data.Store
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** What fills the screen above the bottom bar. */
sealed interface Screen {
    data object Home : Screen
    data object Reports : Screen
    data object Settings : Screen
    data class Edit(val type: TxType, val txId: String? = null) : Screen
    data class CategoryDetail(val categoryId: String) : Screen
    data object Categories : Screen
    data object Repeating : Screen
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = Store(app)
    private val saveLock = Mutex()

    private val _ledger = MutableStateFlow<Ledger?>(null)
    /** Null until the file has been read. */
    val ledger: StateFlow<Ledger?> = _ledger

    var stack by mutableStateOf(listOf<Screen>(Screen.Home))
        private set
    val screen: Screen get() = stack.last()

    var periodType by mutableStateOf(PeriodType.MONTH)
        private set
    var anchor by mutableStateOf(LocalDate.now())
        private set

    var message by mutableStateOf<String?>(null)

    init {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) { store.load() }
            _ledger.value = loaded
            refreshRecurring()
        }
    }

    fun period(): Period = Period.of(periodType, anchor, (_ledger.value?.settings ?: Settings()).weekStart())

    fun choosePeriodType(type: PeriodType) { periodType = type }
    fun shiftPeriod(n: Long) { anchor = period().shift(n).start }
    fun backToToday() { anchor = LocalDate.now() }

    /** New entries are dated today, or the day being looked at when that's another single day. */
    fun defaultDate(): LocalDate {
        val p = period()
        return if (p.type == PeriodType.DAY) p.start else LocalDate.now()
    }

    // --- Navigation

    fun open(s: Screen) {
        stack = if (s == Screen.Home || s == Screen.Reports || s == Screen.Settings) listOf(s) else stack + s
    }

    /** Returns false when there's nowhere to go back to, so the system can close the app. */
    fun back(): Boolean {
        if (stack.size > 1) { stack = stack.dropLast(1); return true }
        if (screen != Screen.Home) { stack = listOf(Screen.Home); return true }
        return false
    }

    // --- Changes

    private fun update(change: (Ledger) -> Ledger) {
        val current = _ledger.value ?: return
        val next = change(current)
        if (next == current) return
        _ledger.value = next
        // Each save writes the newest ledger, so saves that finish out of order can't lose a change.
        viewModelScope.launch(Dispatchers.IO) { saveLock.withLock { store.save(_ledger.value ?: next) } }
    }

    /** Logs recurring items that have come due; called on start and whenever the app is shown. */
    fun refreshRecurring() = update { it.logDueRecurring(LocalDate.now()) }

    fun saveTransaction(tx: Transaction, repeat: Repeat) = update { it.save(tx, repeat, LocalDate.now()) }
    fun deleteTransaction(id: String) = update { it.delete(id) }
    fun stopRecurring(id: String) = update { it.stopRecurring(id) }
    fun saveCategory(c: Category) = update { it.saveCategory(c) }
    fun deleteCategory(id: String, moveTo: String) = update { it.deleteCategory(id, moveTo) }
    fun updateSettings(change: (Settings) -> Settings) = update { it.copy(settings = change(it.settings)) }

    fun exportCsv(uri: Uri) {
        val l = _ledger.value ?: return
        viewModelScope.launch {
            message = try {
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openOutputStream(uri, "wt")!!.use {
                        it.write(Csv.export(l).toByteArray())
                    }
                }
                "Saved ${l.transactions.size} entries"
            } catch (e: Exception) {
                "Couldn't save the file"
            }
        }
    }

    fun importCsv(uri: Uri) {
        viewModelScope.launch {
            val text = try {
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
                }
            } catch (e: Exception) {
                message = "Couldn't open the file"
                return@launch
            }
            val current = _ledger.value ?: return@launch
            val result = Csv.import(current, text)
            update { result.ledger }
            message = buildString {
                append("Added ${result.added} ${if (result.added == 1) "entry" else "entries"}")
                if (result.skipped > 0) append(", ${result.skipped} already here")
                if (result.failed > 0) append(", ${result.failed} rows not understood")
            }
        }
    }
}
