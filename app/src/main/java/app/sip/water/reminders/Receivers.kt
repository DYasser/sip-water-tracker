package app.sip.water.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import app.sip.water.Graph
import app.sip.water.data.Volume
import java.time.LocalDate
import java.time.ZoneId

/** Run [block] off the main thread while keeping the broadcast alive until it finishes. */
private fun BroadcastReceiver.runAsync(block: () -> Unit) {
    val pending = goAsync()
    Thread {
        try {
            block()
        } finally {
            pending.finish()
        }
    }.start()
}

/** The reminder alarm went off. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = runAsync {
        val app = context.applicationContext
        val s = Graph.settings.current
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val total = Graph.repository.totalForDay(LocalDate.now(zone).toEpochDay())
        val met = total >= s.goalMl

        // Re-check at fire time: settings or the log may have changed since this was scheduled.
        val shouldShow = s.reminder.enabled &&
            ReminderPlanner.isInWindow(now, zone, s.reminder) &&
            !(met && s.reminder.skipWhenMet)
        if (shouldShow) {
            Notifications.showReminder(app, total, s.goalMl, s.unit, s.lastAmountMl, s.reminder.sound)
            Graph.settings.update { it.copy(lastReminderAt = now) }
        }
        ReminderScheduler.reschedule(app)
    }
}

/** Buttons on the reminder notification and the home-screen widget. */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        when (intent.action) {
            ACTION_ADD -> runAsync {
                val s = Graph.settings.current
                val ml = intent.getIntExtra(EXTRA_ML, s.lastAmountMl).takeIf { it > 0 } ?: s.lastAmountMl
                Graph.settings.update { it.copy(lastAmountMl = ml) }
                Graph.repository.add(ml) // listeners refresh the widget and the schedule
                Notifications.cancelReminder(app)
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(app, "Added ${Volume.format(ml, s.unit)}", Toast.LENGTH_SHORT).show()
                }
            }
            ACTION_SNOOZE -> runAsync {
                Notifications.cancelReminder(app)
                ReminderScheduler.snooze(app)
            }
        }
    }

    companion object {
        const val ACTION_ADD = "app.sip.water.action.ADD"
        const val ACTION_SNOOZE = "app.sip.water.action.SNOOZE"
        const val EXTRA_ML = "ml"
    }
}

/**
 * Alarms do not survive a reboot, an app update, or a clock/timezone change.
 * Each of those re-plans the next reminder.
 */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = runAsync {
        ReminderScheduler.reschedule(context.applicationContext)
    }
}
