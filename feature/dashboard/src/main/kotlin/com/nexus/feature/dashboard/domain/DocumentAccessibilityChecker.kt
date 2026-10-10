package com.nexus.feature.dashboard.domain

import android.content.Context
import android.net.Uri
import com.nexus.feature.dashboard.data.RecentDocument
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

class DocumentAccessibilityChecker @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun getInaccessibleUris(documents: List<RecentDocument>): Set<String> = withContext(Dispatchers.IO) {
        documents.filter { !isUriAccessible(it.uri) }
            .map { it.uri }
            .toSet()
    }

    private fun isUriAccessible(uriStr: String): Boolean {
        return try {
            val uri = Uri.parse(uriStr)
            if (uri.scheme == "content") {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false
            } else {
                File(uri.path ?: "").exists()
            }
        } catch (_: Exception) {
            false
        }
    }
}
