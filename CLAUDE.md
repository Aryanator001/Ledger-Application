# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Environment Setup

API keys go in `local.properties` (gitignored, already present). The Secrets Gradle Plugin reads from `local.properties` first, then falls back to `.env.example` for defaults:

```
GEMINI_API_KEY=your_key_here
SARVAM_API_KEY=your_sarvam_key_here   # Primary STT (saaras:v3); Android native STT is the fallback
```

The debug signing config in `app/build.gradle.kts` points to `~/.android/debug.keystore`. If that file doesn't exist, remove the `signingConfig = signingConfigs.getByName("debugConfig")` line from the `debug` build type before building.

Release builds require env vars: `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD`.

## Commands

```bash
# Build
./gradlew assembleDebug
./gradlew assembleRelease

# Unit tests (Robolectric, runs on JVM — no device needed)
./gradlew testDebugUnitTest

# Single test
./gradlew testDebugUnitTest --tests "com.example.ExampleUnitTest.testApi"
# Note: ExampleUnitTest.testApi makes a live Gemini network call — requires GEMINI_API_KEY and internet

# New query engine tests (JVM, no device needed)
./gradlew testDebugUnitTest --tests "com.ledger.query.core.*"
./gradlew testDebugUnitTest --tests "com.ledger.query.engine.*"

# Instrumented tests (requires connected device/emulator)
./gradlew connectedAndroidTest

# Screenshot tests (Roborazzi — JVM, no device needed)
./gradlew verifyRoborazziDebug     # compare against stored screenshots
./gradlew recordRoborazziDebug     # update stored screenshots

# Lint
./gradlew lint
```

## Architecture

Single-module Android app (minSdk 24, compileSdk 36). No Hilt/Dagger — all dependencies are wired manually as `lazy` properties on `LedgerApplication` and passed down through ViewModel factories.

### Package Map

| Package | Purpose |
|---|---|
| `com.example` | `LedgerApplication` (app-level DI, seeding), `MainActivity` (nav host, theme) |
| `com.example.data` | Room entities, DAOs, repositories, DataStore prefs, `security/` |
| `com.example.ai` | Full AI pipeline: STT → intent → extract/query → state |
| `com.example.analytics` | Write-through analytics cache over Room |
| `com.example.ui` | Compose screens, components, ViewModels, navigation, theme |
| `com.example.ui.analytics` | `AnalyticsViewModel`, `GeminiInsightsService`, `ReportExporter` |
| `com.example.ui.security` | `LockScreen`, `SecuritySettingsScreen`, `SecurityViewModel` |
| `com.ledger.query.core` | New query engine: `QueryFrame`, `QueryRouter`, `Lexicon`, `TimeExpressionParser` |
| `com.ledger.query.ai` | AI query tier: `AiPlanner` + `GeminiPlannerImpl` (in `GeminiPlanner.kt`); `AiAnalyst` + `GeminiAnalystImpl` (in `GeminiAnalyst.kt`) |
| `com.ledger.query.engine` | `QueryEngine` (tiered orchestrator), `QueryEngineFactory` |
| `com.ledger.query.exec` | `RoomQueryExecutor`, `PlanCache` (Room entity+DAO), `Templater`, `LedgerLog`, `QueryResult` |

### AI Pipeline

The AI feature is the most complex part. A voice transcript flows through these stages, each reflected in the `AiState` sealed class (`com.example.ai.state.AiState`):

```
DualSttProvider (Sarvam primary → AndroidNativeSttProvider fallback on network/API error)
  └─> RuleBasedIntentClassifier  (English / Hindi / Hinglish keyword rules)
        ├─> [Transaction] GeminiTransactionExtractor (Gemini Flash REST)
        │     └─> TransactionValidator → high confidence = AutoCommitting
        │                               low confidence = ConfirmationRequired
        └─> [Query] QueryTriageRouter (legacy) / com.ledger QueryEngine (new)
                  Legacy path:
                  ├─> LocalQueryParser (regex/keyword, confidence >= 0.70 → skip Gemini)
                  └─> GeminiQueryParser → QueryEngine → AnalyticsRepository → FuzzyEntityMatcher
                        └─> GeminiQuerySynthesizer (headline) or QueryResponseFormatter (fallback)
                  New path (com.ledger.query.engine.QueryEngine):
                  ├─> QueryRouter → Tier.LOCAL → RoomQueryExecutor (no AI)
                  ├─> QueryRouter → Tier.AI_PLANNER → GeminiPlannerImpl → RoomQueryExecutor
                  ├─> QueryRouter → Tier.AI_ANALYST → GeminiAnalystImpl (synthesis over raw facts)
                  └─> QueryRouter → Tier.CLARIFY → disambiguation response
```

**Naming collision**: `com.example.ai.query.QueryEngine` (legacy) and `com.ledger.query.engine.QueryEngine` (new) are two different classes with the same simple name. Always use the fully-qualified package when referring to them.

`AiTransactionViewModel` owns the `AiState` `StateFlow` and drives this entire pipeline. It carries both `queryEngine` (legacy `com.example.ai.query.QueryEngine`) and `newQueryEngine` (nullable `com.ledger.query.engine.QueryEngine`). The new engine is constructed by `QueryEngineFactory` when `database`, `scopeIdProvider`, and `geminiApiKeyProvider` are all available. All Gemini calls are plain OkHttp REST with `org.json`; the API key comes from `BuildConfig.GEMINI_API_KEY` via the secrets Gradle plugin.

**New query engine tiers** (`com.ledger.query.core.Tier`): `LOCAL` executes a deterministic SQL plan without any AI call; `AI_PLANNER` uses Gemini to produce a `QueryPlan` (cached in `query_plan_cache` Room table); `AI_ANALYST` passes already-computed facts to Gemini for natural-language synthesis; `CLARIFY` returns an ambiguity-resolution prompt. `QueryRouter` decides the tier based on `QueryFrame` confidence.

**Test fakes**: `com.ledger.query.core.FakeResolvers` and `com.ledger.query.engine.FakeGemini` provide in-memory doubles for the new query engine tests — use these rather than mocking when adding tests.

### Analytics Cache

`analytics_cache` is a Room table used as a read-through cache for query aggregates. Key invariant: **a cached row with `count == 0` is treated as a poisoned cache miss** and skipped — this prevents stale zero-count entries from hiding real data. Cache entries are invalidated by bucket-rollover (e.g. "this month" boundary crossing). `AnalyticsCacheUpdater` is notified on every `TransactionRepository` insert/update/delete to keep aggregates current.

### Data Layer

Room database (`ledger_database`, v11). Entities: `Transaction`, `Category`, `Account`, `Budget`, `AnalyticsCache`, `UserProfile`, `LedgerScope`, `QueryPlanCacheEntity`. All migrations live in `AppDatabase`. Migration path: 1→2, 4→5, 5→6, 6→8, 8→9, 9→10, 10→11 (versions 3 and 7 were skipped — do not create migrations for those numbers). Migration 9→10 adds business profile columns (`businessType`, `address`, `website`, `gstNumber`, `panNumber`) to `user_profiles`. Migration 10→11 creates the `query_plan_cache` table. `fallbackToDestructiveMigration` is enabled as a last resort. `Budget` tracks per-category spending limits; `BudgetRepository` is wired on `LedgerApplication`.

`TransactionRepository` is the single write path for transactions; it hooks `AnalyticsCacheUpdater` on each mutation.

**Soft-delete pattern**: `Transaction`, `Account`, and `Budget` all carry `is_deleted: Boolean` and `updated_at: Long` (Kotlin field `updatedAt`). Logical deletes set `is_deleted = true` rather than physically removing rows — DAOs must filter `WHERE is_deleted = 0` in queries. `Transaction` also has a separate `is_archived: Boolean` field (for UI-level archiving); archived rows are not soft-deleted.

**Amount storage**: `Transaction.amount` is a `Double` in the user's currency unit (not paise). Only `Budget.limitAmount` is stored as a `Long` in paise (×100) to avoid floating-point errors — convert ÷ 100 before displaying budgets.

**Persistence stores** (two separate mechanisms):
- `UserPreferences` (DataStore `"settings"`) — theme, currency, onboarding flag, `active_user_id`, `active_ledger_scope_id`.
- `SecurityStore` (EncryptedSharedPreferences `"security_prefs"`) — app lock settings, PIN, and cached auth session (`cached_uid`, `cached_id_token`, etc.).

**Startup managers** run in `LedgerApplication.onCreate()` after DI is wired: `CacheRecoveryManager.runIfNeeded()` repairs corrupt analytics cache; `CategoryMigrationManager.runIfNeeded()` handles legacy category data.

### Auth & Multi-Tenancy

`AuthRepository` / `AuthRepositoryImpl` handles Google Sign-In via Jetpack Credential Manager + Firebase Auth. Sessions are persisted to `SecurityStore` (EncryptedSharedPreferences) and restored in `AuthViewModel.init`. `UserSession` is the in-memory representation; `UserProfile` is its Room counterpart.

`LedgerScope` is a per-user namespace (type: `"PERSONAL"` or `"BUSINESS"`). `Transaction`, `Account`, and `Budget` each carry a nullable `ledger_scope_id`; rows without one are "unassigned". `DatabaseStitcher.stitchUnassignedData()` bulk-reassigns them to a default scope inside a single Room `@Transaction`.

**Startup auth flow**: `MainActivity` sets `startDestination` to `Route.Login` when `activeSession == null`, otherwise `Route.Home`. After Google sign-in succeeds, the nav graph goes Login → ProfileSetup → Home. If `activeSession` becomes `null` at any point (sign-out), `MainScreen` reactively re-routes to Login with `popUpTo(0)`. `hasCompletedOnboarding` in `UserPreferences` only gates the first-launch `RECORD_AUDIO` permission request — it is separate from auth state.

**Mock sign-in**: `AuthRepository.signInWithMockDevUser()` produces a deterministic `UserSession` without touching Firebase or Credential Manager — useful for emulator testing where Google sign-in is unavailable.

### Analytics Tab

`AnalyticsViewModel` (`com.example.ui.analytics`) drives the Analytics screen. It surfaces:
- **Business health** (`BusinessHealthData`) — income vs expense net, computed via `AnalyticsService`
- **Outstanding balances** — receivables/payables derived from tagged transactions
- **Trend chart** — `TrendPoint` list, switchable between daily/weekly/monthly granularity
- **Category breakdown** — expandable rows with per-category transaction drill-down
- **AI insights** — `GeminiInsightsService` calls Gemini Flash with a transaction summary to generate a natural-language insight string; state is `AiInsightState` (Idle / Loading / Ready / Error)
- **Report export** — `ReportExporter` builds CSV or PDF share intents

`AnalyticsService` (`com.example.data`) is a pure computation layer that aggregates over `TransactionRepository`, `AccountRepository`, and `BudgetRepository` — it does not write to the DB.

### Navigation

Two patterns coexist in `MainActivity`:
- **String routes** — legacy `Route` sealed class. Bottom nav: `Transactions`, `Analytics`, `Settings`. Deep routes: `AccountDetail`, `GeneralSettings`, `SecuritySettings`, `BackupRestore`, `Reminders`, `RecurringReminders`, `Login`, `ProfileSetup`, etc.
- **Type-safe routes** — `@Serializable` objects in `AppRoutes.kt`: `Home` (data object) and `FilteredLedger` (data class with filter params). Use `navController.navigate(FilteredLedger(...))` and `backStackEntry.toRoute<FilteredLedger>()`.

New destinations should prefer the type-safe pattern.

### ViewModels

Two locations — don't confuse them:
- `com.example.ai`: `AiTransactionViewModel` (owns the AI pipeline `AiState` flow)
- `com.example.ui.viewmodel`: `AuthViewModel`, `LedgerViewModel`, `SettingsViewModel`
- `com.example.ui.analytics`: `AnalyticsViewModel`
- `com.example.ui.security`: `SecurityViewModel`

All use manual `ViewModelProvider.Factory`; no Hilt. Factories are named `<ViewModel>Factory` and wired from `LedgerApplication`.

### UI Theming Rules

Hard constraints — do not deviate:
- **Colors**: Always use `MaterialTheme.appColors.*` tokens. Full set defined in `Color.kt`:
  - Backgrounds: `bgBase`, `bgSurface`, `bgSurface2`, `bgSurface3`
  - Brand: `primary`, `primaryLight`
  - Semantic: `income`, `incomeLight`, `expense`, `expenseLight`, `warning`, `warningLight`, `success`, `error`
  - Text: `textPrimary`, `textSecondary`, `textTertiary`, `textDisabled`
  - Borders: `borderLight`, `borderMedium`
  - Tab bar: `tabBackground`, `tabBorder`, `tabActive`, `tabActiveBg`, `tabInactive`
  - Surfaces: `surfaceInverse`, `surfaceSubtle`, `swipeDeleteBg`, `swipeArchiveBg`
  - Never hardcode hex values or use `Color.Red`, `Color.Gray`, etc.
  - For account color pickers, use `accountPalette` (list of hex strings) from `Color.kt`.
- **Typography**: Always use `AppTypography.*` tokens: `display`, `headline1`, `headline2`, `headline3`, `bodyLarge`, `bodyRegular`, `bodySmall`, `caption`, `monospace`. Never use `MaterialTheme.typography.*` or inline `TextStyle()` — if a token is missing, add it to `Typography.kt`.
- **Spacing**: Use `AppSpacing.*` tokens (`xs`=4dp, `sm`=8dp, `md`=12dp, `lg`=16dp, `xl`=24dp, `xxl`=32dp, `xxxl`=48dp, `buttonHeight`=48dp). Don't invent new arbitrary dp values.
- **New semantic colors** (e.g. a "warning yellow"): Add as a new token to the theme file — never inline it at the call site.
- **Dark theme**: XML color overrides for night mode live in `res/values-night/`. When adding new XML color resources, add them to both `values/` and `values-night/`.
- Before reading any existing screen for reference patterns, check `HomeDashboard.kt` and the theme files in `com.example.ui.theme`.

### Categories

System categories are seeded by `LedgerApplication.seedDefaultCategoriesIfNeeded()` on first launch. The AI extraction layer targets a **fixed subset of IDs**: `general`, `food`, `rent`, `shopping`. Don't rename or remove those four IDs — the Gemini prompt and `CandidateResolver` depend on them.

### Localization

UI strings live in `res/values/strings.xml` (English default), `res/values-hi/strings.xml` (Hindi), and `res/values-gu/strings.xml` (Gujarati). Any new string resource must be added to all three files. Use `stringResource(R.string.*)` in Compose — never hardcode visible text.

### Reminders

`RemindersScreen`, `RecurringRemindersListScreen`, and `CreateReminderSheet` exist as UI-only stubs. There is no AlarmManager, WorkManager, or notification scheduling wired up — these screens are placeholders for a future Pro feature.

### Security

`AppLockManager` (singleton) tracks lock state. When `appLockEnabled && isLocked`, `MainActivity` renders `LockScreen` as a full-screen overlay at `zIndex(Float.MAX_VALUE)`. `SecurityRepository` uses DataStore for PIN hash and biometric preference. `MainActivity` extends `FragmentActivity` (required for BiometricPrompt); permission requests must use codes in the lower 16 bits only.

### Theme

`MyApplicationTheme` wraps Material 3. Custom semantic colors are accessed via `MaterialTheme.appColors` (extension on `ColorScheme`). Theme mode (Light / Dark / System) is persisted in DataStore and read synchronously via `runBlocking` during `onCreate` to avoid a flash.

## Relevant Docs

- [Jetpack Compose](https://developer.android.com/develop/ui/compose/documentation)
- [Room](https://developer.android.com/training/data-storage/room)
- [Compose Navigation](https://developer.android.com/guide/navigation/design/kotlin-dsl)
- [Google AI Studio (Gemini API)](https://aistudio.google.com/)
