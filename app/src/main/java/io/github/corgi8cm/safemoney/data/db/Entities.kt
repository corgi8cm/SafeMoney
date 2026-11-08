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
import java.util.UUID

/** 跨裝置唯一的編號（同步時用來對應同一筆資料；本機的 id 每支手機不同）。 */
fun newUid(): String = UUID.randomUUID().toString()

enum class GoalStatus { ACTIVE, COMPLETED, ARCHIVED }

/** 回本目標：一筆想省回來的花費。金額單位為「元」。 */
@Entity(tableName = "goal", indices = [Index(value = ["uid"], unique = true)])
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
    /** DB 第 3 版新增（同步用）。 */
    @ColumnInfo(defaultValue = "''") val uid: String = newUid(),
    /** 使用者最後一次修改的時間（同步時以較新的為準）。DB 第 3 版新增。 */
    @ColumnInfo(defaultValue = "0") val updatedAt: Instant = Instant.EPOCH,
)

/** 記錄頁的快捷原因。DB 第 2 版新增；排序依使用次數，次數相同時依 [position]。 */
@Entity(
    tableName = "quick_reason",
    indices = [Index(value = ["text"], unique = true), Index(value = ["uid"], unique = true)],
)
data class QuickReason(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val position: Int,
    @ColumnInfo(defaultValue = "''") val uid: String = newUid(),
    @ColumnInfo(defaultValue = "0") val updatedAt: Instant = Instant.EPOCH,
)

/**
 * 一次省錢的事件（例：沒叫外送）。
 * 總金額不另存，而是其所有 [SavingAllocation] 的加總，這樣刪除目標時資料不會不一致。
 */
@Entity(tableName = "saving", indices = [Index(value = ["uid"], unique = true)])
data class Saving(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val reason: String,
    val date: LocalDate,
    val createdAt: Instant,
    @ColumnInfo(defaultValue = "''") val uid: String = newUid(),
    /** 原因、日期或分配有變動的時間。DB 第 3 版新增。 */
    @ColumnInfo(defaultValue = "0") val updatedAt: Instant = Instant.EPOCH,
)

/** 刪除紀錄（墓碑）：同步時告訴其他裝置這筆資料已被刪除。DB 第 3 版新增。 */
@Entity(tableName = "deletion")
data class Deletion(
    @PrimaryKey val uid: String,
    val entity: String,
    val deletedAt: Instant,
) {
    companion object {
        const val GOAL = "goal"
        const val SAVING = "saving"
        const val QUICK_REASON = "quick_reason"
    }
}

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
