package io.github.corgi8cm.safemoney.ui.celebration

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.corgi8cm.safemoney.data.SafeMoneyRepository
import io.github.corgi8cm.safemoney.data.db.GoalStatus
import io.github.corgi8cm.safemoney.data.db.GoalWithSaved
import io.github.corgi8cm.safemoney.data.db.progress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** 目前正在慶祝的目標，以及可以接收它超額的其他進行中目標。 */
data class CelebrationUi(
    val goal: GoalWithSaved,
    val transferTargets: List<GoalWithSaved>,
) {
    val overflow: Long get() = goal.progress().overflow
}

/**
 * 依序慶祝剛回本的目標；每個目標若有超額，可以選一個進行中的目標轉過去。
 * 轉過去後若讓下一個目標也回本，會接著慶祝它。
 *
 * 由 ViewModel 持有，畫面只要顯示 [current] 並呼叫 [transfer] / [next]。
 */
class CelebrationController(
    private val repository: SafeMoneyRepository,
    private val scope: CoroutineScope,
) {
    var current by mutableStateOf<CelebrationUi?>(null)
        private set

    /** 本輪慶祝全部看完。 */
    var finished by mutableStateOf(false)
        private set

    var busy by mutableStateOf(false)
        private set

    private val queue = ArrayDeque<Long>()

    fun start(goalIds: List<Long>) {
        queue.addAll(goalIds.filterNot { it in queue })
        if (current == null) showNext()
    }

    /** 不轉移，看下一個（或結束）。 */
    fun next() {
        if (!busy) showNext()
    }

    fun transfer(toGoalId: Long) {
        val from = current?.goal?.goal?.id ?: return
        if (busy) return
        busy = true
        scope.launch {
            try {
                val result = repository.transferOverflow(from, toGoalId)
                queue.addAll(result.completedGoalIds.filterNot { it in queue })
                loadNext()
            } finally {
                busy = false
            }
        }
    }

    private fun showNext() {
        scope.launch { loadNext() }
    }

    private suspend fun loadNext() {
        while (queue.isNotEmpty()) {
            val id = queue.removeFirst()
            val goal = repository.observeGoal(id).first() ?: continue
            val targets = repository.observeGoals().first()
                .filter { it.goal.status == GoalStatus.ACTIVE && it.goal.id != id }
            current = CelebrationUi(goal, targets)
            return
        }
        current = null
        finished = true
    }
}
