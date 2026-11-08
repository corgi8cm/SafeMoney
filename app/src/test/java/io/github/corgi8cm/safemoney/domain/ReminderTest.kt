package io.github.corgi8cm.safemoney.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class ReminderTest {
    private val taipei = ZoneId.of("Asia/Taipei")
    private val nine = LocalTime.of(21, 0)

    private fun at(text: String, zone: ZoneId = taipei) = ZonedDateTime.of(LocalDateTime.parse(text), zone)

    @Test
    fun laterTodayIfNotPassed() {
        assertEquals(at("2026-11-08T21:00"), nextReminderAt(at("2026-11-08T08:30"), nine))
    }

    @Test
    fun tomorrowIfPassedOrExactlyNow() {
        assertEquals(at("2026-11-09T21:00"), nextReminderAt(at("2026-11-08T21:00"), nine))
        assertEquals(at("2026-11-09T21:00"), nextReminderAt(at("2026-11-08T23:59"), nine))
    }

    @Test
    fun crossesMonthAndYear() {
        assertEquals(at("2027-01-01T21:00"), nextReminderAt(at("2026-12-31T22:00"), nine))
    }

    @Test
    fun keepsLocalTimeAcrossDaylightSaving() {
        val ny = ZoneId.of("America/New_York")
        // 2026-11-01 美國結束夏令時間
        val next = nextReminderAt(at("2026-10-31T22:00", ny), nine)
        assertEquals(LocalDateTime.parse("2026-11-01T21:00"), next.toLocalDateTime())
    }

    @Test
    fun noReminderWhenAlreadyRecorded() {
        assertNull(reminderText(recordedToday = true, nearest = NearestGoal("大衣", 100)) { "$$it" })
    }

    @Test
    fun mentionsNearestGoal() {
        val text = reminderText(recordedToday = false, nearest = NearestGoal("大衣", 1_240)) { "$$it" }!!
        assertTrue(text.body.contains("「大衣」還差 $1240"))
    }

    @Test
    fun genericTextWithoutActiveGoal() {
        val text = reminderText(recordedToday = false, nearest = null) { "$$it" }!!
        assertEquals("今天有少花什麼嗎？記一筆吧！", text.body)
    }
}
