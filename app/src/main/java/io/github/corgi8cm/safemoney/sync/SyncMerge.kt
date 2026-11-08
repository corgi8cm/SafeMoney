package io.github.corgi8cm.safemoney.sync

import io.github.corgi8cm.safemoney.data.db.Deletion
import java.time.Duration
import java.time.Instant

/** 墓碑保留這麼久；超過一年沒同步的裝置，被刪的資料可能會再出現（可接受的取捨）。 */
val TOMBSTONE_RETENTION: Duration = Duration.ofDays(365)

/**
 * 合併本機與雲端的資料，兩邊用同樣的規則算出同一個結果：
 *
 * 1. 同一個 uid 以「最後修改時間」較新的版本為準。
 * 2. 刪除時間 ≥ 最後修改時間 → 已刪除（同時發生時刪除優先）；刪除後又修改 → 保留修改。
 * 3. 分配給已刪除目標的部分拿掉；一筆紀錄的分配全部被拿掉時，整筆視為刪除。
 * 4. 兩支手機各自新增了同名的快捷原因，只留一個（uid 較小的）。
 * 5. 超過 [TOMBSTONE_RETENTION] 的墓碑清掉。
 *
 * 同樣的輸入不論順序都得到同樣的結果，所以兩支手機輪流同步最後會一致。
 */
fun mergeSync(local: SyncDoc, remote: SyncDoc?, now: Instant): SyncDoc {
    val docs = listOfNotNull(local, remote)
    val tombstones = HashMap<String, SyncDeletion>()
    docs.flatMap { it.deletions }.forEach { d ->
        val current = tombstones[d.uid]
        if (current == null || d.deletedAt > current.deletedAt) tombstones[d.uid] = d
    }

    fun <T> pickLatest(items: List<T>, uid: (T) -> String, updatedAt: (T) -> Long): List<T> =
        items.groupBy(uid).values.mapNotNull { versions ->
            // 修改時間相同時取內容排序較前的，確保兩邊結果一樣
            val latest = versions.sortedWith(compareByDescending(updatedAt).thenBy { it.toString() }).first()
            val deleted = tombstones[uid(latest)]
            if (deleted != null && deleted.deletedAt >= updatedAt(latest)) null else latest
        }

    val goals = pickLatest(docs.flatMap { it.goals }, { it.uid }, { it.updatedAt })
    var savings = pickLatest(docs.flatMap { it.savings }, { it.uid }, { it.updatedAt })
    var reasons = pickLatest(docs.flatMap { it.quickReasons }, { it.uid }, { it.updatedAt })

    // 還活著的資料，不需要它的墓碑
    val alive = (goals.map { it.uid } + savings.map { it.uid } + reasons.map { it.uid }).toSet()
    alive.forEach { tombstones.remove(it) }

    // 3. 分配給已刪除（或不存在）目標的部分拿掉
    val goalUids = goals.map { it.uid }.toSet()
    savings = savings.mapNotNull { s ->
        val kept = s.allocations.filter { it.goalUid in goalUids }
        when {
            kept.size == s.allocations.size -> s
            kept.isNotEmpty() -> s.copy(allocations = kept)
            else -> {
                val goneAt = s.allocations.mapNotNull { tombstones[it.goalUid]?.deletedAt }.maxOrNull() ?: 0
                tombstones[s.uid] = SyncDeletion(s.uid, Deletion.SAVING, maxOf(goneAt, s.updatedAt))
                null
            }
        }
    }

    // 4. 同名快捷原因只留 uid 較小的
    reasons = reasons.groupBy { it.text }.values.flatMap { same ->
        val keep = same.minBy { it.uid }
        same.filter { it !== keep }.forEach {
            tombstones[it.uid] = SyncDeletion(it.uid, Deletion.QUICK_REASON, it.updatedAt)
        }
        listOf(keep)
    }

    // 5. 清掉太舊的墓碑
    val cutoff = now.minus(TOMBSTONE_RETENTION).toEpochMilli()
    val deletions = tombstones.values.filter { it.deletedAt >= cutoff }

    return SyncDoc(goals = goals, savings = savings, quickReasons = reasons, deletions = deletions).normalized()
}
