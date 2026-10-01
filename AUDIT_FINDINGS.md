# Production Hardening Audit

Baseline source truth: `main@d41e52ef687d185cbc6e9cdd3778c8596a696786` (`docs: add project README`).

Hardening branch: `astra/pdf-merger-production-hardening`.

Final verified code head: `7f2b73e5d8655b55b770d40b79da34b0d113cd94`.

That code head passed committed-wrapper verification, explicit `clean`, `testDebugUnitTest`, independent qpdf encryption inspection, `lintDebug`, `lintRelease`, `assembleDebug`, unsigned `assembleRelease`, and unsigned `bundleRelease`.

This ledger distinguishes code/CI proof from physical-device, provider, Play Console, third-party-reader, and production-signing qualification.

## Baseline build truth

Baseline `main` had an incomplete Gradle wrapper: `gradlew`, `gradlew.bat`, and `gradle-wrapper.jar` were absent. It also had no GitHub Actions workflow or commit checks.

The hardening branch contains the complete Gradle 9.3.1 wrapper. CI is read-only, runs an explicit clean, and fails if the wrapper is incomplete.

## Architecture map

`MainActivity.handleIncomingPdfIntent`
→ `PdfMergerViewModel.addDocumentsFromUris`
→ `FileUtil.copyUriToCache`
→ app-owned `cacheDir/pdf_sessions/<documentId>/source_*.pdf`
→ typed parser/page-count validation
→ lazy `PdfThumbnailHelper.renderPageThumbnail`
→ Compose document/page state
→ `PdfMergerViewModel.startMerge`
→ `PdfMergerEngine.mergePdfPages`
→ PDFBox mixed-memory load + source fidelity guard
→ PDFBox `PDDocument.importPage` + inherited-resource materialization
→ optional `StandardProtectionPolicy`
→ app-private merged PDF
→ `PdfMergerEngine.verifyOutputPdf`
→ `PdfSaveManager`
→ MediaStore (API 29+) / persisted SAF custom tree / SAF picker (API 24-28)
→ open/share through saved URI or narrowly scoped FileProvider.

Automatic raster fallback is disabled because it is not semantically equivalent to vector PDF page import.

## Feature truth table

| Feature | Core behavior | Automated evidence | Status |
| --- | --- | --- | --- |
| Multi-PDF import | URI working copies + typed parsing | parser/order tests; device URI matrix pending | 🟡 |
| ACTION_VIEW / SEND / SEND_MULTIPLE | wired in MainActivity | physical intent matrix pending | 🟡 |
| Page reorder | deterministic model order | exact-order regression | ✅ |
| Rotate 0/90/180/270 | composed page rotation | generated MediaBox/CropBox fixture | ✅ |
| Delete / duplicate / reverse | model transforms | UI/device coverage pending | 🟡 |
| Thumbnail | lazy PdfRenderer work | source/compile; device memory pending | 🟡 |
| Full preview | bounded bitmap cache | device heap/visual pending | 🟡 |
| Encrypted input | typed password flow + unlocked private copy | generated password-required/wrong/correct fixture | ✅ |
| Output user/owner passwords | fail-closed PDFBox protection | reopen regression | ✅ |
| AES-128 identity | Standard V4/R4/AESV2 | independent qpdf gate | ✅ |
| PDF permission flags | verified PDF flags | regression; reader enforcement external | ✅ / external enforcement |
| URI links | vector import | generated fixture | ✅ |
| Text-note annotation | vector import | generated fixture | ✅ |
| Interactive AcroForm/XFA | explicitly rejected | generated form fail-safe fixture | ✅ fail-safe |
| Inherited resources | materialized after import | source contract + full suite | ✅ |
| Downloads save | transactional MediaStore API 29+ | physical provider matrix pending | 🟡 |
| Custom folder | durable SAF grant required | code/CI; provider matrix pending | 🟡 |
| Always ask / Save As | user URI write with byte verification | code/CI; provider failure matrix pending | 🟡 |
| FileProvider scope | merged-output path only | allow/deny Robolectric regression | ✅ |
| Cache/session cleanup | cancel → join → delete | cleanup regressions + source | ✅ |
| Merge cancellation | explicit job/UI cancellation between pages | code/CI; cancellation latency device test pending | 🟡 |
| Dark/light/system theme | Compose theme | visual/accessibility matrix pending | 🟡 |
| Release compile/package | release lint + unsigned APK/AAB | CI | ✅ unsigned packaging |

## KEEP AS-IS

- No broad storage permission.
- No `INTERNET` permission.
- SAF-based document selection and custom folders.
- MediaStore for modern Downloads.
- PDFBox-first vector merge architecture.
- ViewModel + focused utilities; no Hilt/Room/domain-layer expansion required.
- Deterministic non-drag page movement plus exact-position dialog.
- Non-exported, narrowly scoped FileProvider.
- Compose light/dark/system theme modes.
- Production signing secrets remain external.
- Application ID remains unchanged until Play Console history is known.

## Master findings

| ID | Priority | Area | Evidence / baseline risk | Resolution | Verification | Status |
| --- | --- | --- | --- | --- | --- | --- |
| PDF-001 | P0 | Build | incomplete wrapper; no CI | committed Gradle 9.3.1 wrapper + read-only CI | final CI | FIXED |
| PDF-002 | P0 | Output security | protection errors could be swallowed | fail protected export | security regression | FIXED |
| PDF-003 | P0 | Security truth | requested config could masquerade as verified output | UI consumes post-save verification | unit suite | FIXED |
| PDF-004 | P1 | PDF input | parse failure became fake one-page PDF | typed invalid-document failure | malformed fixture | FIXED |
| PDF-005 | P1 | Merge | invalid requested page could silently disappear | fail consistency check | regression | FIXED |
| PDF-006 | P1 | Output validity | output page count trusted | reopen + exact count | order/count regression | FIXED |
| PDF-007 | P1 | Fidelity | silent raster fallback destroyed semantics | automatic raster fallback disabled | text/link/annotation tests | FIXED |
| PDF-008 | P1 | Encrypted input | password/corruption errors conflated | required/wrong/invalid types + unlocked working copy | generated encrypted-input fixture | FIXED |
| PDF-009 | P1 | Privacy | Clear All previously state-only | recursive scoped file cleanup | cleanup regression | FIXED |
| PDF-010 | P1 | Password lifetime | input password retained unnecessarily | transient unlock; no retained input password | source audit | FIXED |
| PDF-011 | P1 | Temp identity | filename-based caches collided | document UUID directories | source audit | FIXED |
| PDF-012 | P1 | Custom SAF save | null/partial write could report success | require persistent grant, stream, byte count; delete app-created failed doc | device/provider failure matrix remains | VERIFY_DEVICE |
| PDF-013 | P1 | MediaStore | pending/partial output risk | transactional write, publish, delete-on-failure | device/storage matrix remains | VERIFY_DEVICE |
| PDF-014 | P1 | API 24-28 storage | direct public Downloads inconsistent with storage model | system picker; no broad permission | device matrix remains | VERIFY_DEVICE |
| PDF-015 | P1 | FileProvider | provider path too broad | merged-output directory only | automated allow/deny regression | FIXED |
| PDF-016 | P1 | Backup/privacy | generated PDFs and permission-bound destination metadata could restore | exclude merged outputs and save-destination prefs; cache not Auto Backup | XML/source; physical backup restore optional | FIXED |
| PDF-017 | P2 | Import performance | eager thumbnail rasterization | visible-page lazy thumbnails | code/CI; benchmark external | FIXED |
| PDF-018 | P2 | Memory | fixed bitmap cache + unrestricted PDFBox stream buffering | bitmap cache 4–24 MB + PDFBox mixed-memory 16 MB main-memory budget/scratch | code/CI; heap benchmark external | FIXED / VERIFY_DEVICE |
| PDF-019 | P2 | Responsive reorder | fixed drag math contradicted adaptive grid | remove unreliable full-card drag; retain deterministic controls | source/CI | FIXED |
| PDF-020 | P1 | Encryption identity | 128-bit key length alone did not prove AES | AES-preferred policy + independent dictionary gate | qpdf Standard/V4/R4/128/AESV2 | FIXED |
| PDF-021 | P1 | Interactive forms/signatures | page import cannot safely rebuild document-level AcroForm/XFA/signature structure | fail explicitly instead of silently detaching fields | generated interactive-form regression | FIXED fail-safe |
| PDF-022 | P2 | Advanced document structure | outlines/bookmarks/tag tree need page-reference remapping | claims withheld; no silent raster substitute | dedicated design/fixtures required | DEFERRED |
| PDF-023 | P2 | Large documents | no representative 500–1,000 page benchmark | lazy thumbnails + mixed-memory PDFBox reduce structural pressure | physical benchmark still required | VERIFY_DEVICE |
| PDF-024 | P2 | Cancellation/lifecycle | reset could race merge/import/unlock/cache deletion | track jobs; cancel; await termination; serialize next session behind cleanup | source + full CI | FIXED / latency VERIFY_DEVICE |
| PDF-025 | P1 | Dependencies/privacy | unused Firebase/AI/network/Room stack | remove unused stack and Gemini metadata | source/dependency/CI | FIXED |
| PDF-026 | P2 | Release | signing external; release package not previously checked | release lint + unsigned APK/AAB package gates; normal signed tasks still require real secrets | final CI | FIXED unsigned / SIGNING_EXTERNAL |
| PDF-027 | P1 | App identity | package may have Play history | deliberately unchanged | Play Console required | VERIFY_PLAY_CONSOLE |
| PDF-028 | P1 | Save As | null stream false success; deleting arbitrary URI could destroy pre-existing file | stream/byte verification; never delete arbitrary user-selected destination on failure | source/CI; provider matrix remains | FIXED / VERIFY_DEVICE |
| PDF-029 | P1 | PDF resources | `importPage` omits inherited page-tree resources | materialize inherited resources | source contract + suite | FIXED |
| PDF-030 | P1 | Privacy copy | absolute “100%/never leaves device” language exceeded data flow | copy describes local processing and explicit save/share boundary | source audit | FIXED |
| PDF-031 | P1 | Security UI | owner-only restrictions could be described as open-password protection | distinct verified security messages | owner-only regression | FIXED |
| PDF-032 | P1 | Stale private data | process death could orphan cache/private output/sample files | next ViewModel startup prunes app-owned stale working/output/sample data | source + cleanup tests | FIXED |
| PDF-033 | P1 | Session races | Clear All could race active merge/import/unlock/thumbnail work | cancel all session mutations, await stop, then delete; new imports wait for cleanup | source + full CI | FIXED |
| PDF-034 | P1 | SAF grants | custom destination stored even without durable permission; old grants leaked | store only after persistent permission succeeds; release replaced/reset grants | source/CI | FIXED / provider VERIFY_DEVICE |
| PDF-035 | P1 | Path traversal | custom output names could be hostile | sanitize + enforce private output root | traversal regression | FIXED |
| PDF-036 | P1 | Save destination truth | pre-Q UI could claim direct Downloads; help text described removed drag behavior | API/provider-aware destination and updated help | source/CI | FIXED |
| PDF-037 | P1 | Signing hygiene | production keystore extensions not ignored | ignore .jks/.keystore/.p12/.pfx; credentials external | repo audit | FIXED |

## PDF fidelity truth

### Verified automatically

- Exact requested page order and final page count.
- Searchable text survives the normal vector path.
- External URI link and text-note annotation fixtures survive merge.
- Rotations 0/90/180/270 preserve expected rotation, MediaBox, and CropBox.
- Inherited page resources are materialized when required.
- Encrypted input rejects missing/wrong passwords, unlocks with the correct password, and remains searchable after merge.
- Interactive AcroForm/XFA input fails explicitly instead of being silently degraded.

### Deliberately unclaimed

- outline/bookmark remapping across arbitrary page selection/reorder
- tagged-PDF document structure
- all annotation subtypes and pathological transparency/resource cases
- source digital-signature validity in the newly generated merged file

Merging creates a new PDF; source signatures are not signatures of the new output.

## Security truth

Protected output is fail-closed. The engine reopens the saved file and verifies the requested security state.

The generated protected fixture is independently inspected in CI with qpdf. The gate asserts:

- `/Filter /Standard`
- `/V 4`
- `/R 4`
- `/Length 128`
- `/CF /StdCF /CFM /AESV2`

Therefore the tested protected-output profile is AES-128. Permission flags remain advisory and require reader enforcement.

Owner-only mode is distinct from user/open-password mode. Owner-only output can be encrypted and permission-restricted while remaining openable without a user password; the UI states that accurately.

## Storage truth

- API 29+: MediaStore Downloads with a pending row until the complete write is published.
- API 24-28: system document picker; no broad storage permission and no silent direct-public-storage fallback.
- Custom folder: preferences are saved only after persistent SAF permission succeeds.
- Replacing/resetting a custom folder releases the old persisted grant when possible.
- Revoked/offline/deleted providers return failure rather than silently redirecting.
- App-created MediaStore/custom-tree outputs are removed after failed writes when possible.
- Manual Save As verifies non-null output stream and copied byte count. It deliberately does not delete an arbitrary destination URI on failure because the file may have pre-existed.
- Repeated app-private output names are uniquified.

Provider/device fault injection remains external qualification.

## Privacy and lifecycle

- Imported sources: document-scoped app cache.
- Unlocked copies: document-scoped app cache; input passwords are not retained.
- Sample PDFs: owned cache and included in cache accounting/cleanup.
- Thumbnails: document-scoped, generated lazily.
- Output passwords: in-memory security config; cleared on session reset.
- Private merged output: app files directory; cleared on session reset and next fresh ViewModel startup.
- Session reset snapshots active merge/import/sample/unlock/thumbnail jobs, cancels them, waits for termination, then deletes app-owned data.
- New import/sample/unlock work waits for startup/reset cleanup to complete.
- Exported PDFs are never deleted by app session cleanup.
- Backup/device transfer excludes private merged PDFs and permission-bound save-destination preferences.
- FileProvider exposes merged output only; unrelated private files are rejected by automated test.
- No app network permission or cloud/analytics dependency exists in the audited build.

## Performance and memory

Structural hardening completed:

- thumbnails are generated on demand rather than for every page during import
- thumbnail/preview bitmap memory cache is bounded to 1/16 of max heap, clamped to 4–24 MB
- PDFBox file loads use mixed-memory scratch buffering with a 16 MB main-memory budget
- duplicate merges are blocked and cooperative cancellation is exposed between pages

No production performance numbers are claimed. The remaining benchmark matrix is 10/100/500/1,000 pages plus a large scanned PDF, measuring first usable UI, thumbnail work, merge time, heap/GC, scratch/temp storage, cancellation latency, and output size on named devices.

## Dependency and signing audit

Removed as unused:

- Firebase BOM / Firebase AI / App Check
- Google Services plugin
- secrets-gradle-plugin
- Retrofit / OkHttp / logging interceptor / Moshi
- Room / KSP
- stale Gemini `.env.example` and AI Studio `metadata.json`

Production keystore extensions are ignored by Git. Normal release tasks fail without `KEYSTORE_PATH`, `STORE_PASSWORD`, and `KEY_PASSWORD`. CI separately builds unsigned release APK/AAB with the signing guard skipped only to verify release compilation and packaging.

## Production scorecard

Scores reflect current evidence, not feature names.

| Area | Score / 10 | Evidence / gap |
| --- | ---: | --- |
| Build reproducibility | 10 | complete wrapper + explicit clean CI |
| PDF import | 8 | typed validation/encrypted fixture; physical URI matrix pending |
| PDF validity | 9 | parser validation + final reopen/count |
| Merge correctness | 9 | exact order/count + no silent skips |
| Fidelity | 8 | text/link/note/boxes/resources; unsupported forms fail safe; doc-level structures pending |
| Rotations | 9 | 0/90/180/270 box regression |
| Encrypted input | 9 | password-required/wrong/correct unlock regression |
| Output protection | 10 | fail-closed + reopen + independent qpdf AES profile |
| Save/storage | 8 | safer transactional semantics; real providers pending |
| Privacy | 10 | offline surface + startup/reset cleanup + backup/grant tightening |
| Cache lifecycle | 10 | scoped, accounted, cancel/join/delete ordering |
| Memory | 8 | bounded bitmaps + mixed PDFBox buffering; device heap benchmark pending |
| Large-document performance | 7 | structural work done; representative benchmark pending |
| UI/UX | 8 | truthful merge/save/security/help semantics; device checks pending |
| Theme | 7 | code present; visual matrix pending |
| Accessibility | 7 | deterministic non-drag reorder; TalkBack/font scale pending |
| Dependencies | 10 | stale AI/network/database stack removed |
| Tests | 10 | generated correctness/security/fidelity/privacy fixtures |
| CI | 10 | wrapper + clean + tests + qpdf + debug/release lint + debug/release packaging |
| Release packaging | 9 | unsigned APK/AAB verified; production signing/R8/install-upgrade pending |
| Play readiness | 7 | device/performance/accessibility/identity/signing evidence remains |

## CI evidence

- Baseline `main@d41e52e...`: no workflow/checks and incomplete wrapper.
- Early hardening CI exposed real API-24/lint issues and one external dependency-download 504; source issues were fixed rather than suppressed.
- Complete Gradle 9.3.1 wrapper is committed and verified read-only.
- Workflow concurrency cancels superseded branch/PR runs.
- `7f2b73e5d8655b55b770d40b79da34b0d113cd94`: wrapper ✅, clean ✅, unit tests ✅, qpdf AES profile ✅, `lintDebug` ✅, `lintRelease` ✅, `assembleDebug` ✅, unsigned `assembleRelease` ✅, unsigned `bundleRelease` ✅.

## Remaining external release qualification

1. Android 24 / 28 / 29 / 33+ / current-device import/save/open/share matrix.
2. Real document-provider fault matrix: revoked grant, deleted folder, offline/cloud provider, removable storage, low/zero space, null/short stream, write exception, overwrite behavior.
3. 10 / 100 / 500 / 1,000-page and large-scan performance/heap/GC/scratch-storage/cancellation benchmarks on representative devices.
4. TalkBack, 1.3x/1.5x/2.0x font scale, contrast, small-phone, landscape, and tablet checks.
5. Outline/bookmark, tagged-PDF, and broader annotation/document-structure policy plus third-party-reader permission checks.
6. Play Console verification before any application-ID change.
7. Real production keystore: signed `assembleRelease` / `bundleRelease`, install/upgrade test, and R8/minification qualification.

## Release decision

The audited code-side P0/P1 hardening is complete and the final code head passes the full automated gate. Keep the PR draft and do **not** merge to `main` or label the app Play-ready until the external qualification items above are completed or explicitly accepted as release risk.
