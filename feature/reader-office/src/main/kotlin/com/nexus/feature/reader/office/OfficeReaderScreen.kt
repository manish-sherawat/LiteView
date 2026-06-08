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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NexusTheme.colors.background)
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
                    val isDark = isSystemInDarkTheme()
                    val textColor = if (isDark) "#e0e0e0" else "#1a1a1a"
                    val htmlWithScript = """
                        <!DOCTYPE html>
                        <html>
                        <head>
                            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=5.0, user-scalable=yes">
                            <style>
                                body {
                                    font-family: sans-serif;
                                    padding: 24px;
                                    padding-top: 120px;
                                    padding-bottom: 100px;
                                    line-height: 1.6;
                                    color: $textColor !important;
                                    background-color: transparent !important;
                                    word-wrap: break-word;
                                }
                                body * {
                                    color: $textColor !important;
                                    background-color: transparent !important;
                                }
                                p { margin-bottom: 16px; margin-top: 0; }
                                img { max-width: 100%; height: auto; }
                                table { border-collapse: collapse; width: 100%; background-color: transparent; }
                                td, th { border: 1px solid #888; padding: 8px; }
                            </style>
                        </head>
                        <body>
                            ${state.htmlContent}
                            <script>document.addEventListener('click', function() { AndroidInterface.toggle(); });</script>
                        </body>
                        </html>
                    """.trimIndent()
                    // Docx Viewer using AndroidView
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
                    
                    val isDark = isSystemInDarkTheme()
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
                                        addJavascriptInterface(ImmersiveToggleInterface { isImmersiveMode = !isImmersiveMode }, "AndroidInterface")
                                        webViewClient = WebViewClient()
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

        AnimatedVisibility(
                visible = !isImmersiveMode,
                modifier = Modifier.align(Alignment.TopCenter),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                NexusTopBar(
                    title = displayName,
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
                                modifier = Modifier.size(24.dp),
                                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(com.nexus.core.theme.NexusTheme.colors.textPrimary)
                            )
                        }
                    }
                )
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
