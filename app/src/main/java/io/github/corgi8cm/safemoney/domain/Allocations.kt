package io.github.corgi8cm.safemoney.domain

/** 使用者輸入的「這筆錢分多少給哪個目標」。 */
data class AllocationInput(val goalId: Long, val amount: Long)

object Allocations {
    /** 至少一個目標、每份金額 > 0、同一目標不可重複。不合法時丟 [IllegalArgumentException]。 */
    fun validate(allocations: List<AllocationInput>) {
        require(allocations.isNotEmpty()) { "至少要分配給一個目標" }
        require(allocations.all { it.amount > 0 }) { "每個目標分到的金額必須大於 0" }
        require(allocations.map { it.goalId }.toSet().size == allocations.size) { "同一個目標不能重複分配" }
    }

    /**
     * 把 [total] 平均分給 [goalIds]，無法整除的餘數從第一個目標開始各多分 1 元。
     * 例：100 元分 3 個目標 → 34、33、33。
     */
    fun splitEvenly(total: Long, goalIds: List<Long>): List<AllocationInput> {
        require(goalIds.isNotEmpty()) { "至少要選一個目標" }
        require(goalIds.toSet().size == goalIds.size) { "同一個目標不能重複分配" }
        require(total >= goalIds.size) { "金額太小，無法讓每個目標都分到至少 1 元" }
        val base = total / goalIds.size
        val remainder = total % goalIds.size
        return goalIds.mapIndexed { index, goalId ->
            AllocationInput(goalId, base + if (index < remainder) 1 else 0)
        }
    }
}
