package com.nexus.feature.reader.office.word.components

import androidx.compose.runtime.Composable
import com.nexus.core.navigation.DocumentType
import com.nexus.core.ui.components.DocumentOptionItem
import com.nexus.core.ui.components.DocumentOptionsBottomSheet

@Composable
fun WordOptionsBottomSheet(
    displayName: String,
    isStarred: Boolean,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onFavorite: () -> Unit,
    onShare: () -> Unit,
    onPrint: () -> Unit,
    onInfo: () -> Unit,
    onOutline: () -> Unit = {},
    onBookmarks: () -> Unit = {},
    docxHeadingsCount: Int = 0,
    onIncreaseFontSize: () -> Unit = {},
    onDecreaseFontSize: () -> Unit = {},
    onToggleSerif: () -> Unit = {},
    isSerif: Boolean = false,
    isPagedView: Boolean = true,
    onTogglePagedView: () -> Unit = {},
    paperSizeName: String? = null,
    onGoToPage: () -> Unit = {},
    totalPages: Int = 1,
    useLibreOfficeEngine: Boolean = true,
    onToggleEngine: () -> Unit = {}
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

    val groupDocx = buildList {
        add(
            DocumentOptionItem(
                title = if (useLibreOfficeEngine) "Engine: LibreOfficeKit" else "Engine: Legacy POI",
                subtitle = if (useLibreOfficeEngine) "Desktop-grade high fidelity (Active)" else "HTML WebView layout (Legacy)",
                iconRes = com.nexus.core.R.drawable.ic_settings_outline,
                onClick = onToggleEngine
            )
        )
        add(
            DocumentOptionItem(
                title = if (isPagedView) "Paged View Mode" else "Reflow View Mode",
                subtitle = if (isPagedView) "Distinct pages (${paperSizeName ?: "Standard"})" else "Continuous mobile scrolling",
                iconRes = if (isPagedView) com.nexus.core.R.drawable.ic_book else com.nexus.core.R.drawable.ic_world,
                onClick = onTogglePagedView
            )
        )
        if (totalPages > 1) {
            add(
                DocumentOptionItem(
                    title = "Go to Page",
                    subtitle = "Jump to page (1–$totalPages)",
                    iconRes = com.nexus.core.R.drawable.ic_search,
                    onClick = onGoToPage
                )
            )
        }
        if (docxHeadingsCount > 0) {
            add(
                DocumentOptionItem(
                    title = "Table of Contents",
                    subtitle = "$docxHeadingsCount sections available",
                    iconRes = com.nexus.core.R.drawable.ic_view_list,
                    onClick = onOutline
                )
            )
        }
        add(
            DocumentOptionItem(
                title = "Bookmarks",
                subtitle = "Manage page bookmarks",
                iconRes = com.nexus.core.R.drawable.ic_tag,
                onClick = onBookmarks
            )
        )
        add(
            DocumentOptionItem(
                title = if (isSerif) "Font: Serif (Book style)" else "Font: Sans-Serif (Modern style)",
                subtitle = "Tap to switch font style",
                iconRes = com.nexus.core.R.drawable.ic_text_scan,
                onClick = onToggleSerif
            )
        )
        add(
            DocumentOptionItem(
                title = "Increase Text Size",
                subtitle = "Make document text larger",
                iconRes = com.nexus.core.R.drawable.ic_maximize,
                onClick = onIncreaseFontSize
            )
        )
        add(
            DocumentOptionItem(
                title = "Decrease Text Size",
                subtitle = "Make document text smaller",
                iconRes = com.nexus.core.R.drawable.ic_minimize,
                onClick = onDecreaseFontSize
            )
        )
    }

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
        fileMeta = "Word Document · DOCX",
        docType = DocumentType.DOCX,
        groups = if (groupDocx.isNotEmpty()) listOf(group1, groupDocx, group2) else listOf(group1, group2),
        onDismiss = onDismiss
    )
}
