# State: OOXML-to-PDF Conversion Pipeline

## Status: Complete
- **Phase 1 - Conversion Pipeline & Stream Abstraction**: Completed.
  - Headless LibreOfficeKit (`LOKitDocumentBridge.exportToPdfBytes` and `LOKitWordEngine`) converts DOCX/DOC to PDF byte stream.
  - Native fallback converter (`NativeWordToPdfConverter`) converts DOCX/DOC directly to in-memory PDF byte streams via Android `PdfDocument`, `StaticLayout`, and vector drawing.
  - Coordinator (`WordEngineCoordinator`) manages the primary headless LOKit engine with graceful automatic fallback to the native vector converter.
- **Phase 2 - In-Memory PDFium View Layer**: Completed.
  - Created `WordPdfiumRenderer` wrapping Android's native PDFium engine (`android.graphics.pdf.PdfRenderer`).
  - Added LRU bitmap caching and thread-safe Mutex rendering.
  - Connected `WordReaderViewModel` with `renderedPages` state and dynamic page rendering.
  - Modernized `WordNativePageViewer` with pinch-to-zoom (up to 4x), double-tap zoom toggle, smooth scrolling, and dark mode inversion filter.
  - Updated `WordReaderScreen` to render native PDF pages with bookmarks and outline jumping.
- **Phase 3 - Strict Storage & Database Clutter-Free Isolation**: Completed & Verified.
  - Zero permanent storage: Transient scratch files are unlinked immediately via POSIX file descriptors (`File.delete()` right after open).
  - Excluded from scanner & MediaStore: Files reside exclusively in app-private `.nomedia` cache directories and exist as unlinked inodes.
  - Excluded from database indexing: `RecentDocumentDao` retains only the original user file URI (`content://...` or `file://.../*.docx`), completely ignoring transient PDF data.
- **Verification**:
  - `gradlew.bat :feature:reader-office:compileDebugKotlin :app:compileDebugKotlin` succeeded with 0 errors.
