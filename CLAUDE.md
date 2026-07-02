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
# Note: ExampleUnitTest.testApi makes a live Gemini network call — requires GEMINI_API_KEY in .env and internet access

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
| `com.example.data` | Room entities, DAOs, repositories, DataStore prefs, `security/` |
| `com.example.ai` | Full AI pipeline: STT → intent → extract/query → state |
| `com.example.analytics` | Write-through analytics cache over Room |
| `com.example.ui` | Compose screens, components, ViewModels, navigation, theme |
| `com.example.ui.analytics` | `AnalyticsViewModel`, `GeminiInsightsService`, `ReportExporter` |
| `com.example.ui.security` | `LockScreen`, `SecuritySettingsScreen`, `SecurityViewModel` |

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

Room database (`ledger_database`, v9). Entities: `Transaction`, `Category`, `Account`, `Budget`, `AnalyticsCache`, `UserProfile`, `LedgerScope`. All migrations live in `AppDatabase`. Migration path: 1→2, 4→5, 5→6, 6→8, 8→9 (versions 3 and 7 were skipped — do not create migrations for those numbers). `fallbackToDestructiveMigration` is enabled as a last resort. `Budget` tracks per-category spending limits; `BudgetRepository` is wired on `LedgerApplication`.

`TransactionRepository` is the single write path for transactions; it hooks `AnalyticsCacheUpdater` on each mutation.

**Soft-delete pattern**: `Transaction`, `Account`, and `Budget` all carry `is_deleted: Boolean` and `last_updated: Long`. Logical deletes set `is_deleted = true` rather than physically removing rows — DAOs must filter `WHERE is_deleted = 0` in queries.

**Budget amounts**: `Budget.limitAmount` is stored as a `Long` in paise (×100) to avoid floating-point errors. Convert ÷ 100 before displaying.

**Persistence stores** (two separate mechanisms):
- `UserPreferences` (DataStore `"settings"`) — theme, currency, onboarding flag, `active_user_id`, `active_ledger_scope_id`.
- `SecurityStore` (EncryptedSharedPreferences `"security_prefs"`) — app lock settings, PIN, and cached auth session (`cached_uid`, `cached_id_token`, etc.).

**Startup managers** run in `LedgerApplication.onCreate()` after DI is wired: `CacheRecoveryManager.runIfNeeded()` repairs corrupt analytics cache; `CategoryMigrationManager.runIfNeeded()` handles legacy category data.

### Auth & Multi-Tenancy

`AuthRepository` / `AuthRepositoryImpl` handles Google Sign-In via Jetpack Credential Manager + Firebase Auth. Sessions are persisted to `SecurityStore` (EncryptedSharedPreferences) and restored in `AuthViewModel.init`. `UserSession` is the in-memory representation; `UserProfile` is its Room counterpart.

`LedgerScope` is a per-user namespace (type: `"PERSONAL"` or `"BUSINESS"`). `Transaction`, `Account`, and `Budget` each carry a nullable `ledger_scope_id`; rows without one are "unassigned". `DatabaseStitcher.stitchUnassignedData()` bulk-reassigns them to a default scope inside a single Room `@Transaction`.

New screens: `LoginScreen` (Google sign-in entry point) and `ProfileSetupScreen` (first-run business name / phone). `AuthViewModel` drives both.

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
