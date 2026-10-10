package com.nexus.feature.reader.office

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusSurface
import com.nexus.core.ui.NexusText
import com.nexus.core.ui.animations.shimmerEffect
import com.nexus.core.ui.animations.springBounceClick
import com.nexus.core.ui.components.NexusButton
import com.nexus.core.ui.components.NexusTopBar
import com.nexus.core.ui.utils.glassBackground
import com.nexus.core.util.toUserFriendlyMessage
import java.net.URLDecoder
import android.content.Intent
import android.print.PrintAttributes
import android.print.PrintManager

@androidx.compose.material3.ExperimentalMaterial3Api
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

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var isImmersiveMode by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    
    val keepScreenAwake by viewModel.keepScreenAwake.collectAsStateWithLifecycle()
    val isStarred by viewModel.isStarred.collectAsStateWithLifecycle()

    DisposableEffect(keepScreenAwake) {
        val window = activity?.window
        if (keepScreenAwake) {
            window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    androidx.compose.runtime.LaunchedEffect(isImmersiveMode) {
        activity?.window?.let { window ->
            val controller = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            if (isImmersiveMode) {
                controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                controller.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    val bottomInset = androidx.compose.foundation.layout.WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val topPadding by animateDpAsState(targetValue = if (isImmersiveMode) 16.dp else 90.dp)
    val bottomPadding by animateDpAsState(targetValue = if (isImmersiveMode) bottomInset else bottomInset + 100.dp)

    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResultCount by remember { mutableStateOf(0) }
    var currentSearchIndex by remember { mutableStateOf(0) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.destroy()
        }
    }
    var isWebView by remember { mutableStateOf(true) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showSheetNavigator by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    
    val systemDarkTheme = isSystemInDarkTheme()
    // Default to light theme as requested
    var isDarkThemeOverride by remember { mutableStateOf<Boolean?>(false) }
    val isDark = isDarkThemeOverride ?: systemDarkTheme

    var isFreezePanesEnabled by remember { mutableStateOf(true) }
    var showJumpColumnDialog by remember { mutableStateOf(false) }
    var jumpColumnInput by remember { mutableStateOf("") }
    
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    var showBookmarksSheet by remember { mutableStateOf(false) }
    var showOutlineNavigator by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(if (isDark) com.nexus.core.theme.darkNexusColors.background else com.nexus.core.theme.lightNexusColors.background)
    ) {
        AnimatedContent(
            targetState = uiState,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                fadeIn(animationSpec = tween(220, delayMillis = 90)) togetherWith fadeOut(animationSpec = tween(90))
            },
                label = "officeReaderState"
            ) { state ->
                when (state) {
                    is OfficeReaderUiState.Loading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = NexusTheme.spacing.lg, vertical = NexusTheme.spacing.xxl),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.md)
                            ) {
                                if (state.message != null || docType.equals("XLSX", ignoreCase = true)) {
                                    androidx.compose.material3.CircularProgressIndicator(color = NexusTheme.colors.primary)
                                    Spacer(modifier = Modifier.height(16.dp))
                                    NexusText(
                                        text = state.message ?: "Parsing Excel Document...",
                                        color = NexusTheme.colors.textPrimary,
                                        style = NexusTheme.typography.h2
                                    )
                                    if (state.progress != null) {
                                        androidx.compose.material3.LinearProgressIndicator(
                                            progress = { state.progress },
                                            modifier = Modifier.fillMaxWidth(0.6f).height(8.dp).clip(androidx.compose.foundation.shape.CircleShape),
                                            color = NexusTheme.colors.primary
                                        )
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.md)
                                    ) {
                                        items(20) { index ->
                                            val widthFraction = when {
                                                index % 5 == 0 -> 0.4f
                                                index % 3 == 0 -> 0.7f
                                                else -> 0.9f
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth(widthFraction)
                                                    .height(16.dp)
                                                    .clip(com.nexus.core.theme.NexusTheme.shapes.extraSmall)
                                                    .shimmerEffect()
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    is OfficeReaderUiState.DocxReady -> {
                    val printCss = if (isWebView) """
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
                            padding: 16px;
                            padding-top: 120px;
                            padding-bottom: 100px;
                            background-color: transparent !important;
                        }
                    """.trimIndent()

                    val htmlWithScript = remember(state.htmlContent, isWebView, isDark) {
                        var html = state.htmlContent
                        if (isDark) {
                            html = html.replace("<body>", "<body class=\"dark-mode\">")
                        }
                        val extraStyle = "<style>\n" +
                            (if (isDark) "body.dark-mode, body.dark-mode * { color: #e0e0e0 !important; }\n" else "") +
                            "$printCss\n</style>"
                        val script = "<script>document.addEventListener('click', function(e) { if (e.target.tagName !== 'A' && !e.target.closest('a')) { AndroidInterface.toggle(); } });</script>"
                        
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
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                view.settings.forceDark = if (isDark) android.webkit.WebSettings.FORCE_DARK_ON else android.webkit.WebSettings.FORCE_DARK_OFF
                            }
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
                    
                    val htmlContent = remember(rows, columnWidths, state.showGridlines, isDark, isFreezePanesEnabled) {
                        buildString {
                            append("<!DOCTYPE html><html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=5.0, user-scalable=yes\">")
                            append("<style>")
                            append("body { font-family: sans-serif; font-size: 14px; margin: 0; padding: 16px; background-color: transparent; color: $textColor !important; } ")
                            append("body * { color: $textColor !important; } ")
                            append("table { border-collapse: separate; border-spacing: 0; background-color: transparent; margin-bottom: 24px; } ")
                            append("td, th { border: ${if(state.showGridlines) "1px solid #888888" else "none"}; padding: 8px 12px; white-space: nowrap; background-color: ${if(isDark) "#1e1e1e" else "#ffffff"}; } ")
                            append("tr:hover td { background-color: ${if(isDark) "#333333" else "#f5f5f5"} !important; } ")
                            if (isFreezePanesEnabled) {
                                append("tr:first-child td { position: sticky; top: 0; z-index: 2; font-weight: bold; box-shadow: 0 2px 2px -1px rgba(0, 0, 0, 0.4); } ")
                                append("td:first-child { position: sticky; left: 0; z-index: 1; font-weight: bold; box-shadow: 2px 0 2px -1px rgba(0, 0, 0, 0.4); } ")
                                append("tr:first-child td:first-child { z-index: 3; box-shadow: 2px 2px 2px -1px rgba(0, 0, 0, 0.4); } ")
                            } else {
                                append("tr:first-child td, td:first-child, tr:first-child td:first-child { font-weight: bold; } ")
                            }
                            append("::-webkit-scrollbar { height: 16px; width: 16px; } ")
                            append("::-webkit-scrollbar-track { background: ${if(isDark) "#121212" else "#f1f1f1"}; } ")
                            append("::-webkit-scrollbar-thumb { background: ${if(isDark) "#666" else "#888"}; border-radius: 8px; border: 4px solid ${if(isDark) "#121212" else "#f1f1f1"}; } ")
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
                            append("<script>document.addEventListener('click', function(e) { if (e.target.tagName !== 'A' && !e.target.closest('a')) { AndroidInterface.toggle(); } });</script>")
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
                                modifier = Modifier.fillMaxWidth().padding(horizontal = NexusTheme.spacing.md, vertical = NexusTheme.spacing.xs),
                                horizontalArrangement = Arrangement.Start
                            ) {
                                Box(
                                    modifier = Modifier
                                        .background(NexusTheme.colors.surfaceVariant, NexusTheme.shapes.pill)
                                        .clickable { showSheetNavigator = true }
                                        .padding(horizontal = NexusTheme.spacing.md, vertical = NexusTheme.spacing.xs)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            painter = androidx.compose.ui.res.painterResource(id = com.nexus.core.R.drawable.ic_view_list),
                                            contentDescription = "Sheets",
                                            tint = NexusTheme.colors.textPrimary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        NexusText(
                                            text = state.sheetNames.getOrNull(state.currentSheet) ?: "Sheet 1",
                                            color = NexusTheme.colors.textPrimary,
                                            style = NexusTheme.typography.buttonLabel
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(painter = painterResource(id = com.nexus.core.R.drawable.ic_arrow_right), contentDescription = null, tint = NexusTheme.colors.textPrimary, modifier = Modifier.size(14.dp))
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
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                        view.settings.forceDark = if (isDark) android.webkit.WebSettings.FORCE_DARK_ON else android.webkit.WebSettings.FORCE_DARK_OFF
                                    }
                                    if (view.tag != htmlContent) {
                                        view.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
                                        view.tag = htmlContent
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                            
                            // Horizontal scroll hint
                            var showScrollHint by remember { mutableStateOf(true) }
                            LaunchedEffect(state.currentSheet) {
                                showScrollHint = true
                                kotlinx.coroutines.delay(3000)
                                showScrollHint = false
                            }
                            
                            androidx.compose.animation.AnimatedVisibility(
                                visible = showScrollHint && !isImmersiveMode,
                                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = NexusTheme.spacing.xxl),
                                enter = fadeIn(),
                                exit = fadeOut()
                            ) {
                                NexusSurface(
                                    shape = NexusTheme.shapes.pill,
                                    color = NexusTheme.colors.primary.copy(alpha = 0.9f),
                                    modifier = Modifier.padding(NexusTheme.spacing.md),
                                    elevation = 8.dp
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = NexusTheme.spacing.md, vertical = NexusTheme.spacing.xs),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs)
                                    ) {
                                        Icon(
                                            painter = androidx.compose.ui.res.painterResource(id = com.nexus.core.R.drawable.ic_view_list),
                                            contentDescription = null,
                                            tint = NexusTheme.colors.onPrimary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        NexusText(
                                            "Swipe horizontally to view more",
                                            style = NexusTheme.typography.caption,
                                            color = NexusTheme.colors.onPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                is OfficeReaderUiState.PasswordRequired -> {
                    var password by remember { mutableStateOf("") }
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(NexusTheme.spacing.md)) {
                            NexusText("This document is password protected", color = NexusTheme.colors.textPrimary)
                            Spacer(modifier = Modifier.height(16.dp))
                            androidx.compose.material3.OutlinedTextField(
                                value = password,
                                onValueChange = { password = it },
                                label = { NexusText("Password", color = NexusTheme.colors.textSecondary) },
                                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                singleLine = true,
                                isError = state.isError,
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (state.isError) {
                                Spacer(modifier = Modifier.height(4.dp))
                                NexusText("Incorrect password", color = NexusTheme.colors.error, style = NexusTheme.typography.caption)
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs)) {
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
                            NexusText(state.message.toUserFriendlyMessage(), color = NexusTheme.colors.error)
                            Spacer(modifier = Modifier.height(16.dp))
                            NexusButton(text = "Go Back", onClick = onBack)
                        }
                    }
                }
            } // End when(state)
            } // End AnimatedContent
            
            // Bottom Navigation Container
            AnimatedVisibility(
                visible = !isImmersiveMode && (uiState is OfficeReaderUiState.DocxReady || uiState is OfficeReaderUiState.XlsxReady),
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = expandVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) + fadeIn(),
                exit = shrinkVertically(animationSpec = spring(stiffness = Spring.StiffnessMedium)) + fadeOut()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs)
                ) {
                    // Floating Position / Mode Badge
                    if (uiState is OfficeReaderUiState.XlsxReady) {
                        val xlsxState = uiState as OfficeReaderUiState.XlsxReady
                        val currentSheetName = xlsxState.sheetNames.getOrNull(xlsxState.currentSheet) ?: "Sheet 1"
                        Box(
                            modifier = Modifier
                                .shadow(
                                    elevation = 8.dp,
                                    shape = NexusTheme.shapes.pill,
                                    spotColor = NexusTheme.colors.primary.copy(alpha = 0.16f),
                                    ambientColor = Color.Black.copy(alpha = 0.10f)
                                )
                                .clip(NexusTheme.shapes.pill)
                                .background(NexusTheme.colors.surface)
                                .border(
                                    width = 1.dp,
                                    color = NexusTheme.colors.divider.copy(alpha = 0.7f),
                                    shape = NexusTheme.shapes.pill
                                )
                                .springBounceClick { showSheetNavigator = true }
                                .padding(horizontal = NexusTheme.spacing.md, vertical = NexusTheme.spacing.xs)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs)
                            ) {
                                Icon(
                                    painter = painterResource(id = com.nexus.core.R.drawable.ic_view_list),
                                    contentDescription = null,
                                    tint = NexusTheme.colors.primary,
                                    modifier = Modifier.size(15.dp)
                                )
                                NexusText(
                                    text = currentSheetName,
                                    style = NexusTheme.typography.body.copy(fontWeight = FontWeight.Bold, fontSize = 13.5.sp),
                                    color = NexusTheme.colors.primary
                                )
                                if (xlsxState.sheetNames.size > 1) {
                                    NexusText(
                                        text = "(${xlsxState.currentSheet + 1}/${xlsxState.sheetNames.size})",
                                        style = NexusTheme.typography.label,
                                        color = NexusTheme.colors.textSecondary
                                    )
                                }
                            }
                        }
                    } else if (uiState is OfficeReaderUiState.DocxReady) {
                        Box(
                            modifier = Modifier
                                .shadow(
                                    elevation = 8.dp,
                                    shape = NexusTheme.shapes.pill,
                                    spotColor = NexusTheme.colors.primary.copy(alpha = 0.16f),
                                    ambientColor = Color.Black.copy(alpha = 0.10f)
                                )
                                .clip(NexusTheme.shapes.pill)
                                .background(NexusTheme.colors.surface)
                                .border(
                                    width = 1.dp,
                                    color = NexusTheme.colors.divider.copy(alpha = 0.7f),
                                    shape = NexusTheme.shapes.pill
                                )
                                .springBounceClick { isWebView = !isWebView }
                                .padding(horizontal = NexusTheme.spacing.md, vertical = NexusTheme.spacing.xs)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs)
                            ) {
                                Icon(
                                    painter = painterResource(id = if (isWebView) com.nexus.core.R.drawable.ic_world else com.nexus.core.R.drawable.ic_book),
                                    contentDescription = null,
                                    tint = NexusTheme.colors.primary,
                                    modifier = Modifier.size(15.dp)
                                )
                                NexusText(
                                    text = if (isWebView) "Web View" else "Mobile View",
                                    style = NexusTheme.typography.body.copy(fontWeight = FontWeight.Bold, fontSize = 13.5.sp),
                                    color = NexusTheme.colors.primary
                                )
                            }
                        }
                    }

                    // Floating Dock Toolbar Container
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentWidth(Alignment.CenterHorizontally)
                            .widthIn(max = 560.dp)
                            .navigationBarsPadding()
                            .padding(horizontal = NexusTheme.spacing.sm, vertical = NexusTheme.spacing.xs)
                            .shadow(
                                elevation = 16.dp,
                                shape = NexusTheme.shapes.large,
                                spotColor = NexusTheme.colors.primary.copy(alpha = if (isDark) 0.28f else 0.14f),
                                ambientColor = Color.Black.copy(alpha = if (isDark) 0.40f else 0.10f)
                            )
                            .clip(NexusTheme.shapes.large)
                            .background(NexusTheme.colors.surface.copy(alpha = 0.95f))
                            .border(
                                width = 1.dp,
                                color = NexusTheme.colors.divider.copy(alpha = 0.65f),
                                shape = NexusTheme.shapes.large
                            )
                            .padding(horizontal = NexusTheme.spacing.xs, vertical = NexusTheme.spacing.xxs)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            if (uiState is OfficeReaderUiState.DocxReady) {
                                OfficeBottomBarActionItem(
                                    modifier = Modifier.weight(1f),
                                    iconPainter = painterResource(id = if (isWebView) com.nexus.core.R.drawable.ic_world else com.nexus.core.R.drawable.ic_book),
                                    label = if (isWebView) "Web" else "Page",
                                    contentDescription = "View Mode",
                                    tint = NexusTheme.colors.primary,
                                    isActive = isWebView,
                                    onClick = { isWebView = !isWebView }
                                )
                            }

                            if (uiState is OfficeReaderUiState.XlsxReady) {
                                OfficeBottomBarActionItem(
                                    modifier = Modifier.weight(1f),
                                    iconPainter = painterResource(id = com.nexus.core.R.drawable.ic_view_list),
                                    label = "Sheets",
                                    contentDescription = "Sheets",
                                    tint = NexusTheme.colors.textPrimary,
                                    onClick = { showSheetNavigator = true }
                                )
                                OfficeBottomBarActionItem(
                                    modifier = Modifier.weight(1f),
                                    iconPainter = painterResource(id = com.nexus.core.R.drawable.ic_view_grid),
                                    label = "Freeze",
                                    contentDescription = "Freeze Panes",
                                    tint = NexusTheme.colors.primary,
                                    isActive = isFreezePanesEnabled,
                                    activeBadgeColor = NexusTheme.colors.primary.copy(alpha = 0.16f),
                                    onClick = { isFreezePanesEnabled = !isFreezePanesEnabled }
                                )
                                OfficeBottomBarActionItem(
                                    modifier = Modifier.weight(1f),
                                    iconPainter = painterResource(id = com.nexus.core.R.drawable.ic_arrow_right),
                                    label = "Jump",
                                    contentDescription = "Jump Column",
                                    tint = NexusTheme.colors.textPrimary,
                                    onClick = { showJumpColumnDialog = true }
                                )
                            }

                            OfficeBottomBarActionItem(
                                modifier = Modifier.weight(1f),
                                iconPainter = painterResource(
                                    id = if (isStarred) com.nexus.core.R.drawable.ic_star_filled else com.nexus.core.R.drawable.ic_star
                                ),
                                label = if (isStarred) "Starred" else "Favorite",
                                contentDescription = "Favorite",
                                tint = Color(0xFFFFB300),
                                isActive = isStarred,
                                activeBadgeColor = Color(0xFFFFB300).copy(alpha = 0.16f),
                                onClick = {
                                    viewModel.toggleFavorite { nowStarred ->
                                        val msg = if (nowStarred) "Added to Favorites" else "Removed from Favorites"
                                        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )

                            OfficeBottomBarActionItem(
                                modifier = Modifier.weight(1f),
                                iconPainter = painterResource(
                                    id = if (isDark) com.nexus.core.R.drawable.ic_theme_light else com.nexus.core.R.drawable.ic_theme_dark
                                ),
                                label = if (isDark) "Light" else "Dark",
                                contentDescription = "Theme",
                                tint = NexusTheme.colors.textPrimary,
                                onClick = { isDarkThemeOverride = !isDark }
                            )

                            if (uiState is OfficeReaderUiState.DocxReady) {
                                OfficeBottomBarActionItem(
                                    modifier = Modifier.weight(1f),
                                    iconPainter = painterResource(id = com.nexus.core.R.drawable.ic_share),
                                    label = "Share",
                                    contentDescription = "Share",
                                    tint = NexusTheme.colors.textPrimary,
                                    onClick = {
                                        var rawUriStr = URLDecoder.decode(encodedUri, "UTF-8")
                                        if (rawUriStr.contains("%")) rawUriStr = URLDecoder.decode(rawUriStr, "UTF-8")
                                        val rawUri = android.net.Uri.parse(rawUriStr)
                                        val shareUri = if (rawUri.scheme == "file") {
                                             androidx.core.content.FileProvider.getUriForFile(
                                                context,
                                                "${context.packageName}.fileprovider",
                                                java.io.File(rawUri.path ?: "")
                                            )
                                        } else {
                                            rawUri
                                        }
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "*/*"
                                            putExtra(Intent.EXTRA_STREAM, shareUri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        val chooser = Intent.createChooser(intent, "Share Document").apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(chooser)
                                    }
                                )
                            }
                        }
                    }
                }
            }

        if (showJumpColumnDialog) {
            AlertDialog(
                onDismissRequest = { showJumpColumnDialog = false },
                title = { NexusText("Jump to Cell / Row / Column", color = NexusTheme.colors.textPrimary) },
                text = {
                    Column {
                        NexusText("Examples:\n• C150 (Cell)\n• C (Column)\n• 150 (Row)", color = NexusTheme.colors.textSecondary)
                        Spacer(modifier = Modifier.height(8.dp))
                        androidx.compose.material3.OutlinedTextField(
                            value = jumpColumnInput,
                            onValueChange = { jumpColumnInput = it.uppercase() },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        showJumpColumnDialog = false
                        val input = jumpColumnInput.trim()
                        if (input.isNotEmpty()) {
                            val match = Regex("^([A-Z]*)([0-9]*)$").find(input)
                            val letters = match?.groupValues?.getOrNull(1) ?: ""
                            val numbers = match?.groupValues?.getOrNull(2) ?: ""
                            
                            var colIndex = 0
                            if (letters.isNotEmpty()) {
                                for (char in letters) {
                                    colIndex = colIndex * 26 + (char - 'A' + 1)
                                }
                            }
                            val rowIndex = numbers.toIntOrNull() ?: 0

                            val js = """
                                var el = null;
                                if ($colIndex > 0 && $rowIndex > 0) {
                                    el = document.querySelector('tr:nth-child(' + $rowIndex + ') td:nth-child(' + $colIndex + ')');
                                } else if ($rowIndex > 0) {
                                    el = document.querySelector('tr:nth-child(' + $rowIndex + ')');
                                } else if ($colIndex > 0) {
                                    el = document.querySelector('tr td:nth-child(' + $colIndex + ')');
                                }
                                if (el) {
                                    el.scrollIntoView({behavior: 'smooth', block: 'center', inline: 'center'});
                                    var oldBg = el.style.backgroundColor;
                                    var oldTrans = el.style.transition;
                                    el.style.transition = 'background-color 0.5s';
                                    el.style.backgroundColor = 'rgba(255, 235, 59, 0.7)';
                                    setTimeout(function() {
                                        el.style.backgroundColor = oldBg;
                                        setTimeout(function() { el.style.transition = oldTrans; }, 500);
                                    }, 3000);
                                } else {
                                    AndroidInterface.toggle(); // Reusing interface to trigger something, or just fail silently
                                }
                            """.trimIndent()
                            webViewRef?.evaluateJavascript(js, null)
                        }
                        jumpColumnInput = ""
                    }) {
                        NexusText("Jump", color = NexusTheme.colors.primary)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { 
                        showJumpColumnDialog = false 
                        jumpColumnInput = ""
                    }) {
                        NexusText("Cancel", color = NexusTheme.colors.textSecondary)
                    }
                },
                containerColor = NexusTheme.colors.surface
            )
        }
        
        if (showInfoDialog && (uiState is OfficeReaderUiState.DocxReady || uiState is OfficeReaderUiState.XlsxReady)) {
            val docxState = uiState as? OfficeReaderUiState.DocxReady
            val xlsxState = uiState as? OfficeReaderUiState.XlsxReady
            val documentType = if (docType.equals("XLSX", ignoreCase = true)) {
                com.nexus.core.navigation.DocumentType.XLSX
            } else {
                com.nexus.core.navigation.DocumentType.DOCX
            }

            val customDetails = buildList {
                if (docxState != null) {
                    docxState.author?.takeIf { it.isNotBlank() }?.let {
                        add(com.nexus.core.ui.components.FileDetailItem(com.nexus.core.R.drawable.ic_home_outline, "Author", it))
                    }
                    docxState.creationDate?.takeIf { it.isNotBlank() }?.let {
                        add(com.nexus.core.ui.components.FileDetailItem(com.nexus.core.R.drawable.ic_startup, "Created", it))
                    }
                    docxState.wordCount?.let {
                        add(com.nexus.core.ui.components.FileDetailItem(com.nexus.core.R.drawable.ic_book, "Word count", "$it words"))
                    }
                    docxState.characterCount?.let {
                        add(com.nexus.core.ui.components.FileDetailItem(com.nexus.core.R.drawable.ic_text_scan, "Character count", "$it characters"))
                    }
                    docxState.readingTimeMinutes?.let {
                        add(com.nexus.core.ui.components.FileDetailItem(com.nexus.core.R.drawable.ic_update_progress, "Est. Reading time", "~$it minutes"))
                    }
                } else if (xlsxState != null) {
                    add(com.nexus.core.ui.components.FileDetailItem(com.nexus.core.R.drawable.ic_book, "Sheets", "${xlsxState.sheetNames.size} sheets"))
                    val totalRows = xlsxState.sheetsRows.sumOf { it.size }
                    add(com.nexus.core.ui.components.FileDetailItem(com.nexus.core.R.drawable.ic_maximize, "Total rows", "$totalRows rows"))
                }
            }

            com.nexus.core.ui.components.NexusFileDetailsSheet(
                fileName = displayName,
                uri = encodedUri,
                docType = documentType,
                customDetails = customDetails,
                onDismissRequest = { showInfoDialog = false }
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
                        modifier = Modifier.fillMaxWidth().padding(NexusTheme.spacing.xs).background(NexusTheme.colors.surfaceVariant, NexusTheme.shapes.small).padding(NexusTheme.spacing.md),
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
                displayName = displayName,
                docType = docType,
                isStarred = isStarred,
                onDismiss = { showMenu = false },
                onRename = {
                    showMenu = false
                    renameText = displayName
                    showRenameDialog = true
                },
                onFavorite = {
                    showMenu = false
                    viewModel.toggleFavorite { nowStarred ->
                        val msg = if (nowStarred) "Added to Favorites" else "Removed from Favorites"
                        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
                onPrint = {
                    showMenu = false
                    webViewRef?.let {
                        try {
                            val printManager = context.getSystemService(android.content.Context.PRINT_SERVICE) as android.print.PrintManager
                            val jobName = "Print - $displayName"
                            val printAdapter = it.createPrintDocumentAdapter(jobName)
                            printManager.print(jobName, printAdapter, android.print.PrintAttributes.Builder().build())
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(context, "Printing failed or is unsupported on this device.", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    } ?: run {
                        android.widget.Toast.makeText(context, "Document is not fully loaded.", android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
                onShare = {
                    showMenu = false
                    try {
                        val uriStr = java.net.URLDecoder.decode(java.net.URLDecoder.decode(encodedUri, "UTF-8"), "UTF-8")
                        val rawUri = android.net.Uri.parse(uriStr)
                        val shareUri = if (rawUri.scheme == "file") {
                            val file = java.io.File(rawUri.path ?: "")
                            androidx.core.content.FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                file
                            )
                        } else {
                            rawUri
                        }
                        val mime = if (docType.equals("XLSX", ignoreCase = true)) {
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                        } else {
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                        }
                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = mime
                            putExtra(android.content.Intent.EXTRA_STREAM, shareUri)
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(android.content.Intent.createChooser(intent, "Share Document").apply {
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        })
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(context, "Failed to share document: ${e.localizedMessage}", android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
                onInfo = {
                    showMenu = false
                    showInfoDialog = true
                },
                onBookmarks = {
                    showMenu = false
                    showBookmarksSheet = true
                },
                docxHeadingsCount = (uiState as? OfficeReaderUiState.DocxReady)?.headings?.size ?: 0
            )
        }
        
        if (showBookmarksSheet) {
            BookmarksBottomSheet(
                bookmarks = bookmarks,
                onAddBookmark = { label ->
                    webViewRef?.evaluateJavascript("Math.round(window.scrollY)") { result ->
                        val scrollY = result?.toIntOrNull() ?: 0
                        viewModel.addBookmark(label, scrollY)
                    }
                },
                onDeleteBookmark = { bk -> viewModel.deleteBookmark(bk) },
                onNavigateToBookmark = { bk ->
                    webViewRef?.evaluateJavascript("window.scrollTo({top: ${bk.pageIndex}, behavior: 'smooth'})", null)
                    showBookmarksSheet = false
                },
                onDismiss = { showBookmarksSheet = false }
            )
        }

        if (showOutlineNavigator && uiState is OfficeReaderUiState.DocxReady) {
            val docxState = uiState as OfficeReaderUiState.DocxReady
            OutlineNavigatorBottomSheet(
                headings = docxState.headings,
                onHeadingSelected = { anchorId ->
                    webViewRef?.evaluateJavascript("scrollToHeading('$anchorId')", null)
                    showOutlineNavigator = false
                },
                onDismiss = { showOutlineNavigator = false }
            )
        }
        
        if (showSheetNavigator && uiState is OfficeReaderUiState.XlsxReady) {
            val xlsxState = uiState as OfficeReaderUiState.XlsxReady
            SheetNavigatorBottomSheet(
                sheetNames = xlsxState.sheetNames,
                currentSheet = xlsxState.currentSheet,
                onSheetSelected = { index ->
                    viewModel.switchSheet(index)
                    showSheetNavigator = false
                },
                onDismiss = { showSheetNavigator = false }
            )
        }

        AnimatedVisibility(
            visible = !isImmersiveMode,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = androidx.compose.animation.slideInVertically(initialOffsetY = { -it }) + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { -it }) + androidx.compose.animation.fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
            ) {
                NexusTopBar(
                    title = displayName,
                    navigationIcon = {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
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
                            androidx.compose.foundation.Image(
                                painter = androidx.compose.ui.res.painterResource(id = com.nexus.core.R.drawable.ic_search),
                                contentDescription = "Search",
                                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(
                                    if (isSearchActive) com.nexus.core.theme.NexusTheme.colors.primary else com.nexus.core.theme.NexusTheme.colors.textPrimary
                                ),
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .springBounceClick { isSearchActive = !isSearchActive }
                                    .padding(NexusTheme.spacing.xs)
                            )
                        }
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(id = com.nexus.core.R.drawable.ic_more_vert),
                            contentDescription = "More options",
                            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(com.nexus.core.theme.NexusTheme.colors.textPrimary),
                            modifier = Modifier
                                .size(40.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .springBounceClick { showMenu = true }
                                .padding(NexusTheme.spacing.xs)
                        )
                    }
                )

                val (query, matchCount, currentIdx) = when (val state = uiState) {
                    is OfficeReaderUiState.XlsxReady -> Triple(state.searchQuery, state.searchMatches.size, state.currentMatchIndex)
                    else -> Triple(searchQuery, searchResultCount, currentSearchIndex)
                }

                com.nexus.core.ui.components.ReaderSearchBar(
                    visible = isSearchActive && !isImmersiveMode,
                    query = query,
                    onQueryChange = { newQuery ->
                        if (uiState is OfficeReaderUiState.XlsxReady) {
                            viewModel.setSearchQuery(newQuery)
                        } else {
                            searchQuery = newQuery
                            webViewRef?.findAllAsync(newQuery)
                        }
                    },
                    matchCount = matchCount,
                    currentMatchIndex = currentIdx,
                    onPreviousMatch = {
                        if (uiState is OfficeReaderUiState.XlsxReady) {
                            viewModel.previousSearchMatch()
                        } else {
                            webViewRef?.findNext(false)
                        }
                    },
                    onNextMatch = {
                        if (uiState is OfficeReaderUiState.XlsxReady) {
                            viewModel.nextSearchMatch()
                        } else {
                            webViewRef?.findNext(true)
                        }
                    },
                    onClose = {
                        isSearchActive = false
                        searchQuery = ""
                        webViewRef?.clearMatches()
                        viewModel.setSearchQuery("")
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
    displayName: String,
    docType: String,
    isStarred: Boolean,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onFavorite: () -> Unit,
    onShare: () -> Unit,
    onPrint: () -> Unit,
    onInfo: () -> Unit,
    onOutline: () -> Unit = {},
    onBookmarks: () -> Unit = {},
    docxHeadingsCount: Int = 0
) {
    val documentType = if (docType.equals("XLSX", ignoreCase = true)) {
        com.nexus.core.navigation.DocumentType.XLSX
    } else {
        com.nexus.core.navigation.DocumentType.DOCX
    }
    val metaText = if (documentType == com.nexus.core.navigation.DocumentType.XLSX) {
        "Spreadsheet Document · XLSX"
    } else {
        "Word Document · DOCX"
    }

    val group1 = listOf(
        com.nexus.core.ui.components.DocumentOptionItem(
            title = "File info",
            subtitle = "View document properties",
            iconRes = com.nexus.core.R.drawable.ic_info,
            onClick = onInfo
        ),
        com.nexus.core.ui.components.DocumentOptionItem(
            title = "Rename",
            subtitle = "Rename this file",
            iconRes = com.nexus.core.R.drawable.ic_rename,
            onClick = onRename
        ),
        com.nexus.core.ui.components.DocumentOptionItem(
            title = if (isStarred) "Starred" else "Star file",
            subtitle = "Add or remove bookmark",
            iconRes = if (isStarred) com.nexus.core.R.drawable.ic_star_filled else com.nexus.core.R.drawable.ic_star,
            isStarred = isStarred,
            showChevron = false,
            onClick = onFavorite
        )
    )
    
    val groupDocx = if (documentType == com.nexus.core.navigation.DocumentType.DOCX) {
        val list = mutableListOf<com.nexus.core.ui.components.DocumentOptionItem>()
        if (docxHeadingsCount > 0) {
            list.add(
                com.nexus.core.ui.components.DocumentOptionItem(
                    title = "Table of Contents",
                    subtitle = "$docxHeadingsCount sections available",
                    iconRes = com.nexus.core.R.drawable.ic_view_list,
                    onClick = onOutline
                )
            )
        }
        list.add(
            com.nexus.core.ui.components.DocumentOptionItem(
                title = "Bookmarks",
                subtitle = "Manage page bookmarks",
                iconRes = com.nexus.core.R.drawable.ic_tag,
                onClick = onBookmarks
            )
        )
        list
    } else emptyList()

    val group2 = listOf(
        com.nexus.core.ui.components.DocumentOptionItem(
            title = "Share",
            subtitle = "Share this document",
            iconRes = com.nexus.core.R.drawable.ic_share,
            onClick = onShare
        ),
        com.nexus.core.ui.components.DocumentOptionItem(
            title = "Print",
            subtitle = "Print or save as PDF",
            iconRes = com.nexus.core.R.drawable.ic_printer,
            onClick = onPrint
        )
    )

    com.nexus.core.ui.components.DocumentOptionsBottomSheet(
        sheetTitle = "Document Options",
        fileName = displayName,
        fileMeta = metaText,
        docType = documentType,
        groups = if (groupDocx.isNotEmpty()) listOf(group1, groupDocx, group2) else listOf(group1, group2),
        onDismiss = onDismiss
    )
}

@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
private fun OutlineNavigatorBottomSheet(
    headings: List<DocxHeading>,
    onHeadingSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = false)
    
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = com.nexus.core.theme.NexusTheme.colors.surface,
        contentColor = com.nexus.core.theme.NexusTheme.colors.textPrimary,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = NexusTheme.spacing.xl)
        ) {
            com.nexus.core.ui.NexusText(
                text = "Table of Contents",
                style = com.nexus.core.theme.NexusTheme.typography.title,
                modifier = Modifier.padding(horizontal = NexusTheme.spacing.lg, vertical = NexusTheme.spacing.xs)
            )
            if (headings.isEmpty()) {
                com.nexus.core.ui.NexusText(
                    text = "No outline available for this document.",
                    style = com.nexus.core.theme.NexusTheme.typography.body,
                    color = com.nexus.core.theme.NexusTheme.colors.textSecondary,
                    modifier = Modifier.padding(horizontal = NexusTheme.spacing.lg, vertical = NexusTheme.spacing.md)
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().padding(horizontal = NexusTheme.spacing.md)) {
                    items(headings) { heading ->
                        val indentPadding = ((heading.level - 1) * 16).dp
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(com.nexus.core.theme.NexusTheme.shapes.medium)
                                .clickable { onHeadingSelected(heading.anchorId) }
                                .padding(vertical = NexusTheme.spacing.sm, horizontal = NexusTheme.spacing.md)
                                .padding(start = indentPadding),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            com.nexus.core.ui.NexusText(
                                text = heading.text,
                                style = com.nexus.core.theme.NexusTheme.typography.body.copy(
                                    fontWeight = if (heading.level == 1) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal
                                ),
                                color = com.nexus.core.theme.NexusTheme.colors.textPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}

@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
private fun BookmarksBottomSheet(
    bookmarks: List<com.nexus.feature.dashboard.data.DocumentBookmark>,
    onAddBookmark: (String) -> Unit,
    onDeleteBookmark: (com.nexus.feature.dashboard.data.DocumentBookmark) -> Unit,
    onNavigateToBookmark: (com.nexus.feature.dashboard.data.DocumentBookmark) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var isAdding by remember { mutableStateOf(false) }
    var newLabel by remember { mutableStateOf("") }
    
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = com.nexus.core.theme.NexusTheme.colors.surface,
        contentColor = com.nexus.core.theme.NexusTheme.colors.textPrimary,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = NexusTheme.spacing.xl)
        ) {
            com.nexus.core.ui.NexusText(
                text = "Bookmarks",
                style = com.nexus.core.theme.NexusTheme.typography.title,
                modifier = Modifier.padding(horizontal = NexusTheme.spacing.lg, vertical = NexusTheme.spacing.xs)
            )
            if (isAdding) {
                Column(modifier = Modifier.padding(horizontal = NexusTheme.spacing.lg, vertical = NexusTheme.spacing.xs)) {
                    androidx.compose.material3.OutlinedTextField(
                        value = newLabel,
                        onValueChange = { newLabel = it },
                        label = { com.nexus.core.ui.NexusText("Bookmark name (e.g. Page 5, Chapter 2)", color = com.nexus.core.theme.NexusTheme.colors.textSecondary) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(NexusTheme.spacing.md)) {
                        com.nexus.core.ui.components.NexusButton(
                            text = "Cancel",
                            onClick = { isAdding = false }
                        )
                        com.nexus.core.ui.components.NexusButton(
                            text = "Save",
                            onClick = {
                                if (newLabel.isNotBlank()) {
                                    onAddBookmark(newLabel)
                                    isAdding = false
                                    newLabel = ""
                                }
                            }
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = NexusTheme.spacing.lg, vertical = NexusTheme.spacing.xs),
                    horizontalArrangement = Arrangement.End
                ) {
                    com.nexus.core.ui.components.NexusButton(
                        text = "+ Add Bookmark",
                        onClick = { isAdding = true }
                    )
                }
                if (bookmarks.isEmpty()) {
                    com.nexus.core.ui.NexusText(
                        text = "No bookmarks saved for this document.",
                        style = com.nexus.core.theme.NexusTheme.typography.body,
                        color = com.nexus.core.theme.NexusTheme.colors.textSecondary,
                        modifier = Modifier.padding(horizontal = NexusTheme.spacing.lg, vertical = NexusTheme.spacing.md)
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().padding(horizontal = NexusTheme.spacing.md)) {
                        items(bookmarks) { bk ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(com.nexus.core.theme.NexusTheme.shapes.medium)
                                    .clickable { onNavigateToBookmark(bk) }
                                    .padding(vertical = NexusTheme.spacing.sm, horizontal = NexusTheme.spacing.md),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = androidx.compose.ui.res.painterResource(id = com.nexus.core.R.drawable.ic_tag),
                                    contentDescription = null,
                                    tint = com.nexus.core.theme.NexusTheme.colors.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    com.nexus.core.ui.NexusText(
                                        text = bk.label,
                                        style = com.nexus.core.theme.NexusTheme.typography.body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                    )
                                    com.nexus.core.ui.NexusText(
                                        text = "Position: ${bk.pageIndex}",
                                        style = com.nexus.core.theme.NexusTheme.typography.caption,
                                        color = com.nexus.core.theme.NexusTheme.colors.textSecondary
                                    )
                                }
                                Icon(
                                    painter = androidx.compose.ui.res.painterResource(id = com.nexus.core.R.drawable.ic_delete),
                                    contentDescription = "Delete",
                                    tint = com.nexus.core.theme.NexusTheme.colors.error,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clickable { onDeleteBookmark(bk) }
                                        .padding(NexusTheme.spacing.xxs)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
private fun SheetNavigatorBottomSheet(
    sheetNames: List<String>,
    currentSheet: Int,
    onSheetSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState()
    
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = com.nexus.core.theme.NexusTheme.colors.surface,
        contentColor = com.nexus.core.theme.NexusTheme.colors.textPrimary,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = NexusTheme.spacing.xl)
        ) {
            com.nexus.core.ui.NexusText(
                text = "Sheets",
                style = com.nexus.core.theme.NexusTheme.typography.title,
                modifier = Modifier.padding(horizontal = NexusTheme.spacing.lg, vertical = NexusTheme.spacing.xs)
            )
            LazyColumn(modifier = Modifier.fillMaxWidth().padding(horizontal = NexusTheme.spacing.md)) {
                itemsIndexed(sheetNames) { index, name ->
                    val isSelected = index == currentSheet
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(com.nexus.core.theme.NexusTheme.shapes.medium)
                            .background(if (isSelected) com.nexus.core.theme.NexusTheme.colors.primary.copy(alpha = 0.1f) else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable { onSheetSelected(index) }
                            .padding(vertical = NexusTheme.spacing.md, horizontal = NexusTheme.spacing.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(id = com.nexus.core.R.drawable.ic_view_list),
                            contentDescription = null,
                            tint = if (isSelected) com.nexus.core.theme.NexusTheme.colors.primary else com.nexus.core.theme.NexusTheme.colors.textSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        com.nexus.core.ui.NexusText(
                            text = name,
                            style = if (isSelected) com.nexus.core.theme.NexusTheme.typography.body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) else com.nexus.core.theme.NexusTheme.typography.body,
                            color = if (isSelected) com.nexus.core.theme.NexusTheme.colors.primary else com.nexus.core.theme.NexusTheme.colors.textPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OfficeBottomBarActionItem(
    label: String,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconPainter: Painter? = null,
    isActive: Boolean = false,
    activeBadgeColor: Color = NexusTheme.colors.primary.copy(alpha = 0.14f),
    enabled: Boolean = true
) {
    val haptic = LocalHapticFeedback.current
    val animatedBadgeColor by animateColorAsState(
        targetValue = if (isActive) activeBadgeColor else Color.Transparent,
        animationSpec = tween(200),
        label = "officeBadgeColor"
    )
    val animatedTint by animateColorAsState(
        targetValue = if (isActive) tint else if (!enabled) NexusTheme.colors.textSecondary.copy(alpha = 0.35f) else NexusTheme.colors.textPrimary.copy(alpha = 0.85f),
        animationSpec = tween(200),
        label = "officeIconTint"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .clip(NexusTheme.shapes.medium)
            .springBounceClick(enabled = enabled) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 2.dp, vertical = NexusTheme.spacing.xxs)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(animatedBadgeColor),
            contentAlignment = Alignment.Center
        ) {
            if (iconPainter != null) {
                Image(
                    painter = iconPainter,
                    contentDescription = contentDescription,
                    colorFilter = ColorFilter.tint(animatedTint),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        NexusText(
            text = label,
            style = NexusTheme.typography.caption.copy(
                fontSize = 10.5.sp,
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium,
                letterSpacing = 0.15.sp
            ),
            color = animatedTint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
