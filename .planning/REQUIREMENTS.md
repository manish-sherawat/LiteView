# Requirements: OOXML-to-PDF Conversion Pipeline & In-Memory PDFium View Layer

## Functional Requirements
- **FR-1: Conversion Service**: On document open, convert DOCX/DOC into an in-memory PDF byte stream (`ByteArray`).
  - Primary: Headless LibreOffice Kit (`LOKitWordEngine`).
  - Fallback: Native OOXML-to-PDF converter (`NativeWordToPdfConverter`) using `android.graphics.pdf.PdfDocument`.
- **FR-2: In-Memory PDFium Renderer**: High-performance view layer backed by Android's native PDFium engine (`PdfRenderer`).
  - Supports page count extraction, dynamic aspect ratios, and on-demand page bitmap rendering with LRU cache.
  - Implements smooth scrolling, pinch-to-zoom, pan, page jumps, theme color filters, and print support directly from memory.
- **FR-3: Clutter-Free Storage Sandbox**:
  - The intermediate PDF is NEVER written to external or permanent storage.
  - Any transient scratch file created during native conversion is immediately deleted upon reading bytes into memory.
  - The intermediate PDF is excluded from the local database index (`RecentDocumentDao` retains the original file's URI).
  - The intermediate PDF is invisible to MediaStore and `StorageScanUseCase`.
