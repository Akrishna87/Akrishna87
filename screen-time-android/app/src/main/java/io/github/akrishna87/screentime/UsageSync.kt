package io.github.akrishna87.screentime

import android.content.Context
import android.content.pm.PackageManager
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Copies usage from Android into the app's own database. Android only keeps detailed usage for
 * about a week, so saving it every few hours is what makes long weeks and months possible.
 */
object UsageSync {
    /** How far back the first run reads. Android usually still has about this much. */
    private const val FIRST_RUN_DAYS = 10L
    private const val KEY_LAST_DAY = "lastSyncedDay"
    private val lock = Mutex()

    suspend fun sync(context: Context): Unit = withContext(Dispatchers.IO) {
        if (!UsageAccess.granted(context)) return@withContext
        lock.withLock {
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val prefs = context.getSharedPreferences("sync", Context.MODE_PRIVATE)
            val earliest = today.minusDays(FIRST_RUN_DAYS)
            val last = prefs.getLong(KEY_LAST_DAY, Long.MIN_VALUE)
            // Days before the last sync are final; the last synced day may not have been over yet.
            val from = if (last == Long.MIN_VALUE) earliest else maxOf(LocalDate.ofEpochDay(last), earliest)

            val usage = try {
                UsageReader(context, zone).read(from)
            } catch (e: SecurityException) {
                return@withLock // usage access was turned off just now
            }

            val dao = UsageDatabase.get(context).usage()
            var date = from
            while (!date.isAfter(today)) {
                val day = date.toEpochDay()
                val found = usage[day]
                // Never replace a day with less than was saved: Android may already have dropped
                // the oldest events of a day at the edge of its history.
                if (found != null && found.total >= dao.dayTotal(day)) {
                    dao.replaceDay(
                        day,
                        found.foregroundMs.map { (pkg, ms) -> AppDay(day, pkg, ms, found.opens[pkg] ?: 0) },
                        found.hourMs.withIndex().filter { it.value > 0 }.map { HourTotal(day, it.index, it.value) },
                    )
                }
                date = date.plusDays(1)
            }

            val packages = usage.values.flatMapTo(HashSet()) { it.foregroundMs.keys }
            dao.saveLabels(packages.mapNotNull { pkg -> label(context, pkg)?.let { AppLabel(pkg, it) } })
            prefs.edit().putLong(KEY_LAST_DAY, today.toEpochDay()).apply()
        }
    }

    private fun label(context: Context, pkg: String): String? = try {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }
}

/** Runs [UsageSync] every few hours, even when the app isn't opened for a while. */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        UsageSync.sync(applicationContext)
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork("usage-sync", ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
