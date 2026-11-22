package com.nexus.feature.reader.office.word.model

/**
 * A heading extracted from a DOCX document, used to populate the outline drawer.
 * @param text    The visible heading text.
 * @param level   Heading level 1–6 (matches Word heading styles).
 * @param anchorId The id attribute set on the <h> element in the generated HTML.
 */
data class DocxHeading(
    val text: String,
    val level: Int,
    val anchorId: String
)

sealed class WordReaderUiState {
    data class Loading(val progress: Float? = null, val message: String? = null) : WordReaderUiState()

    /**
     * DOCX/DOC document loaded. The content is pre-rendered as an HTML string
     * (produced by the ViewModel) so the UI layer only needs to hand it to a WebView.
     */
    data class Ready(
        val htmlContent: String,
        val headings: List<DocxHeading> = emptyList(),
        val showOutline: Boolean = false,
        val author: String? = null,
        val creationDate: String? = null,
        val wordCount: Int? = null,
        val characterCount: Int? = null,
        val readingTimeMinutes: Int? = null,
        val pageCount: Int = 1,
        val paperSizeName: String = "A4",
        val paperDimensions: String = "210 × 297 mm",
        val orientation: String = "Portrait",
        val marginsDescription: String = "Normal"
    ) : WordReaderUiState()

    data class PasswordRequired(
        val encodedUri: String,
        val docType: String,
        val isError: Boolean = false
    ) : WordReaderUiState()

    data class Error(val message: String) : WordReaderUiState()
}
