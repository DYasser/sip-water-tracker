package app.sip.water.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import app.sip.water.Graph
import java.time.LocalDate
import java.time.ZoneId

/**
 * Owns the single pending reminder alarm.
 *
 * AlarmManager rather than WorkManager: periodic work is batched and can drift
 * by tens of minutes, which defeats the point of a reminder. An exact
 * allow-while-idle alarm fires on time even in Doze.
 */
object ReminderScheduler {

    const val SNOOZE_MINUTES = 15

    fun reschedule(context: Context) {
        val settings = Graph.settings
        val repo = Graph.repository
        val s = settings.current
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val today = LocalDate.now(zone).toEpochDay()
        val metToday = repo.totalForDay(today) >= s.goalMl

        val next = ReminderPlanner.nextReminderAt(
            nowMs = now,
            zone = zone,
            config = s.reminder,
            lastDrinkMs = repo.lastEntry()?.timestamp,
            lastFiredMs = s.lastReminderAt.takeIf { it > 0 },
            goalMetToday = metToday,
        )
        if (next == null) cancel(context) else setAlarm(context, next)
        settings.update { it.copy(nextReminderAt = next ?: 0) }
    }

    fun snooze(context: Context, minutes: Int = SNOOZE_MINUTES) {
        val at = System.currentTimeMillis() + minutes * 60_000L
        setAlarm(context, at)
        Graph.settings.update { it.copy(nextReminderAt = at) }
    }

    private fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context))
    }

    private fun setAlarm(context: Context, at: Long) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = pendingIntent(context)
        val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        try {
            if (exactAllowed) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (e: SecurityException) {
            // Exact alarms revoked between the check and the call: fall back to inexact.
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, ReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
