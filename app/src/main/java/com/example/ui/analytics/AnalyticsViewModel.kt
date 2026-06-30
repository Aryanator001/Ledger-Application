package com.example.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AnalyticsService
import com.example.data.CategorySpend
import com.example.data.Transaction
import com.example.data.TransactionRepository
import kotlin.math.roundToLong
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar

// ─── Domain models ────────────────────────────────────────────────────────────

enum class AnalyticsPeriod(val label: String) {
    ThisWeek("This Week"),
    ThisMonth("This Month"),
    Last3Months("Last 3 Months"),
    ThisYear("This Year"),
    Custom("Custom")
}

enum class TrendGranularity { DAILY, WEEKLY, MONTHLY }

data class BusinessHealthData(
    val totalIncomePaise: Long = 0L,
    val totalExpensePaise: Long = 0L,
    val netPaise: Long = 0L
) {
    val status: HealthStatus get() = when {
        netPaise > 0 -> HealthStatus.Profitable
        netPaise == 0L -> HealthStatus.BreakEven
        else -> HealthStatus.Loss
    }
}

enum class HealthStatus { Profitable, BreakEven, Loss }

// MVP heuristic – outstanding balance is detected via keyword scan rather than
// a dedicated schema tag. Future iteration should add a proper tagging system.
data class OutstandingItem(
    val id: String,
    val partyName: String,
    val amountPaise: Long,
    val date: Long,
    val note: String?
)

data class TrendPoint(
    val label: String,
    val incomePaise: Long,
    val expensePaise: Long
)

sealed class AiInsightState {
    object Idle : AiInsightState()
    object Loading : AiInsightState()
    data class Ready(val text: String) : AiInsightState()
    object Error : AiInsightState()
}

// ─── ViewModel ────────────────────────────────────────────────────────────────

class AnalyticsViewModel(
    private val repository: TransactionRepository,
    private val analyticsService: AnalyticsService,
    private val insightsService: GeminiInsightsService
) : ViewModel() {

    private val _selectedPeriod = MutableStateFlow(AnalyticsPeriod.ThisMonth)
    val selectedPeriod: StateFlow<AnalyticsPeriod> = _selectedPeriod.asStateFlow()

    private val _customRange = MutableStateFlow<Pair<Long, Long>?>(null)
    val customRange: StateFlow<Pair<Long, Long>?> = _customRange.asStateFlow()

    private val _businessHealth = MutableStateFlow(BusinessHealthData())
    val businessHealth: StateFlow<BusinessHealthData> = _businessHealth.asStateFlow()

    private val _receivables = MutableStateFlow<List<OutstandingItem>>(emptyList())
    val receivables: StateFlow<List<OutstandingItem>> = _receivables.asStateFlow()

    private val _payables = MutableStateFlow<List<OutstandingItem>>(emptyList())
    val payables: StateFlow<List<OutstandingItem>> = _payables.asStateFlow()

    private val _aiInsight = MutableStateFlow<AiInsightState>(AiInsightState.Idle)
    val aiInsight: StateFlow<AiInsightState> = _aiInsight.asStateFlow()

    private val _trendPoints = MutableStateFlow<List<TrendPoint>>(emptyList())
    val trendPoints: StateFlow<List<TrendPoint>> = _trendPoints.asStateFlow()

    private val _trendGranularity = MutableStateFlow(TrendGranularity.MONTHLY)
    val trendGranularity: StateFlow<TrendGranularity> = _trendGranularity.asStateFlow()

    private val _categoryBreakdown = MutableStateFlow<List<CategorySpend>>(emptyList())
    val categoryBreakdown: StateFlow<List<CategorySpend>> = _categoryBreakdown.asStateFlow()

    private val _expandedCategoryId = MutableStateFlow<String?>(null)
    val expandedCategoryId: StateFlow<String?> = _expandedCategoryId.asStateFlow()

    private val _expandedCategoryTransactions = MutableStateFlow<List<Transaction>>(emptyList())
    val expandedCategoryTransactions: StateFlow<List<Transaction>> = _expandedCategoryTransactions.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Tracks which period the AI insight was generated for so we don't re-call on recomposition.
    private var insightGeneratedForPeriod: String? = null
    private var loadJob: Job? = null

    init {
        loadData()
    }

    fun onPeriodSelected(period: AnalyticsPeriod) {
        if (period == AnalyticsPeriod.Custom) return // wait for custom range
        _selectedPeriod.value = period
        loadData()
    }

    fun onCustomRangeSelected(startMs: Long, endMs: Long) {
        _customRange.value = Pair(startMs, endMs)
        _selectedPeriod.value = AnalyticsPeriod.Custom
        loadData()
    }

    fun onScreenEntered() {
        // Called when the tab is navigated back to – only re-load if nothing loaded yet.
        if (_businessHealth.value.totalIncomePaise == 0L && _businessHealth.value.totalExpensePaise == 0L) {
            loadData()
        }
    }

    fun onCategoryTapped(categoryId: String) {
        val current = _expandedCategoryId.value
        if (current == categoryId) {
            _expandedCategoryId.value = null
            _expandedCategoryTransactions.value = emptyList()
        } else {
            _expandedCategoryId.value = categoryId
            viewModelScope.launch {
                val (startMs, endMs) = dateRange()
                val txs = repository.getByDateRange(startMs, endMs)
                    .filter { it.categoryId == categoryId && it.type == "EXPENSE" }
                _expandedCategoryTransactions.value = txs
            }
        }
    }

    private fun loadData() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _isLoading.value = true
            // Capture date range once so both the loaders and the AI cache key
            // use the same range even if onCustomRangeSelected fires mid-coroutine.
            val (startMs, endMs) = dateRange()

            // Load core sections in parallel
            coroutineScope {
                val healthDeferred = async { loadBusinessHealth(startMs, endMs) }
                val outstandingDeferred = async { loadOutstandingBalance() }
                val trendsDeferred = async { loadTrends(startMs, endMs) }
                val categoryDeferred = async { loadCategoryBreakdown(startMs, endMs) }
                healthDeferred.await()
                outstandingDeferred.await()
                trendsDeferred.await()
                categoryDeferred.await()
            }

            _isLoading.value = false

            // AI insight: generate only when period changes (cached otherwise).
            // Key built from the already-captured range so it matches the data loaded above.
            val periodKey = "${_selectedPeriod.value.name}:$startMs:$endMs"
            if (insightGeneratedForPeriod != periodKey) {
                insightGeneratedForPeriod = periodKey
                loadAiInsight()
            }
        }
    }

    private suspend fun loadBusinessHealth(startMs: Long, endMs: Long) {
        val row = repository.getIncomeExpenseTotalsForRange(startMs, endMs)
        val income = ((row?.incomeTotal ?: 0.0) * 100).roundToLong()
        val expense = ((row?.expenseTotal ?: 0.0) * 100).roundToLong()
        _businessHealth.value = BusinessHealthData(income, expense, income - expense)
    }

    private suspend fun loadOutstandingBalance() {
        val receivableKeywords = setOf("receivable", "due from", "to receive", "credit given", "udhaar diya")
        val payableKeywords = setOf("payable", "udhar", "baki", "due to", "due", "owe", "loan taken", "borrowed")

        val allTx = repository.getAllTransactionsOnce().filter { !it.isArchived }
        val categories = repository.allCategories.first().associateBy { it.id }

        val receivables = mutableListOf<OutstandingItem>()
        val payables = mutableListOf<OutstandingItem>()

        allTx.forEach { tx ->
            val catName = categories[tx.categoryId]?.name?.lowercase() ?: ""
            val searchText = listOf(
                tx.title.lowercase(),
                tx.description?.lowercase() ?: "",
                tx.tags.lowercase(),
                catName
            ).joinToString(" ")

            val isReceivable = receivableKeywords.any { searchText.contains(it) }
            val isPayable = payableKeywords.any { searchText.contains(it) }

            val item = OutstandingItem(
                id = tx.id,
                partyName = tx.title.ifBlank { "Unknown" },
                amountPaise = (tx.amount * 100).roundToLong(),
                date = tx.date,
                note = tx.description
            )

            when {
                isReceivable -> receivables.add(item)
                isPayable -> payables.add(item)
            }
        }

        _receivables.value = receivables.sortedByDescending { it.date }
        _payables.value = payables.sortedByDescending { it.date }
    }

    private suspend fun loadTrends(startMs: Long, endMs: Long) {
        val rangeDays = (endMs - startMs) / (1000 * 60 * 60 * 24)
        val granularity = when {
            rangeDays <= 7 -> TrendGranularity.DAILY
            rangeDays <= 60 -> TrendGranularity.WEEKLY
            else -> TrendGranularity.MONTHLY
        }
        _trendGranularity.value = granularity

        val txs = repository.getByDateRange(startMs, endMs)

        val points = when (granularity) {
            TrendGranularity.DAILY -> groupByDay(txs, startMs, endMs)
            TrendGranularity.WEEKLY -> groupByWeek(txs, startMs, endMs)
            TrendGranularity.MONTHLY -> groupByMonth(txs, startMs, endMs)
        }
        _trendPoints.value = points
    }

    private fun groupByDay(txs: List<Transaction>, startMs: Long, endMs: Long): List<TrendPoint> {
        val fmt = java.text.SimpleDateFormat("dd/MM", java.util.Locale.getDefault())
        val groups = txs.groupBy {
            val c = Calendar.getInstance().apply { timeInMillis = it.date }
            "${c.get(Calendar.YEAR)}-${c.get(Calendar.DAY_OF_YEAR)}"
        }
        val result = mutableListOf<TrendPoint>()
        val cur = Calendar.getInstance().apply { timeInMillis = startMs }
        val end = Calendar.getInstance().apply { timeInMillis = endMs }
        while (!cur.after(end)) {
            val key = "${cur.get(Calendar.YEAR)}-${cur.get(Calendar.DAY_OF_YEAR)}"
            val label = fmt.format(cur.time)
            val dayTxs = groups[key] ?: emptyList()
            result.add(
                TrendPoint(
                    label = label,
                    incomePaise = dayTxs.filter { it.type == "INCOME" || it.type == "CREDIT" }
                        .sumOf { (it.amount * 100).roundToLong() },
                    expensePaise = dayTxs.filter { it.type == "EXPENSE" }
                        .sumOf { (it.amount * 100).roundToLong() }
                )
            )
            cur.add(Calendar.DAY_OF_YEAR, 1)
        }
        return result
    }

    private fun groupByWeek(txs: List<Transaction>, startMs: Long, endMs: Long): List<TrendPoint> {
        val fmt = java.text.SimpleDateFormat("dd/MM", java.util.Locale.getDefault())
        val groups = txs.groupBy {
            val c = Calendar.getInstance().apply { timeInMillis = it.date }
            "${c.get(Calendar.YEAR)}-W${c.get(Calendar.WEEK_OF_YEAR)}"
        }
        val result = mutableListOf<TrendPoint>()
        val cur = Calendar.getInstance().apply {
            timeInMillis = startMs
            set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
            // Snapping to first-day-of-week can rewind into the previous year's week
            // (e.g. Jan 3 → Dec 29), producing a key that no transaction matches.
            // Advance one week to align with the actual startMs week.
            if (timeInMillis < startMs) add(Calendar.WEEK_OF_YEAR, 1)
        }
        val end = Calendar.getInstance().apply { timeInMillis = endMs }
        while (!cur.after(end)) {
            val key = "${cur.get(Calendar.YEAR)}-W${cur.get(Calendar.WEEK_OF_YEAR)}"
            val label = "W${cur.get(Calendar.WEEK_OF_MONTH)} ${fmt.format(cur.time)}"
            val weekTxs = groups[key] ?: emptyList()
            result.add(
                TrendPoint(
                    label = label,
                    incomePaise = weekTxs.filter { it.type == "INCOME" || it.type == "CREDIT" }
                        .sumOf { (it.amount * 100).roundToLong() },
                    expensePaise = weekTxs.filter { it.type == "EXPENSE" }
                        .sumOf { (it.amount * 100).roundToLong() }
                )
            )
            cur.add(Calendar.WEEK_OF_YEAR, 1)
        }
        return result
    }

    private fun groupByMonth(txs: List<Transaction>, startMs: Long, endMs: Long): List<TrendPoint> {
        val monthNames = arrayOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")
        val groups = txs.groupBy {
            val c = Calendar.getInstance().apply { timeInMillis = it.date }
            "${c.get(Calendar.YEAR)}-${c.get(Calendar.MONTH)}"
        }
        val result = mutableListOf<TrendPoint>()
        val cur = Calendar.getInstance().apply {
            timeInMillis = startMs
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val end = Calendar.getInstance().apply { timeInMillis = endMs }
        while (!cur.after(end)) {
            val key = "${cur.get(Calendar.YEAR)}-${cur.get(Calendar.MONTH)}"
            val label = "${monthNames[cur.get(Calendar.MONTH)]} ${cur.get(Calendar.YEAR) % 100}"
            val monthTxs = groups[key] ?: emptyList()
            result.add(
                TrendPoint(
                    label = label,
                    incomePaise = monthTxs.filter { it.type == "INCOME" || it.type == "CREDIT" }
                        .sumOf { (it.amount * 100).roundToLong() },
                    expensePaise = monthTxs.filter { it.type == "EXPENSE" }
                        .sumOf { (it.amount * 100).roundToLong() }
                )
            )
            cur.add(Calendar.MONTH, 1)
        }
        return result
    }

    private suspend fun loadCategoryBreakdown(startMs: Long, endMs: Long) {
        _categoryBreakdown.value = analyticsService.getSpendByCategory(startMs, endMs)
    }

    private suspend fun loadAiInsight() {
        _aiInsight.value = AiInsightState.Loading
        try {
            val health = _businessHealth.value
            val cats = _categoryBreakdown.value.map {
                CategorySummary(it.categoryName, it.totalAmount)
            }
            val text = insightsService.generateInsight(
                totalIncomePaise = health.totalIncomePaise,
                totalExpensePaise = health.totalExpensePaise,
                netPaise = health.netPaise,
                topCategories = cats,
                periodLabel = _selectedPeriod.value.label
            )
            _aiInsight.value = AiInsightState.Ready(text)
        } catch (_: Exception) {
            // Clear the cache key so the next loadData() for this period will retry.
            insightGeneratedForPeriod = null
            _aiInsight.value = AiInsightState.Error
        }
    }

    fun currentDateRange(): Pair<Long, Long> = dateRange()

    suspend fun loadExportData(): Triple<List<com.example.data.Transaction>, Map<String, String>, Map<String, String>> {
        val (startMs, endMs) = dateRange()
        val txs = repository.getByDateRange(startMs, endMs)
        val cats = repository.allCategories.first().associate { it.id to it.name }
        val accs = repository.allAccounts.first().associate { it.id to it.name }
        return Triple(txs, cats, accs)
    }

    private fun dateRange(): Pair<Long, Long> {
        if (_selectedPeriod.value == AnalyticsPeriod.Custom) {
            return _customRange.value ?: thisMonthRange()
        }
        return when (_selectedPeriod.value) {
            AnalyticsPeriod.ThisWeek -> {
                val c = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
                    clearTime()
                }
                val start = c.timeInMillis
                c.add(Calendar.DAY_OF_YEAR, 6)
                c.endOfDay()
                Pair(start, c.timeInMillis)
            }
            AnalyticsPeriod.ThisMonth -> thisMonthRange()
            AnalyticsPeriod.Last3Months -> {
                val end = Calendar.getInstance().apply { endOfDay() }
                val start = Calendar.getInstance().apply {
                    add(Calendar.MONTH, -3)
                    set(Calendar.DAY_OF_MONTH, 1)
                    clearTime()
                }
                Pair(start.timeInMillis, end.timeInMillis)
            }
            AnalyticsPeriod.ThisYear -> {
                val start = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_YEAR, 1)
                    clearTime()
                }
                val end = Calendar.getInstance().apply {
                    set(Calendar.MONTH, Calendar.DECEMBER)
                    set(Calendar.DAY_OF_MONTH, 31)
                    endOfDay()
                }
                Pair(start.timeInMillis, end.timeInMillis)
            }
            else -> thisMonthRange()
        }
    }

    private fun thisMonthRange(): Pair<Long, Long> {
        val start = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            clearTime()
        }
        val end = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
            endOfDay()
        }
        return Pair(start.timeInMillis, end.timeInMillis)
    }

    private fun Calendar.clearTime() {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }

    private fun Calendar.endOfDay() {
        set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
    }
}

// ─── ViewModelFactory ─────────────────────────────────────────────────────────

class AnalyticsViewModelFactory(
    private val repository: TransactionRepository,
    private val analyticsService: AnalyticsService,
    private val insightsService: GeminiInsightsService
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return AnalyticsViewModel(repository, analyticsService, insightsService) as T
    }
}
