package io.github.corgi8cm.safemoney.domain

import io.github.corgi8cm.safemoney.ui.common.formatMoney
import io.github.corgi8cm.safemoney.ui.splash.SPLASH_QUOTES
import io.github.corgi8cm.safemoney.ui.splash.pickQuoteIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import kotlin.random.Random

class ReviewTest {
    private val oct = YearMonth.of(2026, 10)

    private val entries = listOf(
        ReviewEntry(LocalDate.of(2026, 9, 30), "飲料", 100),
        ReviewEntry(LocalDate.of(2026, 10, 1), "飲料", 60),
        ReviewEntry(LocalDate.of(2026, 10, 1), "便當", 80),
        ReviewEntry(LocalDate.of(2026, 10, 15), "飲料", 60),
        ReviewEntry(LocalDate.of(2026, 10, 31), "計程車", 200),
        ReviewEntry(LocalDate.of(2026, 11, 1), "飲料", 999),
    )

    @Test
    fun monthReviewOnlyCountsThatMonth() {
        val review = buildReview(
            entries,
            listOf(CompletedGoal("耳機", LocalDate.of(2026, 10, 20)), CompletedGoal("外套", LocalDate.of(2026, 11, 2))),
            ReviewPeriod.Month(oct),
        )
        assertEquals(400, review.total)
        assertEquals(4, review.count)
        assertEquals(3, review.activeDays)
        assertEquals(100, review.previousTotal)
        assertEquals(listOf("計程車", "飲料", "便當"), review.topReasons.map { it.reason })
        assertEquals(listOf("耳機"), review.completedGoals)
        assertEquals(PeriodTotal(LocalDate.of(2026, 10, 31), 200), review.bestDay)
        assertEquals("比上個月多 \$300", review.comparisonText(::formatMoney))
        assertEquals("2026 年 10 月", review.period.label)
    }

    @Test
    fun yearReviewComparesWithLastYear() {
        val review = buildReview(entries, emptyList(), ReviewPeriod.Year(2026))
        assertEquals(1_499, review.total)
        assertNull(review.comparisonText(::formatMoney)) // 去年沒有紀錄
        assertEquals("2026 年", review.period.label)
    }

    @Test
    fun emptyMonth() {
        val review = buildReview(entries, emptyList(), ReviewPeriod.Month(YearMonth.of(2026, 8)))
        assertTrue(review.isEmpty)
        assertNull(review.bestDay)
    }

    @Test
    fun monthlyCardShowsLastMonthUntilDismissed() {
        val nov3 = LocalDate.of(2026, 11, 3)
        assertEquals(oct, monthlyCardMonth(nov3, dismissed = null) { true })
        assertNull(monthlyCardMonth(nov3, dismissed = oct) { true })
        assertEquals(oct, monthlyCardMonth(nov3, dismissed = YearMonth.of(2026, 9)) { true })
        assertNull(monthlyCardMonth(nov3, dismissed = null) { false }) // 上個月沒有紀錄
        // 一月看的是去年十二月
        assertEquals(YearMonth.of(2026, 12), monthlyCardMonth(LocalDate.of(2027, 1, 1), null) { true })
    }

    @Test
    fun splashNeverRepeatsTheSameQuoteTwiceInARow() {
        val random = Random(42)
        var last: Int? = null
        repeat(200) {
            val next = pickQuoteIndex(SPLASH_QUOTES.size, last, random)
            assertNotEquals(last, next)
            assertTrue(next in SPLASH_QUOTES.indices)
            last = next
        }
        assertEquals(10, SPLASH_QUOTES.size)
    }
}
