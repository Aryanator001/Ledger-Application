# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Environment Setup

Copy `.env.example` to `.env` and set your Gemini API key:
```
GEMINI_API_KEY=your_key_here
```

The debug signing config in `app/build.gradle.kts` points to `~/.android/debug.keystore`. If that file doesn't exist, remove the `signingConfig = signingConfigs.getByName("debugConfig")` line from the `debug` build type before building.

Release builds require env vars: `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD`.

## Commands

```bash
# Build
./gradlew assembleDebug
./gradlew assembleRelease

# Unit tests (Robolectric, runs on JVM)
./gradlew testDebugUnitTest

# Single test
./gradlew testDebugUnitTest --tests "com.example.ExampleUnitTest.testApi"

# Instrumented tests (requires connected device/emulator)
./gradlew connectedAndroidTest

# Screenshot tests (Roborazzi)
./gradlew verifyRoborazziDebug     # compare against stored screenshots
./gradlew recordRoborazziDebug     # update stored screenshots

# Lint
./gradlew lint
```

## Architecture

Single-module Android app. No Hilt/Dagger — all dependencies are wired manually as `lazy` properties on `LedgerApplication` and passed down through ViewModel factories.

### Package Map

| Package | Purpose |
|---|---|
| `com.example` | `LedgerApplication` (app-level DI, seeding), `MainActivity` (nav host, theme) |
| `com.example.data` | Room entities, DAOs, repositories, DataStore prefs, security |
| `com.example.ai` | Full AI pipeline: STT → intent → extract/query → state |
| `com.example.analytics` | Write-through analytics cache over Room |
| `com.example.ui` | Compose screens, components, ViewModels, navigation, theme |

### AI Pipeline

The AI feature is the most complex part. A voice transcript flows through these stages, each backed by a state in `AiState`:

```
SttProvider (Android native STT)
  └─> RuleBasedIntentClassifier  (English / Hindi / Hinglish keyword rules)
        ├─> [Transaction] GeminiTransactionExtractor (Gemini Flash REST)
        │     └─> TransactionValidator → high confidence = AutoCommitting
        │                               low confidence = ConfirmationRequired
        └─> [Query] QueryTriageRouter
                  ├─> LocalQueryParser (regex/keyword, confidence >= 0.70 → skip Gemini)
                  └─> GeminiQueryParser (fallback)
                        └─> QueryEngine → AnalyticsRepository (cache) → Room → FuzzyEntityMatcher
                              └─> GeminiQuerySynthesizer (headline) or QueryResponseFormatter (fallback)
```

`AiTransactionViewModel` owns the `AiState` `StateFlow` and drives this entire pipeline. All Gemini calls are plain OkHttp REST with `org.json`; the API key comes from `BuildConfig.GEMINI_API_KEY` via the secrets Gradle plugin.

### Analytics Cache

`analytics_cache` is a Room table used as a read-through cache for query aggregates. Key invariant: **a cached row with `count == 0` is treated as a poisoned cache miss** and skipped — this prevents stale zero-count entries from hiding real data. Cache entries are invalidated by bucket-rollover (e.g. "this month" boundary crossing). `AnalyticsCacheUpdater` is notified on every `TransactionRepository` insert/update/delete to keep aggregates current.

### Data Layer

Room database (`ledger_database`, v8). Entities: `Transaction`, `Category`, `Account`, `Budget`, `AnalyticsCache`. All migrations live in `AppDatabase`. `fallbackToDestructiveMigration` is enabled as a last resort. `Budget` tracks per-category spending limits; `BudgetRepository` is wired on `LedgerApplication`.

`TransactionRepository` is the single write path for transactions; it hooks `AnalyticsCacheUpdater` on each mutation.

### Analytics Tab

`AnalyticsViewModel` drives the Analytics screen. It surfaces:
- **Business health** (`BusinessHealthData`) — income vs expense net, computed via `AnalyticsService`
- **Outstanding balances** — receivables/payables derived from tagged transactions
- **Trend chart** — `TrendPoint` list, switchable between daily/weekly/monthly granularity
- **Category breakdown** — expandable rows with per-category transaction drill-down
- **AI insights** — `GeminiInsightsService` calls Gemini Flash with a transaction summary to generate a natural-language insight string; state is `AiInsightState` (Idle / Loading / Ready / Error)
- **Report export** — `ReportExporter` builds CSV or PDF share intents

`AnalyticsService` (`com.example.data`) is a pure computation layer that aggregates over `TransactionRepository`, `AccountRepository`, and `BudgetRepository` — it does not write to the DB.

### Navigation

Two patterns coexist in `MainActivity`:
- **String routes** — legacy `Route` sealed class. Bottom nav: `Home`, `Transactions`, `Analytics`, `Settings`. Deep routes: `AccountDetail`, `GeneralSettings`, `SecuritySettings`, etc.
- **Type-safe routes** — `@Serializable` objects in `AppRoutes.kt` (currently `FilteredLedger`); use `navController.navigate(FilteredLedger(...))` and `backStackEntry.toRoute<FilteredLedger>()`.

New destinations should prefer the type-safe pattern.

### Categories

System categories are seeded by `LedgerApplication.seedDefaultCategoriesIfNeeded()` on first launch. The AI extraction layer targets a **fixed subset of IDs**: `general`, `food`, `rent`, `shopping`. Don't rename or remove those four IDs — the Gemini prompt and `CandidateResolver` depend on them.

### Security

`AppLockManager` (singleton) tracks lock state. When `appLockEnabled && isLocked`, `MainActivity` renders `LockScreen` as a full-screen overlay at `zIndex(Float.MAX_VALUE)`. `SecurityRepository` uses DataStore for PIN hash and biometric preference. `MainActivity` extends `FragmentActivity` (required for BiometricPrompt); permission requests must use codes in the lower 16 bits only.

### Theme

`MyApplicationTheme` wraps Material 3. Custom semantic colors are accessed via `MaterialTheme.appColors` (extension on `ColorScheme`). Theme mode (Light / Dark / System) is persisted in DataStore and read synchronously via `runBlocking` during `onCreate` to avoid a flash.

## Relevant Docs

- [Jetpack Compose](https://developer.android.com/develop/ui/compose/documentation)
- [Room](https://developer.android.com/training/data-storage/room)
- [Compose Navigation](https://developer.android.com/guide/navigation/design/kotlin-dsl)
- [Google AI Studio (Gemini API)](https://aistudio.google.com/)
