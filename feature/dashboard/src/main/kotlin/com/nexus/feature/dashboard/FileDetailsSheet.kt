package com.nexus.feature.dashboard

import androidx.compose.runtime.Composable
import com.nexus.core.navigation.DocumentType
import com.nexus.core.ui.components.NexusFileDetailsSheet
import com.nexus.feature.dashboard.data.RecentDocument

@Composable
fun FileDetailsSheet(
    doc: RecentDocument,
    onDismissRequest: () -> Unit
) {
    val docType = runCatching { DocumentType.valueOf(doc.documentType) }
        .getOrDefault(DocumentType.UNKNOWN)

    NexusFileDetailsSheet(
        fileName = doc.fileName,
        uri = doc.uri,
        fileSizeBytes = doc.fileSizeBytes,
        lastOpenedAt = doc.lastOpenedAt,
        docType = docType,
        onDismissRequest = onDismissRequest
    )
}
