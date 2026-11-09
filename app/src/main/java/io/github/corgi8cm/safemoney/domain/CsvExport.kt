package io.github.corgi8cm.safemoney.domain

import io.github.corgi8cm.safemoney.data.db.SavingWithAllocations
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * 匯出成試算表（CSV）。每個分配一行：日期、原因、目標、金額。
 * 一筆紀錄拆給兩個目標就是兩行，所有「金額」加起來剛好是總共省下的錢。
 */
object CsvExport {
    const val MIME_TYPE = "text/csv"

    /** 檔案開頭的標記，讓 Excel 知道是 UTF-8，中文才不會變亂碼。 */
    private const val BOM = "﻿"
    private const val NEWLINE = "\r\n"

    fun build(savings: List<SavingWithAllocations>, goalNames: Map<Long, String>): String {
        val sb = StringBuilder(BOM)
        sb.append(listOf("日期", "原因", "目標", "金額").joinToString(",")).append(NEWLINE)
        savings.sortedWith(compareBy<SavingWithAllocations> { it.saving.date }.thenBy { it.saving.id }).forEach { s ->
            s.allocations.forEach { a ->
                sb.append(
                    listOf(
                        s.saving.date.toString(),
                        escape(s.saving.reason.trim()),
                        escape(goalNames[a.goalId].orEmpty()),
                        a.amount.toString(),
                    ).joinToString(",")
                ).append(NEWLINE)
            }
        }
        return sb.toString()
    }

    /** 「safemoney-2026-10.csv」或「safemoney-all-20261109.csv」 */
    fun fileName(month: YearMonth?, today: LocalDate = LocalDate.now()): String =
        if (month != null) "safemoney-$month.csv" else "safemoney-all-${today.format(DateTimeFormatter.BASIC_ISO_DATE)}.csv"

    /**
     * 有逗號、引號、換行時用引號包起來。開頭是 = + - @ 的文字前面加 '，
     * 避免試算表把使用者打的原因當成公式執行。
     */
    internal fun escape(text: String): String {
        val safe = if (text.isNotEmpty() && text[0] in "=+-@\t\r") "'$text" else text
        return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + safe.replace("\"", "\"\"") + "\""
        } else {
            safe
        }
    }
}
