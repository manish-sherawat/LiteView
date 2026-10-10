# LiteView — PowerPoint Viewer Implementation Tasks

This task tracker outlines the step-by-step engineering work for implementing advanced features and rendering capabilities in the PowerPoint viewer (`:feature:reader-presentation`).

---

## Task Progress Overview

| Phase | Focus Area | Tasks | Status |
| :--- | :--- | :--- | :--- |
| **Phase 1** | Foundation & Design Integration | `PPT-001` to `PPT-004` | **Completed** |
| **Phase 2** | In-Slide Search & Speaker Notes | `PPT-005` to `PPT-007` | **Planned** |
| **Phase 3** | Exporting & Document Actions | `PPT-008` to `PPT-010` | **Planned** |
| **Phase 4** | Advanced Rendering & Shapes | `PPT-011` to `PPT-013` | **Planned** |
| **Phase 5** | Interactive Slideshow & Inking | `PPT-014` to `PPT-016` | **Planned** |
| **Phase 6** | Native LOKit Engine Drop-in | `PPT-017` to `PPT-018` | **Planned** |

---

## Phase 1: Foundation & Design Integration (Completed)

### `PPT-001` — Create Dedicated `:feature:reader-presentation` Module
- **Status:** `Completed`
- **Target Files:**
  - `feature/reader-presentation/build.gradle.kts`
  - `settings.gradle.kts`
  - `feature/reader-presentation/consumer-rules.pro`
- **Description:** Setup multiplatform NDK/CMake build system, Compose UI dependencies, Apache POI libraries (`poi`, `poi-ooxml`, `poi-scratchpad`), and R8 keep rules.

### `PPT-002` — Apache POI Android-Safe Parser Engine
- **Status:** `Completed`
- **Target Files:**
  - `.../engine/poi/PoiPresentationParser.kt`
  - `.../engine/PresentationDocument.kt`
- **Description:** Implement zero-AWT parser for `.pptx` and `.ppt` extracting slide count, aspect ratio, background colors, titles, bullets, images, and tables without `java.awt.*` crashes.

### `PPT-003` — Navigation & Intent Filtering
- **Status:** `Completed`
- **Target Files:**
  - `core/.../navigation/NavigationContract.kt`
  - `app/src/main/AndroidManifest.xml`
  - `app/.../navigation/NexusNavHost.kt`
- **Description:** Register `DocumentType.PPTX`, MIME intent filters for `.pptx` and `.ppt`, and wire route `NexusRoute.PresentationReader`.

### `PPT-004` — Design System Alignment & Luxury UI
- **Status:** `Completed`
- **Target Files:**
  - `.../ui/PresentationReaderScreen.kt`
  - `.../ui/components/SlideViewport.kt`
  - `.../ui/PresentationReaderViewModel.kt`
- **Description:** Align with LiteView's design system: `NexusTopBar`, floating slide position pill, floating bottom dock toolbar, double-tap zoom gestures, slide thumbnail drawer, and recents/favorites persistence.

---

## Phase 2: In-Slide Search & Speaker Notes

### `PPT-005` — Extract Slide Speaker Notes
- **Status:** `Planned` (Priority: High)
- **Target Files:**
  - `.../engine/PresentationDocument.kt`
  - `.../engine/poi/PoiPresentationParser.kt`
- **Description:**
  - Extract speaker notes from `XSLFSlide.getNotes()` and legacy `HSLFSlide.getNotesSheet()`.
  - Store notes text in `PresentationSlideInfo.speakerNotes`.
- **Acceptance Criteria:**
  - Presentations with presenter notes have non-null `speakerNotes`.
  - Slides without notes return null cleanly without errors.

### `PPT-006` — Speaker Notes Collapsible Drawer UI
- **Status:** `Planned` (Priority: High)
- **Target Files:**
  - `.../ui/PresentationReaderScreen.kt`
  - `.../ui/components/SpeakerNotesBottomSheet.kt`
- **Description:**
  - Add a "Notes" action toggle in the floating dock and more options menu.
  - Display speaker notes in a sleek bottom sheet or split panel with adjustable text size.
- **Acceptance Criteria:**
  - Tapping "Notes" opens the drawer with the current slide's presenter notes.
  - Changing slides updates the notes drawer content reactively.

### `PPT-007` — In-Slide Text Search Integration
- **Status:** `Planned` (Priority: High)
- **Target Files:**
  - `.../ui/PresentationReaderViewModel.kt`
  - `.../ui/PresentationReaderScreen.kt`
- **Description:**
  - Integrate `ReaderSearchBar` into `PresentationReaderScreen`.
  - Implement full-text search across titles, bullets, table cells, and speaker notes.
  - Highlight search result count and provide next/previous match jump navigation.
- **Acceptance Criteria:**
  - Search queries instantly navigate to the matching slide.
  - Counter reflects current match index out of total matches found.

---

## Phase 3: Exporting & Document Actions

### `PPT-008` — Export Slide to High-Res Image (PNG)
- **Status:** `Planned` (Priority: High)
- **Target Files:**
  - `.../util/SlideExportUtil.kt`
  - `.../ui/PresentationReaderScreen.kt`
- **Description:**
  - Render the active slide View/Canvas to a bitmap at 1920x1080 resolution.
  - Save image to device `Pictures/` directory via `MediaStore` or trigger `Intent.ACTION_SEND` image chooser.
- **Acceptance Criteria:**
  - User can export current slide as PNG with toast confirmation.
  - Exported image is crisp and correctly proportioned.

### `PPT-009` — Export Entire Presentation to PDF
- **Status:** `Planned` (Priority: High)
- **Target Files:**
  - `.../util/PresentationPdfExporter.kt`
  - `.../ui/PresentationReaderViewModel.kt`
- **Description:**
  - Iterate over all slides and write each slide as a page in `android.graphics.pdf.PdfDocument`.
  - Save the resulting PDF file to Downloads/cache and prompt user to view or share.
- **Acceptance Criteria:**
  - A 20-slide presentation exports into a valid 20-page PDF document offline.
  - Progress indicator displays during PDF compilation.

### `PPT-010` — Slide Bookmarks System
- **Status:** `Planned` (Priority: Medium)
- **Target Files:**
  - `.../ui/PresentationReaderViewModel.kt`
  - `.../ui/PresentationReaderScreen.kt`
- **Description:**
  - Allow saving specific slide indices to `DocumentBookmarkDao`.
  - Provide a Bookmarks bottom sheet listing bookmarked slides with labels.
- **Acceptance Criteria:**
  - Users can bookmark key slides and jump directly to them from any screen.

---

## Phase 4: Advanced Rendering & Shapes

### `PPT-011` — AutoShape Geometry & Fill Rendering
- **Status:** `Planned` (Priority: High)
- **Target Files:**
  - `.../engine/poi/PoiPresentationParser.kt`
  - `.../ui/components/SlideShapeRenderer.kt`
- **Description:**
  - Parse `XSLFAutoShape` geometry types (rectangles, rounded rectangles, arrows, banners, stars, callouts).
  - Convert geometry vertices to Compose `Path` and draw using `Canvas`.
- **Acceptance Criteria:**
  - Standard PowerPoint flowchart boxes, arrows, and banners render cleanly on the slide canvas.

### `PPT-012` — Enhanced Inline Typography & Font Styling
- **Status:** `Planned` (Priority: Medium)
- **Target Files:**
  - `.../engine/PresentationDocument.kt`
  - `.../ui/components/SlideViewport.kt`
- **Description:**
  - Parse per-character formatting: custom RGB text colors, italic, underline, strikethrough, superscript, and subscript.
  - Construct Compose `AnnotatedString` with `SpanStyle` for each text block.
- **Acceptance Criteria:**
  - Colored text, mixed bold/italic runs, and links display with correct styles.

### `PPT-013` — Table Spans & Borders
- **Status:** `Planned` (Priority: Medium)
- **Target Files:**
  - `.../engine/poi/PoiPresentationParser.kt`
  - `.../ui/components/SlideViewport.kt`
- **Description:**
  - Support `colSpan` / `rowSpan` in table models.
  - Parse table borders (color, thickness) and background cell shading colors.
- **Acceptance Criteria:**
  - Complex merged tables display without misaligned columns.

---

## Phase 5: Interactive Slideshow & Inking

### `PPT-014` — Fullscreen Auto-Play Slideshow
- **Status:** `Planned` (Priority: Medium)
- **Target Files:**
  - `.../ui/PresentationReaderViewModel.kt`
  - `.../ui/PresentationReaderScreen.kt`
- **Description:**
  - Add Slideshow mode with auto-advancing slides on configurable timer (3s, 5s, 10s).
  - Add auto-looping option and tap-to-pause controls.
- **Acceptance Criteria:**
  - Slides advance automatically at the chosen interval.

### `PPT-015` — Slide Inking & Virtual Laser Pointer
- **Status:** `Planned` (Priority: Medium)
- **Target Files:**
  - `.../ui/components/SlideInkingCanvas.kt`
  - `.../ui/PresentationReaderScreen.kt`
- **Description:**
  - Add a drawing overlay with Pen, Highlighter, and Virtual Laser Pointer dot.
  - Pen strokes track pointer drag events and render with anti-aliased Bézier paths.
- **Acceptance Criteria:**
  - User can draw notes directly over slides and clear with an eraser tool.

### `PPT-016` — Lightbox Grid Slide Sorter
- **Status:** `Planned` (Priority: Low)
- **Target Files:**
  - `.../ui/components/SlideGridSorterDialog.kt`
  - `.../ui/PresentationReaderScreen.kt`
- **Description:**
  - Fullscreen 2- or 3-column thumbnail grid showing all slides at once.
  - Tapping a thumbnail navigates immediately to that slide.
- **Acceptance Criteria:**
  - Quick overview of large decks (50+ slides) without horizontal scrolling.

---

## Phase 6: Native LibreOfficeKit Engine Drop-in

### `PPT-017` — Precompiled `liblo-native-code.so` Deployment
- **Status:** `Planned` (Priority: Long-Term)
- **Target Files:**
  - `feature/reader-presentation/src/main/jniLibs/arm64-v8a/liblo-native-code.so`
  - `feature/reader-presentation/src/main/jniLibs/armeabi-v7a/liblo-native-code.so`
- **Description:**
  - Place compiled LOKit shared libraries into ABI folders.
  - Activate dynamic symbol binding in `LOKitBridge.kt`.
- **Acceptance Criteria:**
  - `lokitManager.isReady()` returns `true` and renders pixel-perfect slide bitmaps.

### `PPT-018` — Multi-threaded Tile Pre-rendering Cache
- **Status:** `Planned` (Priority: Long-Term)
- **Target Files:**
  - `.../ui/PresentationReaderViewModel.kt`
- **Description:**
  - Implement speculative background rendering of `currentSlideIndex + 1` and `currentSlideIndex - 1` into `LruCache` for instantaneous slide transitions.
- **Acceptance Criteria:**
  - Zero loading flicker when swiping to adjacent slides.
