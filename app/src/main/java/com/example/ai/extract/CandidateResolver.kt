package com.example.ai.extract

/**
 * Resolves raw category and payment-method hints from extraction
 * into actual Room entity IDs that exist in the seeded database.
 * Deterministic, on-device. No network.
 */
class CandidateResolver {

    // Known seeded category IDs.
    private val knownCategoryIds = setOf("general", "food", "rent", "shopping")

    // Map category hint -> Room category ID.
    fun resolveCategoryId(categoryHint: String?): String {
        val hint = categoryHint?.lowercase()?.trim() ?: return "general"
        return when {
            hint in knownCategoryIds -> hint
            hint.contains("food") || hint.contains("chai") || hint.contains("tea") ||
                hint.contains("lunch") || hint.contains("grocery") || hint.contains("khana") -> "food"
            hint.contains("rent") || hint.contains("kiraya") -> "rent"
            hint.contains("shop") || hint.contains("cloth") || hint.contains("kapde") -> "shopping"
            else -> "general"
        }
    }

    // Map payment-method hint -> Room account ID.
    fun resolveAccountId(paymentMethodHint: String?): String {
        val hint = paymentMethodHint?.lowercase()?.trim() ?: return "default_cash"
        return when {
            hint.contains("cash") || hint.contains("nakad") || hint.contains("nagad") -> "default_cash"
            hint.contains("upi") || hint.contains("phonepe") || hint.contains("gpay") ||
                hint.contains("paytm") || hint.contains("online") -> "upi_account"
            hint.contains("bank") || hint.contains("neft") || hint.contains("account") ||
                hint.contains("hdfc") -> "bank_account"
            else -> "default_cash"
        }
    }
}
