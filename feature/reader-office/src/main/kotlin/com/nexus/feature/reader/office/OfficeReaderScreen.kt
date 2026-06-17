package com.nexus.feature.reader.office

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusButton
import com.nexus.core.ui.NexusSurface
import com.nexus.core.ui.NexusText
import com.nexus.core.ui.NexusTopBar
import java.net.URLDecoder
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.print.PrintAttributes
import android.print.PrintManager

@Composable
fun OfficeReaderScreen(
    encodedUri: String,
    fileName: String,
    docType: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OfficeReaderViewModel = hiltViewModel()
) {
    LaunchedEffect(encodedUri, docType) {
        viewModel.loadDocument(encodedUri, docType)
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val displayName = try { URLDecoder.decode(URLDecoder.decode(fileName, "UTF-8"), "UTF-8") } catch (_: Exception) { fileName }

    var isImmersiveMode by remember { mutableStateOf(false) }
    val topPadding by animateDpAsState(targetValue = if (isImmersiveMode) 0.dp else 100.dp)

    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResultCount by remember { mutableStateOf(0) }
    var currentSearchIndex by remember { mutableStateOf(0) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    
    var isPrintLayout by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    val context = LocalContext.current
    
    val systemDarkTheme = isSystemInDarkTheme()
    var isDarkThemeOverride by remember { mutableStateOf<Boolean?>(null) }
    val isDark = isDarkThemeOverride ?: systemDarkTheme

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(if (isDark) com.nexus.core.theme.darkNexusColors.background else com.nexus.core.theme.lightNexusColors.background)
    ) {
        AnimatedContent(
            targetState = uiState,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                    fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                },
                label = "officeReaderState"
            ) { state ->
                when (state) {
                    is OfficeReaderUiState.Loading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            NexusText("Loading document...", color = NexusTheme.colors.textSecondary)
                        }
                    }
                    is OfficeReaderUiState.DocxReady -> {
                    val printCss = if (isPrintLayout) """
                        body {
                            background-color: ${if(isDark) "#121212" else "#f0f0f0"} !important;
                            padding: 24px;
                            padding-top: 120px;
                            padding-bottom: 100px;
                        }
                        #content {
                            background-color: ${if(isDark) "#1e1e1e" else "#ffffff"} !important;
                            width: 210mm;
                            min-height: 297mm;
                            margin: 0 auto;
                            padding: 20mm;
                            box-shadow: 0 4px 8px rgba(0,0,0,0.2);
                            box-sizing: border-box;
                        }
                    """.trimIndent() else """
                        body {
                            padding: 24px;
                            padding-top: 120px;
                            padding-bottom: 100px;
                            background-color: transparent !important;
                        }
                    """.trimIndent()

                    val htmlWithScript = remember(state.htmlContent, isPrintLayout, isDark) {
                        var html = state.htmlContent
                        if (isDark) {
                            html = html.replace("<body>", "<body class=\"dark-mode\">")
                        }
                        val extraStyle = "<style>\n" +
                            (if (isDark) "body.dark-mode, body.dark-mode * { color: #e0e0e0 !important; }\n" else "") +
                            "$printCss\n</style>"
                        val script = "<script>document.addEventListener('click', function() { AndroidInterface.toggle(); });</script>"
                        
                        html.replace("</head>", "$extraStyle\n</head>")
                            .replace("</body>", "$script\n</body>")
                    }
                    // Docx Viewer using AndroidView
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                webViewRef = this
                                setBackgroundColor(0)
                                layoutParams = android.view.ViewGroup.LayoutParams(
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                settings.javaScriptEnabled = true
                                settings.setSupportZoom(true)
                                settings.builtInZoomControls = true
                                settings.displayZoomControls = false
                                setFindListener { activeMatchOrdinal, numberOfMatches, isDoneCounting ->
                                    if (isDoneCounting) {
                                        searchResultCount = numberOfMatches
                                        currentSearchIndex = activeMatchOrdinal
                                    }
                                }
                                addJavascriptInterface(ImmersiveToggleInterface { isImmersiveMode = !isImmersiveMode }, "AndroidInterface")
                                webViewClient = WebViewClient()
                            }
                        },
                        update = { view ->
                            if (view.tag != htmlWithScript) {
                                view.loadDataWithBaseURL(null, htmlWithScript, "text/html", "UTF-8", null)
                                view.tag = htmlWithScript
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                is OfficeReaderUiState.XlsxReady -> {
                    val rows = state.sheetsRows.getOrElse(state.currentSheet) { emptyList() }
                    val columnWidths = state.sheetsColumnWidths.getOrElse(state.currentSheet) { emptyList() }
                    
                    val textColor = if (isDark) "#e0e0e0" else "#1a1a1a"
                    
                    val htmlContent = remember(rows, columnWidths, state.showGridlines, isDark) {
                        buildString {
                            append("<!DOCTYPE html><html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=5.0, user-scalable=yes\">")
                            append("<style>")
                            append("body { font-family: sans-serif; font-size: 14px; margin: 0; padding: 16px; background-color: transparent; color: $textColor !important; } ")
                            append("body * { color: $textColor !important; } ")
                            append("table { border-collapse: collapse; background-color: transparent; border-radius: 4px; overflow: hidden; margin-bottom: 24px; } ")
                            append("td, th { border: ${if(state.showGridlines) "1px solid #888888" else "none"}; padding: 8px 12px; white-space: nowrap; } ")
                            // Add column widths
                            columnWidths.forEachIndexed { i, w ->
                                append("td:nth-child(${i+1}) { min-width: ${w}px; } ")
                            }
                            append("</style></head><body><table>")
                            for (row in rows) {
                                append("<tr>")
                                for (cell in row.cells) {
                                    if (cell.isHidden) continue
                                    val bg = if (cell.backgroundColorHex != null) "background-color: ${cell.backgroundColorHex};" else ""
                                    val fg = if (cell.textColorHex != null) "color: ${cell.textColorHex};" else ""
                                    val fw = if (cell.isBold) "font-weight: bold;" else ""
                                    val colspan = if (cell.colSpan > 1) " colspan=\"${cell.colSpan}\"" else ""
                                    val rowspan = if (cell.rowSpan > 1) " rowspan=\"${cell.rowSpan}\"" else ""
                                    val style = "$bg$fg$fw"
                                    val styleAttr = if (style.isNotEmpty()) " style=\"$style\"" else ""
                                    append("<td$colspan$rowspan$styleAttr>${cell.text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")}</td>")
                                }
                                append("</tr>")
                            }
                            append("</table>")
                            append("<script>document.addEventListener('click', function() { AndroidInterface.toggle(); });</script>")
                            append("</body></html>")
                        }
                    }

                    Column(modifier = Modifier.fillMaxSize().padding(top = topPadding)) {
                        // Sheet Tabs
                        AnimatedVisibility(
                            visible = !isImmersiveMode,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                state.sheetNames.forEachIndexed { index, name ->
                                    val isSelected = index == state.currentSheet
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                if (isSelected) NexusTheme.colors.primary else NexusTheme.colors.surfaceVariant,
                                                NexusTheme.shapes.pill
                                            )
                                            .clickable { viewModel.switchSheet(index) }
                                            .padding(horizontal = 16.dp, vertical = 8.dp)
                                    ) {
                                        NexusText(
                                            text = name,
                                            color = if (isSelected) NexusTheme.colors.onPrimary else NexusTheme.colors.textPrimary
                                        )
                                    }
                                }
                            }
                        }
                        
                        // Table Content using WebView
                        Box(modifier = Modifier.fillMaxSize()) {
                            AndroidView(
                                factory = { ctx ->
                                    WebView(ctx).apply {
                                        setBackgroundColor(0)
                                        layoutParams = android.view.ViewGroup.LayoutParams(
                                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                            android.view.ViewGroup.LayoutParams.MATCH_PARENT
                                        )
                                        settings.javaScriptEnabled = true
                                        settings.setSupportZoom(true)
                                        settings.builtInZoomControls = true
                                        settings.displayZoomControls = false
                                        setFindListener { activeMatchOrdinal, numberOfMatches, isDoneCounting ->
                                            if (isDoneCounting) {
                                                searchResultCount = numberOfMatches
                                                currentSearchIndex = activeMatchOrdinal
                                            }
                                        }
                                        addJavascriptInterface(ImmersiveToggleInterface { isImmersiveMode = !isImmersiveMode }, "AndroidInterface")
                                        webViewClient = WebViewClient()
                                        webViewRef = this
                                    }
                                },
                                update = { view ->
                                    if (view.tag != htmlContent) {
                                        view.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
                                        view.tag = htmlContent
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
                is OfficeReaderUiState.PasswordRequired -> {
                    var password by remember { mutableStateOf("") }
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                            NexusText("This document is password protected", color = NexusTheme.colors.textPrimary)
                            Spacer(modifier = Modifier.height(16.dp))
                            androidx.compose.material3.OutlinedTextField(
                                value = password,
                                onValueChange = { password = it },
                                label = { NexusText("Password", color = NexusTheme.colors.textSecondary) },
                                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                NexusButton(text = "Cancel", onClick = onBack)
                                NexusButton(text = "Open", onClick = {
                                    if (password.isNotEmpty()) {
                                        viewModel.loadDocument(state.encodedUri, state.docType, password)
                                    }
                                })
                            }
                        }
                    }
                }
                is OfficeReaderUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            NexusText(state.message, color = NexusTheme.colors.error)
                            Spacer(modifier = Modifier.height(16.dp))
                            NexusButton(text = "Go Back", onClick = onBack)
                        }
                    }
                }
            } // End when(state)
            } // End AnimatedContent
            
            // Bottom Navigation Bar
            AnimatedVisibility(
                visible = !isImmersiveMode && uiState is OfficeReaderUiState.DocxReady,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                NexusSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 24.dp)
                        .navigationBarsPadding(),
                    shape = NexusTheme.shapes.pill,
                    color = NexusTheme.colors.surfaceVariant,
                    elevation = 16.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Print Layout Mode
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { isPrintLayout = !isPrintLayout }.padding(8.dp)) {
                            Icon(painter = androidx.compose.ui.res.painterResource(id = if (isPrintLayout) com.nexus.core.R.drawable.ic_printer else com.nexus.core.R.drawable.ic_world), contentDescription = "Layout", tint = if (isPrintLayout) NexusTheme.colors.primary else NexusTheme.colors.textPrimary)
                            NexusText(if (isPrintLayout) "Print" else "Web", style = NexusTheme.typography.caption, color = if (isPrintLayout) NexusTheme.colors.primary else NexusTheme.colors.textPrimary)
                        }
                        // Immersive Mode
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { isImmersiveMode = true }.padding(8.dp)) {
                            Icon(painter = androidx.compose.ui.res.painterResource(id = com.nexus.core.R.drawable.ic_maximize), contentDescription = "Immersive", tint = NexusTheme.colors.textPrimary)
                            NexusText("Focus", style = NexusTheme.typography.caption, color = NexusTheme.colors.textPrimary)
                        }
                        // Theme Toggle
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable {
                            isDarkThemeOverride = !isDark
                        }.padding(8.dp)) {
                            Icon(painter = androidx.compose.ui.res.painterResource(id = if (isDark) com.nexus.core.R.drawable.ic_theme_light else com.nexus.core.R.drawable.ic_theme_dark), contentDescription = "Theme", tint = NexusTheme.colors.textPrimary)
                            NexusText("Theme", style = NexusTheme.typography.caption, color = NexusTheme.colors.textPrimary)
                        }
                        // Share
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable {
                            val uri = android.net.Uri.parse(URLDecoder.decode(encodedUri, "UTF-8"))
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "*/*"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share Document"))
                        }.padding(8.dp)) {
                            Icon(painter = androidx.compose.ui.res.painterResource(id = com.nexus.core.R.drawable.ic_share), contentDescription = "Share", tint = NexusTheme.colors.textPrimary)
                            NexusText("Share", style = NexusTheme.typography.caption, color = NexusTheme.colors.textPrimary)
                        }
                    }
                }
            }
        
        if (showInfoDialog && (uiState is OfficeReaderUiState.DocxReady || uiState is OfficeReaderUiState.XlsxReady)) {
            val docxState = uiState as? OfficeReaderUiState.DocxReady
            val xlsxState = uiState as? OfficeReaderUiState.XlsxReady
            AlertDialog(
                onDismissRequest = { showInfoDialog = false },
                title = { NexusText("Document Info", style = NexusTheme.typography.h2, color = NexusTheme.colors.textPrimary) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        NexusText("File: $displayName", color = NexusTheme.colors.textPrimary)
                        if (docxState != null) {
                            NexusText("Author: ${docxState.author ?: "Unknown"}", color = NexusTheme.colors.textPrimary)
                            NexusText("Created: ${docxState.creationDate ?: "Unknown"}", color = NexusTheme.colors.textPrimary)
                            NexusText("Words: ${docxState.wordCount ?: 0}", color = NexusTheme.colors.textPrimary)
                        } else if (xlsxState != null) {
                            NexusText("Sheets: ${xlsxState.sheetNames.size}", color = NexusTheme.colors.textPrimary)
                            val totalRows = xlsxState.sheetsRows.sumOf { it.size }
                            NexusText("Total Rows: $totalRows", color = NexusTheme.colors.textPrimary)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showInfoDialog = false }) {
                        NexusText("OK", color = NexusTheme.colors.primary)
                    }
                },
                containerColor = NexusTheme.colors.surface
            )
        }

        if (showRenameDialog) {
            AlertDialog(
                onDismissRequest = { showRenameDialog = false },
                title = { NexusText("Rename File") },
                text = {
                    androidx.compose.foundation.text.BasicTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        textStyle = NexusTheme.typography.body.copy(color = NexusTheme.colors.textPrimary),
                        modifier = Modifier.fillMaxWidth().padding(8.dp).background(NexusTheme.colors.surfaceVariant, NexusTheme.shapes.small).padding(16.dp),
                        singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showRenameDialog = false
                            android.widget.Toast.makeText(context, "Rename logged: $renameText", android.widget.Toast.LENGTH_LONG).show()
                        }
                    ) {
                        NexusText("Rename", color = NexusTheme.colors.primary)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRenameDialog = false }) {
                        NexusText("Cancel", color = NexusTheme.colors.textSecondary)
                    }
                }
            )
        }

        if (showMenu) {
            OfficeOptionsBottomSheet(
                onDismiss = { showMenu = false },
                onRename = {
                    showMenu = false
                    renameText = displayName
                    showRenameDialog = true
                },
                onFavorite = {
                    showMenu = false
                    android.widget.Toast.makeText(context, "Added to Favorites", android.widget.Toast.LENGTH_SHORT).show()
                },
                onPrint = {
                    showMenu = false
                    webViewRef?.let {
                        val printManager = context.getSystemService(android.content.Context.PRINT_SERVICE) as android.print.PrintManager
                        val jobName = "Print - $displayName"
                        val printAdapter = it.createPrintDocumentAdapter(jobName)
                        printManager.print(jobName, printAdapter, android.print.PrintAttributes.Builder().build())
                    }
                },
                onInfo = {
                    showMenu = false
                    showInfoDialog = true
                }
            )
        }

        AnimatedVisibility(
                visible = !isImmersiveMode,
                modifier = Modifier.align(Alignment.TopCenter),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                if (isSearchActive) {
                    NexusSurface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        shape = NexusTheme.shapes.pill,
                        color = NexusTheme.colors.surfaceVariant,
                        elevation = 8.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.foundation.text.BasicTextField(
                                value = searchQuery,
                                onValueChange = { 
                                    searchQuery = it
                                    webViewRef?.findAllAsync(it)
                                },
                                modifier = Modifier.weight(1f),
                                textStyle = NexusTheme.typography.body.copy(color = NexusTheme.colors.textPrimary),
                                singleLine = true,
                                decorationBox = { innerTextField ->
                                    Box {
                                        if (searchQuery.isEmpty()) {
                                            NexusText("Search...", color = NexusTheme.colors.textSecondary)
                                        }
                                        innerTextField()
                                    }
                                }
                            )
                            if (searchResultCount > 0) {
                                NexusText("${currentSearchIndex + 1}/$searchResultCount", color = NexusTheme.colors.textSecondary, modifier = Modifier.padding(horizontal = 8.dp))
                            }
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Previous", modifier = Modifier.clickable { webViewRef?.findNext(false) }.padding(8.dp), tint = NexusTheme.colors.textPrimary)
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next", modifier = Modifier.clickable { webViewRef?.findNext(true) }.padding(8.dp), tint = NexusTheme.colors.textPrimary)
                            Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.clickable { 
                                isSearchActive = false
                                searchQuery = ""
                                webViewRef?.clearMatches()
                            }.padding(8.dp), tint = NexusTheme.colors.textPrimary)
                        }
                    }
                } else {
                    NexusTopBar(
                        title = displayName,
                        outerVerticalPadding = 4.dp,
                        innerVerticalPadding = 8.dp,
                        iconSize = 40.dp,
                        navigationIcon = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .clickable { onBack() },
                                contentAlignment = Alignment.Center
                            ) {
                                androidx.compose.foundation.Image(
                                    painter = androidx.compose.ui.res.painterResource(id = com.nexus.core.R.drawable.ic_back),
                                    contentDescription = "Back",
                                    modifier = Modifier.size(20.dp),
                                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(com.nexus.core.theme.NexusTheme.colors.textPrimary)
                                )
                            }
                        },
                        actions = {
                            if (uiState is OfficeReaderUiState.DocxReady || uiState is OfficeReaderUiState.XlsxReady) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search",
                                    modifier = Modifier
                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                        .clickable { isSearchActive = true }
                                        .padding(8.dp),
                                    tint = NexusTheme.colors.textPrimary
                                )
                            }
                            androidx.compose.material3.IconButton(onClick = { showMenu = true }, modifier = Modifier.size(40.dp)) {
                                Icon(
                                    painter = androidx.compose.ui.res.painterResource(id = com.nexus.core.R.drawable.ic_more_vert),
                                    contentDescription = "More options",
                                    tint = NexusTheme.colors.textPrimary
                                )
                            }
                        }
                    )
                }
            }
    }
}

private class ImmersiveToggleInterface(private val onToggle: () -> Unit) {
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    @android.webkit.JavascriptInterface
    fun toggle() {
        handler.post { onToggle() }
    }
}

@Composable
private fun OfficeOptionsBottomSheet(
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onFavorite: () -> Unit,
    onPrint: () -> Unit,
    onInfo: () -> Unit
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.BottomCenter
        ) {
            com.nexus.core.ui.NexusSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                        onClick = {} // Prevent clicks on the dialog from dismissing it
                    ),
                shape = com.nexus.core.theme.NexusTheme.shapes.large,
                elevation = 24.dp,
                color = com.nexus.core.theme.NexusTheme.colors.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(32.dp)
                            .height(4.dp)
                            .clip(com.nexus.core.theme.NexusTheme.shapes.pill)
                            .background(com.nexus.core.theme.NexusTheme.colors.textSecondary.copy(alpha = 0.2f))
                            .align(Alignment.CenterHorizontally)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))

                    com.nexus.core.ui.NexusText(
                        text = "Document Options",
                        style = com.nexus.core.theme.NexusTheme.typography.title,
                        modifier = Modifier.padding(bottom = 16.dp, start = 8.dp, end = 8.dp)
                    )

                    val options = listOf(
                        MenuOption("File Info", "View document properties", com.nexus.core.R.drawable.ic_info, onInfo),
                        MenuOption("Rename", "Rename this file", com.nexus.core.R.drawable.ic_rename, onRename),
                        MenuOption("Favorite", "Add to bookmarks", com.nexus.core.R.drawable.ic_star, onFavorite),
                        MenuOption("Print", "Print or save as PDF", com.nexus.core.R.drawable.ic_printer, onPrint)
                    )

                    options.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(com.nexus.core.theme.NexusTheme.shapes.medium)
                                .clickable { option.action() }
                                .padding(vertical = 12.dp, horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.foundation.Image(
                                painter = androidx.compose.ui.res.painterResource(id = option.icon),
                                contentDescription = option.label,
                                modifier = Modifier.size(24.dp),
                                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(com.nexus.core.theme.NexusTheme.colors.textPrimary)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                com.nexus.core.ui.NexusText(
                                    text = option.label,
                                    style = com.nexus.core.theme.NexusTheme.typography.body,
                                    color = com.nexus.core.theme.NexusTheme.colors.textPrimary
                                )
                                com.nexus.core.ui.NexusText(
                                    text = option.subtitle,
                                    style = com.nexus.core.theme.NexusTheme.typography.caption,
                                    color = com.nexus.core.theme.NexusTheme.colors.textSecondary
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

private data class MenuOption(
    val label: String,
    val subtitle: String,
    val icon: Int,
    val action: () -> Unit
)
