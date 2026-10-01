# Production Hardening Audit

Baseline source truth: `main@d41e52ef687d185cbc6e9cdd3778c8596a696786` (`docs: add project README`).

Hardening branch: `astra/pdf-merger-production-hardening`.

This ledger distinguishes code-proven behavior from device/tool verification that is still required.

## Architecture map

`MainActivity.handleIncomingPdfIntent`
→ `PdfMergerViewModel.addDocumentsFromUris`
→ `FileUtil.copyUriToCache`
→ app-owned `cacheDir/pdf_sessions/<documentId>/source_*.pdf`
→ `FileUtil.getPdfPageCount` / `PdfThumbnailHelper.renderPageThumbnail`
→ `PdfMergerViewModel.startMerge`
→ `PdfMergerEngine.mergePdfPages`
→ PDFBox `PDDocument.importPage`
→ optional `StandardProtectionPolicy`
→ `PdfMergerEngine.verifyOutputPdf`
→ app-private `filesDir/merged_pdfs`
→ `PdfSaveManager.saveMergedPdfDirectly`
→ MediaStore (API 29+) / SAF custom tree / SAF picker (API 24-28)
→ FileProvider only for `filesDir/merged_pdfs`.

Automatic raster fallback is disabled on the hardening branch because it is not semantically equivalent to vector page import.

## Feature truth table

| Feature | UI | Core code | End-to-end | Tested | Status |
| --- | --- | --- | --- | --- | --- |
| Multi-PDF import | Yes | Yes | Yes | Partial | 🟡 |
| ACTION_VIEW | N/A | `MainActivity` | Yes | 🧪 | 🟡 |
| ACTION_SEND | N/A | `MainActivity` | Yes | 🧪 | 🟡 |
| ACTION_SEND_MULTIPLE | N/A | `MainActivity` | Yes | 🧪 | 🟡 |
| Document reorder | Yes | ViewModel | Yes | 🧪 | 🟡 |
| Page reorder | Yes | ViewModel | Yes | Core order test | ✅ |
| Rotate | Yes | PDFBox page rotation | Yes | Existing arithmetic only | 🟡 |
| Delete page | Yes | page model | Yes | covered indirectly by selection model | 🟡 |
| Duplicate page | Yes | page model | Yes | 🧪 | 🟡 |
| Reverse order | Yes | page model | Yes | 🧪 | 🟡 |
| Thumbnail | Yes | PdfRenderer | Yes | 🧪 | 🟡 |
| Full preview | Yes | PdfRenderer | Yes | 🧪 | 🟡 |
| Encrypted input | Yes | unlock working copy | Yes | 🧪 | 🟡 |
| Output password | Yes | PDFBox | Yes | regression test | ✅ |
| Owner password | Yes | PDFBox | Yes | regression test | ✅ |
| PDF restrictions | Yes | PDFBox flags | Yes | regression test | ✅ (reader enforcement remains external) |
| Downloads save | Yes | MediaStore API 29+ | Yes | 🧪 device | 🟡 |
| Custom folder | Yes | SAF tree | Yes | 🧪 provider/device | 🟡 |
| Always ask | Yes | SAF picker trigger | Yes | 🧪 | 🟡 |
| Open | Yes | ACTION_VIEW | Yes | 🧪 | 🟡 |
| Share | Yes | FileProvider | Yes | 🧪 | 🟡 |
| Cache cleanup | Yes | recursive scoped cleanup | Yes | unit test | ✅ |
| Dark mode | Yes | Compose theme | Yes | 🧪 device/accessibility | 🟡 |

## KEEP AS-IS

- No broad storage permission.
- No `INTERNET` permission.
- SAF-based document selection.
- MediaStore for modern Downloads.
- PDFBox-first vector merge architecture.
- ViewModel + focused utility architecture; no need for Hilt/Room/domain layers.
- Non-drag page movement controls and absolute-position dialog.
- FileProvider is non-exported.
- Compose light/dark/system theme support.

## Master findings

| ID | Priority | Area | Exact file | Function / location | Evidence | Impact | Fix | Verification | Status |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| PDF-001 | P0 | Build | repo root / `gradle/wrapper` | wrapper files | Baseline had only `gradle-wrapper.properties`; no scripts/JAR | clean clone not reproducible | CI bootstrap prepared; wrapper must be committed | clean clone + CI | IN_PROGRESS |
| PDF-002 | P0 | Security | `PdfMergerEngine.kt` | output protection | baseline swallowed protection exceptions | requested protected output could become unprotected | protection errors fail export; post-save reopen verifies encryption/flags | `PdfHardeningTest.protectedExportIsReopenedAndVerified` | FIXED |
| PDF-003 | P0 | UI security truth | ViewModel / success dialog | merge success | baseline used requested config as verified state | UI could claim protected when unprotected | success metadata comes from `PdfVerificationResult` | unit suite + source inspection | FIXED |
| PDF-004 | P1 | PDF input | `FileUtil.kt` | `getPdfPageCount` | baseline returned 1 after parser failures | corrupt input became fake one-page PDF | parser failure now propagates typed invalid-document error | malformed regression test | FIXED |
| PDF-005 | P1 | PDF merge | `PdfMergerEngine.kt` | page loop | baseline silently `continue`d invalid index | requested page could disappear | invalid requested index fails merge | regression test | FIXED |
| PDF-006 | P1 | Output validity | `PdfMergerEngine.kt` | `verifyOutputPdf` | baseline trusted requested count | silent count mismatch possible | output reopened and exact page count verified | exact order/count test | FIXED |
| PDF-007 | P1 | Fallback fidelity | `PdfMergerEngine.kt` | raster fallback | baseline auto-rasterized PDFBox failures | text/vector/link/form/annotation loss without consent | automatic raster fallback disabled; explicit compatibility error | source + searchable-text regression | FIXED |
| PDF-008 | P1 | Input errors | `PdfMergerEngine.kt` | `loadDocumentSafely` | baseline converted broad failures into password-required | corrupt PDF misdiagnosed as password problem | distinct required/wrong/invalid exceptions | malformed test; password device fixtures pending | FIXED / VERIFY_DEVICE |
| PDF-009 | P1 | Privacy | ViewModel / FileUtil | Clear All / Remove | baseline cleared UI only | private source/unlocked/thumb files persisted | document-scoped dirs + recursive scoped deletion | cleanup regression test | FIXED |
| PDF-010 | P1 | Password lifetime | `PdfDocumentItem.kt`, ViewModel | unlock | baseline duplicated password in model/map | password survived unnecessarily | input password not retained after unlocked copy; reset clears output config | source inspection | FIXED |
| PDF-011 | P1 | Temp collision | FileUtil / thumbnails | cache naming | baseline filename-based caches collided | same-named PDFs could overwrite/reuse assets | document UUID directories and page-number files | source inspection | FIXED |
| PDF-012 | P1 | Save correctness | `PdfSaveManager.kt` | custom tree | baseline null stream still returned success | false save success | non-null stream and copied byte count required; failed created doc removed | device/provider tests pending | FIXED / VERIFY_DEVICE |
| PDF-013 | P1 | Save correctness | `PdfSaveManager.kt` | MediaStore | baseline null stream/publish failure could report success | zero-byte/pending/corrupt output | transactional write, byte-count check, publish check, delete-on-failure | device tests pending | FIXED / VERIFY_DEVICE |
| PDF-014 | P1 | Legacy storage | `PdfSaveManager.kt` | API 24-28 | baseline direct public Downloads without permission | unreliable save/FileProvider mismatch | default direct save uses SAF picker on pre-29; no broad permission added | device tests pending | FIXED / VERIFY_DEVICE |
| PDF-015 | P1 | FileProvider | `file_paths.xml` | provider paths | baseline exposed all cache/files | URI generation scope broader than needed | only `files/merged_pdfs/` exposed | instrumented test pending | FIXED / VERIFY_DEVICE |
| PDF-016 | P1 | Backup/privacy | backup XML | Auto Backup/Data extraction | baseline template rules could back up generated files | privacy claim mismatch | exclude `filesDir/merged_pdfs` from cloud/device transfer; working cache not backup eligible | adb backup/device validation pending | FIXED / VERIFY_DEVICE |
| PDF-017 | P2 | Performance | ViewModel | import loop | all page thumbnails generated before import completes | slow first-use for 500-1,000 pages | convert to on-demand/lazy thumbnail generation | benchmark/device work | OPEN |
| PDF-018 | P2 | Memory | `PdfThumbnailHelper.kt` | LruCache | baseline fixed ~30 MB | disproportionate on low-memory devices | cache now bounded by fraction of max heap, 4-24 MB | heap benchmark pending | FIXED / VERIFY_DEVICE |
| PDF-019 | P2 | Responsive reorder | `PagesGridTab.kt` | drag reorder | adaptive grid but drag math hardcodes 2 columns | wrong drag target on tablet/landscape | derive target from actual LazyGrid layout info | UI/device test | OPEN |
| PDF-020 | P1 | Encryption identity | output security | generated encryption dictionary | 128-bit key length did not prove AES | misleading AES claim | AES wording removed; independently inspect /Filter,/V,/R/crypt filter before reintroducing | qpdf/reader verification | VERIFY_DEVICE |
| PDF-021 | P2 | Forms/bookmarks/signatures | PDFBox import | `importPage` semantics | not proven by current fixtures | preservation may be incomplete | add generated fixtures and document exact behavior | independent fixture tests | OPEN |
| PDF-022 | P2 | Large docs | import/merge | 500-1,000 pages | no representative benchmark | unknown latency/memory/temp footprint | benchmark before Play-ready claim | physical device | VERIFY_DEVICE |
| PDF-023 | P2 | Cancellation | merge loop | coroutine | cancellation now checked between pages; PDFBox calls themselves not interruptible | cancel latency can be long inside one operation | add explicit UI cancel/job plumbing | device test | OPEN |
| PDF-024 | P1 | Dependencies/privacy | Gradle / metadata | dependencies | baseline included unused Firebase/AI/AppCheck/Retrofit/OkHttp/Moshi/Room/KSP | offline architecture ambiguous and larger attack/supply-chain surface | removed unused stack and stale Gemini metadata | dependency/source search + CI | FIXED |
| PDF-025 | P2 | Release | `app/build.gradle.kts` | release | minification disabled; signing external | release artifact not yet qualified | explicit signing validation; R8 deferred until regression suite/device pass | release CI/device | VERIFY_DEVICE |
| PDF-026 | P1 | App identity | Gradle | applicationId/namespace | application ID `com.aistudio.pdfmerger.vqznrk`, namespace `com.example` | identity may be unsuitable, but changing can break Play update path | do not change until Play Console history is verified | Play Console | VERIFY_PLAY_CONSOLE |

## PDF fidelity truth

### Verified in automated test

The PDFBox vector path preserves page-level searchable text for generated text fixtures and produces exact requested page order. The regression marker is extracted from the merged PDF with PDFBox text extraction.

### Not yet independently verified

Links, AcroForms, all annotations, outlines/bookmarks, tagged-PDF structure, digital signatures, transparency edge cases, inherited-resource corner cases, and exact encryption dictionary/cipher identity still require dedicated fixtures/tools.

Digital signatures on source PDFs must not be represented as signatures of the newly generated merged file.

## Storage truth

- API 29+: MediaStore Downloads path, pending row until write completes.
- API 24-28: default public Downloads does not bypass scoped/user-mediated storage; Save As via SAF is required.
- Custom folder: persisted SAF tree permission is used; a revoked/offline/deleted provider returns failure rather than silently redirecting to Downloads.
- Save success requires a created URI, writable stream, complete byte copy, and (for MediaStore) successful publish.
- Failed MediaStore/SAF-created output is deleted when possible.

## Privacy lifecycle

- Imported sources: app cache / document UUID.
- Unlocked copies: same document session directory.
- Thumbnails: document UUID directory.
- Input PDF passwords: used transiently to create the unlocked working copy; not retained in ViewModel/model.
- Output protection passwords: held only in in-memory security config and cleared on session reset.
- App-private merged output: cleared with session/cache reset; exported copies are never deleted by Clear All.
- Backup: app-private merged output explicitly excluded; Android cache is not Auto Backup content.
- FileProvider: merged-output directory only.

## Dependency audit

Removed as unused from application source:

- Firebase BOM / Firebase AI / App Check
- Google Services plugin
- secrets-gradle-plugin
- Retrofit / OkHttp / logging interceptor / Moshi
- Room / KSP
- stale Gemini `.env.example` and AI Studio `metadata.json`

Retained core dependency surface: AndroidX, Compose/Material, lifecycle, coroutines, PDFBox Android, test libraries.

## Production scorecard

Scores reflect current branch evidence, not intended feature names.

| Area | Score / 10 | Note |
| --- | ---: | --- |
| Build reproducibility | 7 | wrapper commit still being finalized |
| PDF import | 7 | typed validation; device URI matrix pending |
| PDF validity | 8 | header + parser + output reopen |
| Merge correctness | 9 | page order/count regression |
| Fidelity | 7 | searchable text verified; forms/etc pending |
| Rotations | 6 | implementation present; box/device fixtures pending |
| Encrypted input | 6 | flow exists; fixture matrix pending |
| Output protection | 9 | fail-closed + reopen verification |
| Save/storage | 8 | transactional code; provider/device matrix pending |
| Privacy | 9 | dependency cleanup + scoped lifecycle/backup |
| Cache lifecycle | 9 | recursive scoped cleanup |
| Memory | 7 | dynamic cache; benchmark pending |
| Large-document performance | 4 | no 500-1,000 page benchmark |
| UI/UX | 7 | functional; save terminology can improve |
| Theme | 7 | code improved; visual matrix pending |
| Accessibility | 6 | alternative reorder exists; TalkBack/font scale pending |
| Dependencies | 9 | stale network/AI stack removed |
| Tests | 7 | meaningful core regressions added; device fixtures pending |
| CI | 8 | workflow in place; final-head run required |
| Release | 5 | signing guarded, R8/device/release bundle not qualified |
| Play readiness | 5 | identity/device/performance/policy checks remain |

## Implementation roadmap

### Completed in this branch
- Build/dependency cleanup.
- CI workflow.
- Fail-closed output security and post-save verification.
- Typed PDF input failures.
- No fake one-page fallback.
- No silent page skip.
- Document-scoped temp storage and password-lifetime reduction.
- Recursive cleanup and private merged-output lifecycle.
- Transactional SAF/MediaStore save.
- Pre-29 SAF strategy.
- Narrow FileProvider.
- Backup exclusions.
- Searchable-text/order/security regression tests.
- Truthful security/privacy copy.

### Required before release approval
1. Commit/verify complete Gradle wrapper and pass clean-clone CI at final head.
2. Run Android 24/28/29/33+/current-device storage matrix.
3. Independently inspect protected PDF encryption dictionary with qpdf or equivalent and test major readers.
4. Add generated link/annotation/form/rotation-box fixtures.
5. Benchmark 10/100/500/1,000 pages and large scans; make thumbnails lazy before claiming large-document readiness.
6. Fix adaptive-grid drag math or disable drag where layout geometry is not reliable.
7. Run TalkBack, 1.3x/1.5x/2.0x font scale, contrast, landscape, and tablet checks.
8. Verify Play Console package identity before any application ID change.
9. Exercise release signing, `lintRelease`, `assembleRelease`, and `bundleRelease`; evaluate R8 only with regression coverage.

## CI evidence

- Baseline `main@d41e52e...`: no workflow/checks.
- Early hardening CI initially hit an external 504 while fetching Kotlin compiler artifacts; not a source failure.
- `d77e893c...`: debug unit tests, lint, and assemble passed.
- `008f6acd...`: unit tests passed; lint exposed an API-24 `File.toPath()` incompatibility introduced by hardening. That issue is fixed by `d819d23f...` using canonical path strings.
- Final-head CI must be green before merge recommendation.

## Release decision

Do **not** merge to `main` or call the app Play-ready yet. P0 security correctness is substantially hardened, but device/provider, encryption-dictionary, large-document, accessibility, application-identity, and release-artifact verification remain explicit gates.
