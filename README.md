# PDF Merger

PDF Merger is a native Android utility for combining PDF pages, reordering and rotating pages, previewing documents, and optionally applying password protection to generated PDFs.

The production-hardening branch prioritizes PDF correctness, local processing, storage safety, fidelity, and truthful security state over cosmetic changes.

## Architecture

```text
MainActivity
  -> PdfMergerViewModel
     -> FileUtil / PdfThumbnailHelper
     -> PdfMergerEngine (PDFBox vector page import)
     -> PdfSaveManager (MediaStore / Storage Access Framework)
```

Imported working copies and thumbnails live in app-owned cache directories scoped by document ID. Generated PDFs are created under app-private `filesDir/merged_pdfs` until the user explicitly saves, opens, shares, or clears the session.

## Current behavior

- Multi-PDF import from the system picker and incoming PDF intents
- Page preview, deterministic reorder controls, duplicate, delete, reverse, and rotation
- Lazy/on-demand page-thumbnail generation
- PDFBox-first vector page merge
- Exact output page-count verification after save
- Optional user/owner passwords and PDF permission flags
- MediaStore save to `Downloads/PDF_Merger` on Android 10+
- SAF custom-folder and Save As flows
- SAF picker on Android 7-9 instead of broad storage permissions
- FileProvider sharing limited to app-private merged PDFs
- Cooperative merge cancellation between pages
- Light, dark, and system theme modes

## Security and privacy model

PDF processing is local to the app. The manifest does not request the `INTERNET` permission, and the project has no Firebase, Retrofit, OkHttp, Room, Gemini, analytics, or other cloud-processing stack.

Protected export is fail-closed: if requested PDF protection cannot be applied and verified, export fails. The saved PDF is reopened and checked for page count, encrypted state, configured password behavior, and permission flags before the UI can report success.

Generated protected output is independently checked in CI with qpdf. The generated fixture verifies the Standard security handler with `/V=4`, `/R=4`, `/Length=128`, and `/CFM=/AESV2`; this is an AES-128 output profile. PDF permission flags remain advisory because enforcement depends on the PDF reader.

Imported working files, unlocked working copies, thumbnails, output-password state, and app-private merged output are cleared by Remove Document / Clear All / Clear Cache as applicable. App-private merged PDFs are excluded from Android cloud backup and device-transfer rules. Files explicitly exported or shared by the user are outside the app-private lifecycle.

## Fidelity policy

The normal merge path imports pages with PDFBox and does not silently rasterize them. Automatic PdfRenderer/Bitmap fallback is disabled because rasterization can destroy searchable text, vectors, links, annotations, forms, accessibility information, and signatures.

Generated regression fixtures currently verify:

- exact requested page order and count
- searchable/selectable text survival on the vector path
- external URI link annotation preservation
- text-note annotation preservation
- 0°, 90°, 180°, and 270° page rotation
- MediaBox and CropBox preservation across those rotations
- inherited page resources are materialized when PDFBox page import would otherwise omit them
- protected-output password/encryption/permission verification

Not claimed as preserved without further dedicated testing:

- AcroForms and document-level form structure
- outlines/bookmarks
- source digital-signature validity in the newly generated PDF
- all annotation subtypes
- tagged-PDF accessibility structure
- every encrypted, malformed, or pathological third-party PDF variant

## Build

Requirements:

- JDK 17
- Android SDK / compile SDK 36.1
- Android device or emulator API 24+

The Gradle 9.3.1 wrapper is fully committed. CI verifies the wrapper instead of repairing it, so a broken clean clone fails visibly.

From a clean clone:

```bash
./gradlew clean
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew lintRelease
./gradlew assembleDebug
```

Release signing is deliberately not committed. Signed release artifacts require:

- `KEYSTORE_PATH`
- `STORE_PASSWORD`
- `KEY_PASSWORD`

The configured release key alias is `upload`. `assembleRelease` and `bundleRelease` intentionally fail validation without real production signing configuration.

## Automated verification

Current hardening CI covers:

- committed wrapper bootstrap
- meaningful PDF/security/privacy unit tests
- independent qpdf encryption-dictionary verification
- `lintDebug`
- `lintRelease`
- `assembleDebug`

## Remaining release gates

The following remain verification items, not marketing claims:

- Android 24/28/29/33+/current-device storage-provider matrix
- custom-folder revocation/offline/removable-storage behavior on real providers
- 10/100/500/1,000-page and large-scan performance benchmarks
- heap/GC behavior for rapid high-resolution preview usage
- TalkBack, 1.3x/1.5x/2.0x font scale, contrast, landscape, and tablet checks
- AcroForm/bookmark/tagged-PDF/signature behavior
- major third-party reader permission behavior
- Play Console application-ID history before changing `com.aistudio.pdfmerger.vqznrk`
- signed `assembleRelease` / `bundleRelease` and R8 qualification

## Key source files

- `PdfMergerEngine.kt` — vector merge, page fidelity, password handling, output protection, verification
- `FileUtil.kt` — URI working-copy validation and private-file lifecycle
- `PdfThumbnailHelper.kt` — lazy thumbnails and bounded preview bitmap cache
- `PdfSaveManager.kt` — transactional MediaStore / SAF export
- `PdfMergerViewModel.kt` — UI state, lazy thumbnail orchestration, cancellation
- `AUDIT_FINDINGS.md` — production-hardening evidence ledger
