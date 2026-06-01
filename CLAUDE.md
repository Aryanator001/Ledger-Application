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

## Relevant Docs

- [Jetpack Compose](https://developer.android.com/develop/ui/compose/documentation)
- [Room](https://developer.android.com/training/data-storage/room)
- [Compose Navigation](https://developer.android.com/guide/navigation/design/kotlin-dsl)
- [Google AI Studio (Gemini API)](https://aistudio.google.com/)
