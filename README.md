<div align="center">
<img width="1200" height="475" alt="GHBanner" src="https://ai.google.dev/static/site-assets/images/share-ais-513315318.png" />
</div>

# 📊 Ledger Application

A smart, AI-powered accounting and expense tracking application built for the modern Indian user. It simplifies financial management by allowing users to record transactions using natural speech in English, Hindi, and Hinglish.

## 🚀 Key Features

*   **🎙️ Voice-to-Transaction:** Add expenses or income by simply speaking. Supports multilingual input (English, Hindi, Hinglish).
*   **🤖 AI Extraction:** Powered by **Google Gemini 1.5 Flash** to precisely extract amounts, categories, entities, and notes from raw audio transcripts.
*   **🔍 Natural Language Queries:** Ask questions like *"Total kharch kitna hua?"* or *"How much did I spend on food this month?"* and get instant answers.
*   **🔒 Privacy & Security:** Built-in Biometric authentication, App Lock, and screenshot protection (`FLAG_SECURE`).
*   **📈 Smart Analytics:** Real-time financial insights with a high-performance analytics caching layer.
*   **📂 Multi-Category Support:** Automatic categorization (Food, Rent, Shopping, etc.) with custom manual entry options.
*   **📱 Modern UI:** Fully built with Jetpack Compose following Material 3 design principles.

## 🛠️ Tech Stack

*   **Language:** Kotlin
*   **UI Framework:** Jetpack Compose
*   **AI Engine:** Google Gemini 1.5 Flash (via REST API)
*   **STT (Speech-to-Text):** Android Native Speech Recognition
*   **Database:** Room (SQLite) for persistence
*   **Key-Value Store:** Jetpack DataStore for preferences and security settings
*   **Networking:** OkHttp
*   **Architecture:** MVVM (Model-View-ViewModel)

## 🏗️ Architecture Overview

The app follows a clean, layered architecture:
*   **`com.example.data`**: Core data layer handling Room entities, DAOs, and Repositories.
*   **`com.example.ai`**: The AI pipeline (STT Provider -> Intent Classifier -> Gemini Extractor/Synthesizer).
*   **`com.example.analytics`**: A specialized write-through cache layer for fast query responses.
*   **`com.example.ui`**: UI components and Compose screens.
*   **`com.example.security`**: App-wide lock management and biometric integration.

## 🏁 Getting Started

### Prerequisites
*   [Android Studio Ladybug](https://developer.android.com/studio) or newer.
*   A Gemini API Key from [Google AI Studio](https://aistudio.google.com/).

### Setup Instructions
1.  **Clone the repository:**
    ```bash
    git clone https://github.com/your-username/Ledger-Application.git
    ```
2.  **Configure Environment Variables:**
    *   Copy `.env.example` to `.env`.
    *   Add your Gemini API Key: `GEMINI_API_KEY=your_api_key_here`.
3.  **Build Configuration:**
    *   If you don't have a local `debug.keystore`, remove the `signingConfig` line in `app/build.gradle.kts` under the `debug` build type.
4.  **Run:**
    *   Sync Gradle and run the app on a physical device or emulator with Google Play Services.

## 🧪 Testing

The project includes a robust testing suite:
*   **Unit Tests:** Run with `./gradlew testDebugUnitTest` (Robolectric).
*   **Screenshot Tests:** Powered by **Roborazzi**.
    *   Verify: `./gradlew verifyRoborazziDebug`
    *   Record: `./gradlew recordRoborazziDebug`
*   **Instrumented Tests:** Run with `./gradlew connectedAndroidTest`.

---
*Developed for a seamless financial tracking experience.*
