package app.sip.water.reminders

import app.sip.water.data.ReminderConfig
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Decides when the next reminder fires. Pure, so every rule is unit tested.
 *
 * The rules, in order:
 *  1. Wait [ReminderConfig.intervalMin] after the anchor. The anchor is the last
 *     reminder, or — in smart mode — whichever is later of the last reminder and
 *     your last drink, so drinking pushes the next nag back.
 *  2. If that moment has already passed (phone was off, app just installed),
 *     wait a full interval from now instead of firing immediately.
 *  3. Goal met and "stop once met" on: nothing more today.
 *  4. Outside active hours: move to the start of the next active window.
 *     Windows may cross midnight (e.g. 20:00–02:00 for night shifts).
 */
object ReminderPlanner {

    fun nextReminderAt(
        nowMs: Long,
        zone: ZoneId,
        config: ReminderConfig,
        lastDrinkMs: Long?,
        lastFiredMs: Long?,
        goalMetToday: Boolean,
    ): Long? {
        if (!config.enabled) return null
        val intervalMs = config.intervalMin.coerceAtLeast(5) * 60_000L

        val anchor = listOfNotNull(lastFiredMs, if (config.smart) lastDrinkMs else null)
            .filter { it > 0 }
            .maxOrNull()
        var t = if (anchor != null) anchor + intervalMs else nowMs + intervalMs
        if (t <= nowMs) t = nowMs + intervalMs

        var at = Instant.ofEpochMilli(t).atZone(zone)
        if (config.skipWhenMet && goalMetToday) {
            val tomorrow = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone)
            if (at.isBefore(tomorrow)) at = tomorrow
        }
        return snapIntoWindow(at, config).toInstant().toEpochMilli()
    }

    fun isInWindow(minuteOfDay: Int, config: ReminderConfig): Boolean {
        val s = config.startMin
        val e = config.endMin
        return when {
            s == e -> true
            s < e -> minuteOfDay in s..e
            else -> minuteOfDay >= s || minuteOfDay <= e
        }
    }

    fun isInWindow(ms: Long, zone: ZoneId, config: ReminderConfig): Boolean {
        val t = Instant.ofEpochMilli(ms).atZone(zone)
        return isInWindow(t.hour * 60 + t.minute, config)
    }

    private fun snapIntoWindow(t: ZonedDateTime, config: ReminderConfig): ZonedDateTime {
        if (isInWindow(t.hour * 60 + t.minute, config)) return t
        var start = t.toLocalDate().atStartOfDay(t.zone).plusMinutes(config.startMin.toLong())
        if (!start.isAfter(t)) start = start.plusDays(1)
        return start
    }
}
