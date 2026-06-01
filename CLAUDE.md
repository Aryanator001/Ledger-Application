# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Environment Setup

Copy `.env.example` to `.env` and set your Gemini API key:
```
GEMINI_API_KEY=your_key_here
```

The `secrets-gradle-plugin` reads `.env` and exposes the key as `BuildConfig.GEMINI_API_KEY`. The key is consumed directly in `GeminiTransactionExtractor` and `GeminiQuerySynthesizer` via raw OkHttp calls — there is no Gemini SDK dependency.

The debug signing config in `app/build.gradle.kts` points to `~/.android/debug.keystore`. If that file doesn't exist on the machine, remove the `signingConfig = signingConfigs.getByName("debugConfig")` line from the `debug` build type before building.

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

Single-module Android app (`com.example`), Jetpack Compose UI, Room + DataStore persistence, MVVM. No DI framework — all singletons are lazy properties on `LedgerApplication`. `minSdk = 24`, `compileSdk = 36`.

### Layers

**`com.example.data`** — Room database (`ledger_database`, schema version 8, `exportSchema = false`), entities (Transaction, Category, Account, Budget), DAOs, and repositories. `TransactionRepository` wraps the DAO and fires `AnalyticsCacheUpdater` on every write. `UserPreferences` and `SecurityStore` use DataStore. `AnalyticsService` is a higher-level facade over the three repositories used by `LedgerViewModel`. Migrations are defined as `MIGRATION_x_y` constants in `AppDatabase`; `fallbackToDestructiveMigration(dropAllTables = true)` covers any unmapped path.

**`com.example.analytics`** — Write-through analytics cache over Room. `AnalyticsKeyResolver` generates stable string keys from a `ParsedQuery` + `TimeToken`. `AnalyticsCacheUpdater` invalidates affected cache rows after each transaction write; entity-type cache entries are capped at 20. `CacheRecoveryManager` self-heals poisoned rows on app start. `CategoryMigrationManager` back-fills category assignments on first run. The cache deliberately skips rows with `count == 0` to avoid the "empty-write poison" bug.

**`com.example.ai`** — Voice-to-transaction pipeline, owned by `AiTransactionViewModel`:

1. **STT**: `AndroidNativeSttProvider` wraps Android's `SpeechRecognizer`.
2. **Intent**: `RuleBasedIntentClassifier` detects language (English / Hindi / Hinglish) then classifies the transcript as `AiIntent.Transaction`, `AiIntent.Query`, or `AiIntent.Unknown` using keyword sets. Strong query words (`kitna`, `how`, `total`, etc.) override transaction verbs.
3. **Transaction extraction**: `GeminiTransactionExtractor` calls `gemini-3.5-flash` with a strict JSON-only prompt and maps the response to a `CandidateTransaction`. Confidence is scored per field; high-confidence complete candidates auto-commit, others go to `AiState.ConfirmationRequired`.
4. **Query**: `QueryTriageRouter` routes to `LocalQueryParser` or `GeminiQueryParser`. `QueryEngine` checks the analytics cache first, falls back to Room aggregation, writes the result back to cache, then calls `GeminiQuerySynthesizer` for a natural-language headline (with a template fallback).

All AI state transitions are modeled as a sealed class `AiState` in `com.example.ai.state`. Key extension properties: `AiState.isInteractive` (requires user input) and `AiState.isBusy` (processing in progress).

**`com.example.ui`** — Compose screens in `ui/screens/`, reusable components in `ui/components/`, theme in `ui/theme/`. The bottom nav shows `Home → Transactions → Settings`; all other screens are pushed onto the back stack. `LedgerViewModel` drives the main CRUD screens; `SettingsViewModel` drives preferences and currency; `SecurityViewModel` drives the lock/biometric screens.

Navigation uses **two coexisting systems**:
- `Route` sealed class (string routes) — defined in `MainActivity.kt`, used for all screens except `FilteredLedger`.
- `ui/navigation/AppRoutes.kt` (`@Serializable` type-safe routes via Compose Navigation 2.8+) — used only for `FilteredLedger`, which carries filter parameters.

**`com.example.data.security`** — `AppLockManager` is a process-wide singleton (initialized in `LedgerApplication.onCreate`) that exposes a `StateFlow<Boolean>` for lock state. `BiometricAuthenticator` wraps `BiometricPrompt`. `SecurityRepository` persists all security settings to DataStore. `MainActivity` overlays `LockScreen` at `zIndex(Float.MAX_VALUE)` when `AppLockManager.isLocked` is true. `FLAG_SECURE` is applied to the window when screenshot protection or preview hiding is enabled.

### Key Wiring

- `LedgerApplication` is the composition root. All repositories and services are created there and passed into ViewModel factories manually.
- `MainActivity` extends `FragmentActivity` (not `ComponentActivity`) — required for `BiometricPrompt`. It creates all ViewModels and the `SttProvider`, then threads them into `MainScreen` which sets up the `NavHost`. `SecurityViewModel` is created via `ViewModelProvider` (not `viewModel()`) so it survives configuration changes before `setContent`.
- Categories are seeded once on first launch from `LedgerApplication.seedDefaultCategoriesIfNeeded`. There are two sets:
  - Named system categories with `exp_*` / `inc_*` IDs (e.g. `exp_food`, `inc_salary`) shown in the UI.
  - AI-aligned shorthand categories (`general`, `food`, `rent`, `shopping`) targeted by the extraction layer — **do not rename these IDs**.
