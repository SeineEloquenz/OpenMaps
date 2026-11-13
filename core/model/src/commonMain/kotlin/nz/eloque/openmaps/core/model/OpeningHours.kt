package nz.eloque.openmaps.core.model

/** Minutes from midnight. [end] exceeds 1440 when the range runs past midnight. */
data class TimeRange(
    val start: Int,
    val end: Int,
)

sealed interface OpenStatus {
    /** [closesAt] is a minute of the day, or null when open around the clock. */
    data class Open(
        val closesAt: Int?,
    ) : OpenStatus

    /** [opensInDays] is 0 for today. Both are null when there are no opening times at all. */
    data class Closed(
        val opensInDays: Int?,
        val opensAt: Int?,
    ) : OpenStatus
}

/** Weekly opening times, indexed by day with Monday as 0. */
class OpeningHours(
    val week: List<List<TimeRange>>,
) {
    fun status(
        day: Int,
        minute: Int,
    ): OpenStatus {
        // A timeline from yesterday to a week ahead, so ranges running past midnight and the next opening are both covered.
        val intervals =
            (0 until TIMELINE_DAYS)
                .flatMap { offset ->
                    week[(day - 1 + offset + 7) % 7].map { TimeRange(it.start + offset * DAY, it.end + offset * DAY) }
                }.sortedBy { it.start }
                .fold(mutableListOf<TimeRange>()) { merged, range ->
                    val last = merged.lastOrNull()
                    if (last != null && range.start <= last.end) {
                        merged[merged.lastIndex] = TimeRange(last.start, maxOf(last.end, range.end))
                    } else {
                        merged += range
                    }
                    merged
                }
        val now = DAY + minute
        intervals.firstOrNull { now >= it.start && now < it.end }?.let { current ->
            return OpenStatus.Open(if (current.end - now >= 7 * DAY) null else current.end % DAY)
        }
        val next = intervals.firstOrNull { it.start > now } ?: return OpenStatus.Closed(null, null)
        return OpenStatus.Closed(next.start / DAY - 1, next.start % DAY)
    }

    companion object {
        private const val DAY = 1440
        private const val TIMELINE_DAYS = 10
        private val DAYS = listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su")
        private val TIME_RANGE = Regex("""(\d{1,2}):(\d{2})-(\d{1,2}):(\d{2})""")

        /**
         * Parses the common subset of the OSM `opening_hours` syntax, such as `Mo-Fr 09:00-18:00; Sa 10:00-14:00; PH off`.
         * Returns null for anything else, so callers can show the raw text.
         */
        fun parse(text: String): OpeningHours? {
            val week = MutableList(7) { emptyList<TimeRange>() }
            val rules = text.split(';').map { it.trim() }.filter { it.isNotEmpty() }
            if (rules.isEmpty()) return null
            for (rule in rules) {
                if (rule == "24/7") {
                    week.fill(listOf(TimeRange(0, DAY)))
                    continue
                }
                val tokens = rule.split(Regex("""\s+"""))
                val dayTokens = tokens.takeWhile { !it.first().isDigit() && it !in CLOSED_WORDS }
                val rest = tokens.drop(dayTokens.size).joinToString("")
                val days =
                    if (dayTokens.isEmpty()) {
                        (0..6).toList()
                    } else {
                        parseDays(dayTokens.joinToString("")) ?: return null
                    }
                // Rules for public or school holidays only are skipped.
                if (days.isEmpty()) continue
                val ranges = if (rest in CLOSED_WORDS) emptyList() else parseTimes(rest) ?: return null
                days.forEach { week[it] = ranges }
            }
            return OpeningHours(week)
        }

        private val CLOSED_WORDS = setOf("off", "closed")

        private fun parseDays(selector: String): List<Int>? =
            selector.split(',').filter { it.isNotEmpty() }.flatMap { item ->
                if (item == "PH" || item == "SH") return@flatMap emptyList()
                val ends = item.split('-').map { DAYS.indexOf(it) }
                when {
                    ends.any { it < 0 } || ends.size > 2 -> return null
                    ends.size == 1 -> ends
                    else -> generateSequence(ends[0]) { (it + 1) % 7 }.takeWhile { it != ends[1] }.toList() + ends[1]
                }
            }

        private fun parseTimes(selector: String): List<TimeRange>? {
            if (selector.isEmpty()) return null
            return selector.split(',').map { item ->
                val match = TIME_RANGE.matchEntire(item) ?: return null
                val (startHour, startMinute, endHour, endMinute) = match.destructured.toList().map { it.toInt() }
                if (startHour > 23 || endHour > 24 || startMinute > 59 || endMinute > 59) return null
                val start = startHour * 60 + startMinute
                val end = endHour * 60 + endMinute
                TimeRange(start, if (end <= start) end + DAY else end)
            }
        }
    }
}
