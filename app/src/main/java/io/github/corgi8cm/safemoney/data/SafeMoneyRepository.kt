package io.github.corgi8cm.safemoney.data

import androidx.room.withTransaction
import io.github.corgi8cm.safemoney.data.db.Goal
import io.github.corgi8cm.safemoney.data.db.GoalStatus
import io.github.corgi8cm.safemoney.data.db.GoalWithSaved
import io.github.corgi8cm.safemoney.data.db.SafeMoneyDatabase
import io.github.corgi8cm.safemoney.data.db.Saving
import io.github.corgi8cm.safemoney.data.db.SavingAllocation
import io.github.corgi8cm.safemoney.data.db.SavingWithAllocations
import io.github.corgi8cm.safemoney.domain.AllocationInput
import io.github.corgi8cm.safemoney.domain.Allocations
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

    // ---- 讀取 ----

    fun observeGoals(): Flow<List<GoalWithSaved>> = goalDao.observeAllWithSaved()

    fun observeGoal(id: Long): Flow<GoalWithSaved?> = goalDao.observeWithSaved(id)

    fun observeSavingsForGoal(goalId: Long): Flow<List<SavingWithAllocations>> =
        savingDao.observeForGoal(goalId)

    fun observeAllSavings(): Flow<List<SavingWithAllocations>> = savingDao.observeAll()

    suspend fun getSaving(id: Long): SavingWithAllocations? = savingDao.getWithAllocations(id)

    fun observeSummary(): Flow<Summary> = observeGoals().map { goals ->
        val visible = goals.filter { it.goal.status != GoalStatus.ARCHIVED }
        Summary(
            goalCount = visible.size,
            completedCount = visible.count { it.goal.status == GoalStatus.COMPLETED },
            totalAmount = visible.sumOf { it.goal.amount },
            totalSaved = visible.sumOf { it.saved },
        )
    }

    // ---- 目標 ----

    suspend fun addGoal(
        name: String,
        amount: Long,
        purchaseDate: LocalDate,
        note: String? = null,
        color: Int? = null,
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

    /** 刪除目標及其分配；因此變成沒有任何分配的省錢紀錄也會一併刪除。 */
    suspend fun deleteGoal(goalId: Long) {
        db.withTransaction {
            val goal = goalDao.getById(goalId)
            if (goal != null) {
                goalDao.delete(goal)
                savingDao.deleteOrphanSavings()
            }
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

    suspend fun deleteSaving(savingId: Long) {
        db.withTransaction {
            val existing = savingDao.getWithAllocations(savingId)
            if (existing != null) {
                savingDao.deleteSaving(savingId) // 分配會因 CASCADE 一併刪除
                refreshCompletion(existing.allocations.map { it.goalId })
            }
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

data class SaveResult(val savingId: Long, val completedGoalIds: List<Long>)

data class TransferResult(val transferred: Long, val completedGoalIds: List<Long>)
