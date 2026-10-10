<div align="center">

<img src="https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white" />
<img src="https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" />
<img src="https://img.shields.io/badge/Jetpack%20Compose-2024.12-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white" />
<img src="https://img.shields.io/badge/Version-3.1.1-orange?style=for-the-badge" />
<img src="https://img.shields.io/badge/Min%20SDK-26%20(Android%208.0)-blue?style=for-the-badge" />
<img src="https://img.shields.io/badge/License-MIT-green?style=for-the-badge" />

</div>

---

<div align="center">

<img src="assets/logo/icon_x512.png" width="180" style="border-radius: 20px;" alt="LiteView Logo" />

</div>

---

<div align="center">

<img src="assets/Banner_1024x500.png" width="100%" style="border-radius: 30px;" alt="LiteView Banner" />

</div>

---

<div align="center">

# LiteView — Document Viewer, Scanner & Annotation Suite

**A fast, elegant, and feature-rich Android document reader & annotation workspace.**
View PDFs, Office files, PowerPoint presentations, and text documents. Annotate PDFs with stamps, text boxes, and precision measurement rulers. Scan physical documents directly with your camera.

</div>

---

## ✨ Key Features

| Feature | Description |
|---|---|
| 📑 **PDF Reader & Viewer** | High-fidelity PDF rendering powered by **MuPDF 1.23** with page rotation, night modes, and search |
| 🧲 **Edge-Docking Toolbar** | Modular floating PDF toolbar with 4-edge smart snapping (**Left**, **Right**, **Top**, **Bottom**) and avatar mode |
| ✍️ **PDF Annotation Suite** | Rich annotation tools including Pen, Translucent Highlighter, Shapes (Rectangles, Ovals, Lines, Arrows) |
| 📝 **Free Text & Stamps** | Single-tap Free Text box insertion and 1-tap Business Stamps (**APPROVED**, **CONFIDENTIAL**, **DRAFT**, **FINAL**, **REJECTED**, **SIGN HERE**) |
| 📏 **Distance Measurement** | Precision blueprint ruler rendering end ticks and calculating live distance in centimeters (`%.1f cm`) |
| 🧹 **Target-Filtered Eraser** | Intelligent eraser engine supporting `Erase All`, `Pen Only`, `Highlighter Only`, and `Shapes Only` filter modes |
| 📽️ **PowerPoint Viewer (PPT/PPTX)** | Fast offline slide presentation reader with fullscreen present mode, thumbnail drawer, and dual-engine POI + LOKit bridge |
| 📊 **Word & Excel Suite** | View `.docx` and `.xlsx` documents via **Apache POI** with dark mode, freeze panes, and jump-to-cell |
| 📝 **Code & Text Viewer** | Clean and readable plain text file reader with syntax highlighting and word wrap toggles |
| 📸 **Document Scanner** | Scan physical pages to PDF or JPEG via **ML Kit Document Scanner** |
| 🔍 **OCR (Text Recognition)** | Extract text from scanned images using **ML Kit Text Recognition** |
| ⭐ **Universal Favorites** | Pin your most-used documents with instant gold star indicators across all reader toolbars |
| 📂 **Auto-Recording Recents** | Opening documents via SAF pickers or external intents auto-registers them in the Recent tab |
| 🎨 **Glassmorphic UI** | Luxury frosted glass surfaces, custom floating pill toolbars, and spring-animated micro-interactions |
| 🗂️ **Smart Dashboard** | View All, Recent, and Starred files with real-time list sync, sorting, and search |
| 🔲 **Multi-Select Actions** | Long-press to select multiple files, then share or delete them in bulk |
| 🏷️ **Tag System** | Create custom color + emoji tags, filter the dashboard by tag, and manage all tags in the Tag Library |
| 🗑️ **Permanent File Delete & Trash** | Safe file deletion with 10-second undo countdown and Recycle Bin management |
| 🌓 **Dark & Light Mode** | Full system theme support, AMOLED Deep Black, and reader background modes |
| 🔄 **In-App Updater** | Checks for new releases via GitHub REST API with session dismissal memory & in-app APK installer |
| 🔃 **Pull to Refresh** | Swipe down to re-scan the file system for new documents |

---

## 📱 Screenshots

<div align="center">

| Home & Dashboard | PDF Reader & Annotations | Word (DOCX) Reader | Excel (XLSX) Sheets |
|:---:|:---:|:---:|:---:|
| <img src="assets/screenshots/home.png" width="220" style="border-radius: 16px;" alt="Home Dashboard"/> | <img src="assets/screenshots/pdf_screen.png" width="220" style="border-radius: 16px;" alt="PDF Reader"/> | <img src="assets/screenshots/doc_screen.png" width="220" style="border-radius: 16px;" alt="Word Document Reader"/> | <img src="assets/screenshots/excel_screen.png" width="220" style="border-radius: 16px;" alt="Excel Spreadsheet Viewer"/> |
| *Smart file list, search, tags & recent history* | *High-fidelity viewer with edge-docked annotations* | *Clean typographic layout with mobile view* | *Full spreadsheet grid with formula & sheet navigation* |

</div>

---

## 🏗️ Architecture & Tech Stack

LiteView is built with a clean, scalable, multi-module architecture following **MVVM** and **Clean Architecture** principles.

### Module Structure

```
NexusDocsViewer/
├── app/                      # Application entry point, DI graph, Navigation host
├── core/                     # Shared UI design system, theme, navigation contracts, database
└── feature/
    ├── dashboard/            # Home screen, Recent/Starred/All file lists, Settings, Trash, Tags
    ├── reader-pdf/           # MuPDF PDF experience, annotation engine, edge-docking toolbar
    ├── reader-office/        # Apache POI Office document viewer (.docx, .xlsx)
    ├── reader-presentation/  # PowerPoint (.pptx, .ppt) viewer, LOKit NDK bridge, POI parser
    ├── reader-text/          # Plain text file reader with syntax highlighting
    └── scanner/              # ML Kit document scanning & OCR
```

### Tech Stack

| Layer | Library / Tool |
|---|---|
| **Language** | Kotlin 2.0.21 |
| **UI** | Jetpack Compose BOM 2024.12, Material 3 |
| **Architecture** | MVVM, multi-module, Clean Architecture |
| **Dependency Injection** | Hilt 2.53.1 |
| **Navigation** | Navigation Compose 2.8.5 |
| **Database** | Room 2.6.1 (recent documents, tags, trash history) |
| **Preferences** | DataStore Preferences 1.1.1 |
| **Async** | Kotlin Coroutines 1.9.0 + StateFlow |
| **PDF Engine** | MuPDF (fitz) 1.23.0 |
| **Office & PPT Engine** | Apache POI 5.2.5 + Native LibreOfficeKit Bridge |
| **ML / AI** | Google ML Kit — Document Scanner & Text Recognition |
| **Image Loading** | Coil 2.6.0 |
| **Animations** | Jetpack Compose Animation + Shared Element Transitions |
| **Permissions** | Accompanist Permissions 0.36.0 |

---

## 🚀 Getting Started

### Prerequisites

- **Android Studio** Hedgehog (2023.1.1) or newer
- **JDK 17**
- **Android SDK** with API Level 26+ installed
- **Android NDK** (version 25+ recommended) for native C++ bridge compilation

### Cloning & Running

```bash
# 1. Clone the repository
git clone https://github.com/manish-sherawat/LiteView.git

# 2. Open the project in Android Studio
# (File -> Open -> select the NexusDocsViewer folder)

# 3. Let Gradle sync finish

# 4. Run the app on a device or emulator (API 26+)
```

### Building a Release APK

```bash
# From the project root
./gradlew assembleRelease

# The signed APK will be located at:
# app/build/outputs/apk/release/app-release.apk
```

---

## 📁 Project Deep Dive

### `core` Module

The foundation of the app. Contains:
- **`NexusTheme`** — Custom design system (colors, typography, shapes, spacing)
- **`NexusComponents`** — Reusable UI components (`NexusSurface`, `NexusText`, `NexusButton`, `NexusTopBar`, `NexusCollapsingTopBar`, etc.)
- **`NexusBottomNav`** — Custom animated bottom navigation bar
- **`navigation/`** — Navigation contracts, `DocumentReaderRouter`, `DocumentType` enum
- **`preferences/`** — `UserPreferencesRepository` (DataStore-backed user settings)

### `feature/dashboard` Module

The main home screen workspace. Contains:
- **`DashboardScreen`** — File list (All / Recent / Starred tabs), search, sort, pull-to-refresh
- **`DashboardViewModel`** — Manages UI state, file scanning, multi-select mode
- **`FileListItem` & `FileGridItem`** — Animated file card components with high-res thumbnails
- **`FileOptionsDialog`** — Contextual action sheet (Share, Rename, Star, Details, Delete)
- **`RecycleBinScreen`** — Trash and file recovery management
- **`TagManagerScreen`** — Custom color and emoji tag categorization
- **`ToolsScreen`** — Built-in PDF tools (Images to PDF, Word to PDF)
- **`SettingsScreen`** — App settings, cache management, AMOLED mode, changelog viewer

### `feature/reader-pdf` Module

Full-featured PDF reader powered by **MuPDF**:
- **Smart Edge Drag-Docking Toolbar** (`PdfAnnotationPill`) — Snap to 4 screen edges with avatar collapse mode
- **Annotation Canvas Overlay** — Pen, Translucent Highlighter, Shapes (Rectangles, Ovals, Lines, Arrows)
- **Free Text Box & Business Stamps** — Interactive text dialogs and 1-tap review badges
- **Distance Measurement Ruler** — Real-time blueprint scale calculation in centimeters (`%.1f cm`)
- **Target-Filtered Eraser** — Filter modes (`Erase All`, `Pen Only`, `Highlighter Only`, `Shapes Only`)
- Page rotation synchronization (90°, 180°, 270°)
- Scroll position persistence and PDF thumbnail caching

### `feature/reader-presentation` Module

PowerPoint (`.pptx` & `.ppt`) presentation reader:
- **Zero-AWT Apache POI Parser** — Offline presentation parser extracting slides, titles, bullets, tables, and images without runtime crashes
- **Native LibreOfficeKit Bridge** — C++ JNI bridge (`libnexus_presentation_engine.so`) ready for precompiled LOKit desktop rendering
- **Luxury Presenter UI** — Floating slide badge, jump-to-slide dialog, bottom dock toolbar, thumbnail drawer, pinch/double-tap zoom, and fullscreen presentation mode

### `feature/reader-office` Module

Office document reader using **Apache POI**:
- Supports `.docx` and `.xlsx` formats
- Renders Word documents in a responsive HTML WebView with Dark Mode adaptation
- Renders Excel spreadsheets with interactive freeze panes, sheet tab switching, and cell jumping

### `feature/reader-text` Module

Plain text and source code viewer:
- Fast rendering of `.txt` files with line numbering and syntax highlighting
- Configurable word wrap and font scaling

### `feature/scanner` Module

Physical document scanning powered by **Google ML Kit**:
- Automatic edge detection, perspective correction, and image filters
- Saves output as **PDF** or **JPEG**
- OCR text extraction from scanned images using `TextRecognition`

---

## 🎨 Design System

LiteView uses a bespoke design language called **Nexus Design System**, implemented entirely in Jetpack Compose:

- **Glassmorphism** — Frosted glass effect on floating UI surfaces (multi-select action bar, floating snackbars, bottom docks)
- **Shared Element Transitions** — Hero animations from file card thumbnail to full reader
- **Spring-Based Micro-Interactions** — Bouncy, responsive press animations (`springBounceClick`) on all interactive elements
- **AMOLED Dark Mode** — Pitch-black dark mode for OLED displays
- **Document Accent Colors** — Each file type (PDF, DOCX, XLSX, PPTX, TXT) has its own curated accent color

---

## 🗺️ Roadmap & Tasks

See the detailed engineering documentation in the `docs/` folder:
- **[PPT_VIEWER_ROADMAP.md](docs/PPT_VIEWER_ROADMAP.md)** — In-depth architectural roadmap for PowerPoint rendering and presentation tools
- **[PPT_VIEWER_TASKS.md](docs/PPT_VIEWER_TASKS.md)** — Step-by-step engineering task tracker

Key upcoming features:
- [x] **PowerPoint (`.pptx` / `.ppt`) Viewer** ✅ v3.2.0
- [x] **Tag Library & Management** (custom color + emoji tags, filters, bulk delete) ✅ v3.0.3
- [x] **Recycle Bin & Safe Delete** ✅ v3.1.0
- [ ] **In-Slide Text Search for Presentations**
- [ ] **Speaker Notes Drawer for PPTX**
- [ ] **Export Presentation to PDF & High-Res PNG**
- [ ] **AI — Chat with Document** (Gemini API integration)
- [ ] **Cloud Backup Hub** (Google Drive / Dropbox sync)

---

## 📄 License

This project is licensed under the **MIT License** — see the [LICENSE](LICENSE) file for details.

---

<div align="center">

Made with ❤️ by **Manish Sherawat**

**[GitHub](https://github.com/manish-sherawat) · [Report a Bug](https://github.com/manish-sherawat/LiteView/issues) · [Request a Feature](https://github.com/manish-sherawat/LiteView/issues)**

</div>
