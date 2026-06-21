package com.nexus.nexusdocs.navigation

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.navigation.NavController
import com.nexus.core.navigation.DocumentReaderRouter
import com.nexus.core.navigation.DocumentType
import com.nexus.core.navigation.NexusRoute
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject

// ─── DocumentReaderRouter Implementation ─────────────────────────────────────
// Provided by Hilt at app-level scope. Implements the navigation contract
// defined in :core. Receives a NavController reference after initialization.
// Feature modules never see or depend on this class — they only use the
// interface from :core.

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DocumentReaderRouterImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : DocumentReaderRouter {

    private var navController: NavController? = null

    fun bind(navController: NavController) {
        this.navController = navController
    }

    override fun openDocument(uri: Uri, mimeType: String?, fileName: String?) {
        val controller = navController ?: return
        val currentContext = controller.context

        CoroutineScope(Dispatchers.Main).launch {
            var resolvedMimeType = mimeType
            var resolvedFileName = fileName

            val finalUri = withContext(Dispatchers.IO) {
                if (uri.scheme == "content") {
                    try {
                        if (resolvedMimeType == null) {
                            resolvedMimeType = currentContext.contentResolver.getType(uri)
                        }
                    } catch (_: Exception) {}

                    try {
                        if (resolvedFileName == null) {
                            currentContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                                if (cursor.moveToFirst() && nameIndex >= 0) {
                                    resolvedFileName = cursor.getString(nameIndex)
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }

                if (uri.scheme == "content") {
                    com.nexus.core.utils.DocumentCache.cacheUri(currentContext, uri)
                } else {
                    uri
                }
            }

            if (resolvedFileName == null) {
                resolvedFileName = uri.lastPathSegment ?: "Document"
            }

            val docType = DocumentType.fromUri(Uri.parse("file:///$resolvedFileName"), resolvedMimeType)
            val encodedUri = URLEncoder.encode(URLEncoder.encode(finalUri.toString(), StandardCharsets.UTF_8.toString()), StandardCharsets.UTF_8.toString())
            val encodedName = URLEncoder.encode(URLEncoder.encode(resolvedFileName!!, StandardCharsets.UTF_8.toString()), StandardCharsets.UTF_8.toString())

            when (docType) {
                DocumentType.PDF -> {
                    controller.navigate(
                        NexusRoute.PdfReader(
                            encodedUri = encodedUri,
                            fileName = encodedName
                        ).buildRoute()
                    )
                }
                DocumentType.DOCX, DocumentType.XLSX -> {
                    controller.navigate(
                        NexusRoute.OfficeReader(
                            encodedUri = encodedUri,
                            fileName = encodedName,
                            docType = docType.name
                        ).buildRoute()
                    )
                }
                DocumentType.TXT -> {
                    controller.navigate(
                        NexusRoute.TextReader(
                            encodedUri = encodedUri,
                            fileName = encodedName
                        ).buildRoute()
                    )
                }
                DocumentType.UNKNOWN -> {
                    // Navigate to a dedicated "unsupported file" screen
                    controller.navigate(NexusRoute.Unsupported(encodedName).buildRoute())
                }
            }
        }
    }

    override fun navigateBack() {
        navController?.popBackStack()
    }

    override fun navigateToDashboard() {
        navController?.navigate(NexusRoute.Dashboard.route) {
            popUpTo(NexusRoute.Dashboard.route) { inclusive = true }
        }
    }
    
    override fun navigateToScanner() {
        navController?.navigate(NexusRoute.Scanner.route)
    }

    override fun navigateToSettings() {
        navController?.navigate(NexusRoute.Settings.route)
    }
}
