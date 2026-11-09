package io.github.corgi8cm.safemoney.data

import androidx.room.withTransaction
import io.github.corgi8cm.safemoney.data.backup.BackupData
import io.github.corgi8cm.safemoney.data.db.Goal
import io.github.corgi8cm.safemoney.data.db.GoalStatus
import io.github.corgi8cm.safemoney.data.db.GoalWithSaved
import io.github.corgi8cm.safemoney.data.db.DEFAULT_QUICK_REASONS
import io.github.corgi8cm.safemoney.data.db.Deletion
import io.github.corgi8cm.safemoney.data.db.QuickReason
import io.github.corgi8cm.safemoney.data.db.RecurringSaving
import io.github.corgi8cm.safemoney.data.db.SafeMoneyDatabase
import io.github.corgi8cm.safemoney.data.db.Saving
import io.github.corgi8cm.safemoney.data.db.SavingAllocation
import io.github.corgi8cm.safemoney.data.db.SavingWithAllocations
import io.github.corgi8cm.safemoney.domain.AllocationInput
import io.github.corgi8cm.safemoney.domain.Allocations
import io.github.corgi8cm.safemoney.domain.NearestGoal
import io.github.corgi8cm.safemoney.domain.Recurrence
import io.github.corgi8cm.safemoney.domain.Summary
import io.github.corgi8cm.safemoney.sync.SyncAllocation
import io.github.corgi8cm.safemoney.sync.SyncDeletion
import io.github.corgi8cm.safemoney.sync.SyncDoc
import io.github.corgi8cm.safemoney.sync.SyncGoal
import io.github.corgi8cm.safemoney.sync.SyncReason
import io.github.corgi8cm.safemoney.sync.SyncRecurring
import io.github.corgi8cm.safemoney.sync.SyncSaving
import java.time.Instant
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
    private val deletionDao = db.deletionDao()
    private val recurringDao = db.recurringDao()

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
                updatedAt = clock.instant(),
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
                    uid = existing.uid,
                    updatedAt = clock.instant(),
                )
            )
            refreshCompletion(listOf(goal.id))
        }
    }

    suspend fun setArchived(goalId: Long, archived: Boolean) {
        db.withTransaction {
            val goal = requireNotNull(goalDao.getById(goalId)) { "找不到目標 $goalId" }
            if (archived) {
                goalDao.update(goal.copy(status = GoalStatus.ARCHIVED, updatedAt = clock.instant()))
            } else if (goal.status == GoalStatus.ARCHIVED) {
                goalDao.update(goal.copy(status = GoalStatus.ACTIVE, completedAt = null, updatedAt = clock.instant()))
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
        val now = clock.instant()
        // 只分給這個目標的紀錄會整筆消失；拆分的紀錄則是內容（分配）有變動
        val (orphaned, split) = savings.partition { s -> s.allocations.all { it.goalId == goalId } }
        deletionDao.insertAll(
            listOf(Deletion(goal.uid, Deletion.GOAL, now)) +
                orphaned.map { Deletion(it.saving.uid, Deletion.SAVING, now) }
        )
        split.forEach { savingDao.updateSaving(it.saving.copy(updatedAt = now)) }
        goalDao.delete(goal)
        savingDao.deleteOrphanSavings()
        DeletedGoal(goal, savings)
    }

    /** 復原 [deleteGoal]：放回目標（原編號、原狀態），以及它在各紀錄中的分配。 */
    suspend fun undoDeleteGoal(deleted: DeletedGoal) {
        db.withTransaction {
            // 復原也是一次修改：修改時間要比刪除新，其他裝置同步時才不會又刪掉
            val now = clock.instant()
            goalDao.insert(deleted.goal.copy(updatedAt = now))
            for (item in deleted.savings) {
                val current = savingDao.getWithAllocations(item.saving.id)
                if (current == null) {
                    // 整筆紀錄當時跟著被刪掉了，原樣放回
                    savingDao.insertSaving(item.saving.copy(updatedAt = now))
                    savingDao.insertAllocations(item.allocations.filter { goalDao.getById(it.goalId) != null })
                } else {
                    // 拆分的紀錄還在，只放回分給這個目標的部分
                    savingDao.updateSaving(current.saving.copy(updatedAt = now))
                    savingDao.insertAllocations(item.allocations.filter { it.goalId == deleted.goal.id })
                }
            }
            clearDeletions(listOf(deleted.goal.uid) + deleted.savings.map { it.saving.uid })
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
            Saving(reason = reason.trim(), date = date, createdAt = clock.instant(), updatedAt = clock.instant())
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
        savingDao.updateSaving(existing.saving.copy(reason = reason.trim(), date = date, updatedAt = clock.instant()))
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
            savingDao.getWithAllocations(allocation.savingId)?.let {
                savingDao.updateSaving(it.saving.copy(updatedAt = clock.instant()))
            }
        }
        TransferResult(transferred = overflow, completedGoalIds = refreshCompletion(listOf(fromGoalId, toGoalId)))
    }

    /** @return 刪除前的紀錄，給「復原」用；不存在時為 null */
    suspend fun deleteSaving(savingId: Long): SavingWithAllocations? = db.withTransaction {
        val existing = savingDao.getWithAllocations(savingId) ?: return@withTransaction null
        deletionDao.insertAll(listOf(Deletion(existing.saving.uid, Deletion.SAVING, clock.instant())))
        savingDao.deleteSaving(savingId) // 分配會因 CASCADE 一併刪除
        refreshCompletion(existing.allocations.map { it.goalId })
        existing
    }

    /** 復原 [deleteSaving]（原編號）。期間被刪掉的目標就不再分配給它；全部目標都不在時不放回。 */
    suspend fun undoDeleteSaving(deleted: SavingWithAllocations) {
        db.withTransaction {
            val allocations = deleted.allocations.filter { goalDao.getById(it.goalId) != null }
            if (allocations.isEmpty() || savingDao.getWithAllocations(deleted.saving.id) != null) return@withTransaction
            savingDao.insertSaving(deleted.saving.copy(updatedAt = clock.instant()))
            savingDao.insertAllocations(allocations)
            clearDeletions(listOf(deleted.saving.uid))
            refreshCompletion(allocations.map { it.goalId })
        }
    }

    // ---- 定期省錢 ----

    fun observeRecurring(): Flow<List<RecurringSaving>> = recurringDao.observeAll()

    suspend fun getRecurring(id: Long): RecurringSaving? = recurringDao.getById(id)

    /** 有沒有還沒回本（而且沒封存）的目標。 */
    suspend fun hasActiveGoal(): Boolean = goalDao.getAllWithSaved().any { it.goal.status == GoalStatus.ACTIVE }

    suspend fun addRecurring(
        reason: String,
        amount: Long,
        days: Int,
        startDate: LocalDate,
        endDate: LocalDate?,
    ): Long = db.withTransaction {
        Recurrence.validate(reason, amount, days, startDate, endDate, LocalDate.now(clock), maxReasonLength = MAX_REASON_LENGTH)
        require(hasActiveGoal()) { "要先有還沒回本的目標，才能設定定期省錢" }
        val now = clock.instant()
        recurringDao.insert(
            RecurringSaving(
                reason = reason.trim(),
                amount = amount,
                days = days,
                startDate = startDate,
                endDate = endDate,
                createdAt = now,
                updatedAt = now,
            )
        )
    }

    /**
     * 修改設定。已經開始的設定不能改開始日（[startDate] 會被忽略）；已經記下的紀錄不受影響。
     */
    suspend fun updateRecurring(
        id: Long,
        reason: String,
        amount: Long,
        days: Int,
        startDate: LocalDate,
        endDate: LocalDate?,
    ) {
        db.withTransaction {
            val existing = requireNotNull(recurringDao.getById(id)) { "找不到這個定期省錢" }
            val today = LocalDate.now(clock)
            val started = existing.startDate <= today
            val start = if (started) existing.startDate else startDate
            Recurrence.validate(reason, amount, days, start, endDate, today, allowPastStart = started, maxReasonLength = MAX_REASON_LENGTH)
            recurringDao.update(
                existing.copy(
                    reason = reason.trim(),
                    amount = amount,
                    days = days,
                    startDate = start,
                    endDate = endDate,
                    updatedAt = clock.instant(),
                )
            )
        }
    }

    /** 移除設定：已經記下的紀錄保留，之後不再跳通知。@return 刪除前的內容，給「復原」用 */
    suspend fun deleteRecurring(id: Long): RecurringSaving? = db.withTransaction {
        val existing = recurringDao.getById(id) ?: return@withTransaction null
        deletionDao.insertAll(listOf(Deletion(existing.uid, Deletion.RECURRING, clock.instant())))
        recurringDao.delete(id)
        existing
    }

    suspend fun undoDeleteRecurring(rule: RecurringSaving) {
        db.withTransaction {
            if (recurringDao.getById(rule.id) != null) return@withTransaction
            recurringDao.insert(rule.copy(updatedAt = clock.instant()))
            clearDeletions(listOf(rule.uid))
        }
    }

    /**
     * 使用者關掉定期省錢的通知：把到 [today] 為止到期的日子全部記下來。
     *
     * 每一筆都照「一項一項省回來」分配（最早建立、還沒回本的目標先補滿，超出的給下一個）。
     * 已經記過（另一支手機同步過來）或被使用者刪掉的那一天不會再記。
     * 沒有任何還沒回本的目標時不記，並移除所有定期省錢設定。
     */
    suspend fun recordPendingRecurring(today: LocalDate = LocalDate.now(clock)): RecurringRecordResult =
        db.withTransaction {
            val rules = recurringDao.getAll()
            val pending = Recurrence.pending(rules, today)
            if (pending.isEmpty()) return@withTransaction RecurringRecordResult.NothingDue
            val now = clock.instant()
            val active = goalDao.getAllWithSaved()
                .filter { it.goal.status == GoalStatus.ACTIVE }
                .sortedWith(compareBy<GoalWithSaved> { it.goal.createdAt }.thenBy { it.goal.id })
            if (active.isEmpty()) {
                deletionDao.insertAll(rules.map { Deletion(it.uid, Deletion.RECURRING, now) })
                recurringDao.deleteAll()
                return@withTransaction RecurringRecordResult.RemovedNoGoals(rules.size)
            }

            val remaining = LinkedHashMap<Long, Long>()
            active.forEach { remaining[it.goal.id] = it.goal.amount - it.saved }
            val touched = LinkedHashSet<Long>()
            var count = 0
            var total = 0L
            for (item in pending) {
                val uid = Recurrence.savingUid(item.rule.uid, item.date)
                if (savingDao.countByUid(uid) > 0 || deletionDao.countByUid(uid) > 0) continue
                val parts = Allocations.fillInOrder(item.rule.amount, remaining.map { it.key to it.value })
                val at = Recurrence.recordedAt(item.date, clock.zone)
                val savingId = savingDao.insertSaving(
                    Saving(reason = item.rule.reason, date = item.date, createdAt = at, uid = uid, updatedAt = at)
                )
                savingDao.insertAllocations(parts.map { SavingAllocation(savingId, it.goalId, it.amount) })
                parts.forEach {
                    remaining[it.goalId] = remaining.getValue(it.goalId) - it.amount
                    touched += it.goalId
                }
                count++
                total += item.rule.amount
            }
            for (rule in rules) {
                val last = pending.lastOrNull { it.rule.id == rule.id }?.date ?: continue
                recurringDao.update(rule.copy(handledThrough = last, updatedAt = now))
            }
            RecurringRecordResult.Recorded(count, total, refreshCompletion(touched))
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
            reasonDao.insert(QuickReason(text = clean, position = reasonDao.maxPosition() + 1, updatedAt = clock.instant()))
        }
    }

    suspend fun renameQuickReason(id: Long, text: String) {
        val clean = validateReason(text)
        db.withTransaction {
            val existing = reasonDao.getAll().firstOrNull { it.id == id } ?: return@withTransaction
            val sameText = reasonDao.getByText(clean)
            require(sameText == null || sameText.id == id) { "「$clean」已經在清單裡了" }
            reasonDao.update(existing.copy(text = clean, updatedAt = clock.instant()))
        }
    }

    suspend fun deleteQuickReason(id: Long) {
        db.withTransaction {
            val existing = reasonDao.getAll().firstOrNull { it.id == id } ?: return@withTransaction
            deletionDao.insertAll(listOf(Deletion(existing.uid, Deletion.QUICK_REASON, clock.instant())))
            reasonDao.delete(id)
        }
    }

    /** 復原刪除的快捷原因；期間又新增了同名的就不重複放回。 */
    suspend fun restoreQuickReason(reason: QuickReason) {
        db.withTransaction {
            if (reasonDao.getByText(reason.text) == null) {
                reasonDao.insert(reason.copy(updatedAt = clock.instant()))
                clearDeletions(listOf(reason.uid))
            }
        }
    }

    /** 換回預設的快捷原因（已經記下的紀錄不受影響）。 */
    suspend fun resetQuickReasons() = replaceQuickReasons(DEFAULT_QUICK_REASONS)

    private suspend fun replaceQuickReasons(texts: List<String>) {
        db.withTransaction {
            val now = clock.instant()
            deletionDao.insertAll(reasonDao.getAll().map { Deletion(it.uid, Deletion.QUICK_REASON, now) })
            reasonDao.deleteAll()
            reasonDao.insertAll(
                texts.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
                    .mapIndexed { index, text -> QuickReason(text = text, position = index, updatedAt = now) }
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
            recurring = recurringDao.getAll(),
        )
    }

    /**
     * 用備份內容**取代**目前所有資料（保留原本的編號）。
     * 全部在同一個 transaction 裡，失敗時原資料不受影響。
     * 還原後依已省金額重新同步目標狀態（不會觸發慶祝）。
     */
    suspend fun restore(data: BackupData) {
        db.withTransaction {
            // 還原 = 取代：手機上有、備份檔沒有的資料，對其他裝置來說就是被刪除了
            val now = clock.instant()
            val keepGoals = data.goals.map { it.uid }.toSet()
            val keepSavings = data.savings.map { it.saving.uid }.toSet()
            deletionDao.insertAll(
                goalDao.getAll().filter { it.uid !in keepGoals }.map { Deletion(it.uid, Deletion.GOAL, now) } +
                    savingDao.getAllWithAllocations().filter { it.saving.uid !in keepSavings }
                        .map { Deletion(it.saving.uid, Deletion.SAVING, now) }
            )
            clearDeletions((keepGoals + keepSavings).toList())
            savingDao.deleteAll()
            goalDao.deleteAll()
            goalDao.insertAll(data.goals)
            savingDao.insertSavings(data.savings.map { it.saving })
            savingDao.insertAllocations(data.savings.flatMap { it.allocations })
            refreshCompletion(data.goals.map { it.id })
            // 舊版備份檔沒有快捷原因，這時保留手機上目前的
            data.quickReasons?.let { replaceQuickReasons(it) }
            // 1.1.0 以前的備份檔沒有定期省錢，這時也保留手機上目前的
            data.recurring?.let { rules ->
                val keep = rules.map { it.uid }.toSet()
                deletionDao.insertAll(
                    recurringDao.getAll().filter { it.uid !in keep }.map { Deletion(it.uid, Deletion.RECURRING, now) }
                )
                clearDeletions(keep.toList())
                recurringDao.deleteAll()
                recurringDao.insertAll(rules)
            }
        }
    }

    // ---- 同步用 ----

    /** 刪除紀錄（墓碑），同步時告訴其他裝置哪些資料被刪了。 */
    suspend fun deletions(): List<Deletion> = deletionDao.getAll()

    /** 這支手機目前的資料，轉成同步檔的格式（用 uid 互相對應）。 */
    suspend fun exportSyncDoc(): SyncDoc = db.withTransaction { readSyncDoc() }

    private suspend fun readSyncDoc(): SyncDoc {
        val goals = goalDao.getAll()
        val uidOf = goals.associate { it.id to it.uid }
        return SyncDoc(
            goals = goals.map { g ->
                SyncGoal(
                    uid = g.uid,
                    name = g.name,
                    amount = g.amount,
                    purchaseDate = g.purchaseDate.toString(),
                    note = g.note,
                    color = g.color,
                    emoji = g.emoji,
                    archived = g.status == GoalStatus.ARCHIVED,
                    createdAt = g.createdAt.toEpochMilli(),
                    completedAt = g.completedAt?.toEpochMilli(),
                    updatedAt = g.updatedAt.toEpochMilli(),
                )
            },
            savings = savingDao.getAllWithAllocations().map { s ->
                SyncSaving(
                    uid = s.saving.uid,
                    reason = s.saving.reason,
                    date = s.saving.date.toString(),
                    createdAt = s.saving.createdAt.toEpochMilli(),
                    updatedAt = s.saving.updatedAt.toEpochMilli(),
                    allocations = s.allocations.mapNotNull { a -> uidOf[a.goalId]?.let { SyncAllocation(it, a.amount) } },
                )
            },
            quickReasons = reasonDao.getAll().map { SyncReason(it.uid, it.text, it.position, it.updatedAt.toEpochMilli()) },
            recurring = recurringDao.getAll().map { r ->
                SyncRecurring(
                    uid = r.uid,
                    reason = r.reason,
                    amount = r.amount,
                    days = r.days,
                    startDate = r.startDate.toString(),
                    endDate = r.endDate?.toString(),
                    handledThrough = r.handledThrough?.toString(),
                    createdAt = r.createdAt.toEpochMilli(),
                    updatedAt = r.updatedAt.toEpochMilli(),
                )
            },
            deletions = deletionDao.getAll().map { SyncDeletion(it.uid, it.entity, it.deletedAt.toEpochMilli()) },
        ).normalized()
    }

    /**
     * 把合併後的同步結果寫進這支手機（保留本機 id，用 uid 對應），不會產生新的墓碑。
     * [expected] 是合併前讀到的本機資料；如果期間使用者又改了東西，就不寫入並回傳 false，
     * 讓同步重新來一次，避免蓋掉剛剛的修改。
     */
    suspend fun applySyncDoc(doc: SyncDoc, expected: SyncDoc): Boolean = db.withTransaction {
        if (readSyncDoc() != expected.normalized()) return@withTransaction false

        val keepGoals = doc.goals.map { it.uid }.toSet()
        val keepSavings = doc.savings.map { it.uid }.toSet()
        // 先刪掉不在結果裡的（目標刪除時分配會 CASCADE 一起刪）
        savingDao.getAllWithAllocations().filter { it.saving.uid !in keepSavings }.forEach { savingDao.deleteSaving(it.saving.id) }
        goalDao.getAll().filter { it.uid !in keepGoals }.forEach { goalDao.delete(it) }

        val localGoals = goalDao.getAll().associateBy { it.uid }
        val goalIds = HashMap<String, Long>()
        for (g in doc.goals) {
            val existing = localGoals[g.uid]
            val status = when {
                g.archived -> GoalStatus.ARCHIVED
                existing != null && existing.status != GoalStatus.ARCHIVED -> existing.status
                else -> GoalStatus.ACTIVE
            }
            val entity = Goal(
                id = existing?.id ?: 0,
                name = g.name,
                amount = g.amount,
                purchaseDate = LocalDate.parse(g.purchaseDate),
                note = g.note,
                color = g.color,
                status = status,
                createdAt = Instant.ofEpochMilli(g.createdAt),
                completedAt = g.completedAt?.let(Instant::ofEpochMilli),
                emoji = g.emoji,
                uid = g.uid,
                updatedAt = Instant.ofEpochMilli(g.updatedAt),
            )
            goalIds[g.uid] = if (existing == null) goalDao.insert(entity) else entity.id.also { goalDao.update(entity) }
        }

        val localSavings = savingDao.getAllWithAllocations().associateBy { it.saving.uid }
        for (s in doc.savings) {
            val existing = localSavings[s.uid]
            val entity = Saving(
                id = existing?.saving?.id ?: 0,
                reason = s.reason,
                date = LocalDate.parse(s.date),
                createdAt = Instant.ofEpochMilli(s.createdAt),
                uid = s.uid,
                updatedAt = Instant.ofEpochMilli(s.updatedAt),
            )
            val savingId = if (existing == null) {
                savingDao.insertSaving(entity)
            } else {
                savingDao.updateSaving(entity)
                savingDao.deleteAllocations(entity.id)
                entity.id
            }
            savingDao.insertAllocations(
                s.allocations.mapNotNull { a -> goalIds[a.goalUid]?.let { SavingAllocation(savingId, it, a.amount) } }
            )
        }

        // 快捷原因：整批換掉（保留原本的 id），避免兩個原因互換名稱時撞到「名稱不可重複」
        val localReasons = reasonDao.getAll().associateBy { it.uid }
        reasonDao.deleteAll()
        reasonDao.insertAll(
            doc.quickReasons.map { r ->
                QuickReason(
                    id = localReasons[r.uid]?.id ?: 0,
                    text = r.text,
                    position = r.position,
                    uid = r.uid,
                    updatedAt = Instant.ofEpochMilli(r.updatedAt),
                )
            }
        )

        // 定期省錢：不在結果裡的刪掉，其餘依 uid 新增或更新（保留本機 id）
        val localRules = recurringDao.getAll().associateBy { it.uid }
        val keepRules = doc.recurring.map { it.uid }.toSet()
        localRules.values.filter { it.uid !in keepRules }.forEach { recurringDao.delete(it.id) }
        for (r in doc.recurring) {
            val existing = localRules[r.uid]
            val entity = RecurringSaving(
                id = existing?.id ?: 0,
                reason = r.reason,
                amount = r.amount,
                days = r.days,
                startDate = LocalDate.parse(r.startDate),
                endDate = r.endDate?.let(LocalDate::parse),
                handledThrough = r.handledThrough?.let(LocalDate::parse),
                createdAt = Instant.ofEpochMilli(r.createdAt),
                uid = r.uid,
                updatedAt = Instant.ofEpochMilli(r.updatedAt),
            )
            if (existing == null) recurringDao.insert(entity) else recurringDao.update(entity)
        }

        val localDeletions = deletionDao.getAll().map { it.uid }
        clearDeletions(localDeletions)
        deletionDao.insertAll(doc.deletions.map { Deletion(it.uid, it.entity, Instant.ofEpochMilli(it.deletedAt)) })

        refreshCompletion(goalIds.values)
        true
    }

    // ---- 內部 ----

    /** 資料又回來了（復原、還原），移除它們的刪除紀錄。分批避免超過 SQLite 參數上限。 */
    private suspend fun clearDeletions(uids: List<String>) {
        uids.chunked(500).forEach { deletionDao.deleteByUids(it) }
    }

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
                // 同步來的目標可能已帶著另一支手機的回本時間，沿用它，兩邊才一致
                goalDao.update(goal.copy(status = GoalStatus.COMPLETED, completedAt = goal.completedAt ?: clock.instant()))
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

/** 關掉定期省錢通知後的結果。 */
sealed interface RecurringRecordResult {
    /** 沒有到期的（例如另一支手機已經記過）。 */
    data object NothingDue : RecurringRecordResult

    /** 記下了 [count] 筆，共 [total] 元；[completedGoalIds] 是因此剛回本的目標。 */
    data class Recorded(val count: Int, val total: Long, val completedGoalIds: List<Long>) : RecurringRecordResult

    /** 所有目標都已經回本，沒有記，並移除了 [removed] 個定期省錢設定。 */
    data class RemovedNoGoals(val removed: Int) : RecurringRecordResult
}
