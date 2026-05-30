package com.example.ai.model

enum class Direction {
    INCOME, EXPENSE, TRANSFER;

    companion object {
        fun fromString(value: String?): Direction? {
            if (value == null) return null
            return entries.find { it.name.equals(value.trim(), ignoreCase = true) }
        }
    }
}
