package com.nexus.feature.reader.office.excel

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexus.core.navigation.DocumentType
import com.nexus.core.navigation.LocalAnimatedVisibilityScope
import com.nexus.core.navigation.LocalSharedTransitionScope
import com.nexus.core.theme.NexusTheme
import com.nexus.core.theme.darkNexusColors
import com.nexus.core.theme.lightNexusColors
import com.nexus.core.ui.NexusSurface
import com.nexus.core.ui.NexusText
import com.nexus.core.ui.animations.shimmerEffect
import com.nexus.core.ui.animations.springBounceClick
import com.nexus.core.ui.animations.viewerBarEnterTransition
import com.nexus.core.ui.animations.viewerBarExitTransition
import com.nexus.core.ui.components.FileDetailItem
import com.nexus.core.ui.components.NexusButton
import com.nexus.core.ui.components.NexusFileDetailsSheet
import com.nexus.core.ui.components.NexusViewerActionButton
import com.nexus.core.ui.components.NexusViewerActionDivider
import com.nexus.core.ui.components.NexusViewerTopBar
import com.nexus.core.ui.components.ReaderSearchBar
import com.nexus.core.util.toUserFriendlyMessage
import com.nexus.feature.reader.office.common.ImmersiveToggleInterface
import com.nexus.feature.reader.office.common.OfficeBottomBarActionItem
import com.nexus.feature.reader.office.common.OfficePasswordPromptCard
import com.nexus.feature.reader.office.excel.components.ExcelOptionsBottomSheet
import com.nexus.feature.reader.office.excel.components.JumpColumnDialog
import com.nexus.feature.reader.office.excel.components.SheetNavigatorBottomSheet
import com.nexus.feature.reader.office.excel.model.ExcelReaderUiState
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.net.URLDecoder

@OptIn(
    androidx.compose.animation.ExperimentalSharedTransitionApi::class
)
@Composable
fun ExcelReaderScreen(
    encodedUri: String,
    fileName: String,
    docType: String = "XLSX",
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExcelReaderViewModel = hiltViewModel()
) {
    LaunchedEffect(encodedUri, docType) {
        viewModel.loadDocument(encodedUri, docType)
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val displayName = try { URLDecoder.decode(URLDecoder.decode(fileName, "UTF-8"), "UTF-8") } catch (_: Exception) { fileName }

    var isImmersiveMode by remember { mutableStateOf(false) }
    var readingProgress by remember { mutableFloatStateOf(0f) }
    val context = LocalContext.current
    val activity = context as? Activity

    val readerPrefs = remember { context.getSharedPreferences("nexus_reader_prefs", Context.MODE_PRIVATE) }
    var showPinchHint by remember {
        mutableStateOf(!readerPrefs.getBoolean("has_seen_pinch_zoom_hint", false))
    }
    LaunchedEffect(showPinchHint) {
        if (showPinchHint) {
            delay(4500)
            showPinchHint = false
            readerPrefs.edit().putBoolean("has_seen_pinch_zoom_hint", true).apply()
        }
    }

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

    LaunchedEffect(isImmersiveMode) {
        activity?.window?.let { window ->
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            if (isImmersiveMode) {
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    val topPadding by animateDpAsState(targetValue = if (isImmersiveMode) 16.dp else 90.dp)

    var isSearchActive by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.destroy()
        }
    }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showSheetNavigator by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }

    val systemDarkTheme = isSystemInDarkTheme()
    var isDarkThemeOverride by remember { mutableStateOf<Boolean?>(false) }
    val isDark = isDarkThemeOverride ?: systemDarkTheme

    var isFreezePanesEnabled by remember { mutableStateOf(true) }
    var showJumpColumnDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    fun showFeedback(msg: String) {
        coroutineScope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(msg)
        }
    }

    val decodedUri = remember(encodedUri) {
        try {
            URLDecoder.decode(URLDecoder.decode(encodedUri, "UTF-8"), "UTF-8")
        } catch (_: Exception) {
            encodedUri
        }
    }

    val hazeState = remember { HazeState() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(if (isDark) darkNexusColors.background else lightNexusColors.background)
            .hazeSource(state = hazeState)
    ) {
        AnimatedContent(
            targetState = uiState,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                fadeIn(animationSpec = tween(220, delayMillis = 90)) togetherWith fadeOut(animationSpec = tween(90))
            },
            label = "excelReaderState"
        ) { state ->
            when (state) {
                is ExcelReaderUiState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 90.dp, bottom = 24.dp, start = 16.dp, end = 16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isDark) darkNexusColors.surface else lightNexusColors.surface)
                                .border(1.dp, NexusTheme.colors.divider.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                repeat(4) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(28.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(NexusTheme.colors.surfaceVariant)
                                            .shimmerEffect()
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            repeat(14) { rowIndex ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    repeat(4) { colIndex ->
                                        val cellWidthFraction = if ((rowIndex + colIndex) % 3 == 0) 0.65f else 0.9f
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(22.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(NexusTheme.colors.surfaceVariant.copy(alpha = 0.6f))
                                                .fillMaxWidth(cellWidthFraction)
                                                .shimmerEffect()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                is ExcelReaderUiState.Ready -> {
                    val rows = state.sheetsRows.getOrElse(state.currentSheet) { emptyList() }
                    val columnWidths = state.sheetsColumnWidths.getOrElse(state.currentSheet) { emptyList() }
                    val textColor = if (isDark) "#e0e0e0" else "#1a1a1a"

                    val htmlContent = remember(rows, columnWidths, state.showGridlines, isDark, isFreezePanesEnabled) {
                        buildString {
                            append("<!DOCTYPE html><html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=5.0, user-scalable=yes\">")
                            append("<style>")
                            append("html, body { font-family: sans-serif; font-size: 14px; margin: 0; padding: 16px; width: 100%; background-color: transparent; color: $textColor !important; overscroll-behavior: contain; overflow: auto; } ")
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

                    Box(modifier = Modifier.fillMaxSize().padding(top = topPadding)) {
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
                                    settings.useWideViewPort = true
                                    settings.loadWithOverviewMode = true
                                    addJavascriptInterface(ImmersiveToggleInterface { isImmersiveMode = !isImmersiveMode }, "AndroidInterface")
                                    setOnScrollChangeListener { v, _, scrollY, _, oldScrollY ->
                                        val delta = scrollY - oldScrollY
                                        if (delta > 20 && !isImmersiveMode) {
                                            isImmersiveMode = true
                                        } else if (delta < -20 && isImmersiveMode) {
                                            isImmersiveMode = false
                                        }
                                        val wv = v as? WebView
                                        val totalContentHeight = (wv?.contentHeight?.toFloat() ?: 0f) * v.context.resources.displayMetrics.density
                                        val scrollRange = totalContentHeight - v.height
                                        if (scrollRange > 0f) {
                                            readingProgress = (scrollY.toFloat() / scrollRange).coerceIn(0f, 1f)
                                        }
                                    }
                                    webViewClient = WebViewClient()
                                    webViewRef = this
                                }
                            },
                            update = { view ->
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    view.settings.forceDark = if (isDark) WebSettings.FORCE_DARK_ON else WebSettings.FORCE_DARK_OFF
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
                            delay(3000)
                            showScrollHint = false
                        }

                        AnimatedVisibility(
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
                                        painter = painterResource(id = com.nexus.core.R.drawable.ic_arrow_left),
                                        contentDescription = null,
                                        tint = NexusTheme.colors.onPrimary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    NexusText(
                                        "Swipe horizontally to view more",
                                        style = NexusTheme.typography.caption,
                                        color = NexusTheme.colors.onPrimary
                                    )
                                    Icon(
                                        painter = painterResource(id = com.nexus.core.R.drawable.ic_arrow_right),
                                        contentDescription = null,
                                        tint = NexusTheme.colors.onPrimary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                is ExcelReaderUiState.PasswordRequired -> {
                    OfficePasswordPromptCard(
                        isError = state.isError,
                        onCancel = onBack,
                        onUnlock = { pwd ->
                            viewModel.loadDocument(state.encodedUri, state.docType, pwd)
                        },
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                is ExcelReaderUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            NexusText(state.message.toUserFriendlyMessage(), color = NexusTheme.colors.error)
                            Spacer(modifier = Modifier.height(16.dp))
                            NexusButton(text = "Go Back", onClick = onBack)
                        }
                    }
                }
            }
        }

        // Bottom Navigation Bar
        AnimatedVisibility(
            visible = !isImmersiveMode && (uiState is ExcelReaderUiState.Ready),
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = viewerBarEnterTransition(fromTop = false),
            exit = viewerBarExitTransition(toTop = false)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs)
            ) {
                // Floating Sheet Badge
                if (uiState is ExcelReaderUiState.Ready) {
                    val xlsxState = uiState as ExcelReaderUiState.Ready
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
                                contentDescription = "Sheet selector",
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
                }

                // Dock Toolbar Container
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
                                    showFeedback(msg)
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
                    }
                }
            }
        }

        // Jump Dialog
        if (showJumpColumnDialog) {
            JumpColumnDialog(
                onDismiss = { showJumpColumnDialog = false },
                onJump = { input ->
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
                        }
                    """.trimIndent()
                    webViewRef?.evaluateJavascript(js, null)
                }
            )
        }

        // Sheet Navigator Bottom Sheet
        if (showSheetNavigator && uiState is ExcelReaderUiState.Ready) {
            val xlsxState = uiState as ExcelReaderUiState.Ready
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

        // Info Dialog
        if (showInfoDialog && uiState is ExcelReaderUiState.Ready) {
            val xlsxState = uiState as ExcelReaderUiState.Ready
            val totalRows = xlsxState.sheetsRows.sumOf { it.size }
            val customDetails = listOf(
                FileDetailItem(com.nexus.core.R.drawable.ic_book, "Sheets", "${xlsxState.sheetNames.size} sheets"),
                FileDetailItem(com.nexus.core.R.drawable.ic_maximize, "Total rows", "$totalRows rows")
            )

            NexusFileDetailsSheet(
                fileName = displayName,
                uri = encodedUri,
                docType = DocumentType.XLSX,
                customDetails = customDetails,
                onDismissRequest = { showInfoDialog = false }
            )
        }

        // Rename Dialog
        if (showRenameDialog) {
            AlertDialog(
                onDismissRequest = { showRenameDialog = false },
                title = { NexusText("Rename File") },
                text = {
                    BasicTextField(
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
                            showFeedback("Rename logged: $renameText")
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

        // Options Bottom Sheet
        if (showMenu) {
            ExcelOptionsBottomSheet(
                displayName = displayName,
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
                        showFeedback(msg)
                    }
                },
                onPrint = {
                    showMenu = false
                    webViewRef?.let {
                        try {
                            val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                            val jobName = "Print - $displayName"
                            val printAdapter = it.createPrintDocumentAdapter(jobName)
                            printManager.print(jobName, printAdapter, PrintAttributes.Builder().build())
                        } catch (e: Exception) {
                            showFeedback("Printing failed or is unsupported on this device.")
                        }
                    } ?: run {
                        showFeedback("Document is not fully loaded.")
                    }
                },
                onShare = {
                    showMenu = false
                    try {
                        val uriStr = URLDecoder.decode(URLDecoder.decode(encodedUri, "UTF-8"), "UTF-8")
                        val rawUri = Uri.parse(uriStr)
                        val shareUri = if (rawUri.scheme == "file") {
                            FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                File(rawUri.path ?: "")
                            )
                        } else {
                            rawUri
                        }
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                            putExtra(Intent.EXTRA_STREAM, shareUri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share Document").apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        })
                    } catch (e: Exception) {
                        showFeedback("Failed to share document: ${e.localizedMessage}")
                    }
                },
                onInfo = {
                    showMenu = false
                    showInfoDialog = true
                }
            )
        }

        // Top Bar & Search Bar
        AnimatedVisibility(
            visible = !isImmersiveMode,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = viewerBarEnterTransition(fromTop = true),
            exit = viewerBarExitTransition(toTop = true)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                NexusViewerTopBar(
                    title = displayName,
                    hazeState = hazeState,
                    onBack = onBack,
                    actions = {
                        if (uiState is ExcelReaderUiState.Ready) {
                            NexusViewerActionButton(
                                painter = painterResource(id = com.nexus.core.R.drawable.ic_search),
                                contentDescription = "Search",
                                isActive = isSearchActive,
                                onClick = { isSearchActive = !isSearchActive }
                            )
                            NexusViewerActionDivider()
                        }
                        NexusViewerActionButton(
                            painter = painterResource(id = com.nexus.core.R.drawable.ic_more_vert),
                            contentDescription = "More options",
                            onClick = { showMenu = true }
                        )
                    }
                )

                if (readingProgress > 0f) {
                    LinearProgressIndicator(
                        progress = { readingProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.5.dp),
                        color = NexusTheme.colors.primary,
                        trackColor = Color.Transparent
                    )
                }

                val xlsxState = uiState as? ExcelReaderUiState.Ready
                val query = xlsxState?.searchQuery ?: ""
                val matchCount = xlsxState?.searchMatches?.size ?: 0
                val currentIdx = xlsxState?.currentMatchIndex ?: 0

                ReaderSearchBar(
                    visible = isSearchActive && !isImmersiveMode,
                    query = query,
                    onQueryChange = { newQuery ->
                        viewModel.setSearchQuery(newQuery)
                    },
                    matchCount = matchCount,
                    currentMatchIndex = currentIdx,
                    onPreviousMatch = { viewModel.previousSearchMatch() },
                    onNextMatch = { viewModel.nextSearchMatch() },
                    onClose = {
                        isSearchActive = false
                        viewModel.setSearchQuery("")
                    }
                )
            }
        }

        // Pinch hint overlay
        AnimatedVisibility(
            visible = showPinchHint && !isImmersiveMode && (uiState is ExcelReaderUiState.Ready),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = if (isImmersiveMode) 24.dp else 105.dp),
            enter = fadeIn(animationSpec = tween(300)) + androidx.compose.animation.scaleIn(initialScale = 0.85f),
            exit = fadeOut(animationSpec = tween(250)) + androidx.compose.animation.scaleOut(targetScale = 0.85f)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(NexusTheme.shapes.pill)
                    .background(NexusTheme.colors.surfaceGlass)
                    .border(1.dp, NexusTheme.colors.divider.copy(alpha = 0.6f), NexusTheme.shapes.pill)
                    .shadow(elevation = 6.dp, shape = NexusTheme.shapes.pill)
                    .clickable {
                        showPinchHint = false
                        readerPrefs.edit().putBoolean("has_seen_pinch_zoom_hint", true).apply()
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Image(
                    painter = painterResource(id = com.nexus.core.R.drawable.ic_arrow_left),
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    colorFilter = ColorFilter.tint(NexusTheme.colors.primary)
                )
                Spacer(modifier = Modifier.width(2.dp))
                Image(
                    painter = painterResource(id = com.nexus.core.R.drawable.ic_arrow_right),
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    colorFilter = ColorFilter.tint(NexusTheme.colors.primary)
                )
                Spacer(modifier = Modifier.width(6.dp))
                NexusText(
                    text = "Pinch to zoom in or out",
                    style = NexusTheme.typography.caption.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = NexusTheme.colors.textPrimary
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = if (isImmersiveMode) 16.dp else 96.dp, start = 16.dp, end = 16.dp),
            snackbar = { snackbarData ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(NexusTheme.shapes.pill)
                        .background(
                            if (isDark) darkNexusColors.surfaceVariant
                            else lightNexusColors.surfaceVariant
                        )
                        .border(
                            width = 1.dp,
                            color = NexusTheme.colors.primary.copy(alpha = 0.45f),
                            shape = NexusTheme.shapes.pill
                        )
                        .shadow(8.dp, NexusTheme.shapes.pill)
                        .padding(horizontal = NexusTheme.spacing.md, vertical = NexusTheme.spacing.xs)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(NexusTheme.spacing.sm)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(NexusTheme.colors.primary.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = com.nexus.core.R.drawable.ic_check),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                colorFilter = ColorFilter.tint(NexusTheme.colors.primary)
                            )
                        }

                        NexusText(
                            text = snackbarData.visuals.message,
                            style = NexusTheme.typography.body.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp),
                            color = if (isDark) darkNexusColors.textPrimary else lightNexusColors.textPrimary
                        )
                    }
                }
            }
        )
    }
}
