package com.example.ai.model

data class QueryResponse(
    val headline: String,
    val totalAmount: Double,
    val transactionCount: Int,
    val transactionIds: List<String>,
    val query: ParsedQuery
)
