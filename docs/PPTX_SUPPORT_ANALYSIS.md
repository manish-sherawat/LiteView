# Architectural Analysis: Adding PPTX Document Support to LiteView (NexusDocsViewer)

## 1. Executive Summary

LiteView currently features high-performance viewers for **PDF** (via MuPDF 1.23), **Word / DOCX / DOC** (via Apache POI HWPF & XWPF), **Excel / XLSX / XLS** (via Apache POI WorkbookFactory), and **Text / Code** (via custom zero-copy streaming engine).

Adding **PowerPoint (.pptx, .ppt)** presentation support is the final major milestone needed to make LiteView a complete, universal offline Office document workstation.

This document analyzes the viable architectural approaches, provides a multi-dimensional comparison matrix, and recommends the optimal strategy for the codebase.

---

## 2. Technical Context & Existing Codebase Assets

Before evaluating options, several key assets already present in this project should be considered:

1. **Apache POI Already Integrated:**
   - `:feature:reader-office` already includes `org.apache.poi:poi:5.2.5`, `poi-ooxml`, and `poi-scratchpad`.
   - POI includes `org.apache.poi.xslf.usermodel.XMLSlideShow` for `.pptx` and `org.apache.poi.hslf.usermodel.HSLFSlideShow` for legacy `.ppt`.
2. **MuPDF 1.23 Already Integrated:**
   - `:feature:reader-pdf` has a production-ready rendering pipeline with zoom, pan, thumbnail strip, dark mode, rotation, and bookmarks.
3. **Android Platform Constraint (Missing AWT):**
   - Standard desktop Apache POI renders slides via `slide.draw(Graphics2D)`. Because standard Android does **not** provide `java.awt.Graphics2D`, direct calls to `slide.draw()` throw `NoClassDefFoundError` unless custom rendering or a shim is used.

---

## 3. Evaluated Approaches

```mermaid
graph TD
    A[PPTX File Input] --> B{Integration Strategy}
    B -->|Option 1| C[Apache POI XSLF + Native Compose Model]
    B -->|Option 2| D[Apache POI -> PDF Conversion + MuPDF Engine]
    B -->|Option 3| E[Apache POI -> HTML5 / Reveal.js + WebView]
    B -->|Option 4| F[Lightweight OpenXML SAX/StAX Parser]
    B -->|Option 5| G[Embedded C++ Engine LibreOfficeKit/NDK]
    B -->|Option 6| H[Commercial SDK Aspose/Foxit/ComPDFKit]

    C --> OUT1[Native Compose UI: HorizontalPager / Slides Grid]
    D --> OUT2[Reuses Existing MuPDF Reader Screen]
    E --> OUT3[In-app WebView with CSS transitions]
    F --> OUT4[Ultra-fast text/image extract into Compose]
    G --> OUT5[Heavy APK +40MB, High Maintenance]
    H --> OUT6[Proprietary License Costs $1k-$5k+/yr]
```

### Option 1: Apache POI XSLF + Structured Compose Presentation Model (Recommended)
* **Mechanism:** Open the file with `XMLSlideShow(stream)`. Extract slide dimensions (16:9 vs 4:3), background colors, shapes, text boxes (`XSLFTextShape`), bullet hierarchies, tables (`XSLFTable`), and images (`XSLFPictureShape`). Map these into clean Kotlin data classes:
  ```kotlin
  data class PresentationSlide(
      val index: Int,
      val title: String?,
      val elements: List<SlideElement>,
      val notes: String? = null
  )
  ```
  Render slides natively in Jetpack Compose using `HorizontalPager` or a vertical thumbnail/slide card list. Element positions are scaled proportionally to screen dimensions using Compose `Box` and `Modifier.layout()`.
* **Pros:**
  - **Zero new dependencies:** Uses POI 5.2.5 already present in the module.
  - **Fluid Native UI:** Supports Compose gestures (pinch-zoom, swipe, double-tap, slide switcher drawer).
  - **100% Offline & Private:** Fully air-gapped.
  - **No AWT dependency:** Bypasses `slide.draw(Graphics2D)`.
* **Cons:**
  - Complex 3D smart shapes or proprietary animations are simplified into static shapes/text.

---

### Option 2: Serverless PPTX to PDF Converter + MuPDF Viewer
* **Mechanism:** Parse the PPTX via POI (or lightweight XML parser), draw slide layouts into Android's native `android.graphics.pdf.PdfDocument` or `pdfbox-android` (already in `libs.versions.toml`), write a temporary `.pdf` cache file, and open it directly in the existing `PdfReaderScreen`.
* **Pros:**
  - **Instant feature completeness:** Immediately inherits page navigation, pinch-to-zoom, page jumping, rotation, bookmarks, search, and printing from the battle-tested MuPDF engine.
  - **Also enables a new tool:** Adds a "PPTX to PDF" converter to the Tools screen.
* **Cons:**
  - Initial 1–2 second conversion latency before rendering starts on large decks.
  - Static view only (no slide speaker notes pane or editing capabilities).

---

### Option 3: Apache POI -> HTML5 / Reveal.js in WebView
* **Mechanism:** Parse PPTX slides and export them as an HTML5 slide deck with CSS styling, embedded images (base64 or local cache URIs), and render inside an Android `WebView`.
* **Pros:**
  - Consistent with how Word (.docx) is currently rendered in `OfficeReaderViewModel.kt`.
  - CSS makes layout formatting, responsive sizing, and dark-mode styling straightforward.
* **Cons:**
  - WebView overhead and higher memory consumption.
  - Clunky gesture feel compared to pure Compose gestures.

---

### Option 4: Custom OpenXML (ZIP + XmlPullParser / StAX)
* **Mechanism:** A `.pptx` file is an ECMA-376 OOXML package (ZIP file containing `ppt/slides/slide*.xml`, `ppt/media/`, `ppt/presentation.xml`). Unzip on-the-fly and stream-parse with Android's built-in `XmlPullParser` or `aalto-xml`.
* **Pros:**
  - Ultra-fast parsing speed (< 90ms).
  - Minimal memory usage (< 15MB peak).
  - Completely independent of Apache POI.
* **Cons:**
  - Requires writing custom XML mapping for Microsoft OpenXML shapes (`p:spTree`, `p:sp`, `a:r`, `a:t`, `a:xfrm`, `a:off`, `a:ext`). Significant initial dev effort.

---

### Option 5: Embedded C++ Suite (LibreOfficeKit / Calligra via NDK)
* **Mechanism:** Compile LibreOffice Core into native Android `.so` libraries and bridge via JNI.
* **Pros:**
  - Desktop-grade fidelity for all Microsoft Office formats.
* **Cons:**
  - **APK Bloat:** Increases APK size by 40–80 MB.
  - High compilation and ABI maintenance overhead (`arm64-v8a`, `armeabi-v7a`, `x86_64`).

---

### Option 6: Commercial Mobile Office SDKs (Aspose, Foxit, ComPDFKit)
* **Mechanism:** License a commercial document SDK.
* **Pros:**
  - Commercial support, out-of-the-box pixel-perfection.
* **Cons:**
  - Expensive recurring cost ($1,500 – $10,000+ per year).
  - Closed-source, may require license keys or activation checks that compromise the air-gapped promise.

---

## 4. Comprehensive Comparison Matrix

| Evaluation Dimension | Option 1: POI + Native Compose | Option 2: POI to PDF + MuPDF | Option 3: POI HTML + WebView | Option 4: Custom OpenXML | Option 5: NDK LibreOfficeKit | Option 6: Commercial SDK |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Visual Fidelity** | High (Text, Tables, Imgs) | Medium-High | Medium | Medium (Text & Imgs) | Very High (99%) | Very High (99%) |
| **New Dependencies** | **None** (Uses POI 5.2.5) | **None** (POI + MuPDF) | **None** | **None** (built-in XML) | Heavy C++ NDK libs | Heavy SDK binaries |
| **APK Size Impact** | **0 MB** | **0 MB** | **0 MB** | **0 MB** | +40 to +80 MB | +10 to +25 MB |
| **Rendering Latency** | < 180 ms | 800ms - 2.0s (first load) | < 250 ms | **< 80 ms** | 1.0s - 3.0s | < 200 ms |
| **Memory Footprint** | ~30 - 45 MB | ~35 - 50 MB | ~50 - 75 MB | **< 18 MB** | ~90 - 150 MB | ~40 - 60 MB |
| **Gesture & UI Polish** | **Native Compose (Top)** | High (MuPDF Zoom/Pan) | Fair (WebView) | **Native Compose (Top)** | Fair (NDK Surface) | High |
| **Implementation Effort**| Medium (~2–3 days) | Low-Medium (~1–2 days) | Medium (~2 days) | High (~5–7 days) | Very High (~2–3 weeks) | Low (~1 day) |
| **Licensing / Cost** | Apache 2.0 (Free) | Apache 2.0 + AGPL (Free) | Apache 2.0 (Free) | Custom (Free) | LGPL / MPL (Free) | **Paid ($1k - $10k/yr)** |
| **Air-gapped / Offline** | **100% Offline** | **100% Offline** | **100% Offline** | **100% Offline** | **100% Offline** | Often needs phone-home |

---

## 5. Architectural Recommendation

### Recommended Primary Approach: **Option 1 (POI XSLF + Native Compose Model)**
* **Why it wins:**
  1. The app already bundles Apache POI 5.2.5 in `:feature:reader-office`. No APK bloat is incurred.
  2. Bypassing Java AWT by converting POI's `XSLFSlide` objects into a clean Kotlin data model (`PresentationSlide`, `SlideText`, `SlideImage`, `SlideTable`) avoids all Android compatibility pitfalls.
  3. Jetpack Compose provides a fluid user experience: horizontal swiping with page indicator, slide overview grid, speaker notes bottom sheet, and full-screen presentation mode.

### Recommended Secondary / Complementary: **Option 2 (PPTX to PDF Tool)**
* Can be implemented alongside Option 1 as a dedicated tool under `feature/dashboard/tools/` ("PowerPoint to PDF"), mirroring the existing "Word to PDF" and "Image to PDF" features.

---

## 6. Implementation Blueprint (Step-by-Step)

### Step 1: Core Navigation & Document Type Registration
In `core/src/main/kotlin/com/nexus/core/navigation/NavigationContract.kt`:
1. Add `PPTX` to `enum class DocumentType`:
   ```kotlin
   enum class DocumentType {
       PDF, DOCX, XLSX, TXT, PPTX, UNKNOWN;
   }
   ```
2. Update extension & MIME type matching:
   - Extensions: `pptx`, `ppt`
   - MIME Types: `application/vnd.openxmlformats-officedocument.presentationml.presentation`, `application/vnd.ms-powerpoint`

### Step 2: Manifest & Intent Filters
In `app/src/main/AndroidManifest.xml`:
- Add `<data android:mimeType="application/vnd.openxmlformats-officedocument.presentationml.presentation" />`
- Add `<data android:mimeType="application/vnd.ms-powerpoint" />`

### Step 3: Office Reader Parser Integration
In `feature/reader-office/src/main/kotlin/com/nexus/feature/reader/office/OfficeReaderViewModel.kt`:
- Add `parsePptx(stream: InputStream): OfficeReaderUiState`
- Use `XMLSlideShow(stream)` to extract slides, titles, shapes, text runs, and images.
- Emit `OfficeReaderUiState.PptxReady(slides = parsedSlides)`.

### Step 4: Compose UI Implementation
In `feature/reader-office`:
- Create `PptxReaderContent.kt`:
  - `HorizontalPager` for presentation mode.
  - Thumbnail bottom sheet for rapid jumping across slides.
  - Aspect ratio box (`16:9` / `4:3`) with pinch-to-zoom.
