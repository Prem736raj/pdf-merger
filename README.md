# PDF Merger

A native Android app for combining multiple PDF files into a single document with page-level control, visual previews, and optional output protection.

Built with **Kotlin**, **Jetpack Compose**, and **PDFBox for Android**.

## Features

- Merge multiple PDF documents into one file
- Preview PDF pages with generated thumbnails
- Reorder pages before merging
- Rotate individual pages while preserving orientation in the output
- Open or share one or multiple PDFs directly into the app
- Handle password-protected PDF files
- Protect merged PDFs with user and owner passwords
- Restrict printing, editing, copying, and annotations
- AES 128-bit output encryption
- Choose the output file name and save destination
- Batch-oriented PDF workflow with merge progress and success feedback
- Material 3 UI built with Jetpack Compose
- Light/dark theme support

## Tech Stack

| Area | Technology |
| --- | --- |
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| PDF processing | PDFBox Android |
| Architecture | ViewModel + Compose state |
| Local data | Room |
| Networking | Retrofit + OkHttp |
| Async work | Kotlin Coroutines |
| Build system | Gradle Kotlin DSL |
| Minimum Android | API 24 (Android 7.0) |
| Target SDK | API 36 |

## Project Structure

```text
pdf-merger/
├── app/
│   ├── src/main/
│   │   ├── java/com/example/
│   │   │   ├── model/       # PDF document, page, merge and security models
│   │   │   ├── ui/          # Compose screens, components and ViewModel
│   │   │   └── util/        # PDF merge, thumbnail, file and save helpers
│   │   ├── res/              # Android resources
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── gradle/
├── build.gradle.kts
├── settings.gradle.kts
└── metadata.json
```

## Getting Started

### Prerequisites

- Android Studio with support for the project's Android Gradle Plugin
- JDK 11 or newer
- Android SDK 36 installed

### Clone the repository

```bash
git clone https://github.com/Prem736raj/pdf-merger.git
cd pdf-merger
```

### Open and run

1. Open the repository in Android Studio.
2. Allow Gradle to sync and download the required dependencies.
3. Select an emulator or Android device running API 24 or newer.
4. Run the `app` configuration.

The project includes an `.env.example` file for optional environment configuration. Keep real API keys and credentials out of version control.

## How It Works

1. Add PDF files from device storage or share PDFs to the app from another Android app.
2. Review the imported documents and page thumbnails.
3. Reorder or rotate pages as needed.
4. Configure the output filename and optional PDF security settings.
5. Merge the selected pages.
6. Save, open, or share the generated PDF.

## PDF Security

The app can create a protected output PDF with:

- User and owner passwords
- Printing restrictions
- Modification restrictions
- Text-copy restrictions
- Annotation restrictions
- 128-bit encryption

Password-protected source PDFs can also be unlocked for processing when the correct password is supplied.

## Development

The main PDF pipeline is implemented in:

- `PdfMergerEngine.kt` — merging, page transforms, decryption and output security
- `PdfThumbnailHelper.kt` — page preview generation
- `PdfSaveManager.kt` — output storage and save-location handling
- `PdfMergerViewModel.kt` — UI state and merge orchestration

## Testing

The repository includes local and instrumented Android test sources under:

```text
app/src/test/
app/src/androidTest/
```

Run them from Android Studio using the standard test actions for the `app` module.

## Contributing

Contributions are welcome. Fork the repository, create a focused branch, make your changes, and open a pull request with a clear description of the behavior you changed.

## Repository

[github.com/Prem736raj/pdf-merger](https://github.com/Prem736raj/pdf-merger)
