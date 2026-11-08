package io.github.corgi8cm.safemoney.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/*
 * 同步檔（存在使用者 Google 雲端硬碟的 App 專用資料夾）。
 * 跟備份檔不同：用 uid 互相對應（本機 id 每支手機不同），並帶著刪除紀錄（墓碑）。
 * 時間一律是 epoch 毫秒，日期是 ISO 字串（2026-11-08）。
 */

@Serializable
data class SyncDoc(
    val format: String = FORMAT_ID,
    val version: Int = CURRENT_VERSION,
    val goals: List<SyncGoal> = emptyList(),
    val savings: List<SyncSaving> = emptyList(),
    val quickReasons: List<SyncReason> = emptyList(),
    val deletions: List<SyncDeletion> = emptyList(),
) {
    /** 排序過的內容，用來比較「有沒有變」。 */
    fun normalized(): SyncDoc = copy(
        goals = goals.sortedBy { it.uid },
        savings = savings.map { s -> s.copy(allocations = s.allocations.sortedBy { it.goalUid }) }.sortedBy { it.uid },
        quickReasons = quickReasons.sortedBy { it.uid },
        deletions = deletions.sortedBy { it.uid },
    )

    companion object {
        const val FORMAT_ID = "safemoney-sync"
        const val CURRENT_VERSION = 1
    }
}

@Serializable
data class SyncGoal(
    val uid: String,
    val name: String,
    val amount: Long,
    val purchaseDate: String,
    val note: String? = null,
    val color: Int? = null,
    val emoji: String? = null,
    /** 只同步「封存」；進行中 / 已回本由各裝置依已省金額自動判斷。 */
    val archived: Boolean = false,
    val createdAt: Long,
    val completedAt: Long? = null,
    val updatedAt: Long,
)

@Serializable
data class SyncSaving(
    val uid: String,
    val reason: String,
    val date: String,
    val createdAt: Long,
    val updatedAt: Long,
    val allocations: List<SyncAllocation>,
)

@Serializable
data class SyncAllocation(val goalUid: String, val amount: Long)

@Serializable
data class SyncReason(val uid: String, val text: String, val position: Int, val updatedAt: Long)

@Serializable
data class SyncDeletion(val uid: String, val entity: String, val deletedAt: Long)

/** 同步檔有問題（格式不對、來自較新版本的 App）。[message] 可直接顯示給使用者。 */
class SyncFormatException(message: String) : Exception(message)

object SyncCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(doc: SyncDoc): String = json.encodeToString(SyncDoc.serializer(), doc.normalized())

    fun decode(text: String): SyncDoc {
        val doc = try {
            json.decodeFromString(SyncDoc.serializer(), text)
        } catch (e: SerializationException) {
            throw SyncFormatException("雲端的同步檔無法讀取")
        } catch (e: IllegalArgumentException) {
            throw SyncFormatException("雲端的同步檔無法讀取")
        }
        if (doc.format != SyncDoc.FORMAT_ID) throw SyncFormatException("雲端的同步檔格式不正確")
        if (doc.version > SyncDoc.CURRENT_VERSION) {
            throw SyncFormatException("雲端資料來自較新版本的省回來，請先更新這支手機上的 App")
        }
        return doc.normalized()
    }
}
