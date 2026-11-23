package com.nexus.feature.reader.office.word

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
import androidx.compose.runtime.mutableIntStateOf
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
import com.nexus.core.ui.NexusText
import com.nexus.core.ui.animations.shimmerEffect
import com.nexus.core.ui.animations.springBounceClick
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
import com.nexus.feature.reader.office.word.components.WordBookmarksBottomSheet
import com.nexus.feature.reader.office.word.components.WordOptionsBottomSheet
import com.nexus.feature.reader.office.word.components.WordOutlineBottomSheet
import com.nexus.feature.reader.office.word.model.WordReaderUiState
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
fun WordReaderScreen(
    encodedUri: String,
    fileName: String,
    docType: String = "DOCX",
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WordReaderViewModel = hiltViewModel()
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
    val docxFontSize by viewModel.docxFontSize.collectAsStateWithLifecycle()
    val docxIsSerif by viewModel.docxIsSerif.collectAsStateWithLifecycle()

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

    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResultCount by remember { mutableIntStateOf(0) }
    var currentSearchIndex by remember { mutableIntStateOf(0) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.destroy()
        }
    }
    var currentPage by remember { mutableIntStateOf(1) }
    var totalPages by remember { mutableIntStateOf(1) }
    var showJumpPageDialog by remember { mutableStateOf(false) }
    var jumpPageInput by remember { mutableStateOf("1") }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }

    LaunchedEffect(uiState) {
        if (uiState is WordReaderUiState.Ready) {
            totalPages = (uiState as WordReaderUiState.Ready).pageCount
        }
    }

    val systemDarkTheme = isSystemInDarkTheme()
    var isDarkThemeOverride by remember { mutableStateOf<Boolean?>(false) }
    val isDark = isDarkThemeOverride ?: systemDarkTheme

    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    var showBookmarksSheet by remember { mutableStateOf(false) }
    var showOutlineNavigator by remember { mutableStateOf(false) }

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
    val sharedScope = LocalSharedTransitionScope.current
    val animatedScope = LocalAnimatedVisibilityScope.current

    var sharedElementModifier: Modifier = Modifier
    var titleSharedModifier: Modifier = Modifier
    if (sharedScope != null && animatedScope != null) {
        with(sharedScope) {
            sharedElementModifier = Modifier.sharedElement(
                sharedContentState = rememberSharedContentState(key = "thumb_$decodedUri"),
                animatedVisibilityScope = animatedScope,
                boundsTransform = { _, _ -> tween(300) }
            )
            titleSharedModifier = Modifier.sharedElement(
                sharedContentState = rememberSharedContentState(key = "title_$decodedUri"),
                animatedVisibilityScope = animatedScope,
                boundsTransform = { _, _ -> tween(300) }
            )
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
            label = "wordReaderState"
        ) { state ->
            when (state) {
                is WordReaderUiState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(sharedElementModifier)
                            .padding(top = 90.dp, bottom = 24.dp, start = 16.dp, end = 16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isDark) darkNexusColors.surface else lightNexusColors.surface)
                                .border(1.dp, NexusTheme.colors.divider.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.55f)
                                    .height(24.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(NexusTheme.colors.surfaceVariant)
                                    .shimmerEffect()
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            repeat(16) { index ->
                                val widthFraction = when {
                                    index % 4 == 0 -> 0.45f
                                    index % 3 == 0 -> 0.75f
                                    else -> 0.95f
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(widthFraction)
                                        .height(14.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(NexusTheme.colors.surfaceVariant.copy(alpha = 0.7f))
                                        .shimmerEffect()
                                )
                            }
                        }
                    }
                }
                is WordReaderUiState.Ready -> {
                    val typographyCss = """
                        body, .docx-page, h1, h2, h3, h4, h5, h6, p, li, td, th {
                            font-family: ${if (docxIsSerif) "Georgia, 'Times New Roman', serif" else "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif"} !important;
                            writing-mode: horizontal-tb !important;
                            text-orientation: mixed !important;
                        }
                        p, li {
                            font-size: ${docxFontSize}px !important;
                            word-break: normal !important;
                            overflow-wrap: break-word !important;
                            white-space: normal !important;
                        }
                        td, th {
                            font-size: ${(docxFontSize * 0.9f).toInt()}px !important;
                            word-break: normal !important;
                            overflow-wrap: normal !important;
                            white-space: normal !important;
                            min-width: 64px !important;
                        }
                    """.trimIndent()

                    val htmlWithScript = remember(state.htmlContent, isDark, docxFontSize, docxIsSerif) {
                        val darkClass = if (isDark) " dark-mode" else ""
                        var html = state.htmlContent
                        html = html.replace("<body class=\"mode-paged\">", "<body class=\"mode-paged$darkClass\">")
                            .replace("<body>", "<body class=\"mode-paged$darkClass\">")
                        val extraStyle = "<style>\n$typographyCss\n</style>"
                        html.replace("</head>", "$extraStyle\n</head>")
                    }

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
                                settings.useWideViewPort = false
                                settings.loadWithOverviewMode = false
                                settings.domStorageEnabled = true
                                settings.layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL
                                setFindListener { activeMatchOrdinal, numberOfMatches, isDoneCounting ->
                                    if (isDoneCounting) {
                                        searchResultCount = numberOfMatches
                                        currentSearchIndex = activeMatchOrdinal
                                    }
                                }
                                addJavascriptInterface(
                                    ImmersiveToggleInterface(
                                        onToggle = { isImmersiveMode = !isImmersiveMode },
                                        onPageChanged = { page, total ->
                                            currentPage = page
                                            if (total > 0) totalPages = total
                                        }
                                    ),
                                    "AndroidInterface"
                                )
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
                            }
                        },
                        update = { view ->
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                view.settings.forceDark = if (isDark) WebSettings.FORCE_DARK_ON else WebSettings.FORCE_DARK_OFF
                            }
                            if (view.tag != htmlWithScript) {
                                view.loadDataWithBaseURL(null, htmlWithScript, "text/html", "UTF-8", null)
                                view.tag = htmlWithScript
                            }
                        },
                        modifier = Modifier.fillMaxSize().then(sharedElementModifier)
                    )
                }
                is WordReaderUiState.PasswordRequired -> {
                    OfficePasswordPromptCard(
                        isError = state.isError,
                        onCancel = onBack,
                        onUnlock = { pwd ->
                            viewModel.loadDocument(state.encodedUri, state.docType, pwd)
                        },
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                is WordReaderUiState.Error -> {
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
            visible = !isImmersiveMode && (uiState is WordReaderUiState.Ready),
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = expandVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) + fadeIn(),
            exit = shrinkVertically(animationSpec = spring(stiffness = Spring.StiffnessMedium)) + fadeOut()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs)
            ) {
                // Floating Mode Badge / Page Indicator
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
                        .springBounceClick {
                            jumpPageInput = "$currentPage"
                            showJumpPageDialog = true
                        }
                        .padding(horizontal = NexusTheme.spacing.md, vertical = NexusTheme.spacing.xs)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs)
                    ) {
                        val readyState = uiState as? WordReaderUiState.Ready
                        Icon(
                            painter = painterResource(id = com.nexus.core.R.drawable.ic_book),
                            contentDescription = "Paged View",
                            tint = NexusTheme.colors.primary,
                            modifier = Modifier.size(15.dp)
                        )
                        val paperName = readyState?.paperSizeName ?: "A4"
                        val badgeText = if (totalPages > 1) "Page $currentPage of $totalPages · $paperName" else "Paged View · $paperName"
                        NexusText(
                            text = badgeText,
                            style = NexusTheme.typography.body.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp),
                            color = NexusTheme.colors.primary
                        )
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
                            iconPainter = painterResource(id = com.nexus.core.R.drawable.ic_book),
                            label = "Page $currentPage",
                            contentDescription = "Go to Page",
                            tint = NexusTheme.colors.primary,
                            isActive = true,
                            onClick = {
                                if (totalPages > 1) {
                                    jumpPageInput = "$currentPage"
                                    showJumpPageDialog = true
                                }
                            }
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

                        OfficeBottomBarActionItem(
                            modifier = Modifier.weight(1f),
                            iconPainter = painterResource(id = com.nexus.core.R.drawable.ic_share),
                            label = "Share",
                            contentDescription = "Share",
                            tint = NexusTheme.colors.textPrimary,
                            onClick = {
                                var rawUriStr = URLDecoder.decode(encodedUri, "UTF-8")
                                if (rawUriStr.contains("%")) rawUriStr = URLDecoder.decode(rawUriStr, "UTF-8")
                                val rawUri = Uri.parse(rawUriStr)
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
                                    type = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
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

        // Info Dialog
        if (showInfoDialog && uiState is WordReaderUiState.Ready) {
            val docxState = uiState as WordReaderUiState.Ready
            val customDetails = buildList {
                add(FileDetailItem(com.nexus.core.R.drawable.ic_book, "Paper Size", "${docxState.paperSizeName} (${docxState.paperDimensions})"))
                add(FileDetailItem(com.nexus.core.R.drawable.ic_compress_arrows, "Orientation", docxState.orientation))
                add(FileDetailItem(com.nexus.core.R.drawable.ic_corner_radius, "Margins", docxState.marginsDescription))
                add(FileDetailItem(com.nexus.core.R.drawable.ic_file_word_new, "Total Pages", "${docxState.pageCount} pages"))
                docxState.author?.takeIf { it.isNotBlank() }?.let {
                    add(FileDetailItem(com.nexus.core.R.drawable.ic_home_outline, "Author", it))
                }
                docxState.creationDate?.takeIf { it.isNotBlank() }?.let {
                    add(FileDetailItem(com.nexus.core.R.drawable.ic_startup, "Created", it))
                }
                docxState.wordCount?.let {
                    add(FileDetailItem(com.nexus.core.R.drawable.ic_book, "Word count", "$it words"))
                }
                docxState.characterCount?.let {
                    add(FileDetailItem(com.nexus.core.R.drawable.ic_text_scan, "Character count", "$it characters"))
                }
                docxState.readingTimeMinutes?.let {
                    add(FileDetailItem(com.nexus.core.R.drawable.ic_update_progress, "Est. Reading time", "~$it minutes"))
                }
            }

            NexusFileDetailsSheet(
                fileName = displayName,
                uri = encodedUri,
                docType = DocumentType.DOCX,
                customDetails = customDetails,
                onDismissRequest = { showInfoDialog = false }
            )
        }

        // Jump to Page Dialog
        if (showJumpPageDialog) {
            AlertDialog(
                onDismissRequest = { showJumpPageDialog = false },
                title = { NexusText("Go to Page") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs)) {
                        NexusText(
                            text = "Enter a page number between 1 and $totalPages:",
                            style = NexusTheme.typography.caption,
                            color = NexusTheme.colors.textSecondary
                        )
                        BasicTextField(
                            value = jumpPageInput,
                            onValueChange = { str ->
                                if (str.isEmpty() || str.all { it.isDigit() }) {
                                    jumpPageInput = str
                                }
                            },
                            textStyle = NexusTheme.typography.body.copy(color = NexusTheme.colors.textPrimary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(NexusTheme.colors.surfaceVariant, NexusTheme.shapes.small)
                                .padding(NexusTheme.spacing.md),
                            singleLine = true
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val target = jumpPageInput.toIntOrNull()?.coerceIn(1, totalPages) ?: 1
                            webViewRef?.evaluateJavascript("window.goToPage($target);", null)
                            currentPage = target
                            showJumpPageDialog = false
                        }
                    ) {
                        NexusText("Go", color = NexusTheme.colors.primary)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showJumpPageDialog = false }) {
                        NexusText("Cancel", color = NexusTheme.colors.textSecondary)
                    }
                }
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
            WordOptionsBottomSheet(
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
                    val readyState = uiState as? WordReaderUiState.Ready
                    if (readyState?.convertedPdfPath != null) {
                        try {
                            val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                            val file = File(readyState.convertedPdfPath)
                            val printAdapter = object : android.print.PrintDocumentAdapter() {
                                override fun onLayout(
                                    oldAttributes: PrintAttributes?,
                                    newAttributes: PrintAttributes?,
                                    cancellationSignal: android.os.CancellationSignal?,
                                    callback: LayoutResultCallback?,
                                    extras: android.os.Bundle?
                                ) {
                                    if (cancellationSignal?.isCanceled == true) {
                                        callback?.onLayoutCancelled()
                                        return
                                    }
                                    val info = android.print.PrintDocumentInfo.Builder(displayName)
                                        .setContentType(android.print.PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                                        .setPageCount(totalPages)
                                        .build()
                                    callback?.onLayoutFinished(info, true)
                                }

                                override fun onWrite(
                                    pages: Array<out android.print.PageRange>?,
                                    destination: android.os.ParcelFileDescriptor?,
                                    cancellationSignal: android.os.CancellationSignal?,
                                    callback: WriteResultCallback?
                                ) {
                                    try {
                                        java.io.FileInputStream(file).use { input ->
                                            java.io.FileOutputStream(destination?.fileDescriptor).use { output ->
                                                input.copyTo(output)
                                            }
                                        }
                                        callback?.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES))
                                    } catch (e: Exception) {
                                        callback?.onWriteFailed(e.message)
                                    }
                                }
                            }
                            printManager.print("Print - $displayName", printAdapter, PrintAttributes.Builder().build())
                        } catch (e: Exception) {
                            showFeedback("Printing failed: ${e.localizedMessage}")
                        }
                    } else {
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
                            type = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
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
                },
                onOutline = {
                    showMenu = false
                    showOutlineNavigator = true
                },
                onBookmarks = {
                    showMenu = false
                    showBookmarksSheet = true
                },
                docxHeadingsCount = (uiState as? WordReaderUiState.Ready)?.headings?.size ?: 0,
                onIncreaseFontSize = { viewModel.increaseDocxFontSize() },
                onDecreaseFontSize = { viewModel.decreaseDocxFontSize() },
                onToggleSerif = { viewModel.toggleDocxSerif() },
                isSerif = docxIsSerif,
                paperSizeName = (uiState as? WordReaderUiState.Ready)?.paperSizeName,
                onGoToPage = {
                    showMenu = false
                    jumpPageInput = "$currentPage"
                    showJumpPageDialog = true
                },
                totalPages = totalPages
            )
        }

        if (showBookmarksSheet) {
            WordBookmarksBottomSheet(
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

        if (showOutlineNavigator && uiState is WordReaderUiState.Ready) {
            val docxState = uiState as WordReaderUiState.Ready
            WordOutlineBottomSheet(
                headings = docxState.headings,
                onHeadingSelected = { anchorId ->
                    webViewRef?.evaluateJavascript("scrollToHeading('$anchorId')", null)
                    showOutlineNavigator = false
                },
                onDismiss = { showOutlineNavigator = false }
            )
        }

        // Top Bar & Search Bar
        AnimatedVisibility(
            visible = !isImmersiveMode,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = androidx.compose.animation.slideInVertically(initialOffsetY = { -it }) + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { -it }) + androidx.compose.animation.fadeOut()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                NexusViewerTopBar(
                    title = displayName,
                    titleModifier = titleSharedModifier,
                    hazeState = hazeState,
                    onBack = onBack,
                    actions = {
                        if (uiState is WordReaderUiState.Ready) {
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

                ReaderSearchBar(
                    visible = isSearchActive && !isImmersiveMode,
                    query = searchQuery,
                    onQueryChange = { newQuery ->
                        searchQuery = newQuery
                        webViewRef?.findAllAsync(newQuery)
                    },
                    matchCount = searchResultCount,
                    currentMatchIndex = currentSearchIndex,
                    onPreviousMatch = { webViewRef?.findNext(false) },
                    onNextMatch = { webViewRef?.findNext(true) },
                    onClose = {
                        isSearchActive = false
                        searchQuery = ""
                        webViewRef?.clearMatches()
                    }
                )
            }
        }

        // Pinch hint overlay
        AnimatedVisibility(
            visible = showPinchHint && !isImmersiveMode && (uiState is WordReaderUiState.Ready),
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
