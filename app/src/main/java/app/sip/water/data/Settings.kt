package app.sip.water.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** When and how reminders fire. Times are minutes after midnight. */
data class ReminderConfig(
    val enabled: Boolean = true,
    val intervalMin: Int = 60,
    val startMin: Int = 8 * 60,
    val endMin: Int = 22 * 60,
    /** Stop reminding for the rest of the day once the goal is met. */
    val skipWhenMet: Boolean = true,
    /** Count the interval from your last drink, not from the last reminder. */
    val smart: Boolean = true,
    val sound: Boolean = false,
)

data class SettingsValues(
    val goalMl: Int = 2000,
    val unit: VolumeUnit = VolumeUnit.ML,
    /** The amount the one-tap button adds: whatever you logged last. */
    val lastAmountMl: Int = 250,
    val cups: List<Int> = listOf(150, 250, 330, 500, 750),
    val reminder: ReminderConfig = ReminderConfig(),
    val dynamicColor: Boolean = false,
    val askedNotifications: Boolean = false,
    /** Written by the scheduler; 0 when nothing is scheduled. */
    val nextReminderAt: Long = 0,
    val lastReminderAt: Long = 0,
)

/** App settings in SharedPreferences, exposed as a flow so the UI follows changes. */
class Settings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("sip", Context.MODE_PRIVATE)
    private val _values = MutableStateFlow(read())
    val values: StateFlow<SettingsValues> = _values.asStateFlow()
    val current: SettingsValues get() = _values.value

    @Synchronized
    fun update(transform: (SettingsValues) -> SettingsValues) {
        val next = transform(_values.value)
        if (next == _values.value) return
        write(next)
        _values.value = next
    }

    private fun read(): SettingsValues {
        val d = SettingsValues()
        val r = d.reminder
        return SettingsValues(
            goalMl = prefs.getInt("goal_ml", d.goalMl),
            unit = runCatching { VolumeUnit.valueOf(prefs.getString("unit", d.unit.name)!!) }.getOrDefault(d.unit),
            lastAmountMl = prefs.getInt("last_amount_ml", d.lastAmountMl),
            cups = prefs.getString("cups", null)
                ?.split(',')
                ?.mapNotNull { it.trim().toIntOrNull() }
                ?.filter { it > 0 }
                ?.takeIf { it.isNotEmpty() }
                ?: d.cups,
            reminder = ReminderConfig(
                enabled = prefs.getBoolean("r_enabled", r.enabled),
                intervalMin = prefs.getInt("r_interval", r.intervalMin),
                startMin = prefs.getInt("r_start", r.startMin),
                endMin = prefs.getInt("r_end", r.endMin),
                skipWhenMet = prefs.getBoolean("r_skip_met", r.skipWhenMet),
                smart = prefs.getBoolean("r_smart", r.smart),
                sound = prefs.getBoolean("r_sound", r.sound),
            ),
            dynamicColor = prefs.getBoolean("dynamic_color", d.dynamicColor),
            askedNotifications = prefs.getBoolean("asked_notifications", d.askedNotifications),
            nextReminderAt = prefs.getLong("next_reminder_at", 0),
            lastReminderAt = prefs.getLong("last_reminder_at", 0),
        )
    }

    private fun write(v: SettingsValues) {
        prefs.edit()
            .putInt("goal_ml", v.goalMl)
            .putString("unit", v.unit.name)
            .putInt("last_amount_ml", v.lastAmountMl)
            .putString("cups", v.cups.joinToString(","))
            .putBoolean("r_enabled", v.reminder.enabled)
            .putInt("r_interval", v.reminder.intervalMin)
            .putInt("r_start", v.reminder.startMin)
            .putInt("r_end", v.reminder.endMin)
            .putBoolean("r_skip_met", v.reminder.skipWhenMet)
            .putBoolean("r_smart", v.reminder.smart)
            .putBoolean("r_sound", v.reminder.sound)
            .putBoolean("dynamic_color", v.dynamicColor)
            .putBoolean("asked_notifications", v.askedNotifications)
            .putLong("next_reminder_at", v.nextReminderAt)
            .putLong("last_reminder_at", v.lastReminderAt)
            .apply()
    }
}
