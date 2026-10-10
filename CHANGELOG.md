Version 4.0.0

🚀 Major Updates & Features
- Introduced new Drag-and-Drop sorting system for the Image to PDF tool.
- Revamped the Image to PDF preview area to display images in a modern Grid style.
- Populated the Dashboard with upcoming tools: Word to PDF, Excel to PDF, Text to PDF, Merge PDF, Split PDF, Protect PDF, Rotate PDF, Compress PDF.
- Added badge system to tools ("Beta" for Image to PDF, "Coming soon" for others).

🎨 UI & Design Improvements
- Redesigned the Tools Tab entirely, enforcing a consistent 2-column Grid layout.
- Added custom premium SVG icons for the Tools tab in the bottom navigation bar.
- Restyled the Image to PDF upload area for a premium look with better feedback states (Idle, Processing, Success, Error).
- Disabled navigation for "Coming soon" tools, displaying a helpful toast message instead.

🐛 Bug Fixes & Optimizations
- Drastically improved PDF Viewer rendering quality (increased resolution by 2.5x for crisp text when zooming).
- Optimized PDF memory cache to prevent OutOfMemory crashes on high-res rendering.
- Fixed an issue where the old Image to PDF UI (arrows) persisted.

Version 3.1.5
Default language – en-GB
✨ New Features
- Smart Bookmarks 
- Table of Contents 
- Document Insights 
- Recycle Bin 
- Richer Document Rendering

🎨 Design & Toolbar
- Redesigned toolbar across all readers
- Toolbar icons are now labeled for clarity
- Refreshed UI for the 3-dot menu across all viewers

🐛 Bug Fixes & Improvements
- Fixed layout and loading issues on the home dashboard
- Resolved crashes and glitches in the 


Version 3.0.4

✨ Improvements:
🎨 Settings page redesigned to match app theme
🎨 Refreshed 3-dot menu on Home screen
🏷️ Redesigned tag creation & Tag Manager screens
📄 Redesigned File Details sheet with metadata & copy location
🖐️ Fixed overly sensitive swipe-to-share/delete gestures
✨ Updated empty states & quick-start presets

---

## [v3.0.3]

Version 3.0.3

✨ What's new:
🏷️ New Tag System — create custom tags with 11 colors & 24 emoji icons
📂 Tag Library Manager to rename, recolor & organize tags in one place
🎯 Live tag colors on document cards & dashboard filters
🎨 Fresh new app icon
📱 Improved layouts for tablets & foldables

🐞 Bug fixes:
🧭 Fixed nav bar icon not displaying correctly on home screen
⌨️ Fixed dialogs overflowing on smaller screens when keyboard is open
🔄 Fixed tag rename/delete sometimes not syncing across all documents

  * Added rich text viewer support and MediaStore scanning for Logs (`.log`), Markdown (`.md`, `.rst`), JSON (`.json`, `.jsonl`, `.geojson`), XML/HTML/SVG, Configs (`.yaml`, `.toml`, `.ini`), CSV/TSV, and Code/Scripts (`.js`, `.css`, etc.).
  * Added syntax highlighting engine with theme-aware palettes.
* **Refreshed Navigation Bar**:
  * Fresh vector icons for Home and Settings with smooth morphing from outlined to filled state upon selection.

---

## [v3.0.1]

### 🌟 Release Highlights
LiteView 3.0.1 brings the brand-new **Modern Paper Document Card** design for List and Grid views, enhanced multi-selection controls, tighter card spacing, and live PDF rendering with stylized Office formats (DOCX, XLSX, TXT).

---

### 🎨 What's New & Improvements
* **Modern Document Card Design**:
  * **Smart Thumbnail System**: Live rendered PDF page thumbnails alongside stylized paper sheets with format lines and folded corners for DOCX, XLSX, and TXT files.
  * **Improved Grid View**: Taller, realistic portrait aspect ratio with reading progress bars and format micro-badges.
  * **Tighter Layout**: Reduced card gaps and clean edge-to-edge alignment.
  * **Full Multi-Selection**: Seamless long-press and tap multi-select flow for bulk operations.

---

## [v3.0.0]

### 🌟 Release Highlights
LiteView 3.0.0 is a major update introducing brand-new **Home Screen Widgets**, real-time **Widget Transparency & Blur customization**, refreshed **Glassmorphic UI menus**, and significant **performance optimizations** with a much smaller app size.

---

### 📱 New Features

* **Home Screen Widgets**:
  * **Quick Actions Bar**: Fast 1-tap shortcuts to Scan documents, Search files, or Browse storage.
  * **Recent Documents**: Scrollable card view of your recently opened files with format badges and timestamps.
  * **Starred Files**: Instant access to your favorite and pinned documents directly from your home screen.
  * **Universal Document Hub**: All-in-one command center combining quick action buttons with your recent files.
  * **Quick Scanner Tile**: Compact 1-tap shortcut to launch the camera document scanner immediately.
  * **Fluid Resizing**: All widgets dynamically adapt their layouts as you resize them from compact pills to full-screen hubs.

* **Widget Customization & Live Preview**:
  * **Live Preview Setup**: Easily preview how your widget will look on your wallpaper before adding it.
  * **Adjustable Transparency**: Customize background opacity from 0% (crystal clear) to 100% (solid).
  * **Frosted Glass Blur**: Enable or disable acrylic glass refraction on supported launchers.
  * **Theme Tinting**: Choose between Auto (matches system), Light, or Dark styling.
  * **In-App Widget Settings**: Fine-tune all placed widgets anytime from the Settings screen.

---

### 🎨 UI & UX Improvements

* **Glassmorphic Sort Menu**:
  * Replaced standard popups with a floating frosted glass menu featuring dedicated sort icons (Date, Name, Type, Size) and smooth selection animations.
* **Streamlined Document Cards**:
  * Cleaned up document cards on the home screen for a more modern and uncluttered look.
* **Improved Storage Permission Screen**:
  * Fixed button positioning and hidden navigation bars during permission requests so buttons are never obstructed.
* **New Static Empty States**:
  * Replaced heavy animations with crisp, lightweight illustration states.
* **Refined Theme Options**:
  * Set Light mode as the clean default theme with tailored accents (Light, Sepia, Forest, and Sunset).

---

### ⚡ Performance & Optimizations

* **Drastically Reduced App Size**:
  * Stripped out heavy, unused libraries to make the app significantly lighter and faster to download.
* **Instant Widget Updates**:
  * Zero-battery-drain background architecture that only refreshes when you open, star, or scan documents.
* **Smoother Animations & Scrolling**:
  * Faster file loading and silky smooth transitions across all document readers.

---

### 🛠️ Bug Fixes

* Fixed navigation bar overlap on modern gesture-navigation devices.
* Fixed pop-up menu alignment and styling on the home dashboard.
* Improved reliability when opening documents directly from third-party apps and file managers.
