# LiteView — PowerPoint Viewer Feature & Rendering Roadmap

This document outlines the architectural enhancements, visual rendering upgrades, and interactive presentation capabilities planned for the LiteView PowerPoint (`.pptx` & `.ppt`) engine (`:feature:reader-presentation`).

---

## 1. Visual Rendering & Graphic Fidelity

### 1.1 Vector AutoShape Geometry
- **Objective:** Render common PowerPoint shapes (rectangles, rounded banners, callout bubbles, arrows, polygons, flowchart elements) natively.
- **Implementation Strategy:**
  - In Apache POI / OOXML, shapes contain predefined shape types (`XSLFAutoShape.getShapeType()`) and preset geometries.
  - Map preset geometry paths to Jetpack Compose `Path` and draw them using `Canvas(modifier = Modifier.fillMaxSize())`.
  - Support shape fill colors (solid RGB, linear gradients) and outline strokes (`border`).

### 1.2 Rich Text Formatting & Typography
- **Objective:** Preserve inline font styles, weights, colors, and paragraph alignment.
- **Capabilities:**
  - Character-level spans: bold, italic, underline, strikethrough, superscript, subscript.
  - Per-span text colors: preserve font colors specified in text runs (`XSLFTextRun.getFontColor()`).
  - Paragraph formatting: left, center, right, justified alignments, line spacing, and paragraph indentation.
  - Hyperlinks: clickable external URLs or internal slide links.

### 1.3 Advanced Table Layout
- **Objective:** Support complex presentation tables with cell spans and shading.
- **Capabilities:**
  - Row and column spanning (`gridSpan` / `rowSpan`).
  - Cell background shading and gradient fills.
  - Custom cell borders (line thickness, color, dashed styles).
  - Vertical cell text alignment (top, center, bottom).

### 1.4 Charts & Diagrams (SmartArt)
- **Objective:** Render embedded PowerPoint charts (bar, column, line, pie) directly in slides.
- **Capabilities:**
  - Parse chart XML (`c:chart` in OOXML parts).
  - Extract categories, series labels, and numeric values.
  - Render with hardware-accelerated Compose Canvas or integrated chart primitives.

### 1.5 Media Elements & Audio/Video
- **Objective:** Enable multimedia playback inside slides.
- **Capabilities:**
  - Extract embedded `.mp4` and `.mp3` media streams from the presentation archive.
  - Provide an in-place video player overlay with playback controls.

### 1.6 LibreOfficeKit Native Rendering (Long-Term Full Parity)
- **Objective:** 100% pixel-perfect desktop Office rendering parity.
- **Capabilities:**
  - Pre-built `liblo-native-code.so` drop-in into `src/main/jniLibs/`.
  - JNI tile rendering with multi-threaded bitmap rendering.
  - Exact kerning, complex WordArt, formula equations, and SmartArt diagrams.

---

## 2. Presentation & Viewing Modes

### 2.1 In-Slide Text Search
- **Objective:** Real-time search across all slides in the presentation.
- **Features:**
  - Integrate `com.nexus.core.ui.components.ReaderSearchBar`.
  - Search query scans slide titles, bullet points, table text, and notes.
  - Slide jumping to search results with match counter (e.g. `3 of 12`).
  - Yellow highlight bounding boxes over matching text.

### 2.2 Speaker & Slide Notes Drawer
- **Objective:** View presenter notes alongside slide content.
- **Features:**
  - Parse speaker notes from `XSLFSlide.getNotes()` / `HSLFSlide.getNotesSheet()`.
  - Collapsible bottom drawer or side sheet toggleable from the floating dock.
  - Adjustable text size for readability during speaking.

### 2.3 Lightbox / Slide Grid Sorter View
- **Objective:** Overview of the entire deck in a multi-column visual grid.
- **Features:**
  - Switch from single-slide view to a 2- or 3-column thumbnail grid.
  - Instantly jump to any slide by tapping a thumbnail card.
  - Drag-free quick scanning of long slide decks (50+ slides).

### 2.4 Laser Pointer & Pen Inking Annotation Mode
- **Objective:** Live presentation tool for annotating slides during review or casting.
- **Features:**
  - Virtual glowing laser pointer dot following user touch.
  - Freehand pen with customizable colors (red, yellow, green) and stroke widths.
  - Highlighter mode (semi-transparent stroke) and eraser tool.
  - Option to clear all annotations or save annotations with the document.

### 2.5 Auto-Play Slideshow
- **Objective:** Automated slide advance for hands-free presentations or kiosk setups.
- **Features:**
  - Configurable slide interval timer (e.g. 3s, 5s, 10s, 15s).
  - Auto-loop option (restarts from Slide 1 upon reaching the end).
  - Pause/resume toggle on screen tap.

### 2.6 Landscape Presentation Lock
- **Objective:** Instant full-screen presentation mode for tablets or screen casting.
- **Features:**
  - One-tap button to force landscape screen orientation.
  - Auto-restore to sensor orientation when exiting presentation mode.

---

## 3. Productivity & Export Tools

### 3.1 Export Presentation to PDF
- **Objective:** Convert presentation decks to portable PDF files offline.
- **Features:**
  - Render each slide to `android.graphics.pdf.PdfDocument`.
  - Preserve original presentation aspect ratios (16:9 widescreen or 4:3 standard).
  - Save to device storage or immediately share via `Intent.ACTION_SEND`.

### 3.2 Export Slide as High-Res Image (PNG/JPEG)
- **Objective:** Save individual slides as images.
- **Features:**
  - Render the active slide at high resolution (1920x1080 or 3840x2160).
  - Save directly to Pictures/Gallery or copy to clipboard.

### 3.3 Print Presentation
- **Objective:** Print slide decks over Wi-Fi / Cloud Print.
- **Features:**
  - Connect to Android's `PrintManager` via `PrintDocumentAdapter`.
  - Configurable layout options (1 slide per page, 2 slides per page with notes, or 4 slides per page handouts).

### 3.4 Slide Bookmarks
- **Objective:** Save important slides for quick reference.
- **Features:**
  - Add bookmarks with custom labels and timestamps using `DocumentBookmarkDao`.
  - Bookmarks drawer listing all saved slide points for instant jumping.

### 3.5 Text Outline Skimmer
- **Objective:** Quick text-only overview of presentations.
- **Features:**
  - Extract all titles, body text, and bullets into a clean Markdown-like reading view.
  - Skim presentation content without rendering images or graphic layouts.

---

## 4. Security & Compatibility

### 4.1 Password-Protected Presentations
- **Features:**
  - Detect encrypted presentations (`EncryptedDocumentException`).
  - Present LiteView's secure password prompt sheet.
  - Decrypt via Apache POI's `Biff8Encryption` (PPT) or `StandardEncryption` (PPTX).

### 4.2 Error Handling & Corrupted Slide Fallback
- **Features:**
  - Isolated try-catch per slide: if slide 5 has corrupted XML or unreadable media, display a placeholder card and render slides 1-4 and 6-10 normally without failing the entire deck.
