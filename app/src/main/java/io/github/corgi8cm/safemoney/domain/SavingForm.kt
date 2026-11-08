package io.github.corgi8cm.safemoney.domain

/** 把「記一筆省錢」表單的輸入轉成分配清單；所有錯誤訊息都在這裡產生，方便測試。 */
object SavingForm {
    sealed interface Result {
        data class Ok(val allocations: List<AllocationInput>) : Result
        data class Error(val message: String) : Result
    }

    /** 正整數金額，其餘（空白、0、非數字）回傳 null。 */
    fun parseAmount(text: String): Long? = text.trim().toLongOrNull()?.takeIf { it > 0 }

    /** 單一目標模式：整筆金額給一個目標。 */
    fun single(totalText: String, goalId: Long?): Result {
        val total = parseAmount(totalText) ?: return Result.Error("請輸入省下的金額")
        if (goalId == null) return Result.Error("請選擇要分給哪個目標")
        return Result.Ok(listOf(AllocationInput(goalId, total)))
    }

    /**
     * 拆分模式：[parts] 依顯示順序列出「勾選的目標 → 輸入的金額文字」。
     * 每個勾選的目標都要有正整數金額，且加總必須等於總金額。
     */
    fun split(totalText: String, parts: List<Pair<Long, String>>): Result {
        val total = parseAmount(totalText) ?: return Result.Error("請輸入省下的金額")
        if (parts.isEmpty()) return Result.Error("請至少勾選一個目標")
        val allocations = parts.map { (goalId, text) ->
            val amount = parseAmount(text) ?: return Result.Error("每個勾選的目標都要填金額")
            AllocationInput(goalId, amount)
        }
        val sum = allocations.sumOf { it.amount }
        if (sum != total) {
            val diff = total - sum
            val hint = if (diff > 0) "還有 $diff 元沒分配" else "多分了 ${-diff} 元"
            return Result.Error("分配加總 $sum 元與金額 $total 元不符（$hint）")
        }
        return Result.Ok(allocations)
    }

    /** 拆分模式中目前已分配的加總（無效的欄位視為 0），給畫面即時顯示。 */
    fun allocatedSum(parts: List<Pair<Long, String>>): Long = parts.sumOf { parseAmount(it.second) ?: 0L }
}
