package io.github.corgi8cm.safemoney.data.backup

import io.github.corgi8cm.safemoney.data.db.Goal
import io.github.corgi8cm.safemoney.data.db.GoalStatus
import io.github.corgi8cm.safemoney.data.db.Saving
import io.github.corgi8cm.safemoney.data.db.SavingAllocation
import io.github.corgi8cm.safemoney.data.db.SavingWithAllocations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class BackupCodecTest {
    private val now = Instant.parse("2026-11-08T12:00:00Z")

    private val sample = BackupData(
        exportedAt = now,
        appVersion = "0.4.0",
        goals = listOf(
            Goal(1, "大衣", 7_000, LocalDate.of(2026, 10, 1), note = "冬天買的", status = GoalStatus.ACTIVE, createdAt = now),
            Goal(
                3, "耳機", 100, LocalDate.of(2026, 9, 1), color = 0xFF2E7D5B.toInt(),
                status = GoalStatus.COMPLETED, createdAt = now, completedAt = now,
            ),
        ),
        savings = listOf(
            SavingWithAllocations(
                Saving(5, "沒叫外送", LocalDate.of(2026, 11, 7), now),
                listOf(SavingAllocation(5, 1, 60), SavingAllocation(5, 3, 100)),
            ),
        ),
    )

    private fun assertRejected(json: String, messagePart: String) {
        try {
            BackupCodec.decode(json)
            fail("should be rejected")
        } catch (e: BackupException) {
            assertTrue("「${e.message}」應包含「$messagePart」", e.message!!.contains(messagePart))
        }
    }

    private fun minimal(goals: String = "[]", savings: String = "[]", format: String = "safemoney-backup", version: Int = 1) =
        """{"format":"$format","version":$version,"exportedAt":"2026-11-08T12:00:00Z","goals":$goals,"savings":$savings}"""

    private val okGoal = """{"id":1,"name":"大衣","amount":7000,"purchaseDate":"2026-10-01","status":"ACTIVE","createdAt":"2026-11-08T12:00:00Z"}"""

    @Test
    fun roundTripKeepsEverything() {
        val text = BackupCodec.encode(sample)
        assertTrue(text.contains("\"format\": \"safemoney-backup\""))
        assertTrue(text.contains("\"purchaseDate\": \"2026-10-01\""))
        assertEquals(sample, BackupCodec.decode(text))
    }

    @Test
    fun emptyBackupIsValid() {
        val data = BackupCodec.decode(minimal())
        assertEquals(emptyList<Goal>(), data.goals)
        assertEquals(now, data.exportedAt)
    }

    @Test
    fun ignoresUnknownFieldsFromFutureMinorChanges() {
        val goal = okGoal.replace("}", ""","icon":"coat"}""")
        val data = BackupCodec.decode(minimal(goals = "[$goal]").replace("{\"format\"", "{\"extra\":1,\"format\""))
        assertEquals("大衣", data.goals.single().name)
    }

    @Test
    fun rejectsGarbage() {
        assertRejected("not json", "不是有效的備份檔")
        assertRejected("""{"hello":"world"}""", "不是有效的備份檔")
    }

    @Test
    fun rejectsOtherFormatsAndNewerVersions() {
        assertRejected(minimal(format = "other-app"), "不是省回來的備份檔")
        assertRejected(minimal(version = 2), "較新版本")
    }

    @Test
    fun rejectsInvalidGoals() {
        assertRejected(minimal(goals = "[$okGoal,$okGoal]"), "目標編號重複")
        assertRejected(minimal(goals = "[${okGoal.replace("7000", "0")}]"), "金額必須大於 0")
        assertRejected(minimal(goals = "[${okGoal.replace("ACTIVE", "LOST")}]"), "不認得的目標狀態")
        assertRejected(minimal(goals = "[${okGoal.replace("2026-10-01", "2026/10/01")}]"), "日期格式不正確")
    }

    @Test
    fun rejectsInvalidSavings() {
        fun saving(allocations: String) =
            """[{"id":1,"reason":"飲料","date":"2026-11-08","createdAt":"2026-11-08T12:00:00Z","allocations":$allocations}]"""
        val goals = "[$okGoal]"
        assertRejected(minimal(goals, saving("[]")), "沒有分配")
        assertRejected(minimal(goals, saving("""[{"goalId":9,"amount":10}]""")), "不存在的目標")
        assertRejected(minimal(goals, saving("""[{"goalId":1,"amount":0}]""")), "金額不大於 0")
        assertRejected(
            minimal(goals, saving("""[{"goalId":1,"amount":5},{"goalId":1,"amount":5}]""")),
            "重複分配",
        )
    }
}
