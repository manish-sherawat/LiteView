# Google Play Store Publishing Guide - LiteView

## 📱 App Metadata Summary
- **App Name**: LiteView
- **Package Name**: `com.nexus.nexusdocs`
- **Version Name**: `2.5.2`
- **Version Code**: `252`
- **Target SDK**: 35 (Android 15)
- **Min SDK**: 26 (Android 8.0)
- **Primary Category**: Tools / Productivity
- **Support Contact Email**: `sherawat003@gmail.com`
- **Privacy Policy URL**: `https://manish-sherawat.github.io/LiteView/privacy.html` *(or your custom domain/Vercel URL)*

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

## 📋 Complete Google Play Console Questionnaires & Answers

Use these exact answers when filling out the mandatory **"App Content"** section in your Google Play Console dashboard.

### 1. Privacy Policy Link
- **URL Field**: `https://manish-sherawat.github.io/LiteView/privacy.html`
- **Local File Reference**: [website/privacy.html](file:///d:/Kotlin/Dex%20Files/NexusDocsViewer/website/privacy.html)

### 2. App Access Questionnaire
- **Question**: Does your app have restricted functionality based on login, credentials, or subscriptions?
- **Answer**: **No, all functionality is available without restrictions.**

### 3. Ads Questionnaire
- **Question**: Does your app contain advertisements?
- **Answer**: **No, my app does not contain ads.**

### 4. Content Rating Questionnaire (IARC)
- **Category**: Utility / Productivity / Tools
- **Email Address**: `sherawat003@gmail.com`
- **Questionnaire Answers**:
  - Does the app contain violence? $\rightarrow$ **No**
  - Does the app contain sexual content or nudity? $\rightarrow$ **No**
  - Does the app contain profanity or crude humor? $\rightarrow$ **No**
  - Does the app contain controlled substances/drugs? $\rightarrow$ **No**
  - Does the app allow users to interact or exchange content online? $\rightarrow$ **No**
  - Does the app share user physical location? $\rightarrow$ **No**
  - Does the app allow purchasing digital goods? $\rightarrow$ **No**
- **Expected Rating**: **PEGI 3 / Everyone (3+)**

### 5. Target Audience & Content
- **Target Age Groups**: **13+, 16-17, 18 and older**
- **Could your store listing unintentionally appeal to children?**: **No**

### 6. News App Questionnaire
- **Question**: Is your app a news app?
- **Answer**: **No**

### 7. COVID-19 Contact Tracing & Status App
- **Answer**: **My app is not a publicly available COVID-19 contact tracing or status app.**

### 8. Data Safety Questionnaire (Detailed Answers)
- **Data Collection & Sharing**:
  - Does your app collect or share any of the required user data types? $\rightarrow$ **No**
  - Is all user data collected processed locally on device? $\rightarrow$ **Yes**
- **Security Practices**:
  - Is user data encrypted in transit? $\rightarrow$ **N/A (No data transmitted over the internet)**
  - Do you provide a way for users to request data deletion? $\rightarrow$ **Yes** *(Users can delete any local file, annotation, or app data anytime directly on their device settings or file manager; no server accounts exist)*

### 9. Storage / All Files Access Permission (`MANAGE_EXTERNAL_STORAGE`)
- **Core Functionality Declaration**: Document Reader, Manager & Camera Scanner.
- **Justification Statement**: 
  > "LiteView is a local document reader and scanner. Its core feature is allowing users to search, open, read, annotate, edit, and organize PDF, Word, Excel, PowerPoint, and Text files stored across local device storage and external SD cards."

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
4. **Complete App Content Questionnaires**:
   - Use the **Questionnaires & Answers** section above to complete all 9 policy declarations.
5. **Create Production / Internal Release**:
   - Navigate to **Production** (or **Testing > Internal testing**).
   - Click **Create new release**.
   - Upload `app-release.aab`.
   - Add Release Notes:
     ```text
     LiteView v2.5.2 Update:
     - Improved PDF rendering performance and smooth vector zooming.
     - Enhanced annotation tools, sticky notes, and digital signatures.
     - Upgraded document scanner quality and multi-page export.
     - Bug fixes, performance optimizations, and UI polish.
     ```
6. **Submit for Review**: Click **Review release** and submit your app for Google Play review.
