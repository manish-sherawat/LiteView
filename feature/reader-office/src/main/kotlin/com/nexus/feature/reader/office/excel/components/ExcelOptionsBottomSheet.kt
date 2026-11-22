package com.nexus.feature.reader.office.excel.components

import androidx.compose.runtime.Composable
import com.nexus.core.navigation.DocumentType
import com.nexus.core.ui.components.DocumentOptionItem
import com.nexus.core.ui.components.DocumentOptionsBottomSheet

@Composable
fun ExcelOptionsBottomSheet(
    displayName: String,
    isStarred: Boolean,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onFavorite: () -> Unit,
    onShare: () -> Unit,
    onPrint: () -> Unit,
    onInfo: () -> Unit
) {
    val group1 = listOf(
        DocumentOptionItem(
            title = "File info",
            subtitle = "View document properties",
            iconRes = com.nexus.core.R.drawable.ic_info,
            onClick = onInfo
        ),
        DocumentOptionItem(
            title = "Rename",
            subtitle = "Rename this file",
            iconRes = com.nexus.core.R.drawable.ic_rename,
            onClick = onRename
        ),
        DocumentOptionItem(
            title = if (isStarred) "Starred" else "Star file",
            subtitle = "Add or remove bookmark",
            iconRes = if (isStarred) com.nexus.core.R.drawable.ic_star_filled else com.nexus.core.R.drawable.ic_star,
            isStarred = isStarred,
            showChevron = false,
            onClick = onFavorite
        )
    )

    val group2 = listOf(
        DocumentOptionItem(
            title = "Share",
            subtitle = "Share this document",
            iconRes = com.nexus.core.R.drawable.ic_share,
            onClick = onShare
        ),
        DocumentOptionItem(
            title = "Print",
            subtitle = "Print or save as PDF",
            iconRes = com.nexus.core.R.drawable.ic_printer,
            onClick = onPrint
        )
    )

    DocumentOptionsBottomSheet(
        sheetTitle = "Document Options",
        fileName = displayName,
        fileMeta = "Spreadsheet Document · XLSX",
        docType = DocumentType.XLSX,
        groups = listOf(group1, group2),
        onDismiss = onDismiss
    )
}
