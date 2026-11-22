package com.nexus.feature.reader.office

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.nexus.feature.reader.office.excel.ExcelReaderScreen
import com.nexus.feature.reader.office.word.WordReaderScreen

/**
 * Delegating Office reader composable that routes to either [WordReaderScreen] or [ExcelReaderScreen]
 * based on [docType]. Retained for backward-compatible routing and shared office entry points.
 */
@Composable
fun OfficeReaderScreen(
    encodedUri: String,
    fileName: String,
    docType: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (docType.equals("XLSX", ignoreCase = true) || docType.equals("XLS", ignoreCase = true)) {
        ExcelReaderScreen(
            encodedUri = encodedUri,
            fileName = fileName,
            docType = docType,
            onBack = onBack,
            modifier = modifier
        )
    } else {
        WordReaderScreen(
            encodedUri = encodedUri,
            fileName = fileName,
            docType = docType,
            onBack = onBack,
            modifier = modifier
        )
    }
}
