# Google Play Store Publishing Guide - LiteView

## 📱 App Metadata Summary
- **App Name**: LiteView
- **Package Name**: `com.nexus.nexusdocs`
- **Version Name**: `2.5.2`
- **Version Code**: `252`
- **Target SDK**: 35 (Android 15)
- **Min SDK**: 26 (Android 8.0)
- **Primary Category**: Tools / Productivity

---

## 📦 Build Instructions

### Generate Android App Bundle (.aab)
To generate the production `.aab` file for Google Play:

```cmd
.\gradlew.bat bundleRelease
```

### Output File Location
- **Path**: `app/build/outputs/bundle/release/app-release.aab`
- **File Size**: ~39.8 MB

---

## 📝 Play Store Listing Content Template

### Short Description (max 80 chars)
Fast, lightweight document viewer & scanner for PDF, Office files, and text docs.

### Full Description (max 4000 chars)
LiteView is a fast, lightweight, and privacy-focused document reader and scanner for Android. View, annotate, scan, and manage all your documents offline with maximum efficiency and high performance.

Key Features:
- 📄 **PDF Reader & Annotator**: View PDFs smoothly, add text highlights, freehand drawings, sticky notes, and signatures.
- 📊 **Office Document Viewer**: Open Word (.docx), Excel (.xlsx), and PowerPoint (.pptx) documents seamlessly.
- 📝 **Text Reader**: Quick viewing for plain text (.txt), Markdown (.md), and code files.
- 📷 **Built-in Document Scanner**: Scan physical documents into clean, multi-page PDF files directly using your camera.
- 📁 **Offline File Management**: Fast local file browsing with recent documents access, search, and bookmarking.
- ⚡ **Modern & Clean UI**: Built with Jetpack Compose, supporting dark mode and dynamic accent colors.

---

## 🔒 Play Store Content & Policy Checklist

### 1. Privacy Policy
- Declare that document processing and scanning happen **100% locally on the device**.
- No personal documents or files are uploaded to external servers.

### 2. Permissions Declarations
- `READ_MEDIA_IMAGES` / `READ_MEDIA_DOCUMENTS` / `MANAGE_EXTERNAL_STORAGE`: Required to locate and open local documents on the device.
- `CAMERA`: Required only for the document scanner feature.

### 3. Data Safety Form Answers
- **Data Collection**: No personal user data collected or shared with third parties.
- **Data Encryption**: Local files are accessed securely via standard Android ContentProviders.

---

## 🚀 Step-by-Step Google Play Console Publishing

1. **Log in to Play Console**: Go to [Google Play Console](https://play.google.com/console).
2. **Create App**:
   - App Name: `LiteView`
   - Language: `English (US)`
   - Type: `App` | Price: `Free`
3. **Set Up Store Listing**:
   - Copy & paste the **Short Description** and **Full Description** provided above.
   - Upload **App Icon**: 512 x 512 px PNG.
   - Upload **Feature Graphic**: 1024 x 500 px PNG.
   - Upload **Phone & Tablet Screenshots**: At least 2 screenshots showing PDF reader and Scanner screens.
4. **Complete Policy Declarations**:
   - Complete Content Rating Questionnaire.
   - Fill Target Audience (13+ or General).
   - Complete Data Safety Form.
5. **Create Production / Internal Release**:
   - Navigate to **Production** (or **Testing > Internal testing**).
   - Click **Create new release**.
   - Upload `app-release.aab`.
   - Add Release Notes:
     ```text
     LiteView v2.5.2 Update:
     - Improved PDF rendering performance and smooth zooming.
     - Enhanced annotation tools and signature saving.
     - Upgraded document scanner quality and export functionality.
     - Bug fixes and UI polish.
     ```
6. **Submit for Review**: Click **Review release** and submit your app for Google Play review.
