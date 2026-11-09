package io.github.corgi8cm.safemoney.data.backup

import io.github.corgi8cm.safemoney.data.db.Goal
import io.github.corgi8cm.safemoney.data.db.GoalStatus
import io.github.corgi8cm.safemoney.data.db.RecurringSaving
import io.github.corgi8cm.safemoney.data.db.Saving
import io.github.corgi8cm.safemoney.data.db.SavingAllocation
import io.github.corgi8cm.safemoney.data.db.SavingWithAllocations
import io.github.corgi8cm.safemoney.data.db.newUid
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
    /** null = 備份檔沒有這項（1.1.0 以前的備份），還原時保留手機上的定期省錢。 */
    val recurring: List<RecurringSaving>? = null,
)

// ---- JSON 檔案格式 ----
// 日期用 ISO-8601 字串（2026-11-08、2026-11-08T12:00:00Z），方便人看，也不受時區影響。
// 第 1 版：基本資料。第 2 版：目標與紀錄加上 uid（跨裝置唯一編號）與 updatedAt；讀第 1 版時自動補上。
// 1.1.0 起第 2 版多了選用的 recurring（定期省錢）。版本號不變，所以 1.0 的 App 仍能匯入（只是忽略這項）。

@Serializable
internal data class BackupFile(
    val format: String,
    val version: Int,
    val exportedAt: String,
    val appVersion: String? = null,
    val goals: List<GoalJson> = emptyList(),
    val savings: List<SavingJson> = emptyList(),
    val quickReasons: List<String>? = null,
    val recurring: List<RecurringJson>? = null,
)

@Serializable
internal data class RecurringJson(
    val uid: String,
    val reason: String,
    val amount: Long,
    val days: Int,
    val startDate: String,
    val endDate: String? = null,
    val handledThrough: String? = null,
    val createdAt: String,
    val updatedAt: String,
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
    val uid: String? = null,
    val updatedAt: String? = null,
)

@Serializable
internal data class SavingJson(
    val id: Long,
    val reason: String,
    val date: String,
    val createdAt: String,
    val allocations: List<AllocationJson>,
    val uid: String? = null,
    val updatedAt: String? = null,
)

@Serializable
internal data class AllocationJson(val goalId: Long, val amount: Long)

/** 備份檔的編碼 / 解碼與驗證，不碰資料庫。 */
object BackupCodec {
    const val FORMAT_ID = "safemoney-backup"
    const val CURRENT_VERSION = 2

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
                    uid = g.uid,
                    updatedAt = g.updatedAt.toString(),
                )
            },
            savings = data.savings.sortedBy { it.saving.id }.map { s ->
                SavingJson(
                    id = s.saving.id,
                    reason = s.saving.reason,
                    date = s.saving.date.toString(),
                    createdAt = s.saving.createdAt.toString(),
                    allocations = s.allocations.sortedBy { it.goalId }.map { AllocationJson(it.goalId, it.amount) },
                    uid = s.saving.uid,
                    updatedAt = s.saving.updatedAt.toString(),
                )
            },
            quickReasons = data.quickReasons,
            recurring = data.recurring?.sortedBy { it.createdAt }?.map { r ->
                RecurringJson(
                    uid = r.uid,
                    reason = r.reason,
                    amount = r.amount,
                    days = r.days,
                    startDate = r.startDate.toString(),
                    endDate = r.endDate?.toString(),
                    handledThrough = r.handledThrough?.toString(),
                    createdAt = r.createdAt.toString(),
                    updatedAt = r.updatedAt.toString(),
                )
            },
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
        check(goals.map { it.uid }.toSet().size == goals.size) { "目標的 uid 重複" }

        val savingIds = HashSet<Long>()
        val savings = file.savings.map { s ->
            check(s.id > 0) { "紀錄編號不正確" }
            check(savingIds.add(s.id)) { "紀錄編號重複（${s.id}）" }
            check(s.allocations.isNotEmpty()) { "有紀錄沒有分配給任何目標" }
            check(s.allocations.all { it.amount > 0 }) { "有紀錄的金額不大於 0" }
            check(s.allocations.map { it.goalId }.toSet().size == s.allocations.size) { "有紀錄重複分配給同一個目標" }
            check(s.allocations.all { it.goalId in goalIds }) { "有紀錄分配給不存在的目標" }
            val createdAt = parseInstant(s.createdAt)
            SavingWithAllocations(
                saving = Saving(
                    id = s.id,
                    reason = s.reason,
                    date = parseDate(s.date),
                    createdAt = createdAt,
                    uid = s.uid?.takeIf { it.isNotBlank() } ?: newUid(),
                    updatedAt = s.updatedAt?.let(::parseInstant) ?: createdAt,
                ),
                allocations = s.allocations.map { SavingAllocation(s.id, it.goalId, it.amount) },
            )
        }

        check(savings.map { it.saving.uid }.toSet().size == savings.size) { "紀錄的 uid 重複" }

        val recurring = file.recurring?.map { r ->
            check(r.uid.isNotBlank()) { "定期省錢沒有 uid" }
            check(r.reason.isNotBlank()) { "定期省錢沒有原因" }
            check(r.amount > 0) { "定期省錢「${r.reason}」的金額必須大於 0" }
            check(r.days in 1..127) { "定期省錢「${r.reason}」的日子不正確" }
            RecurringSaving(
                reason = r.reason,
                amount = r.amount,
                days = r.days,
                startDate = parseDate(r.startDate),
                endDate = r.endDate?.let(::parseDate),
                handledThrough = r.handledThrough?.let(::parseDate),
                createdAt = parseInstant(r.createdAt),
                uid = r.uid,
                updatedAt = parseInstant(r.updatedAt),
            )
        }
        recurring?.let { list -> check(list.map { it.uid }.toSet().size == list.size) { "定期省錢的 uid 重複" } }

        return BackupData(
            exportedAt = parseInstant(file.exportedAt),
            appVersion = file.appVersion,
            goals = goals,
            savings = savings,
            quickReasons = file.quickReasons,
            recurring = recurring,
        )
    }

    private fun GoalJson.toEntity(): Goal {
        val created = parseInstant(createdAt)
        return Goal(
            id = id,
            name = name,
            amount = amount,
            purchaseDate = parseDate(purchaseDate),
            note = note,
            color = color,
            status = GoalStatus.entries.firstOrNull { it.name == status }
                ?: throw BackupException("備份檔內容有誤：不認得的目標狀態「$status」"),
            createdAt = created,
            completedAt = completedAt?.let(::parseInstant),
            emoji = emoji,
            uid = uid?.takeIf { it.isNotBlank() } ?: newUid(),
            updatedAt = updatedAt?.let(::parseInstant) ?: created,
        )
    }

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
