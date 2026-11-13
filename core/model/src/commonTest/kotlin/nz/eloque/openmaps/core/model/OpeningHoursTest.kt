package nz.eloque.openmaps.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OpeningHoursTest {
    private fun at(
        hour: Int,
        minute: Int = 0,
    ) = hour * 60 + minute

    @Test
    fun parsesWeekdaysAndWeekend() {
        val hours = OpeningHours.parse("Mo-Fr 09:00-18:00; Sa 10:00-14:00; PH off")!!
        assertEquals(listOf(TimeRange(at(9), at(18))), hours.week[0])
        assertEquals(listOf(TimeRange(at(10), at(14))), hours.week[5])
        assertEquals(emptyList(), hours.week[6])
    }

    @Test
    fun parsesSplitDaysAndLaterRulesOverride() {
        val hours = OpeningHours.parse("Mo-Su 08:00-12:00, 14:00-18:00; We off")!!
        assertEquals(2, hours.week[1].size)
        assertEquals(emptyList(), hours.week[2])
    }

    @Test
    fun parsesWrappingDayRange() {
        val hours = OpeningHours.parse("Sa-Mo 10:00-16:00")!!
        assertEquals(listOf(0, 5, 6), hours.week.indices.filter { hours.week[it].isNotEmpty() })
    }

    @Test
    fun unsupportedSyntaxReturnsNull() {
        assertNull(OpeningHours.parse("Mo-Fr sunrise-sunset"))
        assertNull(OpeningHours.parse("Apr-Oct: Mo-Fr 09:00-18:00"))
        assertNull(OpeningHours.parse("Mo-Fr 09:00+"))
        assertNull(OpeningHours.parse(""))
    }

    @Test
    fun statusWhileOpenAndClosed() {
        val hours = OpeningHours.parse("Mo-Fr 09:00-18:00")!!
        assertEquals(OpenStatus.Open(at(18)), hours.status(day = 0, minute = at(12)))
        assertEquals(OpenStatus.Closed(0, at(9)), hours.status(day = 0, minute = at(7)))
        assertEquals(OpenStatus.Closed(1, at(9)), hours.status(day = 0, minute = at(19)))
        assertEquals(OpenStatus.Closed(3, at(9)), hours.status(day = 4, minute = at(18)))
    }

    @Test
    fun statusPastMidnight() {
        val hours = OpeningHours.parse("Fr,Sa 20:00-02:00")!!
        assertEquals(OpenStatus.Open(at(2)), hours.status(day = 5, minute = at(1)))
        assertEquals(OpenStatus.Closed(5, at(20)), hours.status(day = 6, minute = at(3)))
    }

    @Test
    fun alwaysOpenAndNeverOpen() {
        assertEquals(OpenStatus.Open(null), OpeningHours.parse("24/7")!!.status(3, at(4)))
        assertEquals(OpenStatus.Closed(null, null), OpeningHours.parse("off")!!.status(3, at(4)))
    }
}
