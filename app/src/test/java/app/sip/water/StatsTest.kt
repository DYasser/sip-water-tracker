package app.sip.water

import app.sip.water.data.GoalChange
import app.sip.water.data.Stats
import app.sip.water.data.Volume
import app.sip.water.data.VolumeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StatsTest {
    private val goal2L = { _: Long -> 2000 }

    @Test fun `streak counts back from today when today is met`() {
        val totals = mapOf(100L to 2000, 99L to 2500, 98L to 2100, 97L to 500)
        assertEquals(3, Stats.streaks(totals, today = 100, goalFor = goal2L).current)
    }

    @Test fun `an unfinished today does not break the streak`() {
        // Morning: only 300 ml so far, but yesterday and the day before were met.
        val totals = mapOf(100L to 300, 99L to 2000, 98L to 2000)
        assertEquals(2, Stats.streaks(totals, today = 100, goalFor = goal2L).current)
    }

    @Test fun `a missed yesterday resets the streak`() {
        val totals = mapOf(100L to 300, 99L to 1000, 98L to 2000)
        assertEquals(0, Stats.streaks(totals, today = 100, goalFor = goal2L).current)
    }

    @Test fun `a day with no drinks at all breaks a run`() {
        val totals = mapOf(100L to 2000, 98L to 2000, 97L to 2000) // nothing logged on 99
        val s = Stats.streaks(totals, today = 100, goalFor = goal2L)
        assertEquals(1, s.current)
        assertEquals(2, s.best)
    }

    @Test fun `best streak remembers the longest run`() {
        val totals = (1L..10L).associateWith { 2000 } + mapOf(11L to 0, 12L to 2000)
        val s = Stats.streaks(totals, today = 12, goalFor = goal2L)
        assertEquals(1, s.current)
        assertEquals(10, s.best)
    }

    @Test fun `no history means no streak`() {
        val s = Stats.streaks(emptyMap(), today = 100, goalFor = goal2L)
        assertEquals(0, s.current)
        assertEquals(0, s.best)
    }

    @Test fun `raising the goal does not rewrite past days`() {
        val history = listOf(GoalChange(0, 2000), GoalChange(100, 3000))
        assertEquals(2000, Stats.goalFor(99, history, fallback = 3000))
        assertEquals(3000, Stats.goalFor(100, history, fallback = 3000))
        assertEquals(3000, Stats.goalFor(150, history, fallback = 3000))

        // 2 L every day: met before the change, not after. The streak survives up to day 99.
        val totals = (95L..100L).associateWith { 2000 }
        val s = Stats.streaks(totals, today = 100) { Stats.goalFor(it, history, 3000) }
        assertEquals(5, s.current) // days 95..99; today (100) is not met under 3 L yet
    }

    @Test fun `without any history the current setting applies everywhere`() {
        assertEquals(2500, Stats.goalFor(42, emptyList(), fallback = 2500))
    }

    @Test fun `suggested goal is 35 ml per kg rounded to 50`() {
        assertEquals(2450, Stats.suggestedGoalMl(70.0))
        assertEquals(2800, Stats.suggestedGoalMl(80.0))
        assertEquals(1000, Stats.suggestedGoalMl(10.0)) // clamped to a sane minimum
    }

    @Test fun `volumes format and parse in both units`() {
        assertEquals("250 ml", Volume.format(250, VolumeUnit.ML))
        assertEquals("1.25 L", Volume.format(1250, VolumeUnit.ML))
        assertEquals("2 L", Volume.format(2000, VolumeUnit.ML))
        assertEquals("8 oz", Volume.format(237, VolumeUnit.OZ))
        assertEquals(237, Volume.parse("8", VolumeUnit.OZ))
        assertEquals(330, Volume.parse("330", VolumeUnit.ML))
        assertEquals(500, Volume.parse("500,0", VolumeUnit.ML)) // comma decimals
        assertNull(Volume.parse("0", VolumeUnit.ML))
        assertNull(Volume.parse("abc", VolumeUnit.ML))
        assertNull(Volume.parse("99999", VolumeUnit.ML)) // not a real drink
    }
}
