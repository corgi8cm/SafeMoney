package io.github.corgi8cm.safemoney.ui.goal

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.corgi8cm.safemoney.data.SafeMoneyRepository
import io.github.corgi8cm.safemoney.data.db.Goal
import io.github.corgi8cm.safemoney.domain.SavingForm
import io.github.corgi8cm.safemoney.ui.Routes
import io.github.corgi8cm.safemoney.ui.celebration.CelebrationController
import io.github.corgi8cm.safemoney.ui.common.formatMoney
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

class GoalEditViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: SafeMoneyRepository,
) : ViewModel() {
    private val goalId: Long? = savedStateHandle.get<Long>(Routes.ARG_GOAL_ID)?.takeIf { it > 0 }
    val isEdit: Boolean = goalId != null

    var name by mutableStateOf("")
    var amountText by mutableStateOf("")
    var purchaseDate by mutableStateOf(LocalDate.now())
    var note by mutableStateOf("")
    var color by mutableStateOf<Int?>(null)
    var emoji by mutableStateOf<String?>(null)

    var loaded by mutableStateOf(!isEdit)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var saving by mutableStateOf(false)
        private set
    var done by mutableStateOf(false)
        private set

    private var original: Goal? = null

    /** 存錢筒裡的錢；新增目標時可以先拿來抵。 */
    val reserveBalance: StateFlow<Long> =
        repository.observeReserve().map { it?.saved ?: 0 }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /** 新增目標時要不要先用存錢筒的錢抵（預設要）。 */
    var useReserve by mutableStateOf(true)

    /** 用存錢筒抵了多少的說明，離開畫面時顯示。 */
    var filledMessage by mutableStateOf<String?>(null)
        private set

    /** 存錢筒的錢一抵就回本時，先慶祝再離開。 */
    val celebration = CelebrationController(repository, viewModelScope)

    init {
        if (goalId != null) {
            viewModelScope.launch {
                val goal = repository.observeGoal(goalId).first()?.goal
                if (goal == null) {
                    done = true
                    return@launch
                }
                original = goal
                name = goal.name
                amountText = goal.amount.toString()
                purchaseDate = goal.purchaseDate
                note = goal.note.orEmpty()
                color = goal.color
                emoji = goal.emoji
                loaded = true
            }
        }
    }

    fun save() {
        if (saving) return
        val amount = SavingForm.parseAmount(amountText)
        error = when {
            name.isBlank() -> "請輸入目標名稱"
            amount == null -> "請輸入大於 0 的金額"
            else -> null
        }
        if (error != null || amount == null) return

        saving = true
        viewModelScope.launch {
            var celebrating = false
            try {
                val existing = original
                if (existing == null) {
                    val id = repository.addGoal(name, amount, purchaseDate, note, color = color, emoji = emoji)
                    if (useReserve && reserveBalance.value > 0) {
                        val result = repository.fillFromReserve(id)
                        if (result.transferred > 0) filledMessage = "已用存錢筒的 ${formatMoney(result.transferred)} 抵掉"
                        if (result.completedGoalIds.isNotEmpty()) {
                            // 保持 saving，慶祝看完（celebration.finished）才離開
                            celebrating = true
                            celebration.start(result.completedGoalIds)
                            return@launch
                        }
                    }
                } else {
                    repository.updateGoal(
                        existing.copy(
                            name = name,
                            amount = amount,
                            purchaseDate = purchaseDate,
                            note = note,
                            color = color,
                            emoji = emoji,
                        )
                    )
                }
                done = true
            } catch (e: IllegalArgumentException) {
                error = e.message
            } finally {
                if (!celebrating) saving = false
            }
        }
    }
}
