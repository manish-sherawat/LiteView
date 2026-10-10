package com.nexus.feature.dashboard.domain

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.nexus.core.navigation.DocumentType
import com.nexus.feature.dashboard.data.RecentDocument
import com.nexus.feature.dashboard.data.TrashedDocument
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

class StorageScanUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun scanStorage(trashedList: List<TrashedDocument>): List<RecentDocument> = withContext(Dispatchers.IO) {
        val trashedUris = trashedList.map { it.originalUri }.toSet()
        val trashedPaths = trashedList.mapNotNull { it.originalPath }.toSet()

        val uri = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.MIME_TYPE
        )

        val supportedMimeTypes = listOf(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-excel",
            "text/plain",
            "text/markdown",
            "application/json",
            "text/xml",
            "text/yaml",
            "application/toml",
            "text/csv",
            "text/tab-separated-values",
            "text/html",
            "text/css",
            "application/javascript",
            "image/svg+xml"
        )
        
        // Use MIME_TYPE instead of LIKE '%.ext' to allow index usage
        val selection = "${MediaStore.Files.FileColumns.MIME_TYPE} IN (${supportedMimeTypes.joinToString(",") { "'$it'" }})"

        val cursor = context.contentResolver.query(
            uri,
            projection,
            selection,
            null,
            "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
        )

        val scanned = mutableListOf<RecentDocument>()
        cursor?.use { c ->
            val dataIdx = c.getColumnIndex(MediaStore.Files.FileColumns.DATA)
            val nameIdx = c.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val sizeIdx = c.getColumnIndex(MediaStore.Files.FileColumns.SIZE)
            val dateIdx = c.getColumnIndex(MediaStore.Files.FileColumns.DATE_MODIFIED)
            val mimeIdx = c.getColumnIndex(MediaStore.Files.FileColumns.MIME_TYPE)

            while (c.moveToNext()) {
                val path = if (dataIdx >= 0) c.getString(dataIdx) else null
                if (path.isNullOrEmpty()) continue
                val file = File(path)
                if (!file.exists() || !file.canRead()) continue
                val fileUriStr = Uri.fromFile(file).toString()
                if (trashedUris.contains(fileUriStr) || trashedPaths.contains(path) || trashedUris.contains("file://$path") || trashedPaths.contains(fileUriStr)) {
                    continue
                }

                val name = if (nameIdx >= 0) c.getString(nameIdx) ?: file.name else file.name
                val size = if (sizeIdx >= 0) c.getLong(sizeIdx) else 0L
                val dateModified = if (dateIdx >= 0) c.getLong(dateIdx) * 1000L else 0L
                val mimeType = if (mimeIdx >= 0) c.getString(mimeIdx) else null

                val ext = file.extension.lowercase()
                val docType = DocumentType.fromUri(Uri.fromFile(file), mimeType)
                if (docType == DocumentType.UNKNOWN) continue

                val resolvedMimeType = mimeType ?: when (ext) {
                    "pdf" -> "application/pdf"
                    "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                    "doc" -> "application/msword"
                    "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    "xls" -> "application/vnd.ms-excel"
                    "md", "markdown" -> "text/markdown"
                    "json", "jsonl", "geojson" -> "application/json"
                    "xml" -> "text/xml"
                    "yaml", "yml" -> "text/yaml"
                    "toml" -> "application/toml"
                    "csv" -> "text/csv"
                    "tsv" -> "text/tab-separated-values"
                    "html", "htm" -> "text/html"
                    "css", "scss", "sass", "less" -> "text/css"
                    "js", "mjs", "cjs", "ts" -> "application/javascript"
                    "svg" -> "image/svg+xml"
                    else -> "text/plain"
                }

                scanned.add(
                    RecentDocument(
                        uri = Uri.fromFile(file).toString(),
                        fileName = name,
                        mimeType = resolvedMimeType,
                        fileSizeBytes = if (size > 0L) size else file.length(),
                        lastOpenedAt = if (dateModified > 0L) dateModified else file.lastModified(),
                        documentType = docType.name
                    )
                )
            }
        }
        scanned.toList()
    }
}
