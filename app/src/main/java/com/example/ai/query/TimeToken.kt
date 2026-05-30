package com.example.ai.query

/**
 * Symbolic time references the parser produces. Resolution to actual
 * millisecond ranges happens deterministically in TimeTokenResolver,
 * never inside any AI/LLM logic.
 */
enum class TimeToken {
    TODAY,
    YESTERDAY,
    THIS_WEEK,
    LAST_WEEK,
    THIS_MONTH,
    LAST_MONTH,
    LIFETIME,
    CUSTOM
}
