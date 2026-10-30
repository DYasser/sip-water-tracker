package app.sip.water.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.os.Build
import android.provider.Settings as SystemSettings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.sip.water.MainActivity
import app.sip.water.R
import app.sip.water.data.Volume
import app.sip.water.data.VolumeUnit

object Notifications {
    /*
     * Android fixes a channel's sound and vibration when it is created; an app
     * cannot change them afterwards. So "sound on/off" is two channels, and the
     * setting picks which one a reminder goes to.
     */
    const val CHANNEL_VIBRATE = "reminders_vibrate"
    const val CHANNEL_SOUND = "reminders_sound"
    private const val REMINDER_ID = 1001
    private val PATTERN = longArrayOf(0, 300, 180, 300)

    private val MESSAGES = listOf(
        "Time for some water",
        "Hydration check",
        "A few sips?",
        "Your body called — it wants water",
        "Quick water break",
    )

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val vibrate = NotificationChannel(CHANNEL_VIBRATE, "Reminders (vibrate)", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Drink reminders that vibrate without a sound"
            enableVibration(true)
            vibrationPattern = PATTERN
            setSound(null, null)
        }
        val sound = NotificationChannel(CHANNEL_SOUND, "Reminders (sound)", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Drink reminders with a sound and vibration"
            enableVibration(true)
            vibrationPattern = PATTERN
            setSound(
                SystemSettings.System.DEFAULT_NOTIFICATION_URI,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
        }
        nm.createNotificationChannels(listOf(vibrate, sound))
    }

    fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
    }

    fun showReminder(
        context: Context,
        totalMl: Int,
        goalMl: Int,
        unit: VolumeUnit,
        quickAddMl: Int,
        withSound: Boolean,
    ) {
        if (!canPost(context)) return
        val remaining = (goalMl - totalMl).coerceAtLeast(0)
        val text = if (remaining > 0) {
            "${Volume.format(totalMl, unit)} of ${Volume.format(goalMl, unit)} · ${Volume.format(remaining, unit)} to go"
        } else {
            "Goal reached — ${Volume.format(totalMl, unit)} today"
        }

        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val add = PendingIntent.getBroadcast(
            context,
            1,
            Intent(context, ActionReceiver::class.java)
                .setAction(ActionReceiver.ACTION_ADD)
                .putExtra(ActionReceiver.EXTRA_ML, quickAddMl),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val snooze = PendingIntent.getBroadcast(
            context,
            2,
            Intent(context, ActionReceiver::class.java).setAction(ActionReceiver.ACTION_SNOOZE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, if (withSound) CHANNEL_SOUND else CHANNEL_VIBRATE)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF0B6BCB.toInt())
            .setContentTitle(MESSAGES.random())
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .addAction(0, "+ ${Volume.format(quickAddMl, unit)}", add)
            .addAction(0, "Snooze ${ReminderScheduler.SNOOZE_MINUTES} min", snooze)
            .build()

        context.getSystemService(NotificationManager::class.java).notify(REMINDER_ID, notification)
    }

    fun cancelReminder(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(REMINDER_ID)
    }
}
