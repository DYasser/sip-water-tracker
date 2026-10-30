package app.sip.water.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

private class WaterDb(context: Context) : SQLiteOpenHelper(context, "sip.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE entries (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "ts INTEGER NOT NULL, " +
                "day INTEGER NOT NULL, " +
                "amount_ml INTEGER NOT NULL)",
        )
        db.execSQL("CREATE INDEX idx_entries_day ON entries(day)")
        db.execSQL("CREATE TABLE goals (day INTEGER PRIMARY KEY, goal_ml INTEGER NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
}

/**
 * The drink log. Calls are synchronous and cheap (a few thousand rows at most),
 * so callers run them on a background thread.
 *
 * [version] ticks after every write, which is how the UI knows to reload; the
 * change listeners are how the widget and the reminder schedule stay in step.
 */
class WaterRepository(context: Context) {
    private val db = WaterDb(context.applicationContext)
    private val _version = MutableStateFlow(0L)
    val version: StateFlow<Long> = _version.asStateFlow()
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    fun addListener(listener: () -> Unit) {
        listeners += listener
    }

    private fun changed() {
        _version.update { it + 1 }
        listeners.forEach { it() }
    }

    fun add(amountMl: Int, at: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): Entry {
        require(amountMl > 0) { "amount must be positive" }
        val day = Instant.ofEpochMilli(at).atZone(zone).toLocalDate().toEpochDay()
        val id = db.writableDatabase.insertOrThrow(
            "entries",
            null,
            ContentValues().apply {
                put("ts", at)
                put("day", day)
                put("amount_ml", amountMl)
            },
        )
        changed()
        return Entry(id, at, day, amountMl)
    }

    /** Put back an entry that was just deleted, with its original id (undo). */
    fun restore(entry: Entry) {
        db.writableDatabase.insertWithOnConflict("entries", null, entry.toValues(), SQLiteDatabase.CONFLICT_REPLACE)
        changed()
    }

    fun delete(id: Long) {
        db.writableDatabase.delete("entries", "id = ?", arrayOf(id.toString()))
        changed()
    }

    fun entriesBetween(fromDay: Long, toDay: Long): List<Entry> =
        queryEntries("day BETWEEN ? AND ?", arrayOf(fromDay.toString(), toDay.toString()))

    fun allEntries(): List<Entry> = queryEntries(null, null)

    fun lastEntry(): Entry? =
        db.readableDatabase.query("entries", COLUMNS, null, null, null, null, "ts DESC", "1").use { c ->
            if (c.moveToFirst()) c.toEntry() else null
        }

    /** Total millilitres per day, for every day that has at least one drink. */
    fun dailyTotals(): Map<Long, Int> {
        val out = HashMap<Long, Int>()
        db.readableDatabase.rawQuery("SELECT day, SUM(amount_ml) FROM entries GROUP BY day", null).use { c ->
            while (c.moveToNext()) out[c.getLong(0)] = c.getInt(1)
        }
        return out
    }

    fun totalForDay(day: Long): Int =
        db.readableDatabase.rawQuery("SELECT COALESCE(SUM(amount_ml), 0) FROM entries WHERE day = ?", arrayOf(day.toString()))
            .use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }

    fun goalHistory(): List<GoalChange> {
        val out = ArrayList<GoalChange>()
        db.readableDatabase.query("goals", arrayOf("day", "goal_ml"), null, null, null, null, "day ASC").use { c ->
            while (c.moveToNext()) out += GoalChange(c.getLong(0), c.getInt(1))
        }
        return out
    }

    fun setGoal(fromDay: Long, goalMl: Int) {
        db.writableDatabase.insertWithOnConflict(
            "goals",
            null,
            ContentValues().apply {
                put("day", fromDay)
                put("goal_ml", goalMl)
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
        changed()
    }

    /** Replace the whole log, for restoring a backup. All or nothing. */
    fun replaceAll(entries: List<Entry>, goals: List<GoalChange>) {
        val w = db.writableDatabase
        w.beginTransaction()
        try {
            w.delete("entries", null, null)
            w.delete("goals", null, null)
            for (e in entries) w.insertOrThrow("entries", null, e.toValues())
            for (g in goals) {
                w.insertOrThrow("goals", null, ContentValues().apply {
                    put("day", g.day)
                    put("goal_ml", g.goalMl)
                })
            }
            w.setTransactionSuccessful()
        } finally {
            w.endTransaction()
        }
        changed()
    }

    fun clearAll() {
        val w = db.writableDatabase
        w.delete("entries", null, null)
        w.delete("goals", null, null)
        changed()
    }

    private fun queryEntries(where: String?, args: Array<String>?): List<Entry> {
        val out = ArrayList<Entry>()
        db.readableDatabase.query("entries", COLUMNS, where, args, null, null, "ts ASC").use { c ->
            while (c.moveToNext()) out += c.toEntry()
        }
        return out
    }

    private fun android.database.Cursor.toEntry() = Entry(getLong(0), getLong(1), getLong(2), getInt(3))

    private fun Entry.toValues() = ContentValues().apply {
        put("id", id)
        put("ts", timestamp)
        put("day", day)
        put("amount_ml", amountMl)
    }

    private companion object {
        val COLUMNS = arrayOf("id", "ts", "day", "amount_ml")
    }
}
