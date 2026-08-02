package com.example.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.Calendar

data class CategorySpend(
    val categoryId: String,
    val categoryName: String,
    val totalAmount: Long, // in paise
    val percentage: Double
)

data class CashflowPoint(
    val label: String, // e.g. "Jan 26"
    val income: Long,  // in paise
    val expense: Long, // in paise
    val net: Long      // in paise
)

data class BudgetStatus(
    val budget: Budget,
    val spentAmount: Long, // in paise
    val percentage: Double,
    val projectedAmount: Long, // in paise
    val isOverBudget: Boolean,
    val daysRemaining: Int
)

class AnalyticsService(
    private val transactionRepo: TransactionRepository,
    private val accountRepo: AccountRepository,
    private val budgetRepo: BudgetRepository
) {

    // All amounts returned in paise (Long). UI layer divides by 100.

    private fun monthRange(month: Int, year: Int): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        cal.clear()
        cal.set(Calendar.YEAR, year)
        cal.set(Calendar.MONTH, month - 1)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startMs = cal.timeInMillis
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        return Pair(startMs, cal.timeInMillis)
    }

    suspend fun getMonthlySpend(month: Int, year: Int): Long = withContext(Dispatchers.IO) {
        val (startMs, endMs) = monthRange(month, year)
        transactionRepo.getByDateRange(startMs, endMs)
            .filter { it.type == "EXPENSE" }
            .sumOf { (it.amount * 100).toLong() }
    }

    suspend fun getMonthlyIncome(month: Int, year: Int): Long = withContext(Dispatchers.IO) {
        val (startMs, endMs) = monthRange(month, year)
        transactionRepo.getByDateRange(startMs, endMs)
            .filter { it.type == "INCOME" || it.type == "CREDIT" }
            .sumOf { (it.amount * 100).toLong() }
    }

    suspend fun getNetWorth(): Long = withContext(Dispatchers.IO) {
        val netWorthDouble = accountRepo.getNetWorthFlow().first()
        (netWorthDouble * 100).toLong()
    }

    suspend fun getSpendByCategory(
        startMs: Long, endMs: Long
    ): List<CategorySpend> = withContext(Dispatchers.IO) {
        val transactions = transactionRepo.getByDateRange(startMs, endMs).filter { it.type == "EXPENSE" }
        val totalAllSpend = transactions.sumOf { (it.amount * 100).toLong() }
        
        val categories = transactionRepo.allCategories.first().associateBy { it.id }

        val grouped = transactions.groupBy { it.categoryId }
        grouped.map { (categoryId, list) ->
            val catTotal = list.sumOf { (it.amount * 100).toLong() }
            val percentage = if (totalAllSpend > 0) (catTotal.toDouble() / totalAllSpend * 100.0) else 0.0
            val catName = categories[categoryId]?.name ?: "Other"
            CategorySpend(categoryId, catName, catTotal, percentage)
        }.sortedByDescending { it.totalAmount }
    }

    suspend fun getCashflow(months: Int): List<CashflowPoint> = withContext(Dispatchers.IO) {
        val points = mutableListOf<CashflowPoint>()
        val cal = Calendar.getInstance()
        val currentYear = cal.get(Calendar.YEAR)
        val currentMonth = cal.get(Calendar.MONTH) // 0-based

        for (i in (months - 1) downTo 0) {
            val targetCal = Calendar.getInstance()
            targetCal.clear()
            targetCal.set(Calendar.YEAR, currentYear)
            targetCal.set(Calendar.MONTH, currentMonth)
            targetCal.add(Calendar.MONTH, -i)
            
            targetCal.set(Calendar.DAY_OF_MONTH, 1)
            targetCal.set(Calendar.HOUR_OF_DAY, 0)
            targetCal.set(Calendar.MINUTE, 0)
            targetCal.set(Calendar.SECOND, 0)
            targetCal.set(Calendar.MILLISECOND, 0)
            val startMs = targetCal.timeInMillis

            targetCal.set(Calendar.DAY_OF_MONTH, targetCal.getActualMaximum(Calendar.DAY_OF_MONTH))
            targetCal.set(Calendar.HOUR_OF_DAY, 23)
            targetCal.set(Calendar.MINUTE, 59)
            targetCal.set(Calendar.SECOND, 59)
            targetCal.set(Calendar.MILLISECOND, 999)
            val endMs = targetCal.timeInMillis

            val transactions = transactionRepo.getByDateRange(startMs, endMs)
            val income = transactions.filter { it.type == "INCOME" || it.type == "CREDIT" }.sumOf { (it.amount * 100).toLong() }
            val expense = transactions.filter { it.type == "EXPENSE" }.sumOf { (it.amount * 100).toLong() }

            val monthName = when (targetCal.get(Calendar.MONTH)) {
                Calendar.JANUARY -> "Jan"
                Calendar.FEBRUARY -> "Feb"
                Calendar.MARCH -> "Mar"
                Calendar.APRIL -> "Apr"
                Calendar.MAY -> "May"
                Calendar.JUNE -> "Jun"
                Calendar.JULY -> "Jul"
                Calendar.AUGUST -> "Aug"
                Calendar.SEPTEMBER -> "Sep"
                Calendar.OCTOBER -> "Oct"
                Calendar.NOVEMBER -> "Nov"
                Calendar.DECEMBER -> "Dec"
                else -> ""
            }
            val label = "$monthName " + (targetCal.get(Calendar.YEAR) % 100)
            points.add(CashflowPoint(label, income, expense, income - expense))
        }
        points
    }

    suspend fun getBudgetStatus(budgetId: String): BudgetStatus? = withContext(Dispatchers.IO) {
        val budget = budgetRepo.getBudgetById(budgetId) ?: return@withContext null
        val cal = Calendar.getInstance()
        
        val startMs: Long
        val endMs: Long
        val totalDays: Int
        val elapsedDays: Int

        when (budget.period) {
            "MONTHLY" -> {
                val targetCal = Calendar.getInstance()
                targetCal.set(Calendar.DAY_OF_MONTH, 1)
                targetCal.set(Calendar.HOUR_OF_DAY, 0)
                targetCal.set(Calendar.MINUTE, 0)
                targetCal.set(Calendar.SECOND, 0)
                targetCal.set(Calendar.MILLISECOND, 0)
                startMs = targetCal.timeInMillis

                totalDays = targetCal.getActualMaximum(Calendar.DAY_OF_MONTH)
                elapsedDays = cal.get(Calendar.DAY_OF_MONTH)

                targetCal.set(Calendar.DAY_OF_MONTH, totalDays)
                targetCal.set(Calendar.HOUR_OF_DAY, 23)
                targetCal.set(Calendar.MINUTE, 59)
                targetCal.set(Calendar.SECOND, 59)
                targetCal.set(Calendar.MILLISECOND, 999)
                endMs = targetCal.timeInMillis
            }
            "WEEKLY" -> {
                val targetCal = Calendar.getInstance()
                // Align to start of week
                val firstDay = targetCal.firstDayOfWeek
                targetCal.set(Calendar.DAY_OF_WEEK, firstDay)
                targetCal.set(Calendar.HOUR_OF_DAY, 0)
                targetCal.set(Calendar.MINUTE, 0)
                targetCal.set(Calendar.SECOND, 0)
                targetCal.set(Calendar.MILLISECOND, 0)
                startMs = targetCal.timeInMillis

                totalDays = 7
                
                val diffMs = cal.timeInMillis - startMs
                val days = (diffMs / (1000 * 60 * 60 * 24)).toInt() + 1
                elapsedDays = days.coerceIn(1, 7)

                targetCal.add(Calendar.DAY_OF_YEAR, 6)
                targetCal.set(Calendar.HOUR_OF_DAY, 23)
                targetCal.set(Calendar.MINUTE, 59)
                targetCal.set(Calendar.SECOND, 59)
                targetCal.set(Calendar.MILLISECOND, 999)
                endMs = targetCal.timeInMillis
            }
            "YEARLY" -> {
                val targetCal = Calendar.getInstance()
                targetCal.set(Calendar.DAY_OF_YEAR, 1)
                targetCal.set(Calendar.HOUR_OF_DAY, 0)
                targetCal.set(Calendar.MINUTE, 0)
                targetCal.set(Calendar.SECOND, 0)
                targetCal.set(Calendar.MILLISECOND, 0)
                startMs = targetCal.timeInMillis

                totalDays = targetCal.getActualMaximum(Calendar.DAY_OF_YEAR)
                elapsedDays = cal.get(Calendar.DAY_OF_YEAR)

                targetCal.set(Calendar.DAY_OF_YEAR, totalDays)
                targetCal.set(Calendar.HOUR_OF_DAY, 23)
                targetCal.set(Calendar.MINUTE, 59)
                targetCal.set(Calendar.SECOND, 59)
                targetCal.set(Calendar.MILLISECOND, 999)
                endMs = targetCal.timeInMillis
            }
            else -> { // Default to monthly
                val targetCal = Calendar.getInstance()
                targetCal.set(Calendar.DAY_OF_MONTH, 1)
                targetCal.set(Calendar.HOUR_OF_DAY, 0)
                targetCal.set(Calendar.MINUTE, 0)
                targetCal.set(Calendar.SECOND, 0)
                targetCal.set(Calendar.MILLISECOND, 0)
                startMs = targetCal.timeInMillis

                totalDays = targetCal.getActualMaximum(Calendar.DAY_OF_MONTH)
                elapsedDays = cal.get(Calendar.DAY_OF_MONTH)

                targetCal.set(Calendar.DAY_OF_MONTH, totalDays)
                targetCal.set(Calendar.HOUR_OF_DAY, 23)
                targetCal.set(Calendar.MINUTE, 59)
                targetCal.set(Calendar.SECOND, 59)
                targetCal.set(Calendar.MILLISECOND, 999)
                endMs = targetCal.timeInMillis
            }
        }

        val transactions = transactionRepo.getByCategory(budget.categoryId)
            .filter { it.date in startMs..endMs && it.type == "EXPENSE" }

        val spentAmount = transactions.sumOf { (it.amount * 100).toLong() }
        val percentage = if (budget.limitAmount > 0) (spentAmount.toDouble() / budget.limitAmount) else 0.0
        val isOverBudget = spentAmount > budget.limitAmount

        val projectedAmount = if (elapsedDays > 0) {
            (spentAmount.toDouble() / elapsedDays * totalDays).toLong()
        } else {
            spentAmount
        }

        val daysRemaining = (totalDays - elapsedDays).coerceAtLeast(0)

        BudgetStatus(
            budget = budget,
            spentAmount = spentAmount,
            percentage = percentage,
            projectedAmount = projectedAmount,
            isOverBudget = isOverBudget,
            daysRemaining = daysRemaining
        )
    }
}
