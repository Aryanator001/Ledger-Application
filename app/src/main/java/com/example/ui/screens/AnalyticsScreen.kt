package com.example.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.CategorySpend
import com.example.data.Transaction
import com.example.ui.analytics.*
import com.example.ui.theme.AppShapes
import com.example.ui.theme.AppTypography
import com.example.ui.theme.appColors
import com.example.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.*
import androidx.compose.ui.res.stringResource
import com.example.R

private val kChartPalette = listOf(
    Color(0xFF1A56E8), Color(0xFF12A67A), Color(0xFFE03F3F), Color(0xFFF59E0B),
    Color(0xFF8B5CF6), Color(0xFFEC4899), Color(0xFF06B6D4), Color(0xFF10B981),
    Color(0xFFF97316), Color(0xFF6366F1), Color(0xFF64748B), Color(0xFF14B8A6)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    viewModel: AnalyticsViewModel,
    settingsViewModel: SettingsViewModel
) {
    val selectedPeriod by viewModel.selectedPeriod.collectAsStateWithLifecycle()
    val businessHealth by viewModel.businessHealth.collectAsStateWithLifecycle()
    val receivables by viewModel.receivables.collectAsStateWithLifecycle()
    val payables by viewModel.payables.collectAsStateWithLifecycle()
    val aiInsight by viewModel.aiInsight.collectAsStateWithLifecycle()
    val trendPoints by viewModel.trendPoints.collectAsStateWithLifecycle()
    val trendGranularity by viewModel.trendGranularity.collectAsStateWithLifecycle()
    val categoryBreakdown by viewModel.categoryBreakdown.collectAsStateWithLifecycle()
    val expandedCategoryId by viewModel.expandedCategoryId.collectAsStateWithLifecycle()
    val expandedCategoryTxs by viewModel.expandedCategoryTransactions.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val currencySymbol by settingsViewModel.currencySymbol.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showCustomPicker by remember { mutableStateOf(false) }
    var pickingStart by remember { mutableStateOf(true) }
    var customStartMs by remember { mutableStateOf(System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000) }
    var customEndMs by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) { viewModel.onScreenEntered() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.appColors.bgBase)
    ) {
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {

            // ── Period selector ────────────────────────────────────────────
            item {
                PeriodSelector(
                    selected = selectedPeriod,
                    onSelect = { period ->
                        if (period == AnalyticsPeriod.Custom) {
                            pickingStart = true
                            showCustomPicker = true
                        } else {
                            viewModel.onPeriodSelected(period)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }

            // ── Section 1: Business Health ─────────────────────────────────
            item {
                BusinessHealthCard(
                    data = businessHealth,
                    currencySymbol = currencySymbol,
                    isLoading = isLoading,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 12.dp)
                )
            }

            // ── Section 2: Outstanding Balance ─────────────────────────────
            item {
                OutstandingBalanceSection(
                    receivables = receivables,
                    payables = payables,
                    currencySymbol = currencySymbol,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 12.dp)
                )
            }

            // ── Section 3: AI Insights ─────────────────────────────────────
            item {
                AiInsightsCard(
                    state = aiInsight,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 12.dp)
                )
            }

            // ── Section 4: Trends ──────────────────────────────────────────
            item {
                TrendsSection(
                    points = trendPoints,
                    granularity = trendGranularity,
                    currencySymbol = currencySymbol,
                    isLoading = isLoading,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 12.dp)
                )
            }

            // ── Section 5: Expense Breakdown ───────────────────────────────
            item {
                ExpenseBreakdownSection(
                    breakdown = categoryBreakdown,
                    expandedCategoryId = expandedCategoryId,
                    currencySymbol = currencySymbol,
                    isLoading = isLoading,
                    onCategoryTap = { viewModel.onCategoryTapped(it) },
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 12.dp)
                )
            }

            // Expanded category transaction list
            if (expandedCategoryId != null && expandedCategoryTxs.isNotEmpty()) {
                item {
                    val catName = categoryBreakdown.find { it.categoryId == expandedCategoryId }?.categoryName
                        ?: stringResource(R.string.transaction_category)
                    SectionCard(
                        title = stringResource(R.string.analytics_transactions_for_template, catName),
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 12.dp)
                    ) {
                        expandedCategoryTxs.take(20).forEach { tx ->
                            ExpandedTransactionRow(tx = tx, currencySymbol = currencySymbol)
                        }
                    }
                }
            }

            // ── Section 6: Export ──────────────────────────────────────────
            item {
                ExportSection(
                    onExportCsv = {
                        scope.launch {
                            val periodLabel = selectedPeriod.label      // snapshot before suspend
                            try {
                                val (txs, cats, accs) = viewModel.loadExportData()
                                val intent = ReportExporter(context).buildCsvShareIntent(
                                    txs, cats, accs, periodLabel
                                )
                                context.startActivity(Intent.createChooser(intent, "Export CSV"))
                            } catch (e: Exception) {
                                Toast.makeText(context, context.getString(R.string.analytics_csv_export_failed_msg), Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onExportPdf = {
                        scope.launch {
                            val periodLabel = selectedPeriod.label      // snapshot before suspend
                            val healthSnapshot = businessHealth         // snapshot before suspend
                            val breakdownSnapshot = categoryBreakdown   // snapshot before suspend
                            try {
                                val (txs, cats, _) = viewModel.loadExportData()
                                val intent = ReportExporter(context).buildPdfShareIntent(
                                    totalIncomePaise = healthSnapshot.totalIncomePaise,
                                    totalExpensePaise = healthSnapshot.totalExpensePaise,
                                    netPaise = healthSnapshot.netPaise,
                                    categoryBreakdown = breakdownSnapshot,
                                    transactions = txs,
                                    categories = cats,
                                    periodLabel = periodLabel
                                )
                                context.startActivity(Intent.createChooser(intent, "Export PDF"))
                            } catch (e: Exception) {
                                Toast.makeText(context, context.getString(R.string.analytics_pdf_export_failed_msg), Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 12.dp)
                )
            }
        }
    }

    // Custom date range dialog (two-step: start then end).
    // key(pickingStart) forces rememberDatePickerState to reinitialize when switching steps —
    // rememberSaveable ignores initialSelectedDateMillis changes after first composition.
    if (showCustomPicker) {
        key(pickingStart) {
            val dateState = rememberDatePickerState(
                initialSelectedDateMillis = if (pickingStart) customStartMs else customEndMs
            )
            DatePickerDialog(
                onDismissRequest = { showCustomPicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        val picked = dateState.selectedDateMillis ?: return@TextButton
                        if (pickingStart) {
                            customStartMs = picked
                            pickingStart = false
                        } else {
                            val endMs = picked + 86_399_999L
                            if (endMs < customStartMs) {
                                Toast.makeText(context, context.getString(R.string.analytics_end_date_error), Toast.LENGTH_SHORT).show()
                                return@TextButton
                            }
                            customEndMs = endMs
                            showCustomPicker = false
                            viewModel.onCustomRangeSelected(customStartMs, customEndMs)
                        }
                    }) { Text(if (pickingStart) stringResource(R.string.analytics_next_btn) else stringResource(R.string.action_confirm)) }
                },
                dismissButton = {
                    TextButton(onClick = { showCustomPicker = false }) { Text(stringResource(R.string.action_cancel)) }
                }
            ) {
                DatePicker(state = dateState)
                if (!pickingStart) {
                    Text(
                        stringResource(R.string.analytics_pick_end_date_msg),
                        style = AppTypography.bodySmall,
                        color = MaterialTheme.appColors.textSecondary,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }
        }
    }
}

// ─── Period Selector ──────────────────────────────────────────────────────────

@Composable
private fun PeriodSelector(
    selected: AnalyticsPeriod,
    onSelect: (AnalyticsPeriod) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        AnalyticsPeriod.entries.forEach { period ->
            val isSelected = period == selected
            val displayLabel = when (period) {
                AnalyticsPeriod.ThisWeek    -> stringResource(R.string.analytics_range_this_week)
                AnalyticsPeriod.ThisMonth   -> stringResource(R.string.analytics_range_this_month)
                AnalyticsPeriod.Last3Months -> stringResource(R.string.analytics_range_last_3_months)
                AnalyticsPeriod.ThisYear    -> stringResource(R.string.analytics_range_this_year)
                AnalyticsPeriod.Custom      -> stringResource(R.string.analytics_range_custom)
            }
            Box(
                modifier = Modifier
                    .clip(AppShapes.full)
                    .background(
                        if (isSelected) MaterialTheme.appColors.primary
                        else MaterialTheme.appColors.bgSurface2
                    )
                    .border(
                        width = if (isSelected) 0.dp else 1.dp,
                        color = MaterialTheme.appColors.borderLight,
                        shape = AppShapes.full
                    )
                    .clickable { onSelect(period) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = displayLabel,
                    style = AppTypography.caption,
                    color = if (isSelected) Color.White else MaterialTheme.appColors.textSecondary
                )
            }
        }
    }
}

// ─── Section 1: Business Health ───────────────────────────────────────────────

@Composable
private fun BusinessHealthCard(
    data: BusinessHealthData,
    currencySymbol: String,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    SectionCard(title = stringResource(R.string.analytics_business_health_title), modifier = modifier) {
        if (isLoading) { LoadingShimmer(); return@SectionCard }

        val (statusLabel, statusColor) = when (data.status) {
            HealthStatus.Profitable -> stringResource(R.string.analytics_status_profitable) to MaterialTheme.appColors.income
            HealthStatus.BreakEven  -> stringResource(R.string.analytics_status_break_even) to MaterialTheme.appColors.warning
            HealthStatus.Loss       -> stringResource(R.string.analytics_status_loss)        to MaterialTheme.appColors.expense
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Box(
                modifier = Modifier
                    .clip(AppShapes.full)
                    .background(statusColor.copy(alpha = 0.12f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) { Text(statusLabel, style = AppTypography.caption, color = statusColor) }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            HealthMetric(
                label = stringResource(R.string.analytics_total_income),
                amountPaise = data.totalIncomePaise,
                currencySymbol = currencySymbol,
                color = MaterialTheme.appColors.income,
                modifier = Modifier.weight(1f)
            )
            HealthMetric(
                label = stringResource(R.string.analytics_total_expense),
                amountPaise = data.totalExpensePaise,
                currencySymbol = currencySymbol,
                color = MaterialTheme.appColors.expense,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.appColors.borderLight)
        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (data.netPaise >= 0) stringResource(R.string.analytics_net_profit)
                       else stringResource(R.string.analytics_net_loss),
                style = AppTypography.bodyLarge,
                color = MaterialTheme.appColors.textPrimary
            )
            Text(
                text = "$currencySymbol${String.format("%,.0f", abs(data.netPaise) / 100.0)}",
                style = AppTypography.amountLarge,
                color = if (data.netPaise >= 0) MaterialTheme.appColors.income
                        else MaterialTheme.appColors.expense
            )
        }
    }
}

@Composable
private fun HealthMetric(
    label: String,
    amountPaise: Long,
    currencySymbol: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(AppShapes.lg)
            .background(MaterialTheme.appColors.bgSurface2)
            .padding(12.dp)
    ) {
        Column {
            Text(label, style = AppTypography.caption, color = MaterialTheme.appColors.textSecondary)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "$currencySymbol${String.format("%,.0f", amountPaise / 100.0)}",
                style = AppTypography.bodyLarge,
                color = color
            )
        }
    }
}

// ─── Section 2: Outstanding Balance ──────────────────────────────────────────

@Composable
private fun OutstandingBalanceSection(
    receivables: List<OutstandingItem>,
    payables: List<OutstandingItem>,
    currencySymbol: String,
    modifier: Modifier = Modifier
) {
    SectionCard(title = stringResource(R.string.analytics_outstanding_balance_title), modifier = modifier) {
        if (receivables.isEmpty() && payables.isEmpty()) {
            Text(
                stringResource(R.string.analytics_no_outstanding_msg),
                style = AppTypography.bodyRegular,
                color = MaterialTheme.appColors.textTertiary,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                stringResource(R.string.analytics_outstanding_hint),
                style = AppTypography.bodySmall,
                color = MaterialTheme.appColors.textTertiary
            )
            return@SectionCard
        }

        if (receivables.isNotEmpty()) {
            OutstandingList(
                heading = stringResource(R.string.analytics_receivables_title),
                items = receivables,
                currencySymbol = currencySymbol,
                amountColor = MaterialTheme.appColors.income
            )
        }
        if (receivables.isNotEmpty() && payables.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.appColors.borderLight)
            Spacer(Modifier.height(12.dp))
        }
        if (payables.isNotEmpty()) {
            OutstandingList(
                heading = stringResource(R.string.analytics_payables_title),
                items = payables,
                currencySymbol = currencySymbol,
                amountColor = MaterialTheme.appColors.expense
            )
        }
    }
}

@Composable
private fun OutstandingList(
    heading: String,
    items: List<OutstandingItem>,
    currencySymbol: String,
    amountColor: Color
) {
    val sdf = remember { SimpleDateFormat("dd MMM", Locale.getDefault()) }
    Text(heading, style = AppTypography.bodySmall, color = MaterialTheme.appColors.textSecondary)
    Spacer(Modifier.height(8.dp))
    items.take(5).forEach { item ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.partyName.ifBlank { stringResource(R.string.analytics_unknown) },
                    style = AppTypography.bodyRegular,
                    color = MaterialTheme.appColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!item.note.isNullOrBlank()) {
                    Text(
                        item.note,
                        style = AppTypography.bodySmall,
                        color = MaterialTheme.appColors.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "$currencySymbol${String.format("%,.0f", item.amountPaise / 100.0)}",
                    style = AppTypography.amount,
                    color = amountColor
                )
                Text(
                    sdf.format(Date(item.date)),
                    style = AppTypography.bodySmall,
                    color = MaterialTheme.appColors.textTertiary
                )
            }
        }
    }
}

// ─── Section 3: AI Insights ───────────────────────────────────────────────────

@Composable
private fun AiInsightsCard(
    state: AiInsightState,
    modifier: Modifier = Modifier
) {
    SectionCard(
        title = stringResource(R.string.analytics_ai_insights_title),
        trailingIcon = {
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.appColors.primary,
                modifier = Modifier.size(18.dp)
            )
        },
        modifier = modifier
    ) {
        when (state) {
            is AiInsightState.Idle    -> Text(stringResource(R.string.analytics_generating_insights_msg),
                style = AppTypography.bodyRegular, color = MaterialTheme.appColors.textTertiary)
            is AiInsightState.Loading -> LoadingShimmer()
            is AiInsightState.Ready   -> Text(state.text,
                style = AppTypography.bodyRegular,
                color = MaterialTheme.appColors.textPrimary,
                lineHeight = 22.sp)
            is AiInsightState.Error   -> Text(stringResource(R.string.analytics_insights_unavailable_msg),
                style = AppTypography.bodyRegular, color = MaterialTheme.appColors.textTertiary)
        }
    }
}

// ─── Section 4: Trends ────────────────────────────────────────────────────────

@Composable
private fun TrendsSection(
    points: List<TrendPoint>,
    granularity: TrendGranularity,
    currencySymbol: String,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    SectionCard(title = stringResource(R.string.analytics_income_expense_trends_title), modifier = modifier) {
        when {
            isLoading -> LoadingShimmer()
            points.isEmpty() -> Text(
                stringResource(R.string.analytics_no_data_period_msg),
                style = AppTypography.bodyRegular,
                color = MaterialTheme.appColors.textTertiary,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            else -> {
                val incomeColor = MaterialTheme.appColors.income
                val expenseColor = MaterialTheme.appColors.expense
                val gridColor = MaterialTheme.appColors.borderLight
                val labelColor = MaterialTheme.appColors.textTertiary

                Row(
                    modifier = Modifier.padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    LegendDot(stringResource(R.string.income), incomeColor)
                    LegendDot(stringResource(R.string.expense), expenseColor)
                }

                TrendLineChart(
                    points = points,
                    incomeColor = incomeColor,
                    expenseColor = expenseColor,
                    gridColor = gridColor,
                    labelColor = labelColor,
                    modifier = Modifier.fillMaxWidth().height(180.dp)
                )
            }
        }
    }
}

@Composable
private fun TrendLineChart(
    points: List<TrendPoint>,
    incomeColor: Color,
    expenseColor: Color,
    gridColor: Color,
    labelColor: Color,
    modifier: Modifier = Modifier
) {
    val maxVal = remember(points) { points.maxOf { maxOf(it.incomePaise, it.expensePaise) }.coerceAtLeast(1L) }
    val labelPaint = remember(labelColor) {
        android.graphics.Paint().apply {
            textSize = 24f
            color = labelColor.toArgb()
            textAlign = android.graphics.Paint.Align.CENTER
        }
    }

    Canvas(modifier = modifier) {
        val leftPad = 16f
        val bottomPad = 32f
        val chartW = size.width - leftPad
        val chartH = size.height - bottomPad

        // Grid lines
        repeat(5) { i ->
            val y = chartH - chartH * i / 4
            drawLine(gridColor, Offset(leftPad, y), Offset(size.width, y), strokeWidth = 1f)
        }

        if (points.size < 2) return@Canvas

        val step = chartW / (points.size - 1)
        fun xOf(i: Int) = leftPad + step * i
        fun incomeY(p: TrendPoint) = chartH - chartH * p.incomePaise.toFloat() / maxVal
        fun expenseY(p: TrendPoint) = chartH - chartH * p.expensePaise.toFloat() / maxVal

        // Income line
        val incomePath = Path().apply {
            moveTo(xOf(0), incomeY(points[0]))
            points.drop(1).forEachIndexed { idx, p -> lineTo(xOf(idx + 1), incomeY(p)) }
        }
        drawPath(incomePath, incomeColor, style = Stroke(3f, cap = StrokeCap.Round))

        // Expense line
        val expensePath = Path().apply {
            moveTo(xOf(0), expenseY(points[0]))
            points.drop(1).forEachIndexed { idx, p -> lineTo(xOf(idx + 1), expenseY(p)) }
        }
        drawPath(expensePath, expenseColor, style = Stroke(3f, cap = StrokeCap.Round))

        // Dots + x-axis labels
        val labelEvery = (points.size / 6).coerceAtLeast(1)
        points.forEachIndexed { i, p ->
            val x = xOf(i)
            drawCircle(incomeColor,  5f, Offset(x, incomeY(p)))
            drawCircle(expenseColor, 5f, Offset(x, expenseY(p)))
            if (i % labelEvery == 0) {
                drawContext.canvas.nativeCanvas.drawText(p.label, x, size.height, labelPaint)
            }
        }
    }
}

// ─── Section 5: Expense Breakdown ─────────────────────────────────────────────

@Composable
private fun ExpenseBreakdownSection(
    breakdown: List<CategorySpend>,
    expandedCategoryId: String?,
    currencySymbol: String,
    isLoading: Boolean,
    onCategoryTap: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    SectionCard(title = stringResource(R.string.analytics_expense_breakdown_title), modifier = modifier) {
        when {
            isLoading -> LoadingShimmer()
            breakdown.isEmpty() -> Text(
                stringResource(R.string.analytics_no_expenses_period_msg),
                style = AppTypography.bodyRegular,
                color = MaterialTheme.appColors.textTertiary,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            else -> {
                val sliceColors = breakdown.mapIndexed { i, _ -> kChartPalette[i % kChartPalette.size] }
                // Derive tappedIndex from the ViewModel's expandedCategoryId so that
                // pie highlight and row expand/collapse are always in sync regardless
                // of whether the user tapped the pie or the row.
                val tappedIndex = breakdown.indexOfFirst { it.categoryId == expandedCategoryId }
                    .takeIf { it >= 0 }

                PieChart(
                    data = breakdown,
                    sliceColors = sliceColors,
                    tappedIndex = tappedIndex,
                    onSliceTap = { idx -> onCategoryTap(breakdown[idx].categoryId) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .padding(bottom = 8.dp)
                )

                breakdown.forEachIndexed { idx, cat ->
                    val isExpanded = cat.categoryId == expandedCategoryId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(AppShapes.md)
                            .background(
                                if (isExpanded) MaterialTheme.appColors.primaryLight
                                else Color.Transparent
                            )
                            .clickable { onCategoryTap(cat.categoryId) }
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(sliceColors[idx])
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            cat.categoryName,
                            style = AppTypography.bodyRegular,
                            color = MaterialTheme.appColors.textPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "$currencySymbol${String.format("%,.0f", cat.totalAmount / 100.0)}",
                            style = AppTypography.amount,
                            color = MaterialTheme.appColors.textPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${String.format("%.1f", cat.percentage)}%",
                            style = AppTypography.bodySmall,
                            color = MaterialTheme.appColors.textTertiary,
                            modifier = Modifier.width(40.dp),
                            textAlign = TextAlign.End
                        )
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.appColors.textTertiary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PieChart(
    data: List<CategorySpend>,
    sliceColors: List<Color>,
    tappedIndex: Int?,
    onSliceTap: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val (sweepAngles, startAngles) = remember(data) {
        val total = data.sumOf { it.totalAmount }.coerceAtLeast(1L).toFloat()
        val sweeps = data.map { it.totalAmount / total * 360f }
        sweeps to sweeps.runningFold(-90f) { acc, s -> acc + s }.dropLast(1)
    }

    Canvas(
        modifier = modifier.pointerInput(data) {
            detectTapGestures { tap ->
                val cx = size.width / 2f
                val cy = size.height / 2f
                val r = minOf(cx, cy) * 0.88f
                val dx = tap.x - cx
                val dy = tap.y - cy
                val dist = sqrt(dx * dx + dy * dy)
                if (dist > r * 0.38f && dist <= r) {
                    var ang = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90f
                    if (ang < 0) ang += 360f
                    sweepAngles.forEachIndexed { idx, sweep ->
                        val s = (startAngles[idx] + 90f).let { if (it < 0) it + 360f else it }
                        val e = (s + sweep).let { if (it >= 360f) it - 360f else it }
                        val hit = if (e >= s) ang >= s && ang < e else ang >= s || ang < e
                        if (hit) { onSliceTap(idx); return@detectTapGestures }
                    }
                }
            }
        }
    ) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = minOf(cx, cy) * 0.88f
        val strokeW = r * 0.5f

        sweepAngles.forEachIndexed { idx, sweep ->
            val expand = if (idx == tappedIndex) 8f else 0f
            val inset = strokeW / 2f + expand
            drawArc(
                color = sliceColors.getOrElse(idx) { Color.Gray },
                startAngle = startAngles[idx],
                sweepAngle = sweep - 1.5f,
                useCenter = false,
                topLeft = Offset(cx - r + inset - expand, cy - r + inset - expand),
                size = Size((r - inset + expand) * 2, (r - inset + expand) * 2),
                style = Stroke(width = strokeW)
            )
        }
    }
}

// ─── Section 6: Export ────────────────────────────────────────────────────────

@Composable
private fun ExportSection(
    onExportCsv: () -> Unit,
    onExportPdf: () -> Unit,
    modifier: Modifier = Modifier
) {
    SectionCard(title = stringResource(R.string.analytics_export_reports_title), modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ExportButton(
                label = stringResource(R.string.analytics_export_csv_btn),
                icon = { Icon(Icons.Default.TableChart, null, modifier = Modifier.size(18.dp)) },
                onClick = onExportCsv,
                modifier = Modifier.weight(1f)
            )
            ExportButton(
                label = stringResource(R.string.analytics_export_pdf_btn),
                icon = { Icon(Icons.Default.FileDownload, null, modifier = Modifier.size(18.dp)) },
                onClick = onExportPdf,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ExportButton(
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(AppShapes.lg)
            .background(MaterialTheme.appColors.bgSurface2)
            .border(1.dp, MaterialTheme.appColors.borderLight, AppShapes.lg)
            .clickable { onClick() }
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            icon()
            Text(label, style = AppTypography.bodyRegular, color = MaterialTheme.appColors.textPrimary)
        }
    }
}

// ─── Shared helpers ───────────────────────────────────────────────────────────

@Composable
private fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    trailingIcon: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppShapes.xl)
            .background(MaterialTheme.appColors.bgSurface)
            .border(1.dp, MaterialTheme.appColors.borderLight, AppShapes.xl)
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            Text(
                title,
                style = AppTypography.headline3,
                color = MaterialTheme.appColors.textPrimary,
                modifier = Modifier.weight(1f)
            )
            trailingIcon?.invoke()
        }
        content()
    }
}

@Composable
private fun LegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(4.dp))
        Text(label, style = AppTypography.caption, color = MaterialTheme.appColors.textSecondary)
    }
}

@Composable
private fun LoadingShimmer() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(3) { i ->
            Box(
                modifier = Modifier
                    .fillMaxWidth(if (i == 2) 0.6f else 1f)
                    .height(16.dp)
                    .clip(AppShapes.md)
                    .background(MaterialTheme.appColors.bgSurface2)
            )
        }
    }
}

@Composable
private fun ExpandedTransactionRow(tx: Transaction, currencySymbol: String) {
    val sdf = remember { SimpleDateFormat("dd MMM", Locale.getDefault()) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                tx.title,
                style = AppTypography.bodyRegular,
                color = MaterialTheme.appColors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                sdf.format(Date(tx.date)),
                style = AppTypography.bodySmall,
                color = MaterialTheme.appColors.textTertiary
            )
        }
        Text(
            "$currencySymbol${String.format("%,.0f", tx.amount)}",
            style = AppTypography.amount,
            color = MaterialTheme.appColors.expense
        )
    }
}
