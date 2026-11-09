package io.github.corgi8cm.safemoney.domain

import io.github.corgi8cm.safemoney.data.db.Saving
import io.github.corgi8cm.safemoney.data.db.SavingAllocation
import io.github.corgi8cm.safemoney.data.db.SavingWithAllocations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

class CsvExportTest {
    private fun saving(id: Long, date: LocalDate, reason: String, vararg parts: Pair<Long, Long>) =
        SavingWithAllocations(
            Saving(id = id, reason = reason, date = date, createdAt = Instant.EPOCH),
            parts.map { (goal, amount) -> SavingAllocation(id, goal, amount) },
        )

    @Test
    fun oneLinePerAllocationOldestFirst() {
        val csv = CsvExport.build(
            listOf(
                saving(2, LocalDate.of(2026, 11, 2), "沒叫外送", 1L to 60L, 2L to 40L),
                saving(1, LocalDate.of(2026, 11, 1), "飲料", 1L to 50L),
            ),
            mapOf(1L to "大衣", 2L to "耳機"),
        )
        assertTrue(csv.startsWith("﻿"))
        val lines = csv.removePrefix("﻿").split("\r\n").filter { it.isNotEmpty() }
        assertEquals(
            listOf(
                "日期,原因,目標,金額",
                "2026-11-01,飲料,大衣,50",
                "2026-11-02,沒叫外送,大衣,60",
                "2026-11-02,沒叫外送,耳機,40",
            ),
            lines,
        )
    }

    @Test
    fun escapesCommasQuotesAndFormulas() {
        assertEquals("\"飲料, 便當\"", CsvExport.escape("飲料, 便當"))
        assertEquals("\"說\"\"好\"\"的\"", CsvExport.escape("說\"好\"的"))
        assertEquals("'=1+1", CsvExport.escape("=1+1"))
        assertEquals("一般", CsvExport.escape("一般"))
    }

    @Test
    fun fileNames() {
        assertEquals("safemoney-2026-10.csv", CsvExport.fileName(YearMonth.of(2026, 10)))
        assertEquals("safemoney-all-20261109.csv", CsvExport.fileName(null, LocalDate.of(2026, 11, 9)))
    }
}
