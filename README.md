# PDF Merger

PDF Merger is a native Android utility for combining PDF pages, reordering and rotating pages, previewing documents, and optionally applying password protection to the generated PDF.

The production-hardening branch prioritizes PDF correctness, local processing, storage safety, and truthful security state over cosmetic changes.

## Architecture

```text
MainActivity
  -> PdfMergerViewModel
     -> FileUtil / PdfThumbnailHelper
     -> PdfMergerEngine (PDFBox vector page import)
     -> PdfSaveManager (MediaStore / Storage Access Framework)
```

Imported working copies and thumbnails are stored only in app-owned cache directories scoped by document ID. Generated PDFs are created under the app-private `filesDir/merged_pdfs` directory until the user saves, opens, shares, or clears the session.

## Current behavior

- Multi-PDF import from the system picker and incoming PDF intents
- Page preview, reorder, duplicate, delete, reverse, and rotation controls
- PDFBox-first vector page merge
- Post-save validation of page count and encryption state
- Optional user/owner passwords and PDF permission flags
- MediaStore save to `Downloads/PDF_Merger` on Android 10+
- SAF custom-folder and Save As flows
- SAF picker fallback on Android 7-9 instead of broad storage permissions
- FileProvider sharing limited to app-private merged PDFs
- Light, dark, and system theme modes

## Security and privacy model

PDF processing is local to the app. The manifest does not request the `INTERNET` permission, and the project has no Firebase, Retrofit, OkHttp, Room, Gemini, or other cloud-processing stack.

When output protection is requested, the merge is treated as failed unless the saved PDF can be reopened and its encrypted state and requested permission flags can be verified. The UI reports verified output state rather than the state of the security toggle.

The app configures PDFBox `StandardProtectionPolicy` with a 128-bit key length. This repository intentionally does **not** label that output “AES-128” until the generated encryption dictionary is independently verified on device/tooling. PDF permission flags are advisory and depend on the PDF reader enforcing them.

Imported working files, unlocked working copies, thumbnails, output passwords, and app-private merged output are cleared by Clear All / Clear Cache. App-private merged PDFs are excluded from Android cloud backup and device-transfer backup rules. Files explicitly exported or shared by the user are outside the app’s private-storage lifecycle.

## Fidelity policy

The normal merge path imports PDF pages with PDFBox and is intended to preserve PDF structure rather than rasterize pages.

Automatic PdfRenderer/Bitmap fallback is disabled on the hardening branch because rasterization can destroy searchable text, vectors, links, annotations, forms, accessibility information, and signatures. A compatibility/raster mode should only be reintroduced as an explicit user-visible mode after dedicated regression testing.

Not yet claimed as preserved without further fixture/device verification:

- AcroForms
- document outlines/bookmarks
- digital signatures
- all annotation subtypes
- tagged-PDF accessibility structure
- every encrypted or malformed third-party PDF variant

## Build

Requirements:

- JDK 17
- Android SDK / compile SDK 36.1
- Android device or emulator API 24+

From a clean clone:

```bash
./gradlew clean
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
```

CI runs the debug unit-test, lint, and assemble gates on pushes and pull requests.

Release signing is deliberately not committed. Signed release artifacts require:

- `KEYSTORE_PATH`
- `STORE_PASSWORD`
- `KEY_PASSWORD`

The configured release key alias is `upload`.

## Tests

The hardening test suite covers:

- exact page order
- searchable text survival on the vector path
- protected-output reopen/verification
- wrong-password rejection
- rejection of malformed PDF page counts
- failure on invalid requested page indexes
- scoped recursive session cleanup

Additional device-level coverage is still required for storage providers, Android 24/28/29/33+, large documents, forms, annotations, links, rotations across mixed page boxes, and independent encryption-dictionary verification.

## Release limitations

The following are release-verification items, not marketing claims:

- Exact output cipher / crypt-filter identity still requires independent inspection (for example with qpdf or equivalent tooling).
- Large-document performance at 500-1,000 pages has not yet been benchmarked on representative devices.
- R8/minification remains disabled until PDFBox/crypto regression coverage passes on release builds.
- The production application ID must not be changed until Play Console ownership/history is checked.
- PDF permission restrictions cannot guarantee that every third-party reader will enforce them.

## Key source files

- `PdfMergerEngine.kt` — vector merge, password handling, output protection, verification
- `FileUtil.kt` — URI working-copy validation and private-file lifecycle
- `PdfThumbnailHelper.kt` — thumbnails and preview bitmap cache
- `PdfSaveManager.kt` — MediaStore / SAF export
- `PdfMergerViewModel.kt` — UI state and orchestration
- `AUDIT_FINDINGS.md` — production-hardening evidence ledger
