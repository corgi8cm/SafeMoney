package io.github.corgi8cm.safemoney.sync

import io.github.corgi8cm.safemoney.data.db.Deletion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SyncMergeTest {
    private val now = Instant.parse("2026-11-09T12:00:00Z")

    private fun goal(uid: String, name: String = uid, updatedAt: Long = 100, archived: Boolean = false) =
        SyncGoal(uid, name, 1_000, "2026-11-01", archived = archived, createdAt = 1, updatedAt = updatedAt)

    private fun saving(uid: String, vararg goals: String, updatedAt: Long = 100) =
        SyncSaving(uid, "飲料", "2026-11-08", createdAt = 1, updatedAt = updatedAt, allocations = goals.map { SyncAllocation(it, 10) })

    private fun gone(uid: String, at: Long, entity: String = Deletion.GOAL) = SyncDeletion(uid, entity, at)

    @Test
    fun firstSyncWithoutRemoteKeepsLocal() {
        val local = SyncDoc(goals = listOf(goal("a")), savings = listOf(saving("s", "a")))
        assertEquals(local.normalized(), mergeSync(local, null, now))
    }

    @Test
    fun unionOfNewItemsFromBothSides() {
        val merged = mergeSync(SyncDoc(goals = listOf(goal("a"))), SyncDoc(goals = listOf(goal("b"))), now)
        assertEquals(listOf("a", "b"), merged.goals.map { it.uid })
    }

    @Test
    fun latestEditWins() {
        val local = SyncDoc(goals = listOf(goal("a", "大衣", updatedAt = 200)))
        val remote = SyncDoc(goals = listOf(goal("a", "外套", updatedAt = 300)))
        assertEquals("外套", mergeSync(local, remote, now).goals.single().name)
        assertEquals("外套", mergeSync(remote, local, now).goals.single().name)
    }

    @Test
    fun deletionBeatsOlderEditAndTiesButNotNewerEdit() {
        val deleted = SyncDoc(deletions = listOf(gone("a", 200)))
        assertTrue(mergeSync(SyncDoc(goals = listOf(goal("a", updatedAt = 150))), deleted, now).goals.isEmpty())
        assertTrue(mergeSync(SyncDoc(goals = listOf(goal("a", updatedAt = 200))), deleted, now).goals.isEmpty())

        // 刪除後在另一支手機又修改（例如按了復原）→ 保留，墓碑也拿掉
        val revived = mergeSync(SyncDoc(goals = listOf(goal("a", updatedAt = 250))), deleted, now)
        assertEquals(listOf("a"), revived.goals.map { it.uid })
        assertTrue(revived.deletions.isEmpty())
    }

    @Test
    fun allocationsToDeletedGoalsAreDroppedAndEmptySavingsDeleted() {
        val local = SyncDoc(
            goals = listOf(goal("a"), goal("b")),
            savings = listOf(saving("split", "a", "b"), saving("onlyB", "b")),
        )
        val remote = SyncDoc(deletions = listOf(gone("b", 500)))
        val merged = mergeSync(local, remote, now)

        assertEquals(listOf("a"), merged.goals.map { it.uid })
        assertEquals(listOf("split"), merged.savings.map { it.uid })
        assertEquals(listOf("a"), merged.savings.single().allocations.map { it.goalUid })
        assertEquals(
            setOf(gone("b", 500), gone("onlyB", 500, Deletion.SAVING)),
            merged.deletions.toSet(),
        )
    }

    @Test
    fun duplicateQuickReasonTextKeepsOne() {
        val local = SyncDoc(quickReasons = listOf(SyncReason("r2", "沒買宵夜", 0, 100)))
        val remote = SyncDoc(quickReasons = listOf(SyncReason("r1", "沒買宵夜", 3, 200)))
        val merged = mergeSync(local, remote, now)
        assertEquals(listOf("r1"), merged.quickReasons.map { it.uid })
        assertEquals(listOf(SyncDeletion("r2", Deletion.QUICK_REASON, 100)), merged.deletions)

        // 另一支手機用同樣的輸入也得到同樣的結果
        assertEquals(merged, mergeSync(remote, local, now))
        // 再同步一次不會再變
        assertEquals(merged, mergeSync(merged, merged, now))
    }

    @Test
    fun oldTombstonesArePurged() {
        val old = now.minus(TOMBSTONE_RETENTION).minusSeconds(1).toEpochMilli()
        val recent = now.minusSeconds(60).toEpochMilli()
        val merged = mergeSync(SyncDoc(deletions = listOf(gone("old", old), gone("new", recent))), null, now)
        assertEquals(listOf("new"), merged.deletions.map { it.uid })
    }

    @Test
    fun mergeIsOrderIndependentAndIdempotent() {
        val a = SyncDoc(
            goals = listOf(goal("g1", updatedAt = 300), goal("g2", updatedAt = 100)),
            savings = listOf(saving("s1", "g1", "g2")),
            deletions = listOf(gone("g3", 400)),
        )
        val b = SyncDoc(
            goals = listOf(goal("g2", "改名", updatedAt = 200), goal("g3", updatedAt = 350)),
            savings = listOf(saving("s2", "g3")),
            deletions = listOf(gone("g1", 250)),
        )
        val ab = mergeSync(a, b, now)
        assertEquals(ab, mergeSync(b, a, now))
        assertEquals(ab, mergeSync(ab, ab, now))
        assertEquals(listOf("g1", "g2"), ab.goals.map { it.uid })
        assertEquals("改名", ab.goals.first { it.uid == "g2" }.name)
        assertTrue(ab.savings.none { it.uid == "s2" })
    }

    @Test
    fun codecRoundTripAndRejectsNewerVersion() {
        val doc = SyncDoc(goals = listOf(goal("a")), savings = listOf(saving("s", "a")))
        assertEquals(doc.normalized(), SyncCodec.decode(SyncCodec.encode(doc)))
        try {
            SyncCodec.decode("""{"format":"safemoney-sync","version":99}""")
            throw AssertionError("should reject")
        } catch (e: SyncFormatException) {
            assertTrue(e.message!!.contains("較新版本"))
        }
    }
}
