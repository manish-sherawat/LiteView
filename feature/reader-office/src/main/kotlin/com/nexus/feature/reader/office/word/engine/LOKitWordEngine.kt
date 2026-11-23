package com.nexus.feature.reader.office.word.engine

import android.content.Context
import android.net.Uri
import android.util.Log
import com.nexus.feature.reader.office.word.lokit.LOKitDocumentBridge
import com.nexus.feature.reader.office.word.lokit.LOKitRuntimeManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enterprise Word rendering engine powered by embedded LibreOfficeKit (LOK).
 *
 * Operational flow:
 * 1. Copies/resolves Content URI to a local file in app cache.
 * 2. Initializes LOK singleton on [LOKitRuntimeManager.lokitDispatcher].
 * 3. Converts document to vector PDF with 100% desktop fidelity.
 * 4. Returns [WordEngineResult.ConvertedPdf].
 */
@Singleton
class LOKitWordEngine @Inject constructor(
    @ApplicationContext private val context: Context
) : WordEngine {

    companion object {
        private const val TAG = "LOKitWordEngine"
    }

    override val engineName: String = "LibreOfficeKit Native Core"

    override suspend fun loadDocument(
        uri: Uri,
        docType: String,
        password: String?
    ): WordEngineResult = withContext(Dispatchers.IO) {
        var localDocFile: File? = null
        var bridge: LOKitDocumentBridge? = null

        try {
            // 1. Resolve URI to a local filesystem path for LOKit POSIX fopen
            localDocFile = resolveUriToLocalFile(uri, docType)
            if (localDocFile == null || !localDocFile.exists() || localDocFile.length() == 0L) {
                return@withContext WordEngineResult.Error("Failed to access local document file for LOKit engine.")
            }

            // 2. Initialize LOKit runtime
            val office = LOKitRuntimeManager.getOffice(context)
                ?: return@withContext WordEngineResult.Error("LibreOfficeKit runtime initialization failed.")

            // 3. Load document into native bridge
            bridge = LOKitDocumentBridge.load(office, localDocFile.absolutePath, password)
            if (bridge == null) {
                // If loading failed and password was empty, check if password is required
                if (password == null) {
                    val errMsg = office.error ?: ""
                    if (errMsg.contains("password", ignoreCase = true) || errMsg.contains("encrypted", ignoreCase = true)) {
                        return@withContext WordEngineResult.PasswordRequired(isError = false)
                    }
                }
                return@withContext WordEngineResult.Error("LibreOfficeKit failed to open document: ${office.error ?: "unsupported or corrupt format"}")
            }

            val pageCount = bridge.getPageCount().coerceAtLeast(1)

            // 4. Convert directly to in-memory PDF byte stream (scratch file immediately deleted)
            val transientDir = File(context.cacheDir, "libreoffice/transient_streams")
            val pdfBytes = bridge.exportToPdfBytes(transientDir)
            if (pdfBytes == null || pdfBytes.isEmpty()) {
                return@withContext WordEngineResult.Error("LibreOfficeKit document conversion failed to generate PDF stream.")
            }

            Log.i(TAG, "LOKit successfully converted ${localDocFile.name} to in-memory PDF (${pdfBytes.size} bytes, $pageCount pages)")
            WordEngineResult.PdfStream(
                pdfBytes = pdfBytes,
                pageCount = pageCount
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Unhandled error in LOKitWordEngine", t)
            WordEngineResult.Error("LOKit error: ${t.localizedMessage ?: "Native engine fault"}", t)
        } finally {
            bridge?.close()
            try {
                if (localDocFile != null && localDocFile.absolutePath.contains("libreoffice/incoming")) {
                    localDocFile.delete()
                }
            } catch (_: Exception) {}
        }
    }

    private fun resolveUriToLocalFile(uri: Uri, docType: String): File? {
        return try {
            if (uri.scheme == "file") {
                val f = File(uri.path ?: "")
                if (f.exists()) return f
            }

            val tempDir = File(context.cacheDir, "libreoffice/incoming").apply { mkdirs() }
            val ext = if (docType.equals("DOC", ignoreCase = true)) "doc" else "docx"
            val tempFile = File(tempDir, "temp_doc_${System.currentTimeMillis()}.$ext")

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
            if (tempFile.exists() && tempFile.length() > 0) tempFile else null
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving URI to local file", e)
            null
        }
    }
}
