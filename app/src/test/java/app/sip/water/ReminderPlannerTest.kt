package app.sip.water

import app.sip.water.data.ReminderConfig
import app.sip.water.reminders.ReminderPlanner
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderPlannerTest {
    private val zone: ZoneId = ZoneId.of("Europe/Paris")
    private val hourly = ReminderConfig(enabled = true, intervalMin = 60, startMin = 8 * 60, endMin = 22 * 60, smart = true)

    private fun at(h: Int, m: Int = 0, day: Int = 10) = LocalDateTime.of(2026, 3, day, h, m).atZone(zone).toInstant().toEpochMilli()

    private fun next(
        now: Long,
        config: ReminderConfig = hourly,
        lastDrink: Long? = null,
        lastFired: Long? = null,
        met: Boolean = false,
    ) = ReminderPlanner.nextReminderAt(now, zone, config, lastDrink, lastFired, met)

    @Test fun `disabled reminders schedule nothing`() {
        assertNull(next(at(10), hourly.copy(enabled = false)))
    }

    @Test fun `one interval after the last reminder`() {
        assertEquals(at(11), next(at(10, 1), lastFired = at(10)))
    }

    @Test fun `drinking pushes the next reminder back in smart mode`() {
        assertEquals(at(11, 30), next(at(10, 30), lastFired = at(10), lastDrink = at(10, 30)))
    }

    @Test fun `without smart mode a drink does not move the schedule`() {
        val plain = hourly.copy(smart = false)
        assertEquals(at(11), next(at(10, 30), plain, lastFired = at(10), lastDrink = at(10, 30)))
    }

    @Test fun `a stale anchor waits a full interval from now instead of firing at once`() {
        // Phone was off all afternoon; on boot at 17:10 the next reminder is 18:10, not "now".
        assertEquals(at(18, 10), next(at(17, 10), lastFired = at(12)))
    }

    @Test fun `nothing outside active hours - the next one is at the start of the day`() {
        // 21:30 + 1 h = 22:30, past the 22:00 end: wait for 08:00 tomorrow.
        assertEquals(at(8, day = 11), next(at(21, 30), lastFired = at(21, 30)))
    }

    @Test fun `before active hours the first reminder lands exactly at the start`() {
        assertEquals(at(8), next(at(6, 0), lastFired = at(6)))
    }

    @Test fun `once the goal is met reminders resume tomorrow`() {
        assertEquals(at(8, day = 11), next(at(15), lastFired = at(14, 30), met = true))
    }

    @Test fun `meeting the goal is ignored when that option is off`() {
        assertEquals(at(15, 30), next(at(15), hourly.copy(skipWhenMet = false), lastFired = at(14, 30), met = true))
    }

    @Test fun `windows can cross midnight for night shifts`() {
        val night = hourly.copy(startMin = 20 * 60, endMin = 2 * 60)
        assertTrue(ReminderPlanner.isInWindow(23 * 60, night))
        assertTrue(ReminderPlanner.isInWindow(1 * 60 + 30, night))
        assertFalse(ReminderPlanner.isInWindow(12 * 60, night))
        // 01:30 + 1 h = 02:30, outside: the window reopens at 20:00 the same day.
        assertEquals(at(20, day = 11), next(at(1, 30, day = 11), night, lastFired = at(1, 30, day = 11)))
        // Inside the window across midnight, reminders simply continue.
        assertEquals(at(0, 30, day = 11), next(at(23, 30), night, lastFired = at(23, 30)))
    }

    @Test fun `equal start and end means all day`() {
        val allDay = hourly.copy(startMin = 0, endMin = 0)
        assertEquals(at(3), next(at(2), allDay, lastFired = at(2)))
    }

    @Test fun `first ever schedule with no history waits one interval`() {
        assertEquals(at(10, 15), next(at(9, 15)))
    }
}
