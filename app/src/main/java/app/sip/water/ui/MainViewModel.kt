package app.sip.water.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.sip.water.Graph
import app.sip.water.data.Backup
import app.sip.water.data.Entry
import app.sip.water.data.ReminderConfig
import app.sip.water.data.SettingsValues
import app.sip.water.data.Stats
import app.sip.water.data.Volume
import app.sip.water.data.VolumeUnit
import app.sip.water.reminders.Notifications
import app.sip.water.reminders.ReminderScheduler
import app.sip.water.widget.WaterWidget
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DayStat(val date: LocalDate, val totalMl: Int, val goalMl: Int) {
    val met: Boolean get() = totalMl >= goalMl && goalMl > 0
    val progress: Float get() = if (goalMl > 0) totalMl.toFloat() / goalMl else 0f
}

data class UiState(
    val loaded: Boolean = false,
    val today: LocalDate = LocalDate.now(),
    val totalMl: Int = 0,
    val goalMl: Int = 2000,
    /** Today's drinks, newest first. */
    val entries: List<Entry> = emptyList(),
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    /** The last 30 days, oldest first. */
    val days: List<DayStat> = emptyList(),
    val entriesByDay: Map<Long, List<Entry>> = emptyMap(),
    /** The first day anything was logged, so history does not show empty days before you started. */
    val firstDay: LocalDate? = null,
    val settings: SettingsValues = SettingsValues(),
) {
    val progress: Float get() = if (goalMl > 0) totalMl.toFloat() / goalMl else 0f
    val remainingMl: Int get() = (goalMl - totalMl).coerceAtLeast(0)
}

/** A snackbar to show, with an optional action (usually Undo). */
class UiMessage(val text: String, val actionLabel: String? = null, val action: (() -> Unit)? = null)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Graph.repository
    private val settings = Graph.settings

    /** Ticks when the date changes, so "today" rolls over at midnight while the app is open. */
    private val dayTicker = flow {
        while (true) {
            emit(LocalDate.now())
            delay(20_000)
        }
    }.distinctUntilChanged()

    val state: StateFlow<UiState> =
        combine(repo.version, settings.values, dayTicker) { _, s, today -> load(s, today) }
            .flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState(settings = settings.current))

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private fun load(s: SettingsValues, today: LocalDate): UiState {
        val todayDay = today.toEpochDay()
        val history = repo.goalHistory()
        val goalOf = { day: Long -> Stats.goalFor(day, history, s.goalMl) }
        val totals = repo.dailyTotals()
        val streaks = Stats.streaks(totals, todayDay, goalOf)

        val from = todayDay - 29
        val byDay = repo.entriesBetween(from, todayDay).groupBy { it.day }
        val days = (from..todayDay).map { d -> DayStat(LocalDate.ofEpochDay(d), totals[d] ?: 0, goalOf(d)) }

        return UiState(
            loaded = true,
            today = today,
            totalMl = totals[todayDay] ?: 0,
            goalMl = goalOf(todayDay),
            entries = byDay[todayDay].orEmpty().sortedByDescending { it.timestamp },
            currentStreak = streaks.current,
            bestStreak = streaks.best,
            days = days,
            entriesByDay = byDay,
            firstDay = totals.keys.minOrNull()?.let(LocalDate::ofEpochDay),
            settings = s,
        )
    }

    private fun io(block: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) { block() }
    }

    private fun say(text: String, actionLabel: String? = null, action: (() -> Unit)? = null) {
        _messages.trySend(UiMessage(text, actionLabel, action))
    }

    private fun fmt(ml: Int) = Volume.format(ml, settings.current.unit)

    // ---- Logging -------------------------------------------------------------

    fun addDrink(ml: Int, at: Long? = null) = io {
        // Settings first, so the widget redraws its button with the new amount.
        settings.update { it.copy(lastAmountMl = ml) }
        val entry = repo.add(ml, at ?: System.currentTimeMillis())
        Notifications.cancelReminder(getApplication())
        say("Added ${fmt(ml)}", "Undo") { removeEntry(entry, announce = false) }
    }

    fun removeEntry(entry: Entry, announce: Boolean = true) = io {
        repo.delete(entry.id)
        if (announce) say("Removed ${fmt(entry.amountMl)}", "Undo") { io { repo.restore(entry) } }
    }

    // ---- Settings ------------------------------------------------------------

    fun setGoal(goalMl: Int) = io {
        val today = LocalDate.now().toEpochDay()
        // Pin the old goal to the past first, so earlier days keep being judged by it.
        if (repo.goalHistory().isEmpty()) repo.setGoal(0, settings.current.goalMl)
        settings.update { it.copy(goalMl = goalMl) }
        repo.setGoal(today, goalMl)
    }

    fun setUnit(unit: VolumeUnit) = io {
        settings.update { it.copy(unit = unit) }
        WaterWidget.updateAll(getApplication())
    }

    fun setCups(cups: List<Int>) = io {
        settings.update { it.copy(cups = cups.distinct().sorted()) }
    }

    fun setDynamicColor(on: Boolean) = io { settings.update { it.copy(dynamicColor = on) } }

    fun markAskedNotifications() = io { settings.update { it.copy(askedNotifications = true) } }

    fun updateReminders(transform: (ReminderConfig) -> ReminderConfig) = io {
        settings.update { it.copy(reminder = transform(it.reminder)) }
        ReminderScheduler.reschedule(getApplication())
    }

    fun sendTestReminder() = io {
        val s = settings.current
        val total = repo.totalForDay(LocalDate.now().toEpochDay())
        Notifications.showReminder(getApplication(), total, s.goalMl, s.unit, s.lastAmountMl, s.reminder.sound)
    }

    // ---- Data ----------------------------------------------------------------

    fun exportCsv(uri: Uri) = io {
        runCatching {
            write(uri, Backup.toCsv(repo.allEntries()))
        }.onSuccess { say("Exported to CSV") }.onFailure { say("Export failed: ${it.message}") }
    }

    fun backup(uri: Uri) = io {
        runCatching {
            write(uri, Backup.toJson(repo.allEntries(), repo.goalHistory(), settings.current))
        }.onSuccess { say("Backup saved") }.onFailure { say("Backup failed: ${it.message}") }
    }

    fun restore(uri: Uri) = io {
        runCatching {
            val text = getApplication<Application>().contentResolver.openInputStream(uri)
                ?.bufferedReader()?.use { it.readText() }
                ?: error("Could not open the file.")
            val contents = Backup.fromJson(text)
            settings.update {
                it.copy(
                    goalMl = contents.goalMl ?: it.goalMl,
                    unit = contents.unit ?: it.unit,
                    cups = contents.cups ?: it.cups,
                )
            }
            repo.replaceAll(contents.entries, contents.goals)
            contents.entries.size
        }.onSuccess { say("Restored $it drinks") }.onFailure { say(it.message ?: "Restore failed") }
    }

    fun deleteAll() = io {
        repo.clearAll()
        say("All drinks deleted")
    }

    private fun write(uri: Uri, text: String) {
        getApplication<Application>().contentResolver.openOutputStream(uri, "wt")
            ?.bufferedWriter()?.use { it.write(text) }
            ?: error("Could not open the file.")
    }
}
