# PDF Merger Pro

A professional Android PDF merger application built with Jetpack Compose and Material Design 3.

## Features

- **Merge PDFs** — Combine 2+ PDF files into one unified document
- **Page Management** — Remove unwanted pages, reorder visually with drag-and-drop
- **Security** — Optional password encryption with configurable printing and copying restrictions
- **Share & Receive** — Open PDFs directly from other apps via share/open-with
- **Privacy-First** — All PDF processing happens 100% on-device
- **Adaptive Ads** — Google AdMob with UMP consent flow (GDPR-compliant)

## Tech Stack

- **Kotlin** + **Jetpack Compose** (Material 3)
- **PdfBox Android** for PDF merging and manipulation
- **Room** for local data persistence
- **Google AdMob** + **UMP** for ads and consent
- **SAF** (Storage Access Framework) for file access

## Setup

**Prerequisites:** [Android Studio](https://developer.android.com/studio)

1. Open Android Studio → **Open** → select this project directory
2. Allow Gradle to sync and download dependencies
3. Run on an emulator or physical device

## Generating Signed AAB in Android Studio (For Play Store)

1. Open the project in Android Studio.
2. In the top menu, go to **Build** → **Generate Signed Bundle / APK...**
3. Select **Android App Bundle** and click **Next**.
4. Key store path: Choose `my-upload-key.jks` located in your project directory.
5. Enter your Key store password, Key alias (`upload`), and Key password.
6. Select destination directory, choose build variant **release**, and click **Finish**.
7. Android Studio will generate the signed `.aab` file in `app/release/`.

## Privacy Policy

The official Privacy Policy for Google Play Console is available at:
**[https://github.com/Prem736raj/pdf-merger/blob/main/PRIVACY_POLICY.md](https://github.com/Prem736raj/pdf-merger/blob/main/PRIVACY_POLICY.md)**

Raw Link:
`https://raw.githubusercontent.com/Prem736raj/pdf-merger/main/PRIVACY_POLICY.md`

## Play Store Readiness Checklist

- [x] Unique `applicationId` (`com.mergepdf.inone`)
- [x] Stable `targetSdk = 36` and `compileSdk = 36`
- [x] R8 minification and resource shrinking enabled for Release
- [x] ProGuard rules configured
- [x] Clean namespace (`com.mergepdf.inone`) across all 39 source files
- [x] Debug/Release AdMob IDs properly separated
- [x] UMP consent flow for GDPR compliance
- [x] Intent filters for PDF open/share
- [x] FileProvider configured
- [x] Privacy Policy created and hosted on GitHub
- [x] Upload Keystore generated (`my-upload-key.jks`)
- [ ] Upload `.aab` to Google Play Console
- [ ] Provide Privacy Policy URL in Play Console App Content settings

## License

All rights reserved.
