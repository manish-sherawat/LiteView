# Project: NexusDocsViewer - OOXML to PDF In-Memory Conversion Pipeline

## Overview
Replace the HTML/WebView Word rendering pipeline with an enterprise OOXML-to-PDF conversion service and in-memory PDFium view layer for DOCX and DOC files.

## Goals
1. **Conversion Pipeline**: Convert DOCX/DOC files into high-fidelity PDF byte streams upon opening via headless LibreOfficeKit (primary) or native OOXML-to-PDF wrapper (fallback).
2. **In-Memory PDFium Rendering**: Render the PDF byte stream directly in memory with zero permanent disk footprint using native PDFium (`android.graphics.pdf.PdfRenderer`), preserving 100% desktop layout, fonts, and typography.
3. **Clutter-Free Storage & Isolation**: Ensure the intermediate PDF is never saved to external/permanent storage, excluded from Room database indexing (`RecentDocumentDao` indexes only the original `.docx`), and hidden from the app's document scanner / MediaStore.
