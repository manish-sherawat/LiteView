# LiteView

A fast, offline, and beautifully animated Android document viewer and scanner. LiteView is designed to be a premium, lightweight alternative for viewing and digitizing documents with a focus on a highly polished user interface and micro-animations.

## 🌟 Key Features

*   **Universal Document Viewer**: Natively read PDF, DOCX, XLSX, and TXT files completely offline without relying on third-party cloud services.
*   **Integrated Camera Scanner**: High-quality document scanner built directly into the app using CameraX. Crop, enhance, and save physical documents instantly as PDFs.
*   **Premium UI & Animations**: Built from the ground up with a custom design system. Features silky smooth transitions, spring-bounce click effects, and fade-slide entrance animations for a delightful user experience.
*   **In-App Auto Updater**: Self-updating mechanism that checks this GitHub repository for new releases, fetches the changelog, and securely downloads and installs the latest APK over the air.
*   **Document Management**: Automatic indexing of recent documents, a beautiful file grid/list toggle, and advanced sorting capabilities.

## 🏛 Architecture & Core

LiteView follows modern Android development best practices:
*   **UI Toolkit**: 100% Jetpack Compose.
*   **Architecture**: MVVM (Model-View-ViewModel) utilizing Unidirectional Data Flow (UDF).
*   **Dependency Injection**: Dagger Hilt.
*   **Concurrency**: Kotlin Coroutines & StateFlow.
*   **Local Storage**: Room Database for caching recent files and bookmarks.

## 🎨 Design System (`core/theme`)

Instead of standard Material Design, LiteView utilizes a completely custom `NexusTheme` to achieve its distinct look:
*   **Typography**: Powered by the modern `Plus Jakarta Sans` font family for crisp, highly readable text across all weights.
*   **Components**: Custom wrappers for almost all UI elements (`NexusButton`, `NexusCard`, `NexusText`, `NexusTopBar`) to ensure design consistency and effortless theming.
*   **Micro-Animations**: Custom modifiers like `springBounceClick()` and `fadeSlideIn()` are injected throughout the app to make the interface feel alive and responsive.

## 📚 Major Libraries Used

*   **AndroidX Compose BOM**: For the entire UI layer.
*   **CameraX**: For the advanced document scanning pipeline.
*   **MuPDF & Apache POI**: For parsing and rendering complex PDF and Office documents offline.
*   **Coil**: For fast, asynchronous image loading and caching.
*   **Hilt**: For managing dependencies across the `core`, `feature:dashboard`, and `feature:scanner` modules.

## 🚀 Building & Contributing

1. Clone the repository: `git clone https://github.com/manish-sherawat/LiteView.git`
2. Open the project in Android Studio.
3. Sync Gradle and run the `app` configuration.

---
*Developed with a focus on speed, privacy, and aesthetics.*
