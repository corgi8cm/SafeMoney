package io.github.corgi8cm.safemoney.sync

import io.github.corgi8cm.safemoney.data.SafeMoneyRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock

/** 雲端存放同步檔的地方（正式版是 Google 雲端硬碟；測試用記憶體）。 */
interface SyncStore {
    /** 讀取同步檔；還沒有時回傳 null。 */
    suspend fun read(): String?

    suspend fun write(text: String)

    /** 刪除雲端的同步檔（使用者選擇「刪除雲端資料」）。 */
    suspend fun delete()
}

data class SyncResult(
    /** 這次有沒有把雲端的變動寫進手機。 */
    val changedLocal: Boolean,
    /** 這次有沒有上傳新的內容。 */
    val uploaded: Boolean,
)

/**
 * 一次同步：讀本機 → 讀雲端 → 合併 → 寫回本機（有變才寫）→ 上傳（有變才傳）。
 * 同一時間只跑一次；合併期間使用者剛好改了資料，就重來（最多 [MAX_ATTEMPTS] 次）。
 */
class SyncEngine(
    private val repository: SafeMoneyRepository,
    private val store: SyncStore,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val mutex = Mutex()

    suspend fun sync(): SyncResult = mutex.withLock {
        repeat(MAX_ATTEMPTS) {
            val local = repository.exportSyncDoc()
            val remote = store.read()?.let(SyncCodec::decode)
            val merged = mergeSync(local, remote, clock.instant())
            val changedLocal = merged != local
            if (changedLocal && !repository.applySyncDoc(merged, expected = local)) {
                return@repeat // 期間有新的修改，重新讀一次
            }
            // 寫進手機後再讀一次：自動回本狀態等由本機重新計算的欄位，以手機上的結果為準上傳
            val result = if (changedLocal) mergeSync(repository.exportSyncDoc(), merged, clock.instant()) else merged
            val uploaded = result != remote
            if (uploaded) store.write(SyncCodec.encode(result))
            return@withLock SyncResult(changedLocal, uploaded)
        }
        throw IllegalStateException("資料一直在變動，稍後再同步")
    }

    private companion object {
        const val MAX_ATTEMPTS = 3
    }
}
