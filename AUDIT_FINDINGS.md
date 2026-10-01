# Production Hardening Audit

Baseline source truth: `main@d41e52ef687d185cbc6e9cdd3778c8596a696786` (`docs: add project README`).

Hardening branch: `astra/pdf-merger-production-hardening`.

Verified code-head CI evidence: `c299fb3a60f1932db00eda39aad2a7ee13cf3d52` passed committed-wrapper verification, `testDebugUnitTest`, independent qpdf encryption inspection, `lintDebug`, `lintRelease`, and `assembleDebug`. Documentation-only commits after that code head must also pass CI before merge.

This ledger separates code/CI proof from device, Play Console, reader, and signed-release verification.

## Baseline build truth

Baseline `main` had an incomplete Gradle wrapper: `gradlew`, `gradlew.bat`, and `gradle-wrapper.jar` were absent. It also had no GitHub Actions workflow or commit checks.

The hardening branch now contains the complete Gradle 9.3.1 wrapper. CI is read-only and verifies the committed wrapper rather than generating or committing missing wrapper files.

## Architecture map

`MainActivity.handleIncomingPdfIntent`
→ `PdfMergerViewModel.addDocumentsFromUris`
→ `FileUtil.copyUriToCache`
→ app-owned `cacheDir/pdf_sessions/<documentId>/source_*.pdf`
→ parser/page-count validation
→ lazy `PdfThumbnailHelper.renderPageThumbnail`
→ Compose document/page state
→ `PdfMergerViewModel.startMerge`
→ `PdfMergerEngine.mergePdfPages`
→ PDFBox `PDDocument.importPage` + inherited-resource materialization
→ optional `StandardProtectionPolicy`
→ app-private merged PDF
→ `PdfMergerEngine.verifyOutputPdf`
→ `PdfSaveManager`
→ MediaStore (API 29+) / SAF custom tree / SAF picker (API 24-28)
→ open/share through the saved URI or narrowly scoped FileProvider.

Automatic raster fallback is disabled because it is not semantically equivalent to vector PDF page import.

## Feature truth table

| Feature | UI | Core code | End-to-end | Tested | Status |
| --- | --- | --- | --- | --- | --- |
| Multi-PDF import | Yes | Yes | Yes | Partial | 🟡 |
| ACTION_VIEW | N/A | Yes | Yes | 🧪 device | 🟡 |
| ACTION_SEND | N/A | Yes | Yes | 🧪 device | 🟡 |
| ACTION_SEND_MULTIPLE | N/A | Yes | Yes | 🧪 device | 🟡 |
| Document reorder | Yes | ViewModel | Yes | 🧪 UI | 🟡 |
| Page reorder | Yes | ViewModel | Yes | exact-order regression | ✅ |
| Rotate 0/90/180/270 | Yes | PDFBox page rotation | Yes | generated box fixture | ✅ |
| Delete page | Yes | page model | Yes | 🧪 UI | 🟡 |
| Duplicate page | Yes | page model | Yes | 🧪 UI | 🟡 |
| Reverse order | Yes | page model | Yes | 🧪 UI | 🟡 |
| Thumbnail | Yes | PdfRenderer | lazy/on-demand | source + CI compile | 🟡 |
| Full preview | Yes | PdfRenderer | Yes | 🧪 memory/device | 🟡 |
| Encrypted input | Yes | unlock working copy | Yes | partial | 🟡 |
| Output user password | Yes | PDFBox | Yes | regression | ✅ |
| Owner password / owner-only | Yes | PDFBox | Yes | regression | ✅ |
| PDF permission flags | Yes | PDFBox | Yes | regression | ✅ reader enforcement external |
| URI links | N/A | PDFBox import | Yes | generated fixture | ✅ |
| Text-note annotation | N/A | PDFBox import | Yes | generated fixture | ✅ |
| Downloads save | Yes | MediaStore API 29+ | Yes | 🧪 device | 🟡 |
| Custom folder | Yes | SAF tree | Yes | 🧪 provider/device | 🟡 |
| Always ask / Save As | Yes | SAF | Yes | source verified; device pending | 🟡 |
| Open | Yes | ACTION_VIEW | Yes | 🧪 device | 🟡 |
| Share | Yes | FileProvider | Yes | 🧪 device | 🟡 |
| Cache cleanup | Yes | recursive scoped cleanup | Yes | regression | ✅ |
| Merge cancellation | Yes | coroutine/job | between pages | 🧪 latency/device | 🟡 |
| Dark mode | Yes | Compose theme | Yes | 🧪 visual/accessibility | 🟡 |

## KEEP AS-IS

- No broad storage permission.
- No `INTERNET` permission.
- SAF-based document selection.
- MediaStore for modern Downloads.
- PDFBox-first vector merge architecture.
- ViewModel + focused utilities; no Hilt/Room/domain-layer expansion required.
- Deterministic non-drag page movement plus exact-position dialog.
- Non-exported FileProvider.
- Compose light/dark/system theme modes.
- Production signing secrets remain external.

## Master findings

| ID | Priority | Area | Exact file | Function / location | Evidence | Impact | Fix | Verification | Status |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| PDF-001 | P0 | Build | repo root / wrapper | wrapper files | baseline wrapper incomplete | clean clone not reproducible | complete Gradle 9.3.1 wrapper committed | CI wrapper gate | FIXED |
| PDF-002 | P0 | Security | `PdfMergerEngine.kt` | output protection | baseline swallowed protection errors | protected request could become plaintext | protection errors fail export | security regression | FIXED |
| PDF-003 | P0 | Security truth | engine/ViewModel/dialog | success state | requested config was treated as verified output | false protected-success UI | reopen output; UI consumes verification result | unit suite | FIXED |
| PDF-004 | P1 | PDF input | `FileUtil.kt` | page count | baseline returned 1 on parse failure | corrupt input became fake one-page PDF | typed parser failure | malformed regression | FIXED |
| PDF-005 | P1 | Merge | `PdfMergerEngine.kt` | page loop | invalid page index silently skipped | requested page disappeared | consistency failure | regression | FIXED |
| PDF-006 | P1 | Output validity | engine | `verifyOutputPdf` | requested count previously trusted | silent output mismatch | reopen and verify exact count | exact-order/count test | FIXED |
| PDF-007 | P1 | Fidelity | engine | fallback | baseline auto-rasterized non-password failures | destroys text/vector/link/form semantics | automatic raster fallback disabled | source + text regression | FIXED |
| PDF-008 | P1 | Input errors | engine | `loadDocumentSafely` | broad errors became password-required | corruption misdiagnosed | distinct password-required/wrong/invalid errors | tests + source | FIXED |
| PDF-009 | P1 | Privacy | ViewModel/FileUtil | Remove/Clear | baseline state-only cleanup | private copies remained | UUID session dirs + recursive scoped deletion | cleanup regression | FIXED |
| PDF-010 | P1 | Password lifetime | model/ViewModel | unlock | password duplicated/retained | unnecessary secret lifetime | unlocked working copy; input password not retained | source audit | FIXED |
| PDF-011 | P1 | Temp identity | FileUtil/thumbnails | cache names | same display names collided | wrong document asset reuse | document UUID directories | source audit | FIXED |
| PDF-012 | P1 | Storage | `PdfSaveManager.kt` | custom tree | null stream could still report success | false save success | require stream/complete byte copy; delete failure artifact | device matrix remains | VERIFY_DEVICE |
| PDF-013 | P1 | Storage | `PdfSaveManager.kt` | MediaStore | partial/pending output risk | corrupt/zero-byte published item | transactional write/publish/delete | device matrix remains | VERIFY_DEVICE |
| PDF-014 | P1 | Legacy storage | save manager | API 24-28 | direct public Downloads incompatible with permission model | unreliable export | use SAF picker; no broad permission | device matrix remains | VERIFY_DEVICE |
| PDF-015 | P1 | FileProvider | `file_paths.xml` | paths | baseline exposed all cache/files | overly broad share eligibility | only merged-output directory exposed | instrumented/device check | VERIFY_DEVICE |
| PDF-016 | P1 | Backup/privacy | backup XML | backup rules | baseline templates could include private generated files | privacy-copy mismatch | merged outputs excluded; cache not Auto Backup content | adb/device validation | VERIFY_DEVICE |
| PDF-017 | P2 | Import performance | ViewModel/PageThumbnailCard | thumbnails | all thumbnails generated before import completed | poor large-doc first use | lazy visible-page thumbnail jobs | code + CI; benchmark pending | FIXED |
| PDF-018 | P2 | Memory | `PdfThumbnailHelper.kt` | LruCache | fixed ~30 MB cache | low-memory pressure | max-heap fraction clamped 4–24 MB | device heap benchmark | VERIFY_DEVICE |
| PDF-019 | P2 | Responsive reorder | grid/card | reorder gesture | fixed-threshold drag did not use adaptive-grid geometry | wrong tablet/landscape target | unreliable full-card drag removed; arrows/swipe/exact-position retained | source + CI | FIXED |
| PDF-020 | P1 | Encryption identity | engine/CI | encryption dictionary | 128-bit key alone did not prove AES | misleading AES claim | prefer AES + verify dictionary independently | qpdf: Standard, V4, R4, 128-bit, AESV2 | FIXED |
| PDF-021 | P2 | Advanced fidelity | PDFBox import | forms/outlines/tags/signatures | document-level structures not rebuilt | preservation can be incomplete | link/text-note/rotation fixtures added; unsupported claims withheld | forms/outlines/tags/signatures remain | DEFERRED |
| PDF-022 | P2 | Large documents | import/merge | 500–1,000 pages | no representative device benchmark | unknown latency/heap/temp footprint | lazy thumbnails reduce front-loaded work | physical device benchmark | VERIFY_DEVICE |
| PDF-023 | P2 | Cancellation | ViewModel/engine/save manager | merge | baseline lacked explicit cancellation | duplicate work/partial output | merge job, UI Cancel, page-boundary checks, partial cleanup | device latency test | VERIFY_DEVICE |
| PDF-024 | P1 | Dependencies/privacy | Gradle/metadata | dependency surface | unused Firebase/AI/network/Room stack | privacy ambiguity/supply-chain surface | removed unused stack and Gemini metadata | source + CI | FIXED |
| PDF-025 | P2 | Release | `app/build.gradle.kts` | release | R8 off; signing external | signed release not qualified | fail clearly without production signing; release lint automated | signed release/R8 still pending | VERIFY_DEVICE |
| PDF-026 | P1 | App identity | Gradle | application ID | `com.aistudio.pdfmerger.vqznrk`; namespace `com.example` | changing can break Play update path | deliberately unchanged | Play Console history required | VERIFY_PLAY_CONSOLE |
| PDF-027 | P1 | Save As | MainScreen/save manager | CreateDocument result | manual picker could toast success on null stream | false user success | centralized transactional URI write | provider/device test | VERIFY_DEVICE |
| PDF-028 | P1 | PDF resources | engine | `importPage` | PDFBox does not automatically copy inherited page-tree resources | fonts/images/operators may be unresolved | materialize inherited resources on imported page | source contract + CI | FIXED |
| PDF-029 | P1 | Privacy copy | Settings/About | privacy text | absolute “100%”/“never leave device” wording exceeded data flow | misleading privacy claim | copy now distinguishes local processing from explicit save/share | source audit | FIXED |
| PDF-030 | P1 | Security UI | success dialog | owner-only protection | encrypted permissions could be described as open-password protection | misleading status | distinguish open password from owner-only encrypted permissions | owner-only regression | FIXED |

## PDF fidelity truth

### Verified automatically

- Exact requested page order and final page count.
- Searchable text survives the normal PDFBox vector path.
- External URI link annotation survives generated-fixture merge.
- Text-note annotation survives generated-fixture merge.
- Rotations 0/90/180/270 preserve expected rotation plus MediaBox/CropBox.
- Inherited page resources are explicitly materialized after `importPage` when the source page relies on its page tree.

### Deliberately unclaimed

- AcroForm document-level structure.
- outlines/bookmarks.
- tagged-PDF structure.
- all annotation subtypes.
- transparency/pathological edge cases.
- source digital-signature validity in the new merged document.

Merging creates a new PDF; source signatures must not be described as valid signatures of that new file.

## Security truth

Protected output is fail-closed. The engine reopens the saved file and verifies the requested security state.

The generated protected fixture is also inspected independently in CI with qpdf. The gate asserts:

- `/Filter /Standard`
- `/V 4`
- `/R 4`
- `/Length 128`
- `/CF /StdCF /CFM /AESV2`

Therefore the generated protected-output profile tested by CI is AES-128. Permission flags are still advisory and require reader enforcement.

Owner-only mode is distinct from user/open-password mode. Owner-only output can be encrypted and permission-restricted while remaining openable without a user password; the success UI now states that accurately.

## Storage truth

- API 29+: MediaStore Downloads with pending row until write succeeds and publish completes.
- API 24-28: SAF picker; no broad storage permission and no silent direct-public-storage fallback.
- Custom folder: persisted tree permission is used; revoked/offline/deleted provider returns failure rather than silently redirecting.
- Manual Save As uses the same transactional non-null-stream and byte-count contract.
- Failed MediaStore/SAF-created outputs are deleted when possible.
- Repeated app-private output names are uniquified rather than unexpectedly overwritten.

Real-provider/device failure modes remain a release gate.

## Privacy lifecycle

- Imported sources: app cache / document UUID.
- Unlocked copies: document-scoped working directory.
- Thumbnails: document UUID directory, generated lazily.
- Input PDF passwords: transient for unlocking; not retained in model/ViewModel after unlocked copy.
- Output passwords: in-memory security config; cleared on session reset.
- Remove Document: removes its app-owned working copies/thumbnails.
- Clear All/Clear Cache: recursively clear owned working files and app-private merged outputs.
- Exported PDFs are not deleted by session cleanup.
- Backup: merged private outputs excluded; cache is outside Auto Backup.
- FileProvider: merged-output directory only.
- Abrupt process death does not persist passwords; abandoned cache after an ungraceful death remains a lifecycle/device consideration until cleanup/system cache eviction.

## Performance and memory

Lazy thumbnail generation removes the baseline O(page-count) raster work from the import critical path. Bitmap memory cache is bounded to 1/16 of max heap, clamped to 4–24 MB.

No production performance numbers are claimed. Required benchmark matrix remains 10/100/500/1,000 pages plus a large scanned PDF, with import time, first usable UI, thumbnail work, merge time, heap/GC, temp storage, and output size measured on named devices/emulators.

## Dependency audit

Removed as unused:

- Firebase BOM / Firebase AI / App Check
- Google Services plugin
- secrets-gradle-plugin
- Retrofit / OkHttp / logging interceptor / Moshi
- Room / KSP
- stale Gemini `.env.example` and AI Studio `metadata.json`

Retained dependency surface is centered on AndroidX, Compose/Material, lifecycle, coroutines, PDFBox Android, and test libraries.

## Production scorecard

Scores reflect current evidence, not feature names.

| Area | Score / 10 | Evidence / gap |
| --- | ---: | --- |
| Build reproducibility | 10 | complete committed wrapper; read-only CI verification |
| PDF import | 7 | header/parser validation; URI/device matrix pending |
| PDF validity | 9 | parser validation + final reopen/count |
| Merge correctness | 9 | exact order/count + no silent skips |
| Fidelity | 8 | text/link/note/boxes/resources covered; document-level structures pending |
| Rotations | 9 | 0/90/180/270 generated-box regression |
| Encrypted input | 7 | typed flow; broader third-party fixtures/device readers pending |
| Output protection | 10 | fail-closed, reopen verification, independent qpdf profile proof |
| Save/storage | 8 | transactional code; provider/device matrix pending |
| Privacy | 9 | offline dependency surface + scoped lifecycle/backup; abrupt-death cache caveat |
| Cache lifecycle | 9 | recursive scoped cleanup + UUID identity |
| Memory | 8 | dynamic bounded cache; heap benchmark pending |
| Large-document performance | 6 | lazy thumbnails implemented; no representative benchmark |
| UI/UX | 8 | merge/save semantics clarified; responsive device checks pending |
| Theme | 7 | code improved; visual matrix pending |
| Accessibility | 7 | deterministic non-drag reorder paths; TalkBack/font scale pending |
| Dependencies | 10 | stale AI/network/database stack removed |
| Tests | 9 | meaningful PDF/security/fidelity/privacy fixtures |
| CI | 10 | wrapper, unit, qpdf, debug/release lint, debug assemble |
| Release | 6 | release lint passes; signed artifacts/R8 not qualified |
| Play readiness | 6 | device/performance/accessibility/identity/signed-release gates remain |

## CI evidence

- Baseline `main@d41e52e...`: no workflow/checks and incomplete wrapper.
- Early hardening CI exposed real API-24/lint issues and one external dependency-download 504; the source issues were fixed rather than suppressed.
- Complete Gradle 9.3.1 wrapper is committed.
- CI now verifies the committed wrapper read-only.
- `c299fb3...` code head: wrapper verification ✅, `testDebugUnitTest` ✅, qpdf encryption profile ✅, `lintDebug` ✅, `lintRelease` ✅, `assembleDebug` ✅.
- Superseded branch/PR runs are cancelled through workflow concurrency so stale runs do not consume verification capacity.
- Documentation-only final head must still be green before merge.

## Remaining release gates

1. Android 24 / 28 / 29 / 33+ / current-device storage matrix.
2. Revoked tree permission, deleted folder, offline provider, removable storage, zero-space, null stream, and write-exception device/provider tests.
3. 10 / 100 / 500 / 1,000-page and large-scan performance/heap/temp-storage benchmarks.
4. Rapid high-resolution preview heap/GC/OOM test.
5. AcroForm, outline/bookmark, tagged-PDF, signature, and broader annotation fixture policy.
6. Major PDF reader checks for advisory permission behavior.
7. TalkBack, font scaling 1.3x/1.5x/2.0x, contrast, landscape, small phone, tablet.
8. Play Console verification before any application-ID change.
9. Real production signing with `lintRelease`, `assembleRelease`, and `bundleRelease`; then evaluate R8/minification with PDFBox/crypto regressions.
10. Decide whether abrupt-process-death stale working-cache pruning needs an explicit next-launch policy.

## Release decision

Do **not** merge to `main` or label the app Play-ready yet. P0 build/security correctness and most P1 code defects are resolved, and AES-128 identity is independently proven for the generated protected fixture. Remaining blockers are external/device/performance/accessibility/Play-identity/signed-release qualification rather than known plaintext-export or corrupt-merge P0 defects.
