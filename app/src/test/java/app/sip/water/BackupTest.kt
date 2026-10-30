package app.sip.water

import app.sip.water.data.Backup
import app.sip.water.data.Entry
import app.sip.water.data.GoalChange
import app.sip.water.data.SettingsValues
import app.sip.water.data.VolumeUnit
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupTest {
    private val entries = listOf(
        Entry(1, 1_700_000_000_000, 19675, 250),
        Entry(2, 1_700_003_600_000, 19675, 500),
    )
    private val goals = listOf(GoalChange(0, 2000), GoalChange(19675, 2500))

    @Test fun `a backup round-trips entries, goals and settings`() {
        val json = Backup.toJson(entries, goals, SettingsValues(goalMl = 2500, unit = VolumeUnit.OZ, cups = listOf(200, 400)))
        val back = Backup.fromJson(json)
        assertEquals(entries.map { it.timestamp to it.amountMl }, back.entries.map { it.timestamp to it.amountMl })
        assertEquals(entries.map { it.day }, back.entries.map { it.day })
        assertEquals(goals, back.goals)
        assertEquals(2500, back.goalMl)
        assertEquals(VolumeUnit.OZ, back.unit)
        assertEquals(listOf(200, 400), back.cups)
    }

    @Test fun `a file that is not a backup is refused with a clear message`() {
        val e = assertThrows(IllegalArgumentException::class.java) { Backup.fromJson("hello") }
        assertEquals("That file is not a Sip backup.", e.message)
        assertThrows(IllegalArgumentException::class.java) { Backup.fromJson("""{"app":"other"}""") }
    }

    @Test fun `csv has a header and one row per drink`() {
        val csv = Backup.toCsv(entries, ZoneId.of("UTC"))
        val lines = csv.trim().lines()
        assertEquals("date,time,amount_ml", lines[0])
        assertEquals(3, lines.size)
        assertEquals("2023-11-14,22:13,250", lines[1])
    }
}
