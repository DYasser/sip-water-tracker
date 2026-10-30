package app.sip.water.data

/** Pure calculations over the log, kept free of Android so they can be unit tested. */
object Stats {

    /**
     * The goal that applied on [day].
     *
     * Changing your goal must not rewrite history: a day you finished at 2 L
     * under a 2 L goal still counts as met after you raise the goal to 3 L.
     * Days before the first recorded change use that first goal.
     */
    fun goalFor(day: Long, history: List<GoalChange>, fallback: Int): Int {
        if (history.isEmpty()) return fallback
        var goal = history.first().goalMl
        for (change in history) {
            if (change.day <= day) goal = change.goalMl else break
        }
        return goal
    }

    /**
     * Current and best runs of consecutive days on which the goal was met.
     *
     * Today does not break the current streak while it is still in progress:
     * if today is not met yet, the streak counts back from yesterday.
     */
    fun streaks(totals: Map<Long, Int>, today: Long, goalFor: (Long) -> Int): Streaks {
        fun met(day: Long) = (totals[day] ?: 0) >= goalFor(day)

        var current = 0
        var d = if (met(today)) today else today - 1
        while (met(d)) {
            current++
            d--
        }

        var best = 0
        val first = totals.keys.minOrNull()
        if (first != null) {
            var run = 0
            for (day in first..today) {
                if (met(day)) {
                    run++
                    if (run > best) best = run
                } else {
                    run = 0
                }
            }
        }
        return Streaks(current = current, best = maxOf(best, current))
    }

    /** A suggested daily goal: about 35 ml per kg of body weight, to the nearest 50 ml. */
    fun suggestedGoalMl(weightKg: Double): Int {
        val raw = weightKg * 35
        return (Math.round(raw / 50) * 50).toInt().coerceIn(1000, 6000)
    }
}
