package io.github.corgi8cm.safemoney.data

import androidx.room.withTransaction
import io.github.corgi8cm.safemoney.data.backup.BackupData
import io.github.corgi8cm.safemoney.data.db.Goal
import io.github.corgi8cm.safemoney.data.db.GoalStatus
import io.github.corgi8cm.safemoney.data.db.GoalWithSaved
import io.github.corgi8cm.safemoney.data.db.DEFAULT_QUICK_REASONS
import io.github.corgi8cm.safemoney.data.db.QuickReason
import io.github.corgi8cm.safemoney.data.db.SafeMoneyDatabase
import io.github.corgi8cm.safemoney.data.db.Saving
import io.github.corgi8cm.safemoney.data.db.SavingAllocation
import io.github.corgi8cm.safemoney.data.db.SavingWithAllocations
import io.github.corgi8cm.safemoney.domain.AllocationInput
import io.github.corgi8cm.safemoney.domain.Allocations
import io.github.corgi8cm.safemoney.domain.NearestGoal
import io.github.corgi8cm.safemoney.domain.Summary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate

/**
 * App 唯一的資料入口。負責：
 * - 驗證輸入（目標金額、分配規則）
 * - 多表寫入包在同一個 transaction
 * - 已省金額達標時自動把目標標記為已回本，不足時退回進行中
 */
class SafeMoneyRepository(
    private val db: SafeMoneyDatabase,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    private val goalDao = db.goalDao()
    private val savingDao = db.savingDao()
    private val reasonDao = db.quickReasonDao()

    // ---- 讀取 ----

    fun observeGoals(): Flow<List<GoalWithSaved>> = goalDao.observeAllWithSaved()

    fun observeGoal(id: Long): Flow<GoalWithSaved?> = goalDao.observeWithSaved(id)

    fun observeSavingsForGoal(goalId: Long): Flow<List<SavingWithAllocations>> =
        savingDao.observeForGoal(goalId)

    fun observeAllSavings(): Flow<List<SavingWithAllocations>> = savingDao.observeAll()

    suspend fun getSaving(id: Long): SavingWithAllocations? = savingDao.getWithAllocations(id)

    fun observeSummary(): Flow<Summary> = observeGoals().map(::summarize)

    /** 目前所有目標（給主畫面小工具一次性讀取）。 */
    suspend fun goalsSnapshot(): List<GoalWithSaved> = goalDao.getAllWithSaved()

    /** 今天是否已有省錢紀錄（給每日提醒判斷要不要打擾）。 */
    suspend fun hasSavingOn(date: LocalDate): Boolean = savingDao.countOnDate(date) > 0

    /** 最接近回本的進行中目標。 */
    suspend fun nearestActiveGoal(): NearestGoal? =
        goalDao.getAllWithSaved()
            .filter { it.goal.status == GoalStatus.ACTIVE }
            .minByOrNull { it.goal.amount - it.saved }
            ?.let { NearestGoal(it.goal.name, (it.goal.amount - it.saved).coerceAtLeast(0)) }

    // ---- 目標 ----

    suspend fun addGoal(
        name: String,
        amount: Long,
        purchaseDate: LocalDate,
        note: String? = null,
        color: Int? = null,
        emoji: String? = null,
    ): Long {
        validateGoal(name, amount)
        return goalDao.insert(
            Goal(
                name = name.trim(),
                amount = amount,
                purchaseDate = purchaseDate,
                note = note?.trim()?.ifEmpty { null },
                color = color,
                createdAt = clock.instant(),
                emoji = emoji?.trim()?.ifEmpty { null },
            )
        )
    }

    /**
     * 更新名稱、金額、日期、備註、顏色。
     * 狀態由 repository 自行維護，傳入的 status / completedAt / createdAt 會被忽略（封存請用 [setArchived]）。
     */
    suspend fun updateGoal(goal: Goal) {
        db.withTransaction {
            validateGoal(goal.name, goal.amount)
            val existing = requireNotNull(goalDao.getById(goal.id)) { "找不到目標 ${goal.id}" }
            goalDao.update(
                goal.copy(
                    name = goal.name.trim(),
                    note = goal.note?.trim()?.ifEmpty { null },
                    emoji = goal.emoji?.trim()?.ifEmpty { null },
                    status = existing.status,
                    completedAt = existing.completedAt,
                    createdAt = existing.createdAt,
                )
            )
            refreshCompletion(listOf(goal.id))
        }
    }

    suspend fun setArchived(goalId: Long, archived: Boolean) {
        db.withTransaction {
            val goal = requireNotNull(goalDao.getById(goalId)) { "找不到目標 $goalId" }
            if (archived) {
                goalDao.update(goal.copy(status = GoalStatus.ARCHIVED))
            } else if (goal.status == GoalStatus.ARCHIVED) {
                goalDao.update(goal.copy(status = GoalStatus.ACTIVE, completedAt = null))
                refreshCompletion(listOf(goalId))
            }
        }
    }

    /**
     * 刪除目標及其分配；因此變成沒有任何分配的省錢紀錄也會一併刪除。
     * @return 刪除前的內容，給「復原」用；目標不存在時為 null
     */
    suspend fun deleteGoal(goalId: Long): DeletedGoal? = db.withTransaction {
        val goal = goalDao.getById(goalId) ?: return@withTransaction null
        val savings = savingDao.getForGoal(goalId)
        goalDao.delete(goal)
        savingDao.deleteOrphanSavings()
        DeletedGoal(goal, savings)
    }

    /** 復原 [deleteGoal]：放回目標（原編號、原狀態），以及它在各紀錄中的分配。 */
    suspend fun undoDeleteGoal(deleted: DeletedGoal) {
        db.withTransaction {
            goalDao.insert(deleted.goal)
            for (item in deleted.savings) {
                if (savingDao.getWithAllocations(item.saving.id) == null) {
                    // 整筆紀錄當時跟著被刪掉了，原樣放回
                    savingDao.insertSaving(item.saving)
                    savingDao.insertAllocations(item.allocations.filter { goalDao.getById(it.goalId) != null })
                } else {
                    // 拆分的紀錄還在，只放回分給這個目標的部分
                    savingDao.insertAllocations(item.allocations.filter { it.goalId == deleted.goal.id })
                }
            }
            refreshCompletion(listOf(deleted.goal.id))
        }
    }

    // ---- 省錢紀錄 ----

    /** 回傳新紀錄 id，以及因這筆紀錄而剛達標的目標（給畫面放慶祝動畫）。 */
    suspend fun addSaving(
        reason: String,
        date: LocalDate,
        allocations: List<AllocationInput>,
    ): SaveResult = db.withTransaction {
        Allocations.validate(allocations)
        requireGoalsExist(allocations)
        val savingId = savingDao.insertSaving(
            Saving(reason = reason.trim(), date = date, createdAt = clock.instant())
        )
        savingDao.insertAllocations(allocations.map { SavingAllocation(savingId, it.goalId, it.amount) })
        SaveResult(savingId, refreshCompletion(allocations.map { it.goalId }))
    }

    /** 回傳因這次修改而剛達標的目標。 */
    suspend fun updateSaving(
        savingId: Long,
        reason: String,
        date: LocalDate,
        allocations: List<AllocationInput>,
    ): List<Long> = db.withTransaction {
        Allocations.validate(allocations)
        requireGoalsExist(allocations)
        val existing = requireNotNull(savingDao.getWithAllocations(savingId)) { "找不到紀錄 $savingId" }
        savingDao.updateSaving(existing.saving.copy(reason = reason.trim(), date = date))
        savingDao.deleteAllocations(savingId)
        savingDao.insertAllocations(allocations.map { SavingAllocation(savingId, it.goalId, it.amount) })
        refreshCompletion(existing.allocations.map { it.goalId } + allocations.map { it.goalId })
    }

    /**
     * 把 [fromGoalId] 超出目標的金額轉給 [toGoalId]。
     *
     * 不新增紀錄，而是把來源目標「最新的」幾筆分配改分給目標：例如最後一筆 500 元裡有 300 是多省的，
     * 這筆就變成「來源 200 + 目標 300」的拆分。紀錄總額不變，來源剛好停在 100%。
     *
     * @return 轉移的金額，以及因此剛達標的目標
     */
    suspend fun transferOverflow(fromGoalId: Long, toGoalId: Long): TransferResult = db.withTransaction {
        require(fromGoalId != toGoalId) { "不能轉給同一個目標" }
        val from = requireNotNull(goalDao.getWithSaved(fromGoalId)) { "找不到目標 $fromGoalId" }
        val to = requireNotNull(goalDao.getById(toGoalId)) { "找不到目標 $toGoalId" }
        require(to.status != GoalStatus.ARCHIVED) { "不能轉給已封存的目標" }

        val overflow = (from.saved - from.goal.amount).coerceAtLeast(0)
        var remaining = overflow
        for (allocation in savingDao.allocationsForGoalNewestFirst(fromGoalId)) {
            if (remaining == 0L) break
            val moved = minOf(allocation.amount, remaining)
            if (moved == allocation.amount) {
                savingDao.deleteAllocation(allocation)
            } else {
                savingDao.updateAllocation(allocation.copy(amount = allocation.amount - moved))
            }
            val existingTarget = savingDao.getAllocation(allocation.savingId, toGoalId)
            if (existingTarget == null) {
                savingDao.insertAllocation(SavingAllocation(allocation.savingId, toGoalId, moved))
            } else {
                savingDao.updateAllocation(existingTarget.copy(amount = existingTarget.amount + moved))
            }
            remaining -= moved
        }
        TransferResult(transferred = overflow, completedGoalIds = refreshCompletion(listOf(fromGoalId, toGoalId)))
    }

    /** @return 刪除前的紀錄，給「復原」用；不存在時為 null */
    suspend fun deleteSaving(savingId: Long): SavingWithAllocations? = db.withTransaction {
        val existing = savingDao.getWithAllocations(savingId) ?: return@withTransaction null
        savingDao.deleteSaving(savingId) // 分配會因 CASCADE 一併刪除
        refreshCompletion(existing.allocations.map { it.goalId })
        existing
    }

    /** 復原 [deleteSaving]（原編號）。期間被刪掉的目標就不再分配給它；全部目標都不在時不放回。 */
    suspend fun undoDeleteSaving(deleted: SavingWithAllocations) {
        db.withTransaction {
            val allocations = deleted.allocations.filter { goalDao.getById(it.goalId) != null }
            if (allocations.isEmpty() || savingDao.getWithAllocations(deleted.saving.id) != null) return@withTransaction
            savingDao.insertSaving(deleted.saving)
            savingDao.insertAllocations(allocations)
            refreshCompletion(allocations.map { it.goalId })
        }
    }

    // ---- 快捷原因 ----

    /** 記錄頁用：最近 [USAGE_WINDOW_DAYS] 天用得越多越前面。 */
    fun observeQuickReasonsByUsage(today: LocalDate = LocalDate.now(clock)): Flow<List<String>> =
        reasonDao.observeByUsage(today.minusDays(USAGE_WINDOW_DAYS - 1)).map { list -> list.map { it.text } }

    /** 設定頁用：依加入順序。 */
    fun observeQuickReasons(): Flow<List<QuickReason>> = reasonDao.observeAll()

    suspend fun addQuickReason(text: String) {
        val clean = validateReason(text)
        db.withTransaction {
            require(reasonDao.getByText(clean) == null) { "「$clean」已經在清單裡了" }
            reasonDao.insert(QuickReason(text = clean, position = reasonDao.maxPosition() + 1))
        }
    }

    suspend fun renameQuickReason(id: Long, text: String) {
        val clean = validateReason(text)
        db.withTransaction {
            val existing = reasonDao.getAll().firstOrNull { it.id == id } ?: return@withTransaction
            val sameText = reasonDao.getByText(clean)
            require(sameText == null || sameText.id == id) { "「$clean」已經在清單裡了" }
            reasonDao.update(existing.copy(text = clean))
        }
    }

    suspend fun deleteQuickReason(id: Long) = reasonDao.delete(id)

    /** 復原刪除的快捷原因；期間又新增了同名的就不重複放回。 */
    suspend fun restoreQuickReason(reason: QuickReason) {
        db.withTransaction {
            if (reasonDao.getByText(reason.text) == null) reasonDao.insert(reason)
        }
    }

    /** 換回預設的快捷原因（已經記下的紀錄不受影響）。 */
    suspend fun resetQuickReasons() = replaceQuickReasons(DEFAULT_QUICK_REASONS)

    private suspend fun replaceQuickReasons(texts: List<String>) {
        db.withTransaction {
            reasonDao.deleteAll()
            reasonDao.insertAll(
                texts.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
                    .mapIndexed { index, text -> QuickReason(text = text, position = index) }
            )
        }
    }

    private fun validateReason(text: String): String {
        val clean = text.trim()
        require(clean.isNotEmpty()) { "原因不能空白" }
        require(clean.length <= MAX_REASON_LENGTH) { "原因最多 $MAX_REASON_LENGTH 個字" }
        return clean
    }

    // ---- 備份與還原 ----

    /** 目前所有資料的快照（給匯出用）。 */
    suspend fun snapshot(appVersion: String?): BackupData = db.withTransaction {
        BackupData(
            exportedAt = clock.instant(),
            appVersion = appVersion,
            goals = goalDao.getAll(),
            savings = savingDao.getAllWithAllocations(),
            quickReasons = reasonDao.getAll().map { it.text },
        )
    }

    /**
     * 用備份內容**取代**目前所有資料（保留原本的編號）。
     * 全部在同一個 transaction 裡，失敗時原資料不受影響。
     * 還原後依已省金額重新同步目標狀態（不會觸發慶祝）。
     */
    suspend fun restore(data: BackupData) {
        db.withTransaction {
            savingDao.deleteAll()
            goalDao.deleteAll()
            goalDao.insertAll(data.goals)
            savingDao.insertSavings(data.savings.map { it.saving })
            savingDao.insertAllocations(data.savings.flatMap { it.allocations })
            refreshCompletion(data.goals.map { it.id })
            // 舊版備份檔沒有快捷原因，這時保留手機上目前的
            data.quickReasons?.let { replaceQuickReasons(it) }
        }
    }

    // ---- 內部 ----

    private fun validateGoal(name: String, amount: Long) {
        require(name.isNotBlank()) { "目標名稱不能空白" }
        require(amount > 0) { "目標金額必須大於 0" }
    }

    private suspend fun requireGoalsExist(allocations: List<AllocationInput>) {
        for (allocation in allocations) {
            requireNotNull(goalDao.getById(allocation.goalId)) { "找不到目標 ${allocation.goalId}" }
        }
    }

    /**
     * 依已省金額同步目標狀態：進行中 → 已回本，或已回本 → 進行中。封存的目標不動。
     * @return 這次剛從進行中變成已回本的目標
     */
    private suspend fun refreshCompletion(goalIds: Collection<Long>): List<Long> {
        val newlyCompleted = mutableListOf<Long>()
        for (id in goalIds.distinct()) {
            val row = goalDao.getWithSaved(id) ?: continue
            val goal = row.goal
            val reached = row.saved >= goal.amount
            if (goal.status == GoalStatus.ACTIVE && reached) {
                goalDao.update(goal.copy(status = GoalStatus.COMPLETED, completedAt = clock.instant()))
                newlyCompleted += id
            } else if (goal.status == GoalStatus.COMPLETED && !reached) {
                goalDao.update(goal.copy(status = GoalStatus.ACTIVE, completedAt = null))
            }
        }
        return newlyCompleted
    }
}

/** 未封存目標的總覽。 */
fun summarize(goals: List<GoalWithSaved>): Summary {
    val visible = goals.filter { it.goal.status != GoalStatus.ARCHIVED }
    return Summary(
        goalCount = visible.size,
        completedCount = visible.count { it.goal.status == GoalStatus.COMPLETED },
        totalAmount = visible.sumOf { it.goal.amount },
        totalSaved = visible.sumOf { it.saved },
    )
}

/** 快捷原因依最近幾天的使用次數排序。 */
const val USAGE_WINDOW_DAYS = 90L

/** 跟記錄頁的原因欄位上限一致。 */
const val MAX_REASON_LENGTH = 40

/** 被刪除的目標，以及刪除前分配給它的所有紀錄（含當時完整的分配）。 */
data class DeletedGoal(val goal: Goal, val savings: List<SavingWithAllocations>)

data class SaveResult(val savingId: Long, val completedGoalIds: List<Long>)

data class TransferResult(val transferred: Long, val completedGoalIds: List<Long>)
