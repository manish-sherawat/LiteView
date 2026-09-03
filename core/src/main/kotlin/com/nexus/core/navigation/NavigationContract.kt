package com.nexus.core.navigation

import android.net.Uri

// ─── Document Type Enum ───────────────────────────────────────────────────────
// Represents the supported document formats in LiteView.
enum class DocumentType {
    PDF,
    DOCX,
    XLSX,
    TXT,
    UNKNOWN;

    companion object {
        private val TXT_EXTENSIONS = setOf(
            "txt", "log",
            "md", "markdown", "rst",
            "json", "jsonl", "geojson",
            "xml",
            "yaml", "yml",
            "toml",
            "ini", "cfg", "conf",
            "csv", "tsv",
            "html", "htm",
            "css", "scss", "sass", "less",
            "js", "mjs", "cjs", "ts",
            "svg",
            "kt", "java", "py", "c", "cpp", "h", "cs", "rb", "sh", "sql"
        )

        private val TXT_MIME_TYPES = setOf(
            "text/plain", "text/markdown", "text/x-markdown",
            "application/json", "text/json", "application/geo+json", "application/x-ndjson", "application/jsonlines",
            "text/xml", "application/xml",
            "text/yaml", "text/x-yaml", "application/x-yaml", "application/yaml",
            "application/toml", "text/x-toml",
            "text/csv", "text/tab-separated-values", "text/tsv",
            "text/html", "application/xhtml+xml",
            "text/css", "text/x-scss", "text/x-sass", "text/x-less",
            "application/javascript", "text/javascript", "application/x-javascript",
            "image/svg+xml", "text/x-log", "text/x-ini", "text/x-config"
        )

        /**
         * Detects [DocumentType] from a file URI or MIME type string.
         * Tries extension matching first, then falls back to MIME type.
         */
        fun fromUri(uri: Uri, mimeType: String? = null): DocumentType {
            val path = uri.path?.lowercase() ?: uri.toString().lowercase()
            val ext = path.substringAfterLast('.', "")
            return when {
                path.endsWith(".pdf") || mimeType == "application/pdf" -> PDF
                path.endsWith(".docx") || mimeType == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> DOCX
                path.endsWith(".doc") || mimeType == "application/msword" -> DOCX
                path.endsWith(".xlsx") || mimeType == "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> XLSX
                path.endsWith(".xls") || mimeType == "application/vnd.ms-excel" -> XLSX
                TXT_EXTENSIONS.contains(ext) || (mimeType != null && TXT_MIME_TYPES.contains(mimeType)) -> TXT
                mimeType?.startsWith("text/") == true -> TXT
                else -> UNKNOWN
            }
        }

        fun fromMimeType(mimeType: String?): DocumentType {
            if (mimeType == null) return UNKNOWN
            return when {
                mimeType == "application/pdf" -> PDF
                mimeType == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ||
                mimeType == "application/msword" -> DOCX
                mimeType == "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" ||
                mimeType == "application/vnd.ms-excel" -> XLSX
                TXT_MIME_TYPES.contains(mimeType) || mimeType.startsWith("text/") -> TXT
                else -> UNKNOWN
            }
        }
    }
}

// ─── Navigation Routes ────────────────────────────────────────────────────────
// Defines all navigation destinations as a sealed class hierarchy.
// Feature modules implement their own sub-routes but conform to this contract.

sealed class NexusRoute(val route: String) {

    // Splash screen - initial startup
    data object Splash : NexusRoute("splash")

    // Welcome Screen - First launch
    data object Welcome : NexusRoute("welcome")

    // Permission Screen - Post-welcome
    data object Permission : NexusRoute("permission")

    // Dashboard — main entry point showing recent files
    data object Dashboard : NexusRoute("dashboard")

    // Settings — accessible from the dashboard
    data object Settings : NexusRoute("settings")
    
    // Scanner - Document Scanning Feature
    data object Scanner : NexusRoute("scanner")

    // PDF Reader
    data class PdfReader(
        val encodedUri: String,
        val fileName: String
    ) : NexusRoute("reader/pdf/{encodedUri}/{fileName}") {
        companion object {
            const val ROUTE = "reader/pdf/{encodedUri}/{fileName}"
            const val ARG_URI = "encodedUri"
            const val ARG_FILE_NAME = "fileName"
        }

        fun buildRoute(): String = "reader/pdf/$encodedUri/$fileName"
    }

    // Office Reader (DOCX, XLSX, PPTX)
    data class OfficeReader(
        val encodedUri: String,
        val fileName: String,
        val docType: String
    ) : NexusRoute("reader/office/{encodedUri}/{fileName}/{docType}") {
        companion object {
            const val ROUTE = "reader/office/{encodedUri}/{fileName}/{docType}"
            const val ARG_URI = "encodedUri"
            const val ARG_FILE_NAME = "fileName"
            const val ARG_DOC_TYPE = "docType"
        }

        fun buildRoute(): String = "reader/office/$encodedUri/$fileName/$docType"
    }

    // Text Reader
    data class TextReader(
        val encodedUri: String,
        val fileName: String
    ) : NexusRoute("reader/text/{encodedUri}/{fileName}") {
        companion object {
            const val ROUTE = "reader/text/{encodedUri}/{fileName}"
            const val ARG_URI = "encodedUri"
            const val ARG_FILE_NAME = "fileName"
        }

        fun buildRoute(): String = "reader/text/$encodedUri/$fileName"
    }

    // Unsupported Format
    data class Unsupported(
        val fileName: String
    ) : NexusRoute("reader/unsupported/{fileName}") {
        companion object {
            const val ROUTE = "reader/unsupported/{fileName}"
            const val ARG_FILE_NAME = "fileName"
        }
        fun buildRoute(): String = "reader/unsupported/$fileName"
    }
}

// ─── DocumentReaderRouter Interface ──────────────────────────────────────────
// Strict navigation contract defined in :core.
// The :app module provides an implementation via Hilt that dispatches
// to the correct feature module based on file type.
// Feature modules NEVER depend on each other — only on :core.

interface DocumentReaderRouter {
    /**
     * Navigate to the appropriate reader for the given document URI.
     * The router detects the document type and routes accordingly.
     *
     * @param uri The document content URI or file URI.
     * @param mimeType Optional MIME type hint from the opening intent.
     * @param fileName Optional display name for the document.
     */
    fun openDocument(uri: Uri, mimeType: String? = null, fileName: String? = null)

    /**
     * Navigate back from the current reader to the dashboard.
     */
    fun navigateBack()

    /**
     * Navigate directly to the dashboard, clearing the back stack.
     */
    fun navigateToDashboard()
    
    /**
     * Navigate to the Document Scanner.
     */
    fun navigateToScanner()

    /**
     * Navigate to the Settings Screen.
     */
    fun navigateToSettings()
}
