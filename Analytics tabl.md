Before writing any UI code, fetch and read the existing theme file (AppColors/AppTheme/Typography) 
and one existing screen (e.g. HomeDashboard.kt) as a reference for visual patterns.

Hard rules — do not deviate:
- ONLY use MaterialTheme.appColors.* tokens for colors. Never hardcode hex values, Color.Red, 
  Color.Gray, etc.
- ONLY use AppTypography.* for text styles. Never use default Material typography 
  (MaterialTheme.typography.*) or inline TextStyle() unless the token genuinely doesn't exist — 
  if it doesn't exist, stop and ask instead of inventing one.
- Match existing spacing/padding values used in HomeDashboard/AiConfirmationCard — don't invent 
  new dp values arbitrarily (8dp/12dp/16dp/24dp grid, whatever this app already uses).
- Match existing card/corner radius, elevation, and border style already used elsewhere in the app.
- New components should look like they were built by the same person who built the existing 
  screens — not visually distinct or "modern redesign" styled.
- If a new color/style genuinely doesn't exist in the theme (e.g. a "warning yellow" for 
  break-even status), add it to the theme file itself as a new token — don't inline it.
- After building each new screen/component, do a self-check: list which theme tokens were used 
  and confirm none were hardcoded.

If at any point existing patterns are ambiguous or missing for a needed UI element, stop and ask 
rather than guessing a new style.

You are Claude Code working inside Android Studio on a voice-first Indian accounting Android app 
(Kotlin, Jetpack Compose, Material 3, Room, DataStore, package com.example, min SDK 26, no Hilt, 
manual ViewModelProvider.Factory).

TASK: Build a new "Analytics" tab — third item in the bottom navigation bar alongside Dashboard 
and Transactions. This is a comprehensive financial analytics screen with 6 sections, ordered 
top to bottom as below.

═══════════════════════════════════════
SECTION 1 — BUSINESS HEALTH OVERVIEW (top card)
═══════════════════════════════════════
- Total Income, Total Expense, Net Profit/Loss for the selected time period
- Status indicator: Profitable (green) / Break-even (yellow) / Loss (red) based on net value
- This is the FIRST thing rendered — query and computation must be fast (<200ms), 
  precompute via Room aggregate query, not in-memory iteration over all transactions

═══════════════════════════════════════
SECTION 2 — OUTSTANDING BALANCE
═══════════════════════════════════════
- Two lists: "Receivables" (customers owe you) and "Payables" (you owe suppliers)
- Detection logic (no schema tagging exists yet): scan Category.name and Transaction.note 
  fields (case-insensitive) for keywords: "receivable", "payable", "udhar", "baki", "due"
- Each item shows: party name (extracted from note/category if possible, else "Unknown"), 
  amount, date
- This is a heuristic MVP solution — flag it as such in a code comment for future iteration 
  toward a proper tagging system

═══════════════════════════════════════
SECTION 3 — AI INSIGHTS
═══════════════════════════════════════
- Auto-generates ONCE when Analytics screen opens (not on every recomposition — cache result 
  in ViewModel state, regenerate only if time period changes or screen is freshly navigated to)
- Model: gemini-3.1-pro-preview (NOTE: this is a preview model, different from the existing 
  gemini-1.5-flash used in transaction extraction — confirm BuildConfig.GEMINI_API_KEY has 
  access to it, may need separate quota check)
- Endpoint: https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-pro-preview:generateContent?key=$apiKey
- Same pattern as existing GeminiTransactionExtractor: OkHttp + org.json, no Retrofit/Moshi
- Input: aggregated summary data for the selected period (NOT raw transaction list — send 
  category totals, income/expense deltas vs previous period, top spending categories — keep 
  payload small for speed)
- Output: 2-3 sentence natural language insight (e.g. "You spent 40% more on food this month 
  compared to last month")
- Show loading shimmer while generating, graceful fallback text if API fails 
  ("Insights unavailable right now")

═══════════════════════════════════════
SECTION 4 — TRENDS
═══════════════════════════════════════
- Line or bar chart: Income vs Expense over time, grouped by week or month depending on 
  selected range (auto-switch granularity: daily for "This Week", weekly for "This Month", 
  monthly for "Last 3 Months"/"This Year"/"Custom" if range > 60 days)
- Implementation: pure Jetpack Compose Canvas — custom-drawn, no chart library dependency
- Two colored lines/bars (income vs expense) with a legend

═══════════════════════════════════════
SECTION 5 — EXPENSE BREAKDOWN
═══════════════════════════════════════
- Pie/doughnut chart: category-wise expense distribution for selected period
- Implementation: pure Compose Canvas custom-drawn pie chart (arc segments via drawArc)
- Tap a segment → expand to show transaction list for that category below the chart
- Legend with category name, amount, percentage

═══════════════════════════════════════
SECTION 6 — EXPORT REPORTS
═══════════════════════════════════════
- Two buttons: "Export PDF" and "Export CSV"
- PDF: use native android.graphics.pdf.PdfDocument (NOT a third-party library — zero new 
  Gradle dependency). Layout: header with business name (if Profile exists, else app name), 
  date range, summary totals, transaction table, simple branding footer
- CSV: plain text writer (StringBuilder + FileWriter), columns: Date, Type, Category, 
  Amount, Account, Note
- Both saved to app-specific external storage, shared via FileProvider + Intent.ACTION_SEND

═══════════════════════════════════════
TIME PERIOD SELECTOR (applies to all sections above)
═══════════════════════════════════════
- Predefined chips: "This Week", "This Month", "Last 3 Months", "This Year", "Custom"
- Custom opens a date range picker (use existing DatePicker pattern if one exists in the 
  codebase, else Compose Material3 DateRangePicker)
- Selecting a new range triggers recomputation of all 6 sections (Business Health, Outstanding 
  Balance is NOT period-filtered — outstanding is always current/all-time, the rest are)

═══════════════════════════════════════
ARCHITECTURE
═══════════════════════════════════════
New files:
- AnalyticsViewModel.kt + AnalyticsViewModelFactory.kt (manual factory, no Hilt)
- AnalyticsRepository.kt — wraps TransactionRepository/AccountRepository/CategoryRepository, 
  adds aggregate query methods (do NOT duplicate raw transaction fetching logic — reuse 
  existing DAOs, add new @Query aggregate methods to existing TransactionDao if needed)
- AnalyticsScreen.kt — top-level Composable, hosts all 6 sections + time period selector
- Sub-composables: BusinessHealthCard, OutstandingBalanceSection, AiInsightsCard, 
  TrendsChart, ExpenseBreakdownChart, ExportReportsSection
- ReportExporter.kt — PDF + CSV generation logic
- GeminiInsightsService.kt — wraps the gemini-3.1-pro-preview call (separate from existing 
  GeminiTransactionExtractor to avoid coupling extraction logic with insights logic)

New Room DAO queries needed on TransactionDao (add, don't replace existing):
- getIncomeExpenseTotalsForRange(startDate, endDate): aggregate SUM by type
- getCategoryWiseExpenseForRange(startDate, endDate): GROUP BY category
- getTransactionsForRangeGroupedByPeriod(startDate, endDate, granularity): for Trends chart
- searchByNoteOrCategoryKeyword(keywords: List<String>): for Outstanding Balance detection

Navigation:
- Add Route.Analytics to existing Route sealed class
- Add third BottomNavigationItem (Dashboard, Transactions, Analytics) with appropriate icon

═══════════════════════════════════════
CONSTRAINTS
═══════════════════════════════════════
- No Hilt/Koin — manual ViewModelProvider.Factory throughout
- No new Gradle dependencies (PDF/CSV/charts all built native — confirm this holds)
- Follow existing theme tokens: MaterialTheme.appColors.bgBase/bgSurface/bgSurface2/primary/
  textPrimary/textSecondary/textTertiary/borderLight, AppTypography.headline3
- All Room queries must be suspend functions or return Flow — no blocking main thread
- AI Insights call must be wrapped in try/catch with graceful UI fallback — never crash 
  the screen if Gemini API fails or times out
- Confidence scores (if any are computed) stay internal — never shown as raw numbers to user

═══════════════════════════════════════
BUILD SEQUENCE
═══════════════════════════════════════
1. Fetch existing TransactionDao, TransactionRepository, AccountRepository, CategoryRepository, 
   Route definitions, BottomNavigation composable, theme tokens file
2. Add aggregate Room queries to TransactionDao
3. Build AnalyticsRepository wrapping the above
4. Build AnalyticsViewModel + Factory with state for: selected period, business health data, 
   outstanding balance lists, AI insight text (+ loading state), trends data, expense breakdown 
   data
5. Build AnalyticsScreen with time period selector wired first (test it filters correctly 
   before building charts)
6. Build BusinessHealthCard + OutstandingBalanceSection (simplest, no charts)
7. Build TrendsChart (Canvas) and ExpenseBreakdownChart (Canvas)
8. Build GeminiInsightsService + AiInsightsCard
9. Build ReportExporter (PDF + CSV) + ExportReportsSection
10. Wire Route.Analytics into NavHost and bottom navigation bar
11. Test: switch time periods, verify all sections recompute, test PDF/CSV export, test AI 
    insight generation and failure fallback

Start by fetching the existing files listed in step 1 and confirm the schema/patterns before 
writing code.
