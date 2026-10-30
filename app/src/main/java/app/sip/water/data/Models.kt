package app.sip.water.data

/** One drink. [day] is the local calendar day it was logged on, as an epoch day. */
data class Entry(
    val id: Long,
    val timestamp: Long,
    val day: Long,
    val amountMl: Int,
)

/** The daily goal in force from [day] onwards, until the next change. */
data class GoalChange(val day: Long, val goalMl: Int)

data class Streaks(val current: Int, val best: Int)
