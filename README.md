# 📖 FrictionFree Diary (FlowDiary)

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)
[![Platform](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com/)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20%26%20Material%203-4285F4.svg)](https://developer.android.com/jetpack/compose)
[![Privacy First](https://img.shields.io/badge/Zero%20Network-100%25%20Offline-success.svg)](#zero-network-privacy-philosophy)

An open-source, privacy-first, 100% offline Android journaling app designed to eliminate every ounce of friction between having a thought and putting it into words.

---

## 🌟 Philosophy

Most modern journal apps suffer from bloated friction: login forms, subscription paywalls, cloud sync popups, complex menus, and privacy concerns.

**FrictionFree Diary** takes the exact opposite approach:
1. **Zero Barrier to Writing**: Open the app and start typing immediately. No login, no accounts, no loading screens.
2. **Zero Network Permissions (`android.permission.INTERNET` is omitted)**: Your entries physically cannot leave your device.
3. **100% Data Ownership**: Encrypted locally with **SQLCipher** and unlocked via hardware biometrics. Export your entire journal to clean, open **JSON** whenever you want.
4. **Permanent Free & Open Source**: MIT Licensed. No subscriptions, no ads, no telemetry.

---

## ✨ Features

### ⚡ Frictionless Quick Capture
- **Instant Compose Mode**: Configure the app in Settings to open directly into a fresh blank entry every single time.
- **Home Screen App Shortcut**: Long press the app icon to jump straight into "New Entry".
- **Real-Time `#hashtag` Detection**: Type `#ideas`, `#gratitude`, or `#goals` anywhere in your text. The app indexes them dynamically—no clunky tag picker menus needed.
- **Quick Formatting Toolbar**: 1-tap buttons for Bold, Italic, Headings, Bullet Lists, Interactive Task Checkboxes (`- [ ]`), Blockquotes, Code Blocks, and Current Timestamp insertion.

### 🔒 Hardware Encryption & Biometrics
- Database encrypted on disk using **SQLCipher** AES-256.
- Android Keystore integration for hardware-backed security.
- Instant unlock via **BiometricPrompt** (Fingerprint & Face unlock) with PIN fallback.

### 🎨 8 Curated Visual Themes
- **OLED Black**: Pure `#000000` for deep battery savings on AMOLED displays.
- **Obsidian Slate**: Modern charcoal dark theme.
- **Paper White**: Minimalist, clean paper-white aesthetic.
- **Warm Sepia**: Cozy parchment book-reading tone.
- **Forest Pine**: Deep evergreen and calming sage.
- **Nordic Frost**: Arctic cool blues and slate.
- **Rose Quartz**: Warm pastel palette.
- **Material You Dynamic**: Adapts to system wallpaper colors (Android 12+).
- **Custom Typography**: Switch between Modern Sans, Literary Serif, and Monospace with configurable font scaling (Compact to Extra Large).

### 🗂️ Views & Organization
- **Stream / List View**: Chronological cards with color bars, attached photo thumbnails, and tags.
- **Week / Timeline View**: Browse entries by week cadence.
- **Month / Calendar View**: Interactive monthly grid showing entry density dots with day-level filtering.
- **Hashtag Cloud View**: Visual tag cloud sorted by frequency.
- **Multiple Notebooks**: Keep distinct compartments for Personal, Work, Ideas, or custom notebooks.
- **Color Coding**: Categorize entries using 8 calming color badges.

### 📸 Media & Geotagging
- Attach photos directly to your entries using Android's modern Photo Picker (no legacy storage permissions needed). Images are securely copied to isolated internal app storage.
- Optional Geotagging: Disabled by default. If enabled, coordinates remain strictly on-device.

### 📲 Sharing from Other Apps
- Share text, articles, or web links directly into the app via Android's Share menu (`ACTION_SEND`).
- **Calendar Event Integration**: Share a calendar invite/event to automatically generate a pre-formatted journal entry with title, date, location, and meeting notes.

### 💾 Backup & Data Portability
- 1-tap **Export to JSON**: Lossless backup containing all entries, notebooks, tags, coordinates, and media metadata.
- 1-tap **Import from JSON**: Easily restore your diary without cloud vendor lock-in.
- **Day One Migration**: Natively imports Day One export `.zip` archives and `.json` files, preserving photos, tags, timestamps, and GPS coordinates.
- **AI Agent Import Specification**: See [`AI_JSON_IMPORT_GUIDE.md`](AI_JSON_IMPORT_GUIDE.md) for the exact schema to have AI assistants (ChatGPT, Claude, Gemini, etc.) format journal entries, reading notes, or transcripts for direct import into the app.

---

## 📱 Tablet & Foldable Support

FrictionFree Diary uses Material 3 Adaptive Navigation (`NavigationSuiteScaffold`):
- **Phones**: Compact bottom navigation bar with fluid screen transitions.
- **Tablets / Foldables / Landscape**: Automatically expands into a side navigation rail or dual-pane layout, making full use of wide screen space.

---

## 🛠️ Architecture & Tech Stack

```
com.frictionfree.diary/
├── data/
│   ├── local/            # Room Database + SQLCipher OpenHelperFactory
│   │   ├── dao/          # EntryDao, NotebookDao, TagDao
│   │   └── entities/     # DiaryEntryEntity, NotebookEntity, TagEntity, CrossRefs
│   ├── model/            # Clean Kotlin domain models (DiaryEntry, Notebook, Tag, ExportData)
│   ├── repository/       # DiaryRepository, SettingsRepository
│   └── security/         # BiometricAuthManager, EncryptionHelper (Keystore)
├── ui/
│   ├── adaptive/         # AdaptiveMainScaffold (Phone & Tablet navigation)
│   ├── components/       # MarkdownRenderer, MarkdownEditorToolbar, ColorPickerRow, EntryCard
│   ├── navigation/       # Type-safe Screen routes & DiaryNavHost
│   ├── screens/          # Editor, Timeline, Calendar, Tags, Notebooks, Settings, Onboarding
│   └── theme/            # 8 Color palettes, Typography scales, and Material 3 theme
└── utils/                # HashtagParser, JsonExporter, ShareIntentHelper, DateFormatters
```

- **Language**: Kotlin 2.1
- **UI Framework**: Jetpack Compose + Material 3
- **Database**: Room 2.6 with SQLCipher 4.6
- **Biometrics**: AndroidX Biometric 1.2
- **Image Loading**: Coil Compose 2.7
- **Serialization**: Kotlinx Serialization JSON 1.7

---

## 🚀 Building & Running

### Prerequisites
- Android Studio Ladybug (2024.2.1+) or newer
- JDK 17
- Android SDK (API 35)

### Open in Android Studio
1. Clone this repository:
   ```bash
   git clone https://github.com/your-username/FrictionFreeDiary.git
   ```
2. Open Android Studio and select **Open**, then choose the cloned `FrictionFreeDiary` folder.
3. Allow Gradle to sync dependencies.
4. Connect an Android device or start an emulator running Android 8.0 (API 26) or higher.
5. Click **Run** (`Shift + F10`).

### Command Line Build
```bash
# Run unit tests
./gradlew testDebugUnitTest

# Assemble debug APK
./gradlew assembleDebug

# Output APK location:
# app/build/outputs/apk/debug/app-debug.apk
```

---

## 🛡️ Zero-Network Privacy Philosophy

FrictionFree Diary's `AndroidManifest.xml` does not declare `android.permission.INTERNET`.
Because the operating system sandbox forbids any socket creation without this permission, you can be mathematically certain that your private thoughts, photos, coordinates, and encryption keys never leave your device.

---

## 📄 License

This project is licensed under the [MIT License](LICENSE).
Feel free to fork, customize, and contribute!
