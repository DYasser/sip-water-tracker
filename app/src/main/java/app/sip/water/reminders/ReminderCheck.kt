package app.sip.water.reminders

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.Vibrator
import android.provider.Settings as SystemSettings

/**
 * Works out why a reminder might not buzz or ring on this particular phone.
 *
 * The notification itself is fine everywhere; what silences it is almost
 * always a setting outside the app — silent mode, Do Not Disturb, a muted
 * channel, or a phone maker's own "quiet new apps" default. Each finding comes
 * with the settings page that fixes it.
 */
object ReminderCheck {

    enum class Fix { AppNotifications, Channel, PhoneSound, DoNotDisturb, TurnOnAppSound }

    data class Finding(val title: String, val detail: String, val fix: Fix?)

    fun run(context: Context, soundWanted: Boolean): List<Finding> {
        val out = ArrayList<Finding>()
        val nm = context.getSystemService(NotificationManager::class.java)
        val audio = context.getSystemService(AudioManager::class.java)

        if (!Notifications.canPost(context)) {
            out += Finding(
                "Notifications are off for Sip",
                "Reminders cannot appear at all until they are allowed.",
                Fix.AppNotifications,
            )
            return out // nothing else matters until this is fixed
        }

        val channelId = if (soundWanted) Notifications.CHANNEL_SOUND else Notifications.CHANNEL_VIBRATE
        val channel = nm.getNotificationChannel(channelId)
        if (channel != null) {
            if (channel.importance < NotificationManager.IMPORTANCE_DEFAULT) {
                out += Finding(
                    "Sip's reminders are set to Silent",
                    "Android shows them quietly in the shade without sound or vibration.",
                    Fix.Channel,
                )
            } else {
                if (!channel.shouldVibrate()) {
                    out += Finding(
                        "Vibration is off for Sip's reminders",
                        "It was switched off in Android's settings for this app.",
                        Fix.Channel,
                    )
                }
                if (soundWanted && channel.sound == null) {
                    out += Finding(
                        "Sound is off for Sip's reminders",
                        "It was switched off in Android's settings for this app.",
                        Fix.Channel,
                    )
                }
            }
        }

        if (nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL) {
            out += Finding(
                "Do Not Disturb is on",
                "It holds back sound and vibration from apps like Sip.",
                Fix.DoNotDisturb,
            )
        }

        when (audio.ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> out += Finding(
                "Your phone is on Silent",
                "On Silent, Android mutes both the sound and the vibration of notifications. Switch to Vibrate or Sound.",
                Fix.PhoneSound,
            )
            AudioManager.RINGER_MODE_VIBRATE -> if (soundWanted) {
                out += Finding("Your phone is on Vibrate", "Reminders will buzz, but no sound will play.", null)
            }
            else -> if (soundWanted && audio.getStreamVolume(AudioManager.STREAM_NOTIFICATION) == 0) {
                out += Finding("Notification volume is at zero", "Turn it up to hear reminders.", Fix.PhoneSound)
            }
        }

        if (notificationVibrationOff(context)) {
            out += Finding(
                "Notification vibration is off on this phone",
                "Your phone's vibration settings have notifications set to off.",
                Fix.PhoneSound,
            )
        }

        val vibrator = context.getSystemService(Vibrator::class.java)
        if (vibrator == null || !vibrator.hasVibrator()) {
            out += Finding("This device cannot vibrate", "Turn on \"Play a sound\" to be reminded.", null)
        }

        if (!soundWanted) {
            out += Finding(
                "Sound is off in Sip",
                "Reminders only vibrate. Turn on \"Play a sound\" if you want to hear them too.",
                Fix.TurnOnAppSound,
            )
        }

        quietByDefaultMaker()?.let { maker ->
            out += Finding(
                "$maker phones may mute new apps",
                "They can switch off sound and vibration for newly installed apps in their own settings. " +
                    "Open Sip's notification settings and turn on sound, vibration and pop-up notifications.",
                Fix.AppNotifications,
            )
        }
        return out
    }

    /**
     * Android 13+ has a system-wide "notification vibration" strength; at zero
     * nothing vibrates. The key is not public API, so a missing value just
     * means we cannot tell — it is never reported as a problem.
     */
    private fun notificationVibrationOff(context: Context): Boolean {
        val cr = context.contentResolver
        val keys = listOf("notification_vibration_intensity", "VIB_NOTIFICATION_MAGNITUDE")
        return keys.any { key ->
            runCatching { SystemSettings.System.getInt(cr, key, -1) }.getOrDefault(-1) == 0
        }
    }

    /** Phone makers known to silence notifications from newly installed apps by default. */
    private fun quietByDefaultMaker(): String? {
        val m = Build.MANUFACTURER.lowercase()
        return when {
            "xiaomi" in m || "redmi" in m || "poco" in m -> "Xiaomi"
            "oppo" in m -> "OPPO"
            "realme" in m -> "realme"
            "vivo" in m -> "vivo"
            "oneplus" in m -> "OnePlus"
            "huawei" in m || "honor" in m -> "Huawei/Honor"
            else -> null
        }
    }
}
