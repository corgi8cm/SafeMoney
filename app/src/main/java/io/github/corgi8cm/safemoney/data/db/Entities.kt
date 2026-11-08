package io.github.corgi8cm.safemoney.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import io.github.corgi8cm.safemoney.domain.Progress
import java.time.Instant
import java.time.LocalDate

enum class GoalStatus { ACTIVE, COMPLETED, ARCHIVED }

/** 回本目標：一筆想省回來的花費。金額單位為「元」。 */
@Entity(tableName = "goal")
data class Goal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val amount: Long,
    val purchaseDate: LocalDate,
    val note: String? = null,
    val color: Int? = null,
    val status: GoalStatus = GoalStatus.ACTIVE,
    val createdAt: Instant,
    val completedAt: Instant? = null,
    /** 目標的小圖示（一個 emoji），沒有時顯示名稱第一個字。DB 第 2 版新增。 */
    val emoji: String? = null,
)

/** 記錄頁的快捷原因。DB 第 2 版新增；排序依使用次數，次數相同時依 [position]。 */
@Entity(tableName = "quick_reason", indices = [Index(value = ["text"], unique = true)])
data class QuickReason(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val position: Int,
)

/**
 * 一次省錢的事件（例：沒叫外送）。
 * 總金額不另存，而是其所有 [SavingAllocation] 的加總，這樣刪除目標時資料不會不一致。
 */
@Entity(tableName = "saving")
data class Saving(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val reason: String,
    val date: LocalDate,
    val createdAt: Instant,
)

/** 一筆省錢紀錄分給某個目標的金額。一筆紀錄可拆給多個目標。 */
@Entity(
    tableName = "saving_allocation",
    primaryKeys = ["savingId", "goalId"],
    foreignKeys = [
        ForeignKey(
            entity = Saving::class,
            parentColumns = ["id"],
            childColumns = ["savingId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Goal::class,
            parentColumns = ["id"],
            childColumns = ["goalId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("goalId")],
)
data class SavingAllocation(
    val savingId: Long,
    val goalId: Long,
    val amount: Long,
)

/** 目標 + 已省金額（由分配加總而來）。 */
data class GoalWithSaved(
    @Embedded val goal: Goal,
    @ColumnInfo(name = "saved") val saved: Long,
)

fun GoalWithSaved.progress(): Progress = Progress(saved = saved, target = goal.amount)

/** 省錢紀錄 + 其所有分配。 */
data class SavingWithAllocations(
    @Embedded val saving: Saving,
    @Relation(parentColumn = "id", entityColumn = "savingId")
    val allocations: List<SavingAllocation>,
)

fun SavingWithAllocations.total(): Long = allocations.sumOf { it.amount }
