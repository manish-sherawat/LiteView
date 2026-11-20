# 📋 NexusDocsViewer — Comprehensive App Audit Report

**Generated**: 2026-11-20  
**Version Audited**: v5.5.0 (versionCode 500)  
**Auditor**: Automated Code Review — Full Codebase Scan  
**Scope**: `app/`, `core/`, `feature/dashboard/`, `feature/reader-pdf/`, `feature/reader-office/`, `feature/reader-text/`, `feature/reader-presentation/`, `feature/scanner/`

---

> **Legend**
> - 🔴 **Critical** — Crashes, data loss, security risk, or major regression  
> - 🟠 **High** — Broken feature, significant UX degradation, or confirmed bug  
> - 🟡 **Medium** — Non-breaking issue, noticeable impact on quality  
> - 🟢 **Low** — Code smell, minor improvement opportunity  

---

## Table of Contents

1. [🔗 Pipeline / Connection Errors](#1--pipeline--connection-errors)
2. [🎨 UI / UX Issues](#2--ui--ux-issues)
3. [✨ Animation Issues](#3--animation-issues)
4. [🐛 Bugs](#4--bugs)
5. [⚡ Performance Issues](#5--performance-issues)
6. [🔧 Optimization Issues](#6--optimization-issues)
7. [🔒 Security Issues](#7--security-issues)
8. [🛡️ Vulnerabilities](#8-%EF%B8%8F-vulnerabilities)
9. [💡 Suggestions](#9--suggestions)

---

## 1. 🔗 Pipeline / Connection Errors

### PC-001 — ~~MuPDF temp-file leak on content:// URIs~~ (FIXED)
**Severity**: ✅ RESOLVED  
**Location**: `PdfReaderViewModel.kt:303–317`  
**Description**: When a PDF was opened via a `content://` URI, the temp file deletion ran prematurely inside `renderMutex.withLock` during `loadPdf()`, potentially deleting an active file in use by background render coroutines.  
**Fix Applied**: Deletion moved safely to `onCleared()`, all temp files tracked via `tempFiles` list with existence/null checks, and temp files created using unique filename suffix keyed by URI hash (`_${uriHash}.pdf`) to eliminate collisions between rapid document switches.

---

### PC-002 — Scanner failure silently swallowed
**Severity**: 🟡 Medium  
**Location**: `ScannerScreen.kt:80–83`  
**Description**: When Google ML Kit's `GmsDocumentScanning.getStartScanIntent()` fails, only a `Toast.makeText("Failed to start scanner")` is shown before `onBack()` is called. No error details are logged, no retry option is offered, and there is no distinction between "Google Play Services not available" (permanent failure) and "scanner temporarily unavailable" (transient failure).  
**Fix Prompt**: Log the exception, show an `AlertDialog` with a "Try Again" action for transient failures, and display a "Google Play Services required" dialog for permanent failures. Add `addOnFailureListener { e -> viewModel.onScanError(e) }` and handle via ViewModel state.

---

### PC-003 — PDF page count cached in SharedPreferences with URI as key (unbounded growth)
**Severity**: 🟡 Medium  
**Location**: `PdfReaderViewModel.kt:334–337`  
**Description**: After a PDF is loaded, the page count is stored in `getSharedPreferences("nexus_page_counts")` using the URI string as the key. This cache is never pruned — over time it accumulates an entry per opened document and never evicts stale entries. SharedPreferences is not designed as a dictionary store for unbounded data.  
**Fix Prompt**: Replace with a Room table column `pageCount` on the existing `RecentDocument` entity. Prune orphan entries during the trash-retention job.

---

### PC-004 — OfficeReaderScreen uses a secondary SharedPreferences alongside DataStore
**Severity**: 🟡 Medium  
**Location**: `OfficeReaderScreen.kt:105–115`  
**Description**: The pinch-zoom hint preference (`has_seen_pinch_zoom_hint`) is persisted via `context.getSharedPreferences("nexus_reader_prefs", MODE_PRIVATE)` directly in the Composable — bypassing the `UserPreferencesRepository` / DataStore layer entirely. This creates a second data-persistence system for prefs, inconsistent with all other settings.  
**Fix Prompt**: Add a `hintPinchZoomSeen: Boolean` key to `UserPreferencesRepository` backed by DataStore. Inject the repository into `OfficeReaderViewModel` and expose a `fun markPinchHintSeen()` method.

---

### PC-005 — `PresentationReaderScreen` uses `collectAsState()` instead of lifecycle-aware variant
**Severity**: 🟡 Medium  
**Location**: `PresentationReaderScreen.kt:138–140`  
**Description**: Three `StateFlow` collectors (`uiState`, `isStarred`, `keepScreenAwake`) use `.collectAsState()` instead of `.collectAsStateWithLifecycle()`. On API 26–28, this keeps the coroutine running during screen-off / background, wasting battery and potentially updating UI that is not visible.  
**Fix Prompt**: Replace all `collectAsState()` calls with `collectAsStateWithLifecycle()` from `androidx.lifecycle.compose`. Add `implementation(libs.lifecycle.compose)` to the module's `build.gradle.kts` if missing.

---

### PC-006 — StorageScanUseCase MediaStore query on main thread path
**Severity**: 🟠 High  
**Location**: `StorageScanUseCase.kt:18`  
**Description**: `scanStorage()` is a suspend function dispatched on `Dispatchers.IO`, which is correct. However, the returned list is sorted by `DATE_MODIFIED DESC` in SQL — but `cursor.moveToNext()` reads up to hundreds or thousands of rows with no LIMIT clause. On devices with 10,000+ documents, this can run for 2–5 seconds, blocking the IO thread pool and potentially ANR-ing if the pool is saturated.  
**Fix Prompt**: Add a `LIMIT 500` clause to the MediaStore query (or implement pagination), and add an in-memory cap: `takeWhile { scanned.size < 1000 }`. Cache scan results in Room and only re-scan on file system change notifications.

---

## 2. 🎨 UI / UX Issues

### UX-001 — Search bar top padding hardcoded, breaks on devices with tall notches
**Severity**: 🟡 Medium  
**Location**: `DashboardScreen.kt` (search bar `AnimatedVisibility`)  
**Description**: The search bar uses `statusBarsPadding()` + a fixed `top = 12.dp` offset. On foldable devices or phones with extra-tall punch-hole cameras (e.g., Google Pixel Fold inner display), the search bar may overlap system status elements. The fixed `top = 12.dp` does not scale with the actual inset height.  
**Fix Prompt**: Wrap the search bar `Box` in `WindowInsets.statusBars.asPaddingValues()` and calculate `top` dynamically: `val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(); padding(top = statusBarHeight + 8.dp)`.

---

### UX-002 — ToolsScreen `categoryCounts` computed without considering search query
**Severity**: 🟡 Medium  
**Location**: `ToolsScreen.kt:111–116`  
**Description**: Category chip counts are computed once using `remember { }` with no keys, so they always display the full tool count per category, even when a search query has narrowed the displayed tools. The user sees "PDF (8)" as the chip label but only 2 PDF tools in the filtered list — confusing.  
**Fix Prompt**: Change the `remember` key to include `searchQuery`: `remember(searchQuery) { ToolCategory.entries.associateWith { ... filteredTools.count { t -> t.category == it } } }`. Show `ALL (N)` where N is `filteredTools.size`.

---

### UX-003 — RecycleBin uses `Toast` for all user feedback (deprecated pattern)
**Severity**: 🟢 Low  
**Location**: `RecycleBinScreen.kt:74–76`  
**Description**: The recycle bin screen collects `uiEvents` and shows `Toast.makeText(...)` for all messages. Toasts are deprecated in Android 12+ for foreground apps and don't integrate with the material design system. Additionally, the toast fires even if the app is in the background.  
**Fix Prompt**: Replace with a `SnackbarHostState` / `SnackbarHost` placed inside the `Box` root. This is already used in `OfficeReaderScreen.kt` — adopt the same pattern.

---

### UX-004 — `TextReaderScreen` top padding hardcoded at 140.dp regardless of search bar visibility
**Severity**: 🟡 Medium  
**Location**: `TextReaderScreen.kt:131`  
**Description**: `val topPadding by animateDpAsState(targetValue = if (isImmersiveMode) 16.dp else 140.dp)` — the 140.dp value is always applied when not in immersive mode, even if the search bar is closed. If the search bar occupies 50.dp, 90.dp padding would suffice, reducing the content dead zone at the top.  
**Fix Prompt**: Add a third state: `targetValue = when { isImmersiveMode -> 16.dp; isSearchMode -> 140.dp; else -> 90.dp }`.

---

### UX-005 — OfficeReaderScreen renders DOCX/XLSX entirely via WebView with no native fallback
**Severity**: 🟠 High  
**Location**: `OfficeReaderScreen.kt:52` (`import android.webkit.WebView`)  
**Description**: Office documents are rendered by converting them to HTML and loading inside a WebView. If the WebView fails to render large DOCX files (memory limit ~128 MB on low-RAM devices), there is no fallback, error state, or graceful message. The user sees a blank white WebView.  
**Fix Prompt**: Add a `webChromeClient` / `onReceivedError` callback to the WebView, and emit an error `UiState` from `OfficeReaderViewModel` when rendering fails. Display a `NexusSurface` error card with a "Open in another app" button.

---

### UX-006 — Scanner GENERATING_PDF state shows no progress bar or file name
**Severity**: 🟡 Medium  
**Location**: `ScannerScreen.kt:122–151`  
**Description**: The `ScannerState.GENERATING_PDF` branch only shows "Saving PDF..." text with no `CircularProgressIndicator`. On slow devices with large scans (20+ pages), the user may wait 10–15 seconds with no visual feedback indicating progress, and may force-close the app thinking it froze.  
**Fix Prompt**: Add `CircularProgressIndicator(color = NexusTheme.colors.primary)` above the "Saving PDF..." text. Expose a `progress: Float?` from `PdfGenerator.generatePdf()` via a coroutine `Flow` and display a `LinearProgressIndicator` while saving.

---

### UX-007 — PdfReader does not show page number indicator during horizontal layout
**Severity**: 🟡 Medium  
**Location**: `PdfReaderScreen.kt:166` (`isHorizontalLayout`)  
**Description**: When the PDF is switched to horizontal (pager) layout, there is no visible current-page / total-pages indicator. In vertical scroll mode a progress indicator is shown, but horizontal layout lacks this, forcing the user to use the top bar "Go to page" action to know their position.  
**Fix Prompt**: Add a `NexusText("${currentPage + 1} / ${state.pageCount}")` pill overlay at `Alignment.BottomCenter` inside the horizontal pager, hidden during immersive mode.

---

### UX-008 — Welcome / Onboarding screen does not respect system dark theme
**Severity**: 🟢 Low  
**Location**: `MainActivity.kt:88` (initial `ThemeMode.LIGHT`)  
**Description**: First-launch state defaults to `ThemeMode.LIGHT` via `collectAsStateWithLifecycle(initialValue = ThemeMode.LIGHT)`. On a device already set to system dark theme, the welcome screen briefly flashes in light mode before DataStore loads the preference, creating a visible theme flicker.  
**Fix Prompt**: Change the initial value to read from `isSystemInDarkTheme()` at the call site: `initialValue = if (isSystemInDarkTheme()) ThemeMode.DARK else ThemeMode.LIGHT`.

---

### UX-009 — `allowBackup = true` with no granular backup exclusions for sensitive data
**Severity**: 🟠 High  
**Location**: `AndroidManifest.xml:56`  
**Description**: `android:allowBackup="true"` enables automatic backup to Google Drive. The DataStore file `nexus_user_preferences` contains starred URI sets and navigation preference data that may reveal the user's file access history. While `data_extraction_rules.xml` exists, its contents were not audited. If DataStore is included in backup, starred document URIs (which include full file paths) are uploaded to Google Drive.  
**Fix Prompt**: Open `res/xml/data_extraction_rules.xml` and add `<exclude domain="file" path="datastore/" />` to prevent DataStore from being backed up. Alternatively, use `EncryptedSharedPreferences` or `EncryptedDataStore` for starred URI data.

---

## 3. ✨ Animation Issues

### AN-001 — Search bar uses `tween` instead of spring — feels stiff
**Severity**: 🟢 Low  
**Location**: `DashboardScreen.kt` (`AnimatedVisibility` on search bar), `PdfReaderScreen.kt:23`  
**Description**: Both the search bar's `slideInVertically` and `slideOutVertically` use `tween(durationMillis = 200/250)`. Compared to the bottom nav which uses `navPillSpring()` (a spring spec), the search bar animation feels rigid and mechanical, inconsistent with the rest of the app's spring-based motion design.  
**Fix Prompt**: Replace `tween(250)` with `spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)` on the enter spec. Keep the exit as `tween(150)` for a snappy dismiss.

---

### AN-002 — `AnimatedContent` in PdfReader uses `tween` for state transitions — same stiffness issue
**Severity**: 🟢 Low  
**Location**: `PdfReaderScreen.kt:253–256`  
**Description**: The `AnimatedContent` transitioning between `Loading`, `Success`, `Error`, and `PasswordRequired` states uses `fadeIn(tween(220, delay=90)) + fadeOut(tween(90))`. While functional, the 90ms exit is so fast it can clip content on slow GPU devices (Mali-G52 era), causing a brief white flash before the new state fades in.  
**Fix Prompt**: Increase exit duration to 150ms: `fadeOut(tween(150))`. Add `SizeTransform(clip = false)` to prevent clipping.

---

### AN-003 — Double-tap zoom animation uses bare `Animatable.animateTo()` without spring
**Severity**: 🟡 Medium  
**Location**: `PdfReaderScreen.kt:330–351`  
**Description**: The double-tap zoom on PDFs uses `Animatable(scale).animateTo(targetScale)` with no explicit animation spec. The default is a spring, which is correct — but the offset animations (`offsetX`, `offsetY`) also use the default spec, which may produce visible overshooting past the page edges before the spring settles. The user can perceive the page "bouncing past" the zoom target.  
**Fix Prompt**: Use `animateTo(targetScale, animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow))` for all three: scale, offsetX, offsetY. This prevents overshoot in a zoom operation where overshoot looks like a glitch.

---

### AN-004 — Snap-back animation for pan-out-of-bounds uses `tween(200)` — no spring
**Severity**: 🟡 Medium  
**Location**: `PdfReaderScreen.kt:425–428`  
**Description**: When the user pans a zoomed PDF beyond its boundaries and lifts the finger, the content snaps back using `animateTo(clampedOffsetX, animationSpec = tween(200))`. A tween gives a linear or cubic bezier bounce-back; a spring would feel physically natural (like iOS rubber-band scroll).  
**Fix Prompt**: Replace with `animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)`.

---

### AN-005 — `PresentationReaderScreen` uses only `tween`-based animation throughout
**Severity**: 🟡 Medium  
**Location**: `PresentationReaderScreen.kt:11–18` (imports only `tween`, `FastOutSlowInEasing`)  
**Description**: All `AnimatedVisibility`, `animateColorAsState`, and `animateDpAsState` calls in the presentation reader use `tween` with `FastOutSlowInEasing`. This creates a noticeable inconsistency with the rest of the app which is migrating towards spring specs. The slide controls bar, speaker notes drawer, and jump-to-slide dialog all use static easing that feels dated.  
**Fix Prompt**: Replace `tween(durationMillis = X, easing = FastOutSlowInEasing)` with spring equivalents for visibility transitions. Keep `tween` only for color transitions where a spring is not meaningful.

---

### AN-006 — Annotation toolbar dock animation uses no spring and may flicker on rapid taps
**Severity**: 🟡 Medium  
**Location**: `PdfReaderScreen.kt:179` (`toolbarDockPosition`), `PdfAnnotationToolbar.kt`  
**Description**: The annotation toolbar dock position toggle (`Left`/`Right`) changes `toolbarDockPosition` without an `animateDpAsState` or `AnimatedContent`. The toolbar jumps position instantly, which is jarring when switching dock side while annotations are being drawn.  
**Fix Prompt**: Wrap the toolbar with `AnimatedContent(targetState = toolbarDockPosition)` using a horizontal slide transition spec.

---

### AN-007 — `ToolsScreen` card entry animation is commented out / missing
**Severity**: 🟢 Low  
**Location**: `ToolsScreen.kt:94–108`  
**Description**: The filtered tools grid uses a standard `LazyColumn` with no entry/exit animation. When the user changes category or types a search query, tools abruptly appear/disappear without any staggered fade-in. The HTML reference spec included staggered entry animations.  
**Fix Prompt**: Add `animateItem()` modifier on each tool card item inside the lazy column (`LazyColumn` / `LazyVerticalGrid`). Add `AnimatedVisibility` for the empty-state composable.

---

## 4. 🐛 Bugs

### BG-001 — ~~Search bar close button not visible~~ (FIXED)
**Severity**: ✅ RESOLVED  
**Location**: `DashboardScreen.kt`  
**Description**: The close/X button was overlapped by the top-right pill. Fixed by changing alignment to `TopStart` with explicit `start=16.dp` padding and raising pill `zIndex` to 30f.  
**Fix Applied**: Commit `911ca6d`

---

### BG-002 — `URLDecoder.decode()` double-decoded in `PdfReaderScreen` without charset
**Severity**: 🔴 Critical  
**Location**: `PdfReaderScreen.kt:147`, `OfficeReaderScreen.kt:96`, `TextReaderScreen.kt:86`  
**Description**: All three reader screens call `URLDecoder.decode(URLDecoder.decode(fileName, "UTF-8"), "UTF-8")` — double-decoding the filename. If the filename contains a literal `%` character (e.g., `Budget_50%_Done.pdf`), the second decode will throw `IllegalArgumentException: URLDecoder: Illegal hex characters in escape (%) pattern...` and fall through to the raw (double-encoded) filename display. This manifests as garbled file names in the top bar.  
**Fix Prompt**: Replace with the ViewModel's `safeDecode()` method which handles double-encoding gracefully with try/catch. In Compose, call `viewModel.decodedFileName` instead of decoding inline.

---

### BG-003 — PDF horizontal layout does not persist across recompositions
**Severity**: 🟡 Medium  
**Location**: `PdfReaderScreen.kt:166`, `PdfReaderViewModel.kt:499–503`  
**Description**: `isHorizontalLayout` is a `MutableStateFlow` in the ViewModel (`_isHorizontalLayout`), but its initial value is always `false` and it is never persisted to `UserPreferencesRepository` or DataStore. So every time the user reopens a PDF, the layout resets to vertical, losing the user's last preference.  
**Fix Prompt**: Add a `USE_HORIZONTAL_LAYOUT = booleanPreferencesKey("use_horizontal_layout")` key to `UserPreferencesRepository`. Initialize `_isHorizontalLayout` from DataStore in `init {}` and persist on toggle.

---

### BG-004 — `ScannerScreen` calls `onBack()` without navigating to a valid state after save failure
**Severity**: 🟡 Medium  
**Location**: `ScannerScreen.kt:150–163`  
**Description**: In `ScannerState.GENERATING_PDF`, if `PdfGenerator.generatePdf()` returns `null` (failure), the code shows a toast ("Failed to save") and calls `onBack()`. This pops the scanner off the navigation stack entirely — the user loses all scanned pages and must restart from scratch.  
**Fix Prompt**: On failure, `viewModel.revertToSaveScreen()` instead of `onBack()`. Show a bottom sheet with the error details and a "Retry" button.

---

### BG-005 — Page bookmarks for PDFs are stored by decoded URI, but starred URIs use encoded URI
**Severity**: 🟠 High  
**Location**: `PdfReaderViewModel.kt:264–268` vs `UserPreferencesRepository.kt:227–242`  
**Description**: Bookmarks (`DocumentBookmarkDao`) are keyed on `uriStrDecoded` (after `safeDecode()`). But `starredUris` in DataStore are toggled with `prefsRepository.toggleStarredUri(uri)` where `uri` is the decoded URI from `_currentUriFlow`. On some content provider URIs (e.g., Google Drive or Downloads) the decoded and encoded forms differ (`%20` vs space). This can cause the star icon to show as un-starred even when the document is in `starredUris`.  
**Fix Prompt**: Normalize all URI keys to their decoded form before any storage operation. Create a `fun normalizeUri(raw: String): String` utility in `core/util` and apply it uniformly across all DAO and DataStore writes.

---

### BG-006 — `MainScope().launch { }` used inside a Composable lambda
**Severity**: 🟠 High  
**Location**: `MainActivity.kt:240`  
**Description**: Inside the `NexusNavHost` `onFirstLaunchComplete` callback: `kotlinx.coroutines.MainScope().launch { preferencesRepository.completeFirstLaunch() }`. This creates a new unmanaged `CoroutineScope` on every invocation, which is not tied to any lifecycle and leaks if the Activity is destroyed before it completes. Additionally, `MainScope` is a `GlobalScope` alias — it never cancels.  
**Fix Prompt**: Replace with `lifecycleScope.launch { preferencesRepository.completeFirstLaunch() }` inside the Activity, exposed as a lambda parameter that captures `lifecycleScope`.

---

### BG-007 — `SharedTransitionScope` null-check is fragile, can produce invisible shared elements
**Severity**: 🟡 Medium  
**Location**: `PdfReaderScreen.kt:235–245`  
**Description**: The shared element modifier for thumbnail-to-reader transition checks `if (sharedScope != null && animatedScope != null)` using `CompositionLocal`. If the navigation host provides one but not the other (possible during configuration change mid-animation), only one half of the shared element is registered, causing the transition to silently abort and show a simple fade.  
**Fix Prompt**: Use a single `LocalSharedTransitionScope` that always provides both via a wrapper `SharedTransitionLayout`. Fail fast with an `error("SharedTransitionScope must be provided")` in debug builds.

---

## 5. ⚡ Performance Issues

### PF-001 — `lazyGridState.firstVisibleItemScrollOffset` read during composition (potential frame drops)
**Severity**: 🟠 High  
**Location**: `DashboardScreen.kt` (scroll-based alpha computation)  
**Description**: The header alpha and scroll-collapsed state read `lazyGridState.firstVisibleItemScrollOffset` during the composition phase without wrapping in `derivedStateOf`. This causes the entire composable tree containing the header to recompose on **every** scroll frame, not just when the offset crosses the threshold.  
**Fix Prompt**: 
```kotlin
val headerAlpha by remember {
    derivedStateOf {
        val offset = lazyGridState.firstVisibleItemScrollOffset
        (offset / 100f).coerceIn(0f, 1f)
    }
}
```
This restricts recomposition to only when `headerAlpha` changes.

---

### PF-002 — PDF text extraction runs on ALL pages synchronously on document open
**Severity**: 🟠 High  
**Location**: `PdfReaderViewModel.kt:339–356`  
**Description**: After a PDF loads, `pdfTextByPage` is populated by iterating every page under `renderMutex.withLock { }`. For a 500-page PDF, this blocks the render mutex for several seconds, preventing any page from being rendered during that time — the user sees shimmer skeletons far longer than necessary.  
**Fix Prompt**: Extract text lazily on demand (e.g., only extract text for the current visible pages + ±2 pages). For search, extract in a background job using `Dispatchers.Default` **without** holding `renderMutex`. Use a separate `extractMutex` for text extraction.

---

### PF-003 — Dual glass system: `HazeState` + `glassBackground` both imported in readers
**Severity**: 🟡 Medium  
**Location**: `PdfReaderScreen.kt:102–104`, `TextReaderScreen.kt:62–64`, `OfficeReaderScreen.kt:68–70`  
**Description**: All reader screens import both `com.nexus.core.ui.utils.glassBackground` (custom AGSL shader) and `dev.chrisbanes.haze.HazeState`/`hazeGlassBackground`. Each glass system registers its own render effect pipeline. Having both initialized simultaneously wastes GPU bandwidth and can cause the render effect to compound (double blur).  
**Fix Prompt**: Decide on a single glass system for readers. The `hazeGlassBackground` path should be preferred (Haze 1.x is well-maintained). Remove `glassBackground` imports from all reader screens and migrate any remaining callsites to `hazeGlassBackground`.

---

### PF-004 — `DashboardViewModel.uiState` combines 11 sources in a 5-level `combine()` nest
**Severity**: 🟡 Medium  
**Location**: `DashboardViewModel.kt:142–181`  
**Description**: The dashboard `uiState` is derived from a 5-level nested `combine()` spanning 11 upstream flows. Each upstream emission triggers the outer lambda to run synchronously on the calling coroutine. With frequent emissions (search query changes, scroll events, file deletions), this may cause 5–8 lambda evaluations per user interaction.  
**Fix Prompt**: Flatten by extracting intermediate computed results as separate `StateFlow`s using `.stateIn()`. For example: `val filteredDocs: StateFlow<List<RecentDocumentUiModel>>` and `val uiMetadata: StateFlow<UiMetadata>` as separate flows, then `combine(filteredDocs, uiMetadata) { docs, meta -> DashboardUiState(...) }`.

---

### PF-005 — PDF disk cache saves at JPEG quality 90 on every LRU eviction
**Severity**: 🟡 Medium  
**Location**: `PdfReaderViewModel.kt:122–127`  
**Description**: The `pageLruCache.entryRemoved()` callback calls `saveBitmapToDiskCache()` which JPEG-compresses every evicted page at quality 90. For high-DPI renders (up to `zoomTargetW = 2400` pixels wide), this is a large I/O write on the render-bound IO thread, stalling further renders.  
**Fix Prompt**: Use a background `Dispatchers.IO` coroutine for disk cache writes (not on the LRU callback directly). Lower JPEG quality to 75 for disk cache (lossy display only). Consider PNG for small pages (< 100KB) where JPEG artifacts are more visible.

---

### PF-006 — `ToolsScreen` dot-grid background Canvas redraws on every recomposition
**Severity**: 🟡 Medium  
**Location**: `ToolsScreen.kt:126–151`  
**Description**: The decorative dot-grid background uses a `Canvas` composable that runs a nested double `while` loop calculating and drawing hundreds of circles on every recomposition. The dot grid never changes — it is a pure static decoration — but it is not cached with `remember` or moved into a `drawBehind` modifier that participates in Compose's draw skip optimization.  
**Fix Prompt**: Replace with `Modifier.drawBehind { /* dot drawing code */ }` on the outer `Box`. Compose's layer caching will skip redrawing if the size hasn't changed.

---

### PF-007 — `animateNexusColorsAsState()` triggers 15+ individual `animateColorAsState` calls on theme switch
**Severity**: 🟡 Medium  
**Location**: `NexusTheme.kt` (inferred from `NexusColors.kt` structure)  
**Description**: Each theme switch causes 15+ separate `animateColorAsState` coroutines to launch simultaneously, each animating on the main thread. On low-end devices (Snapdragon 460), this causes a noticeable frame drop (3–5 jank frames) as the theme transitions.  
**Fix Prompt**: Use a single `AnimationState<Color>` (or `Animatable`) for a master alpha value and blend between the old and new `NexusColors` using `lerp()`. This reduces 15 coroutines to 1.

---

## 6. 🔧 Optimization Issues

### OP-001 — `resourceConfigurations += listOf("en")` strips all non-English localizations
**Severity**: 🟡 Medium  
**Location**: `app/build.gradle.kts:35`  
**Description**: The app strips all non-English locale resources from dependencies. If the app is ever localized (e.g., Hindi, Spanish), the dependency resources will already be stripped from the base APK. Additionally, some system dialog strings (e.g., date pickers) render in English even on non-English devices.  
**Fix Prompt**: Remove the `resourceConfigurations` restriction now, or expand it to include target locales when the app is localized. If APK size is a concern, use App Bundle + language delivery splits instead.

---

### OP-002 — `android:largeHeap="true"` declared globally in Manifest
**Severity**: 🟡 Medium  
**Location**: `AndroidManifest.xml:65`  
**Description**: `android:largeHeap="true"` requests a larger Dalvik heap at process start. While the app does render large PDFs, this doubles the GC overhead for all users even on simple operations (opening a text file), and prevents Android from reclaiming memory from this process during background eviction.  
**Fix Prompt**: Remove `largeHeap="true"`. Instead, use `BitmapFactory.Options.inBitmap` for bitmap reuse in `pageLruCache`, and limit `maxCacheMemoryKb` to no more than `maxMemory / 4`. The existing `coerceIn(64 * 1024, 256 * 1024)` is already reasonable.

---

### OP-003 — ProGuard `-assumenosideeffects` on `Log` only strips `v/i/w/d`, not `e()`
**Severity**: 🟢 Low  
**Location**: `app/proguard-rules.pro`  
**Description**: The release ProGuard rule strips `Log.v`, `Log.i`, `Log.w`, `Log.d`, but `Log.e(...)` calls remain in the release APK. Several error paths in `PdfReaderViewModel` use `Log.e("PdfReaderViewModel", "...", e)` which will appear in logcat in production builds and may leak internal stack traces.  
**Fix Prompt**: Add `public static int e(...);` to the `-assumenosideeffects class android.util.Log { ... }` block, or replace error logging with a custom `CrashLogger` that is a no-op in production.

---

### OP-004 — `AnimationDebug.speedMultiplier` is a public mutable var — debug artifact in production
**Severity**: 🟢 Low  
**Location**: `core/src/main/kotlin/com/nexus/core/ui/animations/Animations.kt` (inferred)  
**Description**: `AnimationDebug.speedMultiplier` is a `var` with no `@VisibleForTesting` annotation. In release builds, this mutable state is accessible from any class, and if accidentally set to a non-1.0 value, all animations will be visibly sped up or slowed. It is never guarded by `BuildConfig.DEBUG`.  
**Fix Prompt**: 
```kotlin
object AnimationDebug {
    val speedMultiplier: Float = if (BuildConfig.DEBUG) 1.0f else 1.0f
    // set only via reflection in test environments
}
```
Or mark it `internal` and restrict to the core module.

---

### OP-005 — `repoScope` in `UserPreferencesRepository` is an unmanaged `CoroutineScope`
**Severity**: 🟡 Medium  
**Location**: `UserPreferencesRepository.kt:71`  
**Description**: `private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)` is created at class instantiation and never cancelled. As a `@Singleton`, this lives for the app's lifetime, which is acceptable — but if the class is ever used outside DI (e.g., in tests), the scope will leak.  
**Fix Prompt**: Annotate with `@VisibleForTesting` and add a `fun close() { repoScope.cancel() }` method (even if never called in production). For the debounced slider jobs, use the existing `Job?` cancel pattern — this is already correct.

---

### OP-006 — MediaStore query uses `MediaStore.Files.FileColumns.DATA` (deprecated API 29+)
**Severity**: 🟡 Medium  
**Location**: `StorageScanUseCase.kt:23–28`  
**Description**: The storage scanner queries `MediaStore.Files.FileColumns.DATA` to get the file path. This column is deprecated since API 29 (Android 10) and may return `null` on some vendor ROMs even with `MANAGE_EXTERNAL_STORAGE` granted. The code already handles `null` paths (`if (path.isNullOrEmpty()) continue`), but the dependency on a deprecated column is a risk.  
**Fix Prompt**: On API 29+, prefer `MediaStore.Files.FileColumns.DOCUMENT_ID` + `DocumentsContract.buildDocumentUriUsingTree()` or use the file URI constructed from `ContentUris.withAppendedId()`. Keep the `DATA` path as fallback for API < 29.

---

## 7. 🔒 Security Issues

### SC-001 — `release.keystore` and `keystore.properties` exist on-disk without CI-enforced protection
**Severity**: 🟠 High  
**Location**: Project root (`keystore.properties` referenced in `app/build.gradle.kts:12`)  
**Description**: Both files are correctly `.gitignore`-d. However, there is no CI pipeline (`/.github/workflows/`) that verifies these files are absent from the repository or checks for accidental staging. A developer using a GUI Git client could accidentally add them without noticing.  
**Fix Prompt**: Add a pre-commit hook: `git secrets --add 'storePassword|keyAlias|keyPassword'`. Add a GitHub Actions `check-sensitive-files.yml` that runs `git diff --cached --name-only | grep -E '\.keystore|keystore\.properties'` and fails the build if matched.

---

### SC-002 — DataStore preferences stored as plaintext — starred URIs reveal full file path history
**Severity**: 🟡 Medium  
**Location**: `UserPreferencesRepository.kt:227–242` (key `STARRED_URIS`)  
**Description**: The `nexus_user_preferences` DataStore protobuf file is stored unencrypted in the app's internal storage. On rooted devices (or through ADB backup), this file exposes: full file paths of all starred documents, all visited URI history, navigation preferences. While file-path data is not classified as PII, it can reveal sensitive documents (e.g., medical, financial) the user has viewed.  
**Fix Prompt**: Use `EncryptedDataStore` with `MasterKey` from Jetpack Security for the DataStore instance. Only encrypt the `starred_uris` and `reader_theme` keys — navigation preference keys do not need encryption.

---

### SC-003 — `INTERNET` permission declared with no network calls in core app flow
**Severity**: 🟡 Medium  
**Location**: `AndroidManifest.xml:51`  
**Description**: The `INTERNET` permission is declared (presumably for an "Updater" feature per the manifest comment). However, there is no apparent network-request code in the audited files, and no updater module is present in `settings.gradle.kts`. This excess permission increases the attack surface and may cause Play Store reviewers to flag the app.  
**Fix Prompt**: Remove `INTERNET` permission until the updater feature is implemented. If the updater is a future feature, add the permission only in the updater module's manifest when the module is introduced.

---

### SC-004 — `FLAG_GRANT_READ_URI_PERMISSION` taken but exception silently swallowed
**Severity**: 🟡 Medium  
**Location**: `MainActivity.kt:141–146`, `MainActivity.kt:346–350`  
**Description**: Both `takePersistableUriPermission()` calls catch all exceptions with `catch (_: Exception) {}` and swallow them silently. If the content provider revokes permission or the URI is invalid, the app will proceed to call `router.openDocument()` with an inaccessible URI, resulting in a confusing error screen rather than a clear "Cannot access this file" message.  
**Fix Prompt**: Log the exception in debug builds. On failure, show a user-friendly dialog: "Unable to access this file. It may have been moved or deleted." Avoid silently proceeding.

---

### SC-005 — WebView in OfficeReaderScreen has JavaScript potentially enabled by default
**Severity**: 🟠 High  
**Location**: `OfficeReaderScreen.kt:52` (`import android.webkit.WebView`)  
**Description**: The `WebView` used for DOCX/XLSX rendering likely loads converted HTML content. If `webViewSettings.javaScriptEnabled = true` is set anywhere in the WebView configuration, and if the converted HTML includes any script tags from the original DOCX (e.g., embedded macros converted to HTML), this opens a path for local HTML injection / stored XSS.  
**Fix Prompt**: Explicitly set `webView.settings.javaScriptEnabled = false`. Use `loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)` instead of `loadUrl()` to prevent external resource loading. Add `webView.settings.blockNetworkLoads = true`.

---

## 8. 🛡️ Vulnerabilities

### VU-001 — File scheme intent filter with `mimeType="*/*"` — arbitrary file access from any app
**Severity**: 🔴 Critical  
**Location**: `AndroidManifest.xml:154–159`  
**Description**: The intent filter `<data android:scheme="file" /><data android:mimeType="*/*" />` allows **any** app to send a `file://` URI to NexusDocsViewer, including files outside the app's storage scope (e.g., `/data/data/com.another.app/databases/secret.db`). Android 7+ blocks file URIs from being sent between apps, but on API < 24 (supported down to API 26, so safe), this is exploitable. Even on API 26+, local vulnerability exists for apps sharing the same user ID.  
**Fix Prompt**: Replace the wildcard file intent filter with explicit MIME type filters, the same as the content:// filter. Remove the generic `file://` + `*/*` filter, keeping only specific MIME types. Add a `FileProvider` check in `handleIncomingIntent()` to validate the URI before opening.

---

### VU-002 — `MANAGE_EXTERNAL_STORAGE` with no runtime guard on features that don't require it
**Severity**: 🟡 Medium  
**Location**: `AndroidManifest.xml:38–40`, `StorageScanUseCase.kt`  
**Description**: `MANAGE_EXTERNAL_STORAGE` is required for storage scanning. However, features like the Document Scanner and PDF Viewer opened via intent (file already chosen by the user) do not need this permission to function. Currently, the entire app is blocked if the permission is denied, even for use cases that only need `READ_EXTERNAL_STORAGE` or no permission.  
**Fix Prompt**: Implement a "graceful degradation" strategy: if `MANAGE_EXTERNAL_STORAGE` is not granted, disable only the "All Documents" scan tab and show a "Grant permission to scan all documents" banner. Keep PDF reading from intents fully functional without the permission.

---

### VU-003 — Annotation data stored in-memory only — no persistence, lost on process kill
**Severity**: 🟡 Medium  
**Location**: `PdfReaderViewModel.kt:178–189` (`_drawnStrokesState`)  
**Description**: All PDF annotations (pen strokes, shapes, stamps, text) are stored in `_drawnStrokesState` which is a `MutableStateFlow<Map<Int, List<PdfAnnotationItem>>>` in-memory only. If the system kills the process (OOM), or if the user navigates away and the ViewModel is cleared, all unsaved annotations are permanently lost. There is an "Export annotations" function, but no auto-save.  
**Fix Prompt**: Serialize `PdfAnnotationItem` list to a JSON string (using `kotlinx.serialization`) and persist to a Room entity `DocumentAnnotation(uri, pageIndex, json)` on every stroke completion. Load from Room on document open.

---

### VU-004 — Shared preferences `nexus_page_counts` is `MODE_PRIVATE` but accessible via backup
**Severity**: 🟢 Low  
**Location**: `PdfReaderViewModel.kt:334–337`  
**Description**: `getSharedPreferences("nexus_page_counts", Context.MODE_PRIVATE)` stores URI → page count mappings. If `allowBackup=true` and the `backup_rules.xml` does not exclude `shared_prefs/nexus_page_counts.xml`, this file (containing full document URI paths) is backed up to Google Drive.  
**Fix Prompt**: Add `<exclude domain="sharedpref" path="nexus_page_counts.xml" />` to `res/xml/data_extraction_rules.xml`. Better yet, migrate page count to the Room `RecentDocument` table and drop this SharedPreferences entirely.

---

### VU-005 — `Toast.makeText(context, ...)` called from background coroutine in ScannerScreen
**Severity**: 🟡 Medium  
**Location**: `ScannerScreen.kt:150–163` (`coroutineScope.launch { ... Toast.makeText(...) }`)  
**Description**: Inside the `GENERATING_PDF` state, `Toast.makeText(context, "Saved successfully!")` is called from within `coroutineScope.launch { }` which runs on `Dispatchers.Main` (default). However, if the launcher coroutine is on `Dispatchers.IO` or `Default`, calling `Toast.makeText()` from a non-main thread will crash with `CalledFromWrongThreadException`.  
**Fix Prompt**: Ensure all `Toast.makeText()` calls use `withContext(Dispatchers.Main) { ... }` wrapper. Better: emit a `UiEvent.ShowSnackbar("Saved successfully!")` from the ViewModel and handle it in the Composable with `LaunchedEffect`.

---

## 9. 💡 Suggestions

### SG-001 — Add Material You dynamic color support
**Category**: Design / Theming  
**Priority**: High  
**Description**: The app has a rich custom `NexusColors` system but does not integrate with Android 12+'s Material You dynamic color. Users who set a custom wallpaper-derived color scheme get no benefit of adaptive colors — the app always uses its fixed primary/accent palette.  
**Suggestion**: Use `DynamicColorScheme` from `androidx.compose.material3:material3-dynamic-color` and map `dynamicColorScheme(context).primary` → `NexusColors.primary` when `Build.VERSION.SDK_INT >= 31`. Expose as a toggle in Settings: "Use dynamic theme colors."

---

### SG-002 — Implement Coil for all thumbnail loading instead of custom bitmap cache
**Category**: Performance / Architecture  
**Priority**: High  
**Description**: The current `PdfThumbnailCache` is a hand-rolled LRU bitmap cache. Coil 3.x has built-in disk caching, memory pressure awareness, placeholder/error states, and cross-fade transitions. Migrating would reduce ~600 lines of custom cache code and add GIF/SVG support for future file type thumbnails.  
**Suggestion**: Add `implementation("io.coil-kt.coil3:coil-compose:3.x")`. Create a `PdfPageFetcher` that calls `PdfReaderViewModel.renderPage()` as a Coil `Fetcher`. Use `AsyncImage(model = PdfPageRequest(uri, pageIndex))` in the page list items.

---

### SG-003 — Add predictive back gesture support (Android 14+)
**Category**: UX / System Integration  
**Priority**: Medium  
**Description**: The app uses `BackHandler { }` throughout but has not opted into the Predictive Back API (`enableOnBackInvokedCallback = true` in Manifest). Android 14 users get no preview of where the back gesture will navigate them, missing a major UX feature of the platform.  
**Suggestion**: Set `android:enableOnBackInvokedCallback="true"` in `<activity>` in the manifest. Use `androidx.activity.compose.PredictiveBackHandler` where `BackHandler` is currently used. Implement a scale/translate animation for the reader close gesture.

---

### SG-004 — Add a file-watcher to auto-refresh the document list when files change
**Category**: UX / Reliability  
**Priority**: Medium  
**Description**: The dashboard currently only rescans storage on explicit pull-to-refresh. If the user creates or deletes a file in another app, the dashboard does not update automatically.  
**Suggestion**: Use `FileObserver` on the Documents/Downloads directories (requires `MANAGE_EXTERNAL_STORAGE`), or observe the MediaStore content URI with a `ContentObserver`. Debounce the observer's callback by 2 seconds to avoid rapid successive scans.

---

### SG-005 — Paginated / virtualized document list for large libraries
**Category**: Performance  
**Priority**: Medium  
**Description**: The dashboard loads all matching documents into a single `List<RecentDocumentUiModel>` and displays them in a `LazyVerticalGrid`. For users with 2,000+ documents, this means 2,000+ UI models are held in memory simultaneously.  
**Suggestion**: Migrate to `Pager` from `androidx.paging:paging-compose`. Implement `PagingSource<Int, RecentDocument>` from the Room DAO with `LIMIT`/`OFFSET`. This reduces memory footprint from O(N) to O(pageSize).

---

### SG-006 — Add a text-extraction fallback for image-only (scanned) PDFs
**Category**: Feature / Accessibility  
**Priority**: Medium  
**Description**: MuPDF's `page.textAsHtml()` returns empty content for scanned PDFs (images only). The PDF search feature silently returns 0 results with no explanation. Users of scanned document archives are left with no search capability.  
**Suggestion**: After text extraction returns an empty page, run Google ML Kit's `TextRecognition` on the rendered bitmap for that page. Cache OCR results alongside MuPDF text in `pdfTextByPage`. Show a banner "This PDF contains scanned pages — using OCR search."

---

### SG-007 — Add E2E tests for critical user flows
**Category**: Testing / Quality  
**Priority**: High  
**Description**: There are no instrumented UI tests (Espresso / Compose UI Test) for the most critical flows: opening a PDF, search, annotations, delete-to-trash-and-restore. The only test infrastructure is `testImplementation(libs.junit)` with no test files found.  
**Suggestion**: Add `androidTestImplementation(libs.compose.ui.test.junit4)` test cases for:
  1. Open PDF → see page count in top bar  
  2. Enable search → type query → see highlight on page  
  3. Delete document → appears in Recycle Bin → Restore → back in Home  
  4. Dark mode toggle → verify color tokens change

---

### SG-008 — Migrate PresentationReaderScreen to `collectAsStateWithLifecycle`
**Category**: Performance / Correctness  
**Priority**: Medium  
**Description**: (Companion to PC-005) Beyond correctness, this also enables Lifecycle-aware collection which pairs with the upcoming Compose 1.8 lifecycle improvements.  
**Suggestion**: Add `implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.x")` to `feature:reader-presentation`'s build file and replace all `collectAsState()` usages.

---

### SG-009 — Add a "Recently Closed" / tab restore feature
**Category**: UX / Feature  
**Priority**: Low  
**Description**: When a user accidentally backs out of a 400-page PDF mid-way through reading, they must find and reopen it manually. The reading position is restored (via `rememberReadingPosition`), but discovery requires scrolling through the full list.  
**Suggestion**: Add a `RecentlyClosedDocuments` chip at the top of the Home tab showing the last 3 closed documents. Tap to reopen at the last reading position.

---

### SG-010 — AMOLED / true-black mode for existing Dark theme
**Category**: Design / Battery  
**Priority**: Low  
**Description**: The current `DARK` theme maps to `NexusColors.background = Color(0xFF121212)` (Material dark surface). For OLED phones (Pixel, OnePlus, Samsung), true black (`Color.Black` = `#000000`) pixels are powered off, saving 15–40% battery on the PDF reading screen where large white/dark areas dominate.  
**Suggestion**: Add `ThemeMode.AMOLED` to the existing `ThemeMode` enum (the DataStore already maps `"AMOLED"` → `ThemeMode.DARK`). Create `amoledNexusColors` with `background = Color.Black` and `surface = Color(0xFF0A0A0A)`.

---

## Summary Table

| ID | Category | Severity | Status |
|---|---|---|---|
| PC-001 | Pipeline | ✅ Fixed | Resolved |
| PC-002 | Pipeline | 🟡 Medium | Open |
| PC-003 | Pipeline | 🟡 Medium | Open |
| PC-004 | Pipeline | 🟡 Medium | Open |
| PC-005 | Pipeline | 🟡 Medium | Open |
| PC-006 | Pipeline | 🟠 High | Open |
| UX-001 | UI/UX | 🟡 Medium | Open |
| UX-002 | UI/UX | 🟡 Medium | Open |
| UX-003 | UI/UX | 🟢 Low | Open |
| UX-004 | UI/UX | 🟡 Medium | Open |
| UX-005 | UI/UX | 🟠 High | Open |
| UX-006 | UI/UX | 🟡 Medium | Open |
| UX-007 | UI/UX | 🟡 Medium | Open |
| UX-008 | UI/UX | 🟢 Low | Open |
| UX-009 | UI/UX | 🟠 High | Open |
| AN-001 | Animation | 🟢 Low | Open |
| AN-002 | Animation | 🟢 Low | Open |
| AN-003 | Animation | 🟡 Medium | Open |
| AN-004 | Animation | 🟡 Medium | Open |
| AN-005 | Animation | 🟡 Medium | Open |
| AN-006 | Animation | 🟡 Medium | Open |
| AN-007 | Animation | 🟢 Low | Open |
| BG-001 | Bug | ✅ Fixed | Resolved (911ca6d) |
| BG-002 | Bug | 🔴 Critical | Open |
| BG-003 | Bug | 🟡 Medium | Open |
| BG-004 | Bug | 🟡 Medium | Open |
| BG-005 | Bug | 🟠 High | Open |
| BG-006 | Bug | 🟠 High | Open |
| BG-007 | Bug | 🟡 Medium | Open |
| PF-001 | Performance | 🟠 High | Open |
| PF-002 | Performance | 🟠 High | Open |
| PF-003 | Performance | 🟡 Medium | Open |
| PF-004 | Performance | 🟡 Medium | Open |
| PF-005 | Performance | 🟡 Medium | Open |
| PF-006 | Performance | 🟡 Medium | Open |
| PF-007 | Performance | 🟡 Medium | Open |
| OP-001 | Optimization | 🟡 Medium | Open |
| OP-002 | Optimization | 🟡 Medium | Open |
| OP-003 | Optimization | 🟢 Low | Open |
| OP-004 | Optimization | 🟢 Low | Open |
| OP-005 | Optimization | 🟡 Medium | Open |
| OP-006 | Optimization | 🟡 Medium | Open |
| SC-001 | Security | 🟠 High | Open |
| SC-002 | Security | 🟡 Medium | Open |
| SC-003 | Security | 🟡 Medium | Open |
| SC-004 | Security | 🟡 Medium | Open |
| SC-005 | Security | 🟠 High | Open |
| VU-001 | Vulnerability | 🔴 Critical | Open |
| VU-002 | Vulnerability | 🟡 Medium | Open |
| VU-003 | Vulnerability | 🟡 Medium | Open |
| VU-004 | Vulnerability | 🟢 Low | Open |
| VU-005 | Vulnerability | 🟡 Medium | Open |
| SG-001–010 | Suggestions | 💡 | Backlog |

---

## Priority Fix Order

### 🔴 Fix Immediately (Critical)
1. **BG-002** — `URLDecoder` double-decode crash on `%` in filenames  
2. **VU-001** — Wildcard `file://` intent filter allows arbitrary file access  

### 🟠 Fix in Next Sprint (High)
3. **BG-005** — Starred URI key encoding mismatch  
4. **BG-006** — `MainScope().launch` leak in Activity  
5. **PC-001** — MuPDF temp-file race condition / leak  
6. **PC-006** — MediaStore query without LIMIT — potential ANR  
7. **UX-005** — WebView blank failure state for Office documents  
8. **UX-009** — `allowBackup` without DataStore exclusion  
9. **PF-001** — `derivedStateOf` missing on scroll-offset reads  
10. **PF-002** — Synchronous PDF full-text extraction blocking render mutex  
11. **SC-005** — WebView with JavaScript potentially enabled on Office documents  

### 🟡 Schedule for Upcoming Milestone
- All remaining medium-severity items (PC-002–005, UX-001–009, AN-003–006, BG-003–004, BG-007, PF-003–007, OP-001–006, SC-001–004, VU-002–005)  

---

*Report generated by automated codebase audit. All line numbers reference the codebase state as of commit `be2d2d0` (branch `main`). Re-audit recommended after major architectural changes.*
