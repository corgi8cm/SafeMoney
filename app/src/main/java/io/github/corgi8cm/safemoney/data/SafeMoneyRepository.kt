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

    suspend fun addSaving(
        reason: String,
        date: LocalDate,
        allocations: List<AllocationInput>,
    ): Long = db.withTransaction {
        Allocations.validate(allocations)
        requireGoalsExist(allocations)
        val savingId = savingDao.insertSaving(
            Saving(reason = reason.trim(), date = date, createdAt = clock.instant())
        )
        savingDao.insertAllocations(allocations.map { SavingAllocation(savingId, it.goalId, it.amount) })
        refreshCompletion(allocations.map { it.goalId })
        savingId
    }

    suspend fun updateSaving(
        savingId: Long,
        reason: String,
        date: LocalDate,
        allocations: List<AllocationInput>,
    ) {
        db.withTransaction {
            Allocations.validate(allocations)
            requireGoalsExist(allocations)
            val existing = requireNotNull(savingDao.getWithAllocations(savingId)) { "找不到紀錄 $savingId" }
            savingDao.updateSaving(existing.saving.copy(reason = reason.trim(), date = date))
            savingDao.deleteAllocations(savingId)
            savingDao.insertAllocations(allocations.map { SavingAllocation(savingId, it.goalId, it.amount) })
            refreshCompletion(existing.allocations.map { it.goalId } + allocations.map { it.goalId })
        }
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

    /** 依已省金額同步目標狀態：進行中 → 已回本，或已回本 → 進行中。封存的目標不動。 */
    private suspend fun refreshCompletion(goalIds: Collection<Long>) {
        for (id in goalIds.toSet()) {
            val row = goalDao.getWithSaved(id) ?: continue
            val goal = row.goal
            val reached = row.saved >= goal.amount
            if (goal.status == GoalStatus.ACTIVE && reached) {
                goalDao.update(goal.copy(status = GoalStatus.COMPLETED, completedAt = clock.instant()))
            } else if (goal.status == GoalStatus.COMPLETED && !reached) {
                goalDao.update(goal.copy(status = GoalStatus.ACTIVE, completedAt = null))
            }
        }
    }
}
