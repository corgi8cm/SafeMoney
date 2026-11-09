package io.github.corgi8cm.safemoney.domain

import io.github.corgi8cm.safemoney.data.db.RecurringSaving
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

class RecurrenceTest {
    // 2026/11/09 是星期一
    private val monday = LocalDate.of(2026, 11, 9)

    private fun rule(
        days: Int = Recurrence.EVERY_DAY,
        start: LocalDate = monday,
        end: LocalDate? = null,
        handled: LocalDate? = null,
    ) = RecurringSaving(
        reason = "不買咖啡",
        amount = 60,
        days = days,
        startDate = start,
        endDate = end,
        handledThrough = handled,
        createdAt = Instant.EPOCH,
        uid = "r",
        updatedAt = Instant.EPOCH,
    )

    @Test
    fun startDayItselfIsDue() {
        assertEquals(listOf(monday), Recurrence.pendingDates(rule(), monday))
    }

    @Test
    fun notDueBeforeStart() {
        assertTrue(Recurrence.pendingDates(rule(start = monday.plusDays(1)), monday).isEmpty())
    }

    @Test
    fun weekdaysSkipTheWeekend() {
        val nextMonday = monday.plusDays(7)
        val dates = Recurrence.pendingDates(rule(days = Recurrence.WEEKDAYS), nextMonday)
        assertEquals(6, dates.size) // 一～五，加上下週一
        assertTrue(dates.none { it.dayOfWeek == DayOfWeek.SATURDAY || it.dayOfWeek == DayOfWeek.SUNDAY })
    }

    @Test
    fun alreadyRecordedDaysAreNotDueAgain() {
        val dates = Recurrence.pendingDates(rule(handled = monday.plusDays(2)), monday.plusDays(4))
        assertEquals(listOf(monday.plusDays(3), monday.plusDays(4)), dates)
    }

    @Test
    fun stopsAtEndDate() {
        val dates = Recurrence.pendingDates(rule(end = monday.plusDays(1)), monday.plusDays(10))
        assertEquals(listOf(monday, monday.plusDays(1)), dates)
        assertTrue(Recurrence.isEnded(rule(end = monday.plusDays(1)), monday.plusDays(2)))
    }

    @Test
    fun describeDays() {
        assertEquals("每天", Recurrence.describe(Recurrence.EVERY_DAY))
        assertEquals("平日", Recurrence.describe(Recurrence.WEEKDAYS))
        assertEquals("週末", Recurrence.describe(Recurrence.WEEKENDS))
        val mwf = Recurrence.bit(DayOfWeek.MONDAY) or Recurrence.bit(DayOfWeek.WEDNESDAY) or Recurrence.bit(DayOfWeek.FRIDAY)
        assertEquals("每週一、三、五", Recurrence.describe(mwf))
        assertEquals(Recurrence.WEEKDAYS, Recurrence.toggle(Recurrence.EVERY_DAY, DayOfWeek.SATURDAY).let {
            Recurrence.toggle(it, DayOfWeek.SUNDAY)
        })
    }

    @Test(expected = IllegalArgumentException::class)
    fun startCannotBeInThePast() {
        Recurrence.validate("不買咖啡", 60, Recurrence.EVERY_DAY, monday.minusDays(1), null, today = monday)
    }

    @Test(expected = IllegalArgumentException::class)
    fun needsAtLeastOneDay() {
        Recurrence.validate("不買咖啡", 60, 0, monday, null, today = monday)
    }
}
