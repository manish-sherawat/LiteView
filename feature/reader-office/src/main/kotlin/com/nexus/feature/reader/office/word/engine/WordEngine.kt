package com.nexus.feature.reader.office.word.engine

import android.graphics.Bitmap
import android.net.Uri
import com.nexus.feature.reader.office.word.model.DocxHeading
import java.io.File

/**
 * Result emitted by an implementation of [WordEngine].
 */
sealed interface WordEngineResult {

    /**
     * Document was converted to a high-fidelity PDF by the native engine.
     */
    data class ConvertedPdf(
        val pdfFile: File,
        val pageCount: Int,
        val headings: List<DocxHeading> = emptyList()
    ) : WordEngineResult

    /**
     * Document was rendered as pages of bitmaps by the native engine.
     */
    data class PaginatedBitmaps(
        val pageCount: Int,
        val pageRenderer: suspend (pageIndex: Int, widthPx: Int, heightPx: Int) -> Bitmap?,
        val headings: List<DocxHeading> = emptyList()
    ) : WordEngineResult

    /**
     * Document was rendered as paginated HTML (legacy POI engine).
     */
    data class Html(
        val htmlContent: String,
        val headings: List<DocxHeading>,
        val pageCount: Int,
        val wordCount: Int,
        val characterCount: Int,
        val readingTimeMinutes: Int,
        val author: String?,
        val creationDate: String?,
        val paperSizeName: String,
        val paperDimensions: String,
        val orientation: String,
        val marginsDescription: String
    ) : WordEngineResult

    data class PasswordRequired(val isError: Boolean = false) : WordEngineResult

    data class Error(val message: String, val cause: Throwable? = null) : WordEngineResult
}

/**
 * Abstraction for document parsing & rendering engines (LibreOfficeKit vs Apache POI).
 */
interface WordEngine {
    val engineName: String

    suspend fun loadDocument(
        uri: Uri,
        docType: String,
        password: String? = null
    ): WordEngineResult
}
