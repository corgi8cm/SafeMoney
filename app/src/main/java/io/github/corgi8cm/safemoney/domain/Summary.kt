package io.github.corgi8cm.safemoney.domain

/** 所有（未封存）目標的總覽。 */
data class Summary(
    val goalCount: Int,
    val completedCount: Int,
    val totalAmount: Long,
    val totalSaved: Long,
) {
    /** 沒有任何目標時為 null。 */
    val progress: Progress? get() = if (totalAmount > 0) Progress(totalSaved, totalAmount) else null
}
