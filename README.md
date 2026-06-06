<h1 align="center">
  <img src="assets/logo.png" width="80" alt="LiteView Logo" width="80" alt="LiteView Logo"/><br/>
  LiteView
</h1>

<p align="center">
  <b>A fast, offline, and beautifully animated Android document viewer and scanner.</b>
</p>

---

## 📖 Overview

**LiteView** (formerly NexusDocs) is designed to be a premium, lightweight alternative to bloated document viewers. Built entirely with modern Android technologies (Jetpack Compose, Kotlin, Coroutines), it focuses on providing a highly polished, distraction-free reading experience while maintaining complete offline privacy. 

Whether you need to read an Excel sheet, scan a physical document to PDF, or manage your local files, LiteView handles it seamlessly without relying on third-party cloud services.

## ✨ Key Features

### 📄 Universal Document Support
Natively read the most common document formats right on your device:
- **PDF (.pdf)**: High-performance rendering via **MuPDF**.
- **Office (.docx, .xlsx)**: Offline parsing and rendering powered by **Apache POI**.
- **Text (.txt)**: Clean, customizable text reading interface.

### 📸 Built-In Camera Scanner
A high-quality document scanner built directly into the app using **CameraX**. 
- Capture physical documents effortlessly.
- Crop, enhance, and digitize images.
- Save directly as universally compatible PDFs.

### 🔄 In-App Auto-Updater
Never miss an update. LiteView features a custom built `AppUpdater` engine that:
- Automatically queries the GitHub Releases API for new versions.
- Displays comprehensive, interactive changelogs.
- Downloads the latest APK securely over the air using Android's `DownloadManager`.

### 🗂 Intelligent File Management
- **Recent Documents**: Automatically indexes your recently opened files using a local **Room Database**.
- **View Modes**: Toggle between a beautiful, animated Grid view and a detailed List view.
- **Sorting**: Sort files alphabetically, by size, or by date modified.

---

## 🎨 The Nexus Design System

Rather than relying on standard Material Design, LiteView utilizes a completely custom UI framework called **NexusTheme** to achieve a distinctive, premium feel inspired by glassmorphism and modern UI trends.

### 1. Typography
Powered exclusively by the **Plus Jakarta Sans** font family, delivering crisp, highly readable text across all weights, ensuring a modern aesthetic.

### 2. Custom Components
Almost all standard Compose UI elements are wrapped in bespoke components to guarantee absolute design consistency:
- `NexusSurface` & `NexusCard`: For elevated, perfectly rounded containers.
- `NexusButton` & `NexusTextField`: For sleek inputs and interactions.
- `NexusTopBar`: For immersive, translucent navigation headers.

### 3. Micro-Animations
The UI is engineered to feel alive and responsive:
- **`springBounceClick()`**: A custom modifier that applies a deeply satisfying physics-based bounce effect to buttons and list items when tapped.
- **`fadeSlideIn()`**: Screens and dialogs smoothly fade and slide into view.
- **Shimmer Effects**: Beautiful loading skeletons during file indexing.

---

## 🏛 Architecture & Tech Stack

LiteView strictly adheres to modern Android development best practices, ensuring scalability, testability, and peak performance.

### Architecture
- **MVVM (Model-View-ViewModel)**: Strict separation of concerns.
- **Unidirectional Data Flow (UDF)**: UI states are represented by immutable data classes managed by `StateFlow`.
- **Modularization**: The codebase is cleanly split into `app`, `core`, and independent `feature` modules (`dashboard`, `scanner`, `reader-pdf`, etc.).

### Core Libraries
- **Jetpack Compose (BOM)**: The entire presentation layer is built declaratively.
- **Dagger Hilt**: For robust, compile-time Dependency Injection.
- **Kotlin Coroutines & Flow**: For asynchronous operations and reactive streams.
- **Room Database**: For robust, offline local caching of document metadata and bookmarks.
- **Coil**: For fast, memory-efficient asynchronous image loading.
- **CameraX**: For handling complex camera lifecycles during scanning.

---

## 🚀 Setup & Installation

To build LiteView locally on your machine:

1. **Clone the repository:**
   ```bash
   git clone https://github.com/manish-sherawat/LiteView.git
   ```
2. **Open the Project:**
   Open the cloned folder in the latest version of **Android Studio**.
3. **Sync Gradle:**
   Allow Android Studio to download the necessary dependencies via the Gradle sync.
4. **Build and Run:**
   Select the `app` run configuration and deploy to an emulator or a physical device running Android 8.0 (API 26) or higher.

---

## 🤝 Contributing

Contributions are always welcome! If you have a feature request, bug report, or want to improve the UI further:
1. Fork the Project.
2. Create your Feature Branch (`git checkout -b feature/AmazingFeature`).
3. Commit your Changes (`git commit -m 'Add some AmazingFeature'`).
4. Push to the Branch (`git push origin feature/AmazingFeature`).
5. Open a Pull Request.

---
*Developed with a focus on speed, offline privacy, and unmatched aesthetics.*
