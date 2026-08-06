package com.example.ai.query

/**
 * Paise-precision aggregate result returned by TransactionRepository and used by AnalyticsRepository.
 * Amounts are stored as Long (paise) to avoid floating-point accumulation errors at the
 * display boundary. totalAmount is a computed property for legacy callers.
 */
data class QueryAggregate(
    val totalAmountPaise: Long,
    val count: Int,
    val transactionIds: List<String>
) {
    val totalAmount: Double get() = totalAmountPaise / 100.0

    companion object {
        fun fromDouble(amount: Double, count: Int, ids: List<String>) =
            QueryAggregate(Math.round(amount * 100), count, ids)

        fun fromPaise(paise: Long, count: Int, ids: List<String>) =
            QueryAggregate(paise, count, ids)
    }
}
