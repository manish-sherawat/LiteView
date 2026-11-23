package com.nexus.feature.reader.office.word.lokit

import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.withContext
import org.libreoffice.kit.Document
import org.libreoffice.kit.Office
import java.io.File
import java.nio.ByteBuffer

/**
 * Thread-safe wrapper around a native [Document] handle in LibreOfficeKit.
 * Enforces execution strictly on [LOKitRuntimeManager.lokitDispatcher].
 */
class LOKitDocumentBridge private constructor(
    private val nativeDoc: Document
) {

    companion object {
        private const val TAG = "LOKitDocumentBridge"
        private const val TWIPS_PER_INCH = 1440.0

        /**
         * Loads a document from a local file path.
         */
        suspend fun load(
            office: Office,
            filePath: String,
            password: String? = null
        ): LOKitDocumentBridge? = withContext(LOKitRuntimeManager.lokitDispatcher) {
            try {
                if (password != null) {
                    office.setDocumentPassword(filePath, password)
                }

                val doc = office.documentLoad(filePath)
                if (doc == null) {
                    val err = office.error ?: "Unknown error"
                    Log.e(TAG, "Failed to load document via LOKit: $err (path: $filePath)")
                    return@withContext null
                }

                doc.initializeForRendering()
                LOKitDocumentBridge(doc)
            } catch (t: Throwable) {
                Log.e(TAG, "Exception during LOKit document load", t)
                null
            }
        }
    }

    /**
     * Total number of pages (for Writer text documents) or parts (for Calc/Impress).
     */
    suspend fun getPageCount(): Int = withContext(LOKitRuntimeManager.lokitDispatcher) {
        try {
            if (nativeDoc.documentType == Document.DOCTYPE_TEXT) {
                val rects = nativeDoc.partPageRectangles
                if (!rects.isNullOrBlank()) {
                    return@withContext rects.trim().split("[,;\\s]+".toRegex()).size / 4
                }
            }
            nativeDoc.parts.coerceAtLeast(1)
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating page count", e)
            1
        }
    }

    /**
     * Document width in Twips.
     */
    suspend fun getWidthTwips(): Long = withContext(LOKitRuntimeManager.lokitDispatcher) {
        try { nativeDoc.documentWidth } catch (_: Exception) { 11906L } // ~A4 width
    }

    /**
     * Document height in Twips.
     */
    suspend fun getHeightTwips(): Long = withContext(LOKitRuntimeManager.lokitDispatcher) {
        try { nativeDoc.documentHeight } catch (_: Exception) { 16838L } // ~A4 height
    }

    /**
     * Document type (Document.DOCTYPE_TEXT, DOCTYPE_SPREADSHEET, etc.)
     */
    suspend fun getDocumentType(): Int = withContext(LOKitRuntimeManager.lokitDispatcher) {
        try { nativeDoc.documentType } catch (_: Exception) { Document.DOCTYPE_OTHER }
    }

    /**
     * Renders a specific page to an Android [Bitmap] at the specified target resolution.
     */
    suspend fun renderPageBitmap(
        pageIndex: Int,
        targetWidthPx: Int,
        targetHeightPx: Int
    ): Bitmap? = withContext(LOKitRuntimeManager.lokitDispatcher) {
        try {
            var pageX = 0
            var pageY = 0
            var pageWTwips = nativeDoc.documentWidth.toInt().coerceAtLeast(1)
            var pageHTwips = nativeDoc.documentHeight.toInt().coerceAtLeast(1)

            if (nativeDoc.documentType == Document.DOCTYPE_TEXT) {
                val rects = nativeDoc.partPageRectangles
                if (!rects.isNullOrBlank()) {
                    val tokens = rects.trim().split("[,;\\s]+".toRegex())
                    val baseIdx = pageIndex * 4
                    if (tokens.size >= baseIdx + 4) {
                        pageX = tokens[baseIdx].toDouble().toInt()
                        pageY = tokens[baseIdx + 1].toDouble().toInt()
                        pageWTwips = tokens[baseIdx + 2].toDouble().toInt().coerceAtLeast(1)
                        pageHTwips = tokens[baseIdx + 3].toDouble().toInt().coerceAtLeast(1)
                    }
                }
            } else {
                nativeDoc.part = pageIndex
            }

            val bufferSize = targetWidthPx * targetHeightPx * 4
            val directBuffer = ByteBuffer.allocateDirect(bufferSize) ?: return@withContext null

            nativeDoc.paintTile(
                directBuffer,
                targetWidthPx,
                targetHeightPx,
                pageX,
                pageY,
                pageWTwips,
                pageHTwips
            )

            val bitmap = Bitmap.createBitmap(targetWidthPx, targetHeightPx, Bitmap.Config.ARGB_8888)
            directBuffer.rewind()
            bitmap.copyPixelsFromBuffer(directBuffer)
            bitmap
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to render page $pageIndex to bitmap", t)
            null
        }
    }

    /**
     * Exports/converts the document into an in-memory PDF byte stream.
     * The intermediate scratch file is placed in a private cache directory,
     * immediately read into memory, and deleted with zero storage footprint.
     */
    suspend fun exportToPdfBytes(transientDir: File): ByteArray? = withContext(LOKitRuntimeManager.lokitDispatcher) {
        var scratchFile: File? = null
        try {
            if (!transientDir.exists()) transientDir.mkdirs()
            val noMedia = File(transientDir, ".nomedia")
            if (!noMedia.exists()) try { noMedia.createNewFile() } catch (_: Exception) {}

            scratchFile = File(transientDir, "lok_scratch_${System.nanoTime()}.pdf")
            nativeDoc.saveAs(scratchFile.absolutePath, "pdf", "SelectPdfVersion=1,IsSkipEmptyPages=true")
            if (scratchFile.exists() && scratchFile.length() > 0) {
                val bytes = scratchFile.readBytes()
                Log.i(TAG, "Successfully generated in-memory PDF byte stream (${bytes.size} bytes)")
                bytes
            } else {
                Log.e(TAG, "LOKit saveAs failed to produce PDF bytes")
                null
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to convert document to in-memory PDF byte stream", t)
            null
        } finally {
            try {
                scratchFile?.delete()
            } catch (_: Exception) {}
        }
    }

    /**
     * Exports/converts the loaded document into a PDF file at [outputPdfFile].
     * Provides 100% desktop fidelity and seamless integration with PDF view engines.
     */
    suspend fun exportToPdf(outputPdfFile: File): Boolean = withContext(LOKitRuntimeManager.lokitDispatcher) {
        try {
            if (outputPdfFile.exists()) {
                outputPdfFile.delete()
            }
            outputPdfFile.parentFile?.mkdirs()

            nativeDoc.saveAs(outputPdfFile.absolutePath, "pdf", "SelectPdfVersion=1,IsSkipEmptyPages=true")
            val success = outputPdfFile.exists() && outputPdfFile.length() > 0
            if (success) {
                Log.i(TAG, "Successfully converted document to PDF: ${outputPdfFile.absolutePath} (${outputPdfFile.length()} bytes)")
            } else {
                Log.e(TAG, "LOKit saveAs reported completion but output file is missing or 0 bytes")
            }
            success
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to convert document to PDF via LOKit", t)
            false
        }
    }

    /**
     * Closes and frees native document handles.
     */
    suspend fun close() = withContext(LOKitRuntimeManager.lokitDispatcher) {
        try {
            nativeDoc.destroy()
            Log.i(TAG, "Native Document handle destroyed")
        } catch (t: Throwable) {
            Log.e(TAG, "Error destroying native Document handle", t)
        }
    }
}
