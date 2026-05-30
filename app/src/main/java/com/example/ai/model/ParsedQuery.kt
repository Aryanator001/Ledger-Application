package com.example.ai.model

data class ParsedQuery(
    val entity: String?,
    val direction: Direction?,
    val categoryHint: String?,
    val timeRangeStart: Long?,
    val timeRangeEnd: Long?,
    val rawTranscript: String
)
