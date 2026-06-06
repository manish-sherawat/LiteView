package com.nexus.core.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import java.io.File
import java.io.FileOutputStream

object DocumentCache {
    private const val TAG = "DocumentCache"

    /**
     * Copies the content of a content URI to a local file in the app's cache directory.
     * Returns the file:// URI of the cached copy, or the original URI if caching fails.
     */
    fun cacheUri(context: Context, uri: Uri): Uri {
        if (uri.scheme != "content") return uri

        return try {
            val fileName = getFileName(context, uri) ?: "Document"
            val cacheDir = File(context.cacheDir, "document_cache")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            
            cleanupCache(cacheDir)
            
            val cacheFile = File(cacheDir, fileName)
            
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(cacheFile).use { output ->
                    input.copyTo(output)
                }
            }
            Uri.fromFile(cacheFile)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cache URI: $uri", e)
            uri
        }
    }

    private fun cleanupCache(cacheDir: File) {
        val maxCacheSize = 100 * 1024 * 1024L // 100 MB limit
        var currentSize = cacheDir.listFiles()?.sumOf { it.length() } ?: 0L
        if (currentSize > maxCacheSize) {
            val files = cacheDir.listFiles()?.sortedBy { it.lastModified() } ?: return
            for (file in files) {
                if (currentSize <= maxCacheSize / 2) break // Reduce to 50MB to avoid frequent cleanups
                val size = file.length()
                if (file.delete()) {
                    currentSize -= size
                }
            }
        }
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        var name: String? = null
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex >= 0) {
                    name = cursor.getString(nameIndex)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query filename", e)
        }
        return name ?: uri.lastPathSegment
    }
}
