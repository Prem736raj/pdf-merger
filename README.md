# PDF Merger

PDF Merger is a native Android utility for combining PDF pages, reordering and rotating pages, previewing documents, and optionally applying verified password protection to generated PDFs.

The production-hardening branch prioritizes PDF correctness, local processing, storage safety, fidelity, lifecycle cleanup, and truthful security state over cosmetic changes.

## Architecture

```text
MainActivity
  -> PdfMergerViewModel
     -> FileUtil / PdfThumbnailHelper
     -> PdfMergerEngine (PDFBox vector page import)
     -> PdfSaveManager (MediaStore / Storage Access Framework)
```

Imported working copies and thumbnails live in app-owned cache directories scoped by document ID. Generated PDFs are created under app-private `filesDir/merged_pdfs` until the user explicitly saves, opens, shares, or clears the session.

A fresh ViewModel prunes abandoned app-owned working cache and private merged outputs from a prior process. Session reset cancels active merge/import/sample/unlock/thumbnail work, waits for those jobs to stop, and only then deletes app-owned files.

## Current behavior

- Multi-PDF import from the system picker and incoming PDF intents
- Page preview, deterministic reorder controls, duplicate, delete, reverse, and rotation
- Lazy/on-demand page thumbnails
- PDFBox-first vector page merge with inherited-resource materialization
- Bounded PDFBox mixed-memory/scratch-file loading and bounded bitmap caching
- Exact output page-count verification after save
- Optional user/owner passwords and PDF permission flags
- MediaStore save to `Downloads/PDF_Merger` on Android 10+
- SAF custom-folder and Save As flows
- SAF picker on Android 7-9 instead of broad storage permissions
- Persisted custom-folder access only when Android grants a durable SAF permission
- FileProvider sharing limited to app-private merged PDFs
- Cooperative merge cancellation between pages
- Interactive AcroForm/XFA input fails explicitly instead of silently detaching document-level fields
- Light, dark, and system theme modes

## Security and privacy model

PDF processing is local to the app. The manifest does not request the `INTERNET` permission, and the project has no Firebase, Retrofit, OkHttp, Room, Gemini, analytics, or other cloud-processing stack.

Protected export is fail-closed: if requested PDF protection cannot be applied and verified, export fails. The saved PDF is reopened and checked for page count, encrypted state, configured password behavior, and permission flags before the UI can report success.

Generated protected output is independently checked in CI with qpdf. The generated fixture verifies the Standard security handler with `/V=4`, `/R=4`, `/Length=128`, and `/CFM=/AESV2`; this is an AES-128 output profile. PDF permission flags remain advisory because enforcement depends on the PDF reader.

Generated encrypted-input tests verify password-required, wrong-password rejection, successful unlock to an unencrypted app-private working copy, and searchable-text survival after merge.

Imported working files, unlocked working copies, generated sample PDFs, thumbnails, output-password state, and app-private merged output are removed by the appropriate document/session cleanup. Private merged PDFs and permission-bound save-destination preferences are excluded from Android backup/device transfer. Files explicitly exported or shared by the user are outside the app-private lifecycle.

Production keystore file extensions are ignored by Git, and production signing credentials remain external.

## PDF fidelity policy

The normal merge path imports pages with PDFBox and does not silently rasterize them. Automatic PdfRenderer/Bitmap fallback is disabled because rasterization can destroy searchable text, vectors, links, annotations, forms, accessibility information, and signatures.

Generated regression fixtures verify:

- exact requested page order and count
- searchable/selectable text survival on the vector path
- external URI link annotation preservation
- text-note annotation preservation
- 0°, 90°, 180°, and 270° page rotation
- MediaBox and CropBox preservation across those rotations
- inherited page resources are materialized when PDFBox page import would otherwise omit them
- encrypted-input unlock behavior
- protected-output password/encryption/permission verification
- hostile output names cannot escape the private merged-output directory
- FileProvider accepts merged output and rejects unrelated private files
- interactive AcroForm/XFA inputs fail explicitly rather than being silently degraded

Not claimed as preserved without further dedicated design/testing:

- outlines/bookmarks across arbitrary page selection/reordering
- tagged-PDF document structure
- every annotation subtype
- source digital-signature validity in the newly generated PDF
- every malformed or pathological third-party PDF variant

Merging creates a new PDF. A source document's digital signature must not be represented as a valid signature of the newly generated merged file.

## Storage behavior

- Android 10+: automatic Downloads uses MediaStore and publishes the item only after a complete write.
- Android 7-9: saving is user-mediated through the system document picker.
- Custom SAF folders are remembered only after persistent read/write permission succeeds.
- Replacing/resetting a custom folder releases the previous persisted URI grant when possible.
- App-created MediaStore/custom-tree outputs are deleted on failed writes when possible.
- Manual Save As verifies a writable stream and copied byte count, but does **not** delete an arbitrary destination URI on failure because that URI may have existed before the save.
- Repeated app-private output names are uniquified rather than unexpectedly overwritten.

Real document-provider behavior still requires the device/provider matrix listed below.

## Build and CI

Requirements:

- JDK 17
- Android SDK / compile SDK 36.1
- Android device or emulator API 24+

The Gradle 9.3.1 wrapper is fully committed. CI verifies the committed wrapper instead of repairing it.

From a clean clone:

```bash
./gradlew clean
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew lintRelease
./gradlew assembleDebug
```

The final hardening code head `7f2b73e5d8655b55b770d40b79da34b0d113cd94` passed:

- committed-wrapper verification
- explicit `clean`
- `testDebugUnitTest`
- independent qpdf encryption-dictionary verification
- `lintDebug`
- `lintRelease`
- `assembleDebug`
- unsigned `assembleRelease -x validateReleaseSigning`
- unsigned `bundleRelease -x validateReleaseSigning`

The unsigned release steps verify the release compile/package graph only. Normal `assembleRelease` and `bundleRelease` still depend on `validateReleaseSigning` and require real production signing configuration.

Signed release artifacts require:

- `KEYSTORE_PATH`
- `STORE_PASSWORD`
- `KEY_PASSWORD`

The configured release key alias is `upload`.

## Remaining release qualification

These items require external/device/account evidence and are not claimed complete:

- Android 24/28/29/33+/current-device import/save/open/share matrix
- revoked/deleted/offline/removable-storage document-provider behavior, low-storage/write-failure cases
- representative 10/100/500/1,000-page and large-scan performance, heap/GC, temp-storage, and cancellation-latency benchmarks
- TalkBack, 1.3x/1.5x/2.0x font scale, contrast, small-phone, landscape, and tablet checks
- outline/bookmark, tagged-PDF, and broader annotation/document-structure policy
- major third-party reader behavior for advisory PDF permission flags
- Play Console history/ownership before changing `com.aistudio.pdfmerger.vqznrk`
- real production-keystore `assembleRelease` / `bundleRelease`, install/upgrade verification, and R8 qualification

## Key source files

- `PdfMergerEngine.kt` — vector merge, fidelity guards, bounded PDFBox loading, output protection, verification
- `FileUtil.kt` — URI working-copy validation and private-file lifecycle
- `PdfThumbnailHelper.kt` — lazy thumbnails and bounded preview bitmap cache
- `PdfSaveManager.kt` — MediaStore / SAF export and persisted-folder permissions
- `PdfMergerViewModel.kt` — UI state, session transactions, lazy thumbnails, cancellation
- `PdfHardeningTest.kt` — generated correctness/security/privacy/fidelity regression fixtures
- `AUDIT_FINDINGS.md` — production-hardening evidence ledger
