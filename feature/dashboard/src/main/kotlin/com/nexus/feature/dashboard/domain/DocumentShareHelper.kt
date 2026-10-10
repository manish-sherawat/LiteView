package com.nexus.feature.dashboard.domain

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

class DocumentShareHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun shareDocument(uriStr: String) {
        val rawUri = Uri.parse(uriStr)
        val shareUri = if (rawUri.scheme == "file") {
            val file = File(rawUri.path ?: "")
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } else {
            rawUri
        }
        
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_STREAM, shareUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        
        val chooser = Intent.createChooser(intent, "Share File").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    fun shareSelectedDocuments(uris: Set<String>) {
        if (uris.isEmpty()) return
        
        val shareUris = ArrayList<Uri>()
        for (uriStr in uris) {
            val rawUri = Uri.parse(uriStr)
            val shareUri = if (rawUri.scheme == "file") {
                val file = File(rawUri.path ?: "")
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            } else {
                rawUri
            }
            shareUris.add(shareUri)
        }

        val intent = if (shareUris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = "*/*"
                putExtra(Intent.EXTRA_STREAM, shareUris[0])
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, shareUris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        
        val chooser = Intent.createChooser(
            intent,
            if (shareUris.size == 1) "Share File" else "Share ${shareUris.size} Files"
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
