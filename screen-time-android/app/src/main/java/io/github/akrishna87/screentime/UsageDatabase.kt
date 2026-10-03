package io.github.akrishna87.screentime

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** How long one app was in the foreground on one day. `day` is a local date's epoch day. */
@Entity(tableName = "app_day", primaryKeys = ["day", "packageName"])
data class AppDay(val day: Long, val packageName: String, val foregroundMs: Long, val opens: Int)

/** Screen time across all apps in one hour of one day (0–23), for the Day chart. */
@Entity(tableName = "hour_total", primaryKeys = ["day", "hour"])
data class HourTotal(val day: Long, val hour: Int, val foregroundMs: Long)

/** An app's name, kept so apps that were uninstalled later still show up by name. */
@Entity(tableName = "app_label")
data class AppLabel(@PrimaryKey val packageName: String, val label: String)

data class AppTotal(val packageName: String, val label: String?, val foregroundMs: Long, val opens: Int)

data class DayTotal(val day: Long, val foregroundMs: Long)

@Dao
abstract class UsageDao {
    @Query(
        """
        SELECT a.packageName, l.label, SUM(a.foregroundMs) AS foregroundMs, SUM(a.opens) AS opens
        FROM app_day a LEFT JOIN app_label l ON l.packageName = a.packageName
        WHERE a.day BETWEEN :from AND :to
        GROUP BY a.packageName
        ORDER BY foregroundMs DESC
        """
    )
    abstract fun appTotals(from: Long, to: Long): Flow<List<AppTotal>>

    @Query("SELECT day, SUM(foregroundMs) AS foregroundMs FROM app_day WHERE day BETWEEN :from AND :to GROUP BY day")
    abstract fun dayTotals(from: Long, to: Long): Flow<List<DayTotal>>

    @Query("SELECT day, foregroundMs FROM app_day WHERE packageName = :packageName AND day BETWEEN :from AND :to")
    abstract fun appDays(packageName: String, from: Long, to: Long): Flow<List<DayTotal>>

    @Query("SELECT * FROM hour_total WHERE day = :day")
    abstract fun hours(day: Long): Flow<List<HourTotal>>

    @Query("SELECT MIN(day) FROM app_day")
    abstract fun firstDay(): Flow<Long?>

    @Query("SELECT COALESCE(SUM(foregroundMs), 0) FROM app_day WHERE day = :day")
    abstract suspend fun dayTotal(day: Long): Long

    @Query("DELETE FROM app_day WHERE day = :day")
    abstract suspend fun clearAppDays(day: Long)

    @Query("DELETE FROM hour_total WHERE day = :day")
    abstract suspend fun clearHours(day: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAppDays(rows: List<AppDay>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertHours(rows: List<HourTotal>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun saveLabels(rows: List<AppLabel>)

    @Transaction
    open suspend fun replaceDay(day: Long, apps: List<AppDay>, hours: List<HourTotal>) {
        clearAppDays(day)
        clearHours(day)
        insertAppDays(apps)
        insertHours(hours)
    }
}

@Database(entities = [AppDay::class, HourTotal::class, AppLabel::class], version = 1, exportSchema = false)
abstract class UsageDatabase : RoomDatabase() {
    abstract fun usage(): UsageDao

    companion object {
        @Volatile
        private var instance: UsageDatabase? = null

        fun get(context: Context): UsageDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, UsageDatabase::class.java, "usage.db")
                .build()
                .also { instance = it }
        }
    }
}
