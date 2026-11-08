package io.github.corgi8cm.safemoney.data.backup

import io.github.corgi8cm.safemoney.data.db.Goal
import io.github.corgi8cm.safemoney.data.db.GoalStatus
import io.github.corgi8cm.safemoney.data.db.Saving
import io.github.corgi8cm.safemoney.data.db.SavingAllocation
import io.github.corgi8cm.safemoney.data.db.SavingWithAllocations
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeParseException

/** 備份檔內容有問題時丟出，[message] 可直接顯示給使用者。 */
class BackupException(message: String) : Exception(message)

/** 解析並驗證過的備份內容，可以直接寫進資料庫。 */
data class BackupData(
    val exportedAt: Instant,
    val appVersion: String?,
    val goals: List<Goal>,
    val savings: List<SavingWithAllocations>,
    /** null = 備份檔沒有這項（舊版備份），還原時保留手機上的快捷原因。 */
    val quickReasons: List<String>? = null,
)

// ---- JSON 檔案格式（第 1 版）----
// 日期用 ISO-8601 字串（2026-11-08、2026-11-08T12:00:00Z），方便人看，也不受時區影響。

@Serializable
internal data class BackupFile(
    val format: String,
    val version: Int,
    val exportedAt: String,
    val appVersion: String? = null,
    val goals: List<GoalJson> = emptyList(),
    val savings: List<SavingJson> = emptyList(),
    val quickReasons: List<String>? = null,
)

@Serializable
internal data class GoalJson(
    val id: Long,
    val name: String,
    val amount: Long,
    val purchaseDate: String,
    val note: String? = null,
    val color: Int? = null,
    val status: String,
    val createdAt: String,
    val completedAt: String? = null,
    val emoji: String? = null,
)

@Serializable
internal data class SavingJson(
    val id: Long,
    val reason: String,
    val date: String,
    val createdAt: String,
    val allocations: List<AllocationJson>,
)

@Serializable
internal data class AllocationJson(val goalId: Long, val amount: Long)

/** 備份檔的編碼 / 解碼與驗證，不碰資料庫。 */
object BackupCodec {
    const val FORMAT_ID = "safemoney-backup"
    const val CURRENT_VERSION = 1

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true // 之後的版本加欄位，舊版 App 仍可讀
        encodeDefaults = true
    }

    fun encode(data: BackupData): String {
        val file = BackupFile(
            format = FORMAT_ID,
            version = CURRENT_VERSION,
            exportedAt = data.exportedAt.toString(),
            appVersion = data.appVersion,
            goals = data.goals.sortedBy { it.id }.map { g ->
                GoalJson(
                    id = g.id,
                    name = g.name,
                    amount = g.amount,
                    purchaseDate = g.purchaseDate.toString(),
                    note = g.note,
                    color = g.color,
                    status = g.status.name,
                    createdAt = g.createdAt.toString(),
                    completedAt = g.completedAt?.toString(),
                    emoji = g.emoji,
                )
            },
            savings = data.savings.sortedBy { it.saving.id }.map { s ->
                SavingJson(
                    id = s.saving.id,
                    reason = s.saving.reason,
                    date = s.saving.date.toString(),
                    createdAt = s.saving.createdAt.toString(),
                    allocations = s.allocations.sortedBy { it.goalId }.map { AllocationJson(it.goalId, it.amount) },
                )
            },
            quickReasons = data.quickReasons,
        )
        return json.encodeToString(BackupFile.serializer(), file)
    }

    /** 解析並完整驗證；任何問題都丟 [BackupException]，不會回傳不一致的資料。 */
    fun decode(text: String): BackupData {
        val file = try {
            json.decodeFromString(BackupFile.serializer(), text)
        } catch (e: SerializationException) {
            throw BackupException("這不是有效的備份檔（無法讀取內容）")
        } catch (e: IllegalArgumentException) {
            throw BackupException("這不是有效的備份檔（無法讀取內容）")
        }
        if (file.format != FORMAT_ID) throw BackupException("這不是省回來的備份檔")
        if (file.version > CURRENT_VERSION) throw BackupException("這個備份檔來自較新版本的省回來，請先更新 App")
        if (file.version < 1) throw BackupException("備份檔版本不正確")

        val goals = file.goals.map { it.toEntity() }
        val goalIds = HashSet<Long>()
        for (g in goals) {
            check(g.id > 0) { "目標編號不正確" }
            check(goalIds.add(g.id)) { "目標編號重複（${g.id}）" }
            check(g.name.isNotBlank()) { "有目標沒有名稱" }
            check(g.amount > 0) { "「${g.name}」的金額必須大於 0" }
        }

        val savingIds = HashSet<Long>()
        val savings = file.savings.map { s ->
            check(s.id > 0) { "紀錄編號不正確" }
            check(savingIds.add(s.id)) { "紀錄編號重複（${s.id}）" }
            check(s.allocations.isNotEmpty()) { "有紀錄沒有分配給任何目標" }
            check(s.allocations.all { it.amount > 0 }) { "有紀錄的金額不大於 0" }
            check(s.allocations.map { it.goalId }.toSet().size == s.allocations.size) { "有紀錄重複分配給同一個目標" }
            check(s.allocations.all { it.goalId in goalIds }) { "有紀錄分配給不存在的目標" }
            SavingWithAllocations(
                saving = Saving(id = s.id, reason = s.reason, date = parseDate(s.date), createdAt = parseInstant(s.createdAt)),
                allocations = s.allocations.map { SavingAllocation(s.id, it.goalId, it.amount) },
            )
        }

        return BackupData(
            exportedAt = parseInstant(file.exportedAt),
            appVersion = file.appVersion,
            goals = goals,
            savings = savings,
            quickReasons = file.quickReasons,
        )
    }

    private fun GoalJson.toEntity() = Goal(
        id = id,
        name = name,
        amount = amount,
        purchaseDate = parseDate(purchaseDate),
        note = note,
        color = color,
        status = GoalStatus.entries.firstOrNull { it.name == status }
            ?: throw BackupException("備份檔內容有誤：不認得的目標狀態「$status」"),
        createdAt = parseInstant(createdAt),
        completedAt = completedAt?.let(::parseInstant),
        emoji = emoji,
    )

    private inline fun check(condition: Boolean, message: () -> String) {
        if (!condition) throw BackupException("備份檔內容有誤：${message()}")
    }

    private fun parseDate(text: String): LocalDate = try {
        LocalDate.parse(text)
    } catch (e: DateTimeParseException) {
        throw BackupException("備份檔內容有誤：日期格式不正確（$text）")
    }

    private fun parseInstant(text: String): Instant = try {
        Instant.parse(text)
    } catch (e: DateTimeParseException) {
        throw BackupException("備份檔內容有誤：時間格式不正確（$text）")
    }
}
