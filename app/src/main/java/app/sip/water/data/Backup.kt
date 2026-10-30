package app.sip.water.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.json.JSONArray
import org.json.JSONObject

/**
 * Backup and export formats.
 *
 * Everything lives only on the phone, so a backup file is the way to move to a
 * new phone or survive a reinstall. CSV is for looking at the data elsewhere.
 */
object Backup {
    const val VERSION = 1

    data class Contents(
        val entries: List<Entry>,
        val goals: List<GoalChange>,
        val goalMl: Int?,
        val unit: VolumeUnit?,
        val cups: List<Int>?,
    )

    fun toJson(entries: List<Entry>, goals: List<GoalChange>, settings: SettingsValues): String {
        val root = JSONObject()
        root.put("app", "sip")
        root.put("version", VERSION)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("settings", JSONObject().apply {
            put("goalMl", settings.goalMl)
            put("unit", settings.unit.name)
            put("cups", JSONArray(settings.cups))
        })
        root.put("goals", JSONArray().apply {
            goals.forEach { put(JSONObject().put("day", it.day).put("goalMl", it.goalMl)) }
        })
        root.put("entries", JSONArray().apply {
            entries.forEach {
                put(JSONObject().put("ts", it.timestamp).put("day", it.day).put("ml", it.amountMl))
            }
        })
        return root.toString()
    }

    /** Parse a backup. Throws [IllegalArgumentException] with a readable message when the file is not one. */
    fun fromJson(text: String): Contents {
        val root = try {
            JSONObject(text)
        } catch (e: Exception) {
            throw IllegalArgumentException("That file is not a Sip backup.")
        }
        require(root.optString("app") == "sip") { "That file is not a Sip backup." }
        require(root.optInt("version", 0) in 1..VERSION) { "This backup is from a newer version of Sip." }

        val entriesJson = root.optJSONArray("entries") ?: JSONArray()
        val entries = (0 until entriesJson.length()).map { i ->
            val o = entriesJson.getJSONObject(i)
            Entry(id = (i + 1).toLong(), timestamp = o.getLong("ts"), day = o.getLong("day"), amountMl = o.getInt("ml"))
        }.filter { it.amountMl > 0 }

        val goalsJson = root.optJSONArray("goals") ?: JSONArray()
        val goals = (0 until goalsJson.length()).map { i ->
            val o = goalsJson.getJSONObject(i)
            GoalChange(o.getLong("day"), o.getInt("goalMl"))
        }

        val s = root.optJSONObject("settings")
        val cups = s?.optJSONArray("cups")?.let { arr -> (0 until arr.length()).map { arr.getInt(it) }.filter { it > 0 } }
        return Contents(
            entries = entries,
            goals = goals,
            goalMl = s?.optInt("goalMl", 0)?.takeIf { it > 0 },
            unit = s?.optString("unit")?.let { runCatching { VolumeUnit.valueOf(it) }.getOrNull() },
            cups = cups?.takeIf { it.isNotEmpty() },
        )
    }

    fun toCsv(entries: List<Entry>, zone: ZoneId = ZoneId.systemDefault()): String {
        val date = DateTimeFormatter.ISO_LOCAL_DATE
        val time = DateTimeFormatter.ofPattern("HH:mm")
        val sb = StringBuilder("date,time,amount_ml\n")
        for (e in entries.sortedBy { it.timestamp }) {
            val t = Instant.ofEpochMilli(e.timestamp).atZone(zone)
            sb.append(LocalDate.ofEpochDay(e.day).format(date)).append(',')
                .append(t.format(time)).append(',')
                .append(e.amountMl).append('\n')
        }
        return sb.toString()
    }
}
