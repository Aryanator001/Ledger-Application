package com.example.ai.query

import com.example.ai.model.Direction
import com.example.ai.model.ParsedQuery
import java.text.NumberFormat
import java.util.Locale

/**
 * Builds the user-facing headline string from the parsed query and raw data.
 * Pure template logic — no AI calls. Replaced by GeminiQuerySynthesizer in 6B.
 */
class QueryResponseFormatter(
    private val timeTokenResolver: TimeTokenResolver
) {

    private val rupeeFormat: NumberFormat = NumberFormat.getNumberInstance(Locale("en", "IN")).apply {
        maximumFractionDigits = 0
    }

    fun format(
        query: ParsedQuery,
        timeToken: TimeToken,
        totalAmount: Double,
        transactionCount: Int
    ): String {
        val amountText = "₹${rupeeFormat.format(totalAmount)}"
        val timeLabel = timeTokenResolver.label(timeToken)
        val timeSuffix = if (timeLabel.isNotEmpty()) " $timeLabel" else ""

        // CASE 1: Entity balance (no direction set → net balance)
        if (query.entity != null && query.direction == null) {
            val absText = "₹${rupeeFormat.format(kotlin.math.abs(totalAmount))}"
            return when {
                totalAmount > 0 -> "${query.entity.replaceFirstChar { it.uppercase() }} owes you $absText"
                totalAmount < 0 -> "You owe ${query.entity.replaceFirstChar { it.uppercase() }} $absText"
                else -> "${query.entity.replaceFirstChar { it.uppercase() }} ka balance is settled"
            }
        }

        // CASE 2: Entity + direction (e.g., "Vijay ko kitna diya")
        if (query.entity != null && query.direction != null) {
            val verb = when (query.direction) {
                Direction.EXPENSE -> "given to"
                Direction.INCOME -> "received from"
                Direction.TRANSFER -> "transferred with"
            }
            return "Total $amountText $verb ${query.entity.replaceFirstChar { it.uppercase() }}$timeSuffix"
        }

        // CASE 3: Category total
        if (query.categoryHint != null) {
            val categoryName = query.categoryHint.replaceFirstChar { it.uppercase() }
            return "$categoryName: $amountText$timeSuffix ($transactionCount transactions)"
        }

        // CASE 4: Total expense / income (no entity, no category)
        if (query.direction != null) {
            val label = when (query.direction) {
                Direction.EXPENSE -> "Total expense"
                Direction.INCOME -> "Total income"
                Direction.TRANSFER -> "Total transfers"
            }
            return "$label$timeSuffix: $amountText ($transactionCount transactions)"
        }

        // CASE 5: Fallback — sum across everything (rare in practice)
        return "Total activity$timeSuffix: $amountText across $transactionCount transactions"
    }
}
