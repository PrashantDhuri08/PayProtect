# PayProtect — Real-Time AI Scam & Fraud Protection for Android

**PayProtect** is an intelligent, local, real-time Android security application designed to protect users against payment scams, phishing attempts, and fraudulent content. Using a local, on-device TensorFlow Lite (TFLite) machine learning model and Android's Accessibility Services, PayProtect actively monitors screen content in real-time and alerts users immediately when high-risk scam patterns are detected.

[Download Latest APK](https://github.com/PrashantDhuri08/PayProtect/releases/latest) | [View All Releases](https://github.com/PrashantDhuri08/PayProtect/releases)

---

## Research & Machine Learning Highlights

> **PayProtect — On-Device Scam Detection (Research Paper)**  
> **Tech Stack & Domains**: Python, Kotlin, TensorFlow Lite, NLP, Edge AI

- **Real-Time Privacy-First Architecture**: Developed a privacy-first Android accessibility app for real-time payment scam detection using TensorFlow Lite and `AccessibilityService` to analyze on-screen content locally.
- **Multilingual Dataset Curation**: Developed the scam-detection CNN model and curated a custom **30K multilingual SMS dataset** covering **English, Hindi, Marathi, and Hinglish** across 5 distinct classes (_spam, promotional, transactional, ham, not ham_).
- **MobileBERT Optimization & Performance**: Optimized a **MobileBERT** model down to **4.8MB**, achieving **97.1% accuracy** with **sub-20ms on-device inference** on mobile hardware.

---

## Key Features

- **Real-Time On-Device Detection**: Scans visible screen text dynamically as users navigate apps using Android `AccessibilityService`.
- **Complete Privacy & Offline Operation**: Performs 100% on-device Natural Language Processing (NLP) inference using TensorFlow Lite — no sensitive screen text or financial data is ever uploaded to external servers.
- **Instant Overlay Warnings**: Displays a priority system overlay popup (`SYSTEM_ALERT_WINDOW`) over suspect apps when a high-confidence scam is flagged.
- **Custom Machine Learning Pipeline**: Utilizes tokenization (`tokenizer_vocab.json`) and a lightweight, fine-tuned TFLite classifier (`scam_detector_merged_data.tflite`).
- **User Permission Management**: Interface in the app for requesting and managing Accessibility and System Overlay permissions.

---

## System Architecture & Data Flow

```
┌────────────────────────────────────────────────────────┐
│                   Android Screen View                  │
└───────────────────────────┬────────────────────────────┘
                            │ (Screen content changes)
                            ▼
┌────────────────────────────────────────────────────────┐
│           ScamDetectorService (Accessibility)           │
│  - Extracts node hierarchy & text from active windows   │
└───────────────────────────┬────────────────────────────┘
                            │ (Extracted Raw Text)
                            ▼
┌────────────────────────────────────────────────────────┐
│                 TFLiteScamPredictor                    │
│  1. Clean & Normalise Text                             │
│  2. Tokenize using JSON Vocab (max sequence = 100)     │
│  3. Run TFLite Inference Engine (XNNPACK Accelerated)  │
└───────────────────────────┬────────────────────────────┘
                            │
                   Is Scam? (Confidence > 0.5)
                            │
               ┌────────────┴────────────┐
               │ YES                     │ NO
               ▼                         ▼
┌───────────────────────────────┐ ┌─────────────┐
│        PopupManager           │ │  (No Action)│
│  - Trigger Top Window Overlay │ └─────────────┘
│  - Display Warning Alert      │
└───────────────────────────────┘
```

---

## Tech Stack & Prerequisites

### Android App

- **Language**: Kotlin
- **Build System**: Gradle (Kotlin DSL, Gradle 8.x)
- **Minimum SDK**: API Level 26 (Android 8.0 Oreo)
- **Target SDK**: API Level 35 (Android 15)
- **UI Framework**: Android XML / Jetpack Compose & Material 3
- **Machine Learning**:
  - TensorFlow Lite (`org.tensorflow:tensorflow-lite:2.15.0`)
  - TFLite Support & Select TF Ops (`tensorflow-lite-support:0.4.4`, `tensorflow-lite-select-tf-ops:2.16.1`)
  - Gson (`com.google.code.gson:gson:2.10.1`) for token mapping
- **Asynchronous Execution**: Kotlin Coroutines (`kotlinx-coroutines-android`)

### Machine Learning & Backend Pipeline

- **Python Package Manager**: [`uv`](https://github.com/astral-sh/uv) fast Python package runner.
- **Model Format**: TensorFlow Lite (`.tflite`) with custom JSON tokenizer vocabulary (`tokenizer_vocab.json`).

---

## Repository Structure

```
PayProtect/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── assets/
│   │   │   │   ├── scam_detector_merged_data.tflite  # Pre-trained TFLite NLP model
│   │   │   │   └── tokenizer_vocab.json              # Tokenizer vocabulary dictionary
│   │   │   ├── java/com/example/payprotect/
│   │   │   │   ├── MainActivity.kt                  # Main launcher activity & permissions interface
│   │   │   │   ├── ScamDetectorService.kt           # Background AccessibilityService listener
│   │   │   │   ├── TFLiteScamPredictor.kt           # NLP preprocessor & TFLite inference engine
│   │   │   │   └── PopupManager.kt                  # System alert overlay manager
│   │   │   ├── res/
│   │   │   │   ├── layout/                          # UI layouts (activity_main, scam_alert_popup)
│   │   │   │   └── xml/accessibility_service_config.xml # Accessibility service metadata
│   │   │   └── AndroidManifest.xml                  # App manifest & service declarations
│   ├── build.gradle.kts                             # Module-level build configuration
│   └── proguard-rules.pro
├── build.gradle.kts                                 # Project-level build configuration
├── settings.gradle.kts                              # Project settings & repositories
└── README.md
```

---

## Getting Started

### 1. Download APK (Quick Start)

You can download the compiled APK directly onto your Android device:
1. Download `PayProtect.apk` from [GitHub Releases](https://github.com/PrashantDhuri08/PayProtect/releases/latest).
2. Install the APK on your device (enable "Install from Unknown Sources" if prompted).
3. Open PayProtect and grant the required Accessibility and Overlay permissions.

---

### 2. Building & Running from Source

#### Prerequisites
- **Android Studio** (Ladybug / Jellyfish or newer recommended)
- **JDK 11** or higher
- Android device or emulator running API 26+

#### Build Steps
1. Clone this repository to your local machine:
   ```bash
   git clone https://github.com/PrashantDhuri08/PayProtect.git
   cd PayProtect
   ```
2. Open the project in **Android Studio**.
3. Allow Gradle to sync and build project dependencies.
4. Run the project on an Android device or emulator (`Shift + F10`).

---

### 3. Granting Required Permissions

To allow PayProtect to monitor screen text and present scam warnings:

1. **System Overlay Permission**:
   - Open PayProtect and tap **"Test Scam Alert Pop-up"**.
   - You will be prompted to grant **Draw Over Other Apps** (`SYSTEM_ALERT_WINDOW`) permission.
2. **Accessibility Service**:
   - Tap **"Enable Scam Detection Service"**.
   - Navigate to **Accessibility Settings** -> **Installed Apps** -> Select **PayProtect Scam Detector** and turn the service **ON**.

---

## Backend & Model Training (Python with `uv`)

If you wish to retrain or update the scam classification model:

1. Ensure [`uv`](https://github.com/astral-sh/uv) is installed:
   ```bash
   pip install uv
   ```
2. Initialize and run your backend training environment using `uv`:

   ```bash
   # Create and sync environment
   uv venv
   uv pip install tensorflow pandas numpy scikit-learn

   # Run training script
   uv run train_scam_model.py
   ```

3. Export the newly generated `.tflite` model and `tokenizer_vocab.json` files to `app/src/main/assets/`.

---

## Security & Privacy

- **No Remote Data Transmission**: Text parsed by the accessibility service is processed exclusively in-memory on the device and discarded.
- **Explicit User Consent**: The application strictly requires manual authorization for Accessibility and Overlay permissions before activating any background scanning.

---

## Contributing

Contributions, issues, and feature requests are welcome. Feel free to check out the issues page or submit a pull request.

---

## License

This project is open-source and available under the [MIT License](LICENSE).
