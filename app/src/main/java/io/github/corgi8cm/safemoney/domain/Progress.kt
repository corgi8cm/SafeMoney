package io.github.corgi8cm.safemoney.domain

/** 回本進度：已省金額相對於目標金額的狀態。金額單位皆為「元」。 */
data class Progress(val saved: Long, val target: Long) {
    init {
        require(target > 0) { "target must be positive" }
        require(saved >= 0) { "saved must not be negative" }
    }

    /** 0.0 ~ 1.0，超過目標時固定為 1.0（給進度條用）。 */
    val fraction: Float get() = (saved.toDouble() / target).coerceAtMost(1.0).toFloat()

    /** 還差多少元，已達成時為 0。 */
    val remaining: Long get() = (target - saved).coerceAtLeast(0)

    /** 超出目標的金額，可轉給下一個目標。 */
    val overflow: Long get() = (saved - target).coerceAtLeast(0)

    val isCompleted: Boolean get() = saved >= target
}
