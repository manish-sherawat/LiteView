# Roadmap: OOXML-to-PDF Conversion Pipeline

## Phase 1: Conversion Pipeline & Stream Abstraction
- Update `LOKitDocumentBridge` with `exportToPdfBytes(): ByteArray?`
- Create `NativeWordToPdfConverter` for reliable in-memory OOXML/DOC to PDF byte stream conversion fallback
- Update `LOKitWordEngine` and `WordEngineResult` to return `WordEngineResult.PdfStream`
- Configure `WordEngineCoordinator` to prioritize headless LibreOfficeKit with seamless native fallback

## Phase 2: In-Memory PDFium Engine
- Create `WordPdfiumRenderer` wrapping Android's native PDFium engine (`PdfRenderer`)
- Support memory-backed `ParcelFileDescriptor` with immediate scratch unlink/deletion
- Add thread-safe page bitmap rendering and LRU bitmap caching

## Phase 3: Word Reader View Layer Modernization
- Update `WordReaderViewModel` to drive `WordPdfiumRenderer`
- Replace `WebView` in `WordReaderScreen` with Compose lazy column PDF pages, pinch-to-zoom, pan, page jumps, and dark mode filters
- Update printing and sharing to use the in-memory stream and original URI

## Phase 4: Storage, Database & Scanner Verification
- Verify intermediate PDF is never on external storage
- Verify `RecentDocumentDao` retains only the original document URI
- Verify MediaStore and `StorageScanUseCase` remain clutter-free
- Compile and test build
