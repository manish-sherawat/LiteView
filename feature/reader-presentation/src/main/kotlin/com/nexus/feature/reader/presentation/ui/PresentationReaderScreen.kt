package com.nexus.feature.reader.presentation.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import com.nexus.core.ui.animations.viewerBarEnterTransition
import com.nexus.core.ui.animations.viewerBarExitTransition
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.nexus.core.navigation.DocumentType
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusSurface
import com.nexus.core.ui.NexusText
import com.nexus.core.ui.animations.springBounceClick
import com.nexus.core.ui.components.FileDetailItem
import com.nexus.core.ui.components.NexusFileDetailsSheet
import com.nexus.core.ui.components.DocumentOptionItem
import com.nexus.core.ui.components.DocumentOptionsBottomSheet
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Timer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import com.nexus.core.ui.components.NexusTopBar
import com.nexus.core.ui.components.NexusViewerTopBar
import com.nexus.core.ui.components.NexusViewerActionButton
import com.nexus.core.ui.components.NexusViewerActionDivider
import com.nexus.core.ui.components.NexusFloatingIconButton
import com.nexus.core.ui.components.ReaderSearchBar
import com.nexus.core.ui.utils.hazeGlassBackground
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import com.nexus.feature.reader.presentation.model.PresentationUiState
import com.nexus.feature.reader.presentation.ui.components.AsyncSlideThumbnail
import com.nexus.feature.reader.presentation.ui.components.NativeSlideContent
import com.nexus.feature.reader.presentation.ui.components.SlideGridSorterSheet
import com.nexus.feature.reader.presentation.ui.components.SlideViewport
import com.nexus.feature.reader.presentation.ui.components.SpeakerNotesDrawer
import kotlinx.coroutines.launch
import java.io.File
import java.net.URLDecoder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresentationReaderScreen(
    encodedUri: String,
    fileName: String,
    onNavigateBack: () -> Unit,
    viewModel: PresentationReaderViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isStarred by viewModel.isStarred.collectAsState()
    val keepScreenAwake by viewModel.keepScreenAwake.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = context as? Activity
    val isDark = isSystemInDarkTheme()
    val haptic = LocalHapticFeedback.current

    var isImmersiveMode by remember { mutableStateOf(false) }
    var showJumpDialog by remember { mutableStateOf(false) }
    var showMenuSheet by remember { mutableStateOf(false) }
    var showInfoSheet by remember { mutableStateOf(false) }
    var showSlideshowSettings by remember { mutableStateOf(false) }
    var jumpSlideInput by remember { mutableStateOf("") }

    val decodedFileName = remember(fileName) {
        try {
            val d1 = URLDecoder.decode(fileName, "UTF-8")
            if (d1.contains("%")) URLDecoder.decode(d1, "UTF-8") else d1
        } catch (_: Exception) {
            fileName
        }
    }

    LaunchedEffect(encodedUri, fileName) {
        viewModel.loadDocument(encodedUri, fileName)
    }

    val isFullscreen = (uiState as? PresentationUiState.Success)?.isFullscreen == true || isImmersiveMode

    // Handle screen keep-awake
    DisposableEffect(keepScreenAwake, isFullscreen) {
        val window = activity?.window
        if (keepScreenAwake || isFullscreen) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Handle system bars in fullscreen / presentation mode
    LaunchedEffect(isFullscreen) {
        activity?.window?.let { window ->
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            if (isFullscreen) {
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            activity?.window?.let { window ->
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // ─── 2.6 Landscape Presentation Lock ───────────────────────────────────
    val isLandscapeLocked = (uiState as? PresentationUiState.Success)?.isLandscapeLocked == true
    DisposableEffect(isLandscapeLocked) {
        if (isLandscapeLocked) {
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        onDispose {
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    fun sharePresentation() {
        try {
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
            val mime = if (decodedFileName.endsWith(".ppt", ignoreCase = true)) {
                "application/vnd.ms-powerpoint"
            } else {
                "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            }
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, shareUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Share Presentation").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            Toast.makeText(context, "Unable to share presentation: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    BackHandler(enabled = true) {
        val successState = uiState as? PresentationUiState.Success
        if (successState?.isSearchActive == true) {
            viewModel.toggleSearch()
        } else if (successState?.showSpeakerNotes == true) {
            viewModel.toggleSpeakerNotes()
        } else if (successState?.isGridSorterVisible == true) {
            viewModel.toggleGridSorter()
        } else if (successState?.isSlideshowActive == true) {
            viewModel.toggleSlideshow()
        } else if (successState?.isLandscapeLocked == true) {
            viewModel.toggleLandscapeLock()
        } else if (successState?.showThumbnails == true) {
            viewModel.toggleThumbnails()
        } else if (isImmersiveMode || successState?.isFullscreen == true) {
            if (successState?.isFullscreen == true) viewModel.toggleFullscreen()
            isImmersiveMode = false
        } else {
            onNavigateBack()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NexusTheme.colors.background)
    ) {
        when (val state = uiState) {
            is PresentationUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = NexusTheme.colors.primary,
                            strokeWidth = 3.5.dp,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        NexusText(
                            text = "Loading Presentation...",
                            style = NexusTheme.typography.body,
                            color = NexusTheme.colors.textSecondary
                        )
                    }
                }
            }

            is PresentationUiState.EngineNotReady, is PresentationUiState.Error -> {
                val errorMsg = if (state is PresentationUiState.EngineNotReady) state.message else (state as PresentationUiState.Error).message
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    NexusSurface(
                        shape = NexusTheme.shapes.large,
                        color = NexusTheme.colors.surface,
                        elevation = 8.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(NexusTheme.colors.error.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = com.nexus.core.R.drawable.ic_close),
                                    contentDescription = null,
                                    colorFilter = ColorFilter.tint(NexusTheme.colors.error),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            NexusText(
                                text = "Unable to Open Presentation",
                                style = NexusTheme.typography.titleLarge,
                                color = NexusTheme.colors.textPrimary,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            NexusText(
                                text = errorMsg,
                                style = NexusTheme.typography.body,
                                color = NexusTheme.colors.textSecondary,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(22.dp))
                            Box(
                                modifier = Modifier
                                    .clip(NexusTheme.shapes.medium)
                                    .background(NexusTheme.colors.primary)
                                    .springBounceClick(onClick = onNavigateBack)
                                    .padding(horizontal = 26.dp, vertical = 12.dp)
                            ) {
                                NexusText(
                                    text = "Go Back",
                                    style = NexusTheme.typography.buttonLabel,
                                    color = NexusTheme.colors.onPrimary
                                )
                            }
                        }
                    }
                }
            }

            is PresentationUiState.Success -> {
                val hazeState = remember { HazeState() }
                val pagerState = rememberPagerState(
                    initialPage = state.currentSlideIndex.coerceIn(0, maxOf(0, state.slideCount - 1)),
                    pageCount = { maxOf(1, state.slideCount) }
                )

                LaunchedEffect(pagerState) {
                    snapshotFlow { pagerState.currentPage }.collect { page ->
                        if (state.currentSlideIndex != page) {
                            viewModel.setCurrentSlide(page)
                        }
                    }
                }

                LaunchedEffect(state.currentSlideIndex) {
                    if (pagerState.currentPage != state.currentSlideIndex) {
                        pagerState.animateScrollToPage(state.currentSlideIndex)
                    }
                }

                LaunchedEffect(state.currentMatchIndex, state.searchMatches) {
                    if (state.isSearchActive && state.searchMatches.isNotEmpty()) {
                        val targetSlide = state.searchMatches.getOrNull(state.currentMatchIndex)?.slideIndex
                        if (targetSlide != null && targetSlide != pagerState.currentPage) {
                            pagerState.animateScrollToPage(targetSlide)
                        }
                    }
                }

                // ─── Main Slide HorizontalPager Viewport ───────────────────────────
                val contentPaddingTop by animateDpAsState(
                    targetValue = if (!isFullscreen) {
                        if (state.isSearchActive) 116.dp else 56.dp
                    } else 0.dp,
                    label = "pptTopPadding"
                )
                val contentPaddingBottom by animateDpAsState(
                    targetValue = if (!isFullscreen) {
                        if (state.showThumbnails) 140.dp else 148.dp
                    } else 0.dp,
                    label = "pptBottomPadding"
                )

                HorizontalPager(
                    state = pagerState,
                    userScrollEnabled = true,
                    contentPadding = PaddingValues(top = contentPaddingTop, bottom = contentPaddingBottom),
                    modifier = Modifier
                        .fillMaxSize()
                        .hazeSource(hazeState)
                ) { pageIndex ->
                    val slide = state.slides.getOrNull(pageIndex)
                    if (slide != null) {
                        SlideViewport(
                            slideInfo = slide,
                            loadSlideBitmap = { idx, w, h -> viewModel.getSlideBitmap(idx, w, h) },
                            searchQuery = if (state.isSearchActive) state.searchQuery else "",
                            onToggleImmersive = { isImmersiveMode = !isImmersiveMode }
                        )
                    }
                }

                // ─── Top Bar (Standard Nexus Luxury Top Bar) ─────────────────────
                AnimatedVisibility(
                    visible = !isFullscreen,
                    enter = viewerBarEnterTransition(fromTop = true),
                    exit = viewerBarExitTransition(toTop = true),
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    NexusViewerTopBar(
                        title = if (state.fileName.isNotBlank()) state.fileName else decodedFileName,
                        hazeState = hazeState,
                        onBack = onNavigateBack,
                        actions = {
                            // Search Action
                            NexusViewerActionButton(
                                painter = painterResource(id = com.nexus.core.R.drawable.ic_search),
                                contentDescription = "Search",
                                isActive = state.isSearchActive,
                                onClick = { viewModel.toggleSearch() }
                            )
                            NexusViewerActionDivider()
                            // More Options Action (Three dots)
                            NexusViewerActionButton(
                                painter = painterResource(id = com.nexus.core.R.drawable.ic_more_vert),
                                contentDescription = "More options",
                                onClick = { showMenuSheet = true }
                            )
                        }
                    )
                }

                // ─── 2.1 In-Slide Text Search Bar ─────────────────────────────────
                AnimatedVisibility(
                    visible = state.isSearchActive && !isFullscreen,
                    enter = viewerBarEnterTransition(fromTop = true),
                    exit = viewerBarExitTransition(toTop = true),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 56.dp)
                ) {
                    ReaderSearchBar(
                        visible = state.isSearchActive,
                        query = state.searchQuery,
                        onQueryChange = { viewModel.search(it) },
                        matchCount = state.searchMatches.size,
                        currentMatchIndex = state.currentMatchIndex,
                        onPreviousMatch = { viewModel.previousSearchMatch() },
                        onNextMatch = { viewModel.nextSearchMatch() },
                        onClose = { viewModel.toggleSearch() },
                        placeholderText = "Search slides, tables & notes..."
                    )
                }

                // ─── 2.5 Auto-Play Slideshow Floating Indicator Banner ────────────
                val slideshowTopPadding = when {
                    isFullscreen -> 16.dp
                    state.isSearchActive -> 120.dp
                    else -> 64.dp
                }
                AnimatedVisibility(
                    visible = state.isSlideshowActive,
                    enter = viewerBarEnterTransition(fromTop = true),
                    exit = viewerBarExitTransition(toTop = true),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = slideshowTopPadding)
                ) {
                    NexusSurface(
                        shape = NexusTheme.shapes.pill,
                        color = NexusTheme.colors.surface.copy(alpha = 0.95f),
                        elevation = 12.dp,
                        borderWidth = 1.dp,
                        borderColor = NexusTheme.colors.primary.copy(alpha = 0.5f),
                        modifier = Modifier
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF00E676))
                            )
                            NexusText(
                                text = "Slideshow (${state.slideshowIntervalSeconds}s${if (state.isSlideshowLoop) " • Loop" else ""})",
                                style = NexusTheme.typography.body.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
                                color = NexusTheme.colors.primary
                            )
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = "Slideshow Settings",
                                tint = NexusTheme.colors.textSecondary,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { showSlideshowSettings = true }
                            )
                            Icon(
                                imageVector = Icons.Default.Pause,
                                contentDescription = "Pause Slideshow",
                                tint = NexusTheme.colors.textPrimary,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { viewModel.toggleSlideshow() }
                            )
                        }
                    }
                }

                // ─── Floating Bottom Controls & Slide Navigation ─────────────────────────
                AnimatedVisibility(
                    visible = !isFullscreen && !state.showThumbnails,
                    enter = viewerBarEnterTransition(fromTop = false),
                    exit = viewerBarExitTransition(toTop = false),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Max width constraint container (matches width: min(100%, 420px))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 420.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // ── Slide Navigation (Arrows + Counter Pill + Progress Track) ──
                            val canGoBack = pagerState.currentPage > 0
                            val canGoForward = pagerState.currentPage < state.slideCount - 1
                            val currentFormatted = String.format("%02d", (state.currentSlideIndex + 1).coerceAtLeast(1))
                            val totalFormatted = String.format("%02d", state.slideCount.coerceAtLeast(1))

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    modifier = Modifier.wrapContentWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    // Previous Slide Button (44dp circular)
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .shadow(
                                                elevation = 6.dp,
                                                shape = CircleShape,
                                                spotColor = NexusTheme.colors.primary.copy(alpha = if (isDark) 0.24f else 0.08f),
                                                ambientColor = Color.Black.copy(alpha = if (isDark) 0.35f else 0.06f)
                                            )
                                            .hazeGlassBackground(
                                                hazeState = hazeState,
                                                shape = CircleShape,
                                                borderWidth = 1.dp,
                                                borderColor = if (isDark) Color.White.copy(alpha = 0.12f) else Color(0xFFE4E6EE)
                                            )
                                            .alpha(if (canGoBack) 1f else 0.35f)
                                            .springBounceClick(enabled = canGoBack) {
                                                if (canGoBack) {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    scope.launch {
                                                        pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                                    }
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = "Previous Slide",
                                            tint = NexusTheme.colors.textPrimary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    // Slide Counter Pill (Click to jump / overview)
                                    Box(
                                        modifier = Modifier
                                            .defaultMinSize(minWidth = 112.dp)
                                            .height(44.dp)
                                            .shadow(
                                                elevation = 6.dp,
                                                shape = CircleShape,
                                                spotColor = NexusTheme.colors.primary.copy(alpha = if (isDark) 0.24f else 0.08f),
                                                ambientColor = Color.Black.copy(alpha = if (isDark) 0.35f else 0.06f)
                                            )
                                            .hazeGlassBackground(
                                                hazeState = hazeState,
                                                shape = CircleShape,
                                                borderWidth = 1.dp,
                                                borderColor = if (isDark) Color.White.copy(alpha = 0.12f) else Color(0xFFE4E6EE)
                                            )
                                            .springBounceClick {
                                                jumpSlideInput = (state.currentSlideIndex + 1).toString()
                                                showJumpDialog = true
                                            }
                                            .padding(horizontal = 18.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            NexusText(
                                                text = currentFormatted,
                                                style = NexusTheme.typography.body.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp
                                                ),
                                                color = NexusTheme.colors.textPrimary
                                            )
                                            NexusText(
                                                text = "/ $totalFormatted",
                                                style = NexusTheme.typography.body.copy(
                                                    fontWeight = FontWeight.Normal,
                                                    fontSize = 14.sp
                                                ),
                                                color = NexusTheme.colors.textSecondary
                                            )
                                        }
                                    }

                                    // Next Slide Button (44dp circular)
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .shadow(
                                                elevation = 6.dp,
                                                shape = CircleShape,
                                                spotColor = NexusTheme.colors.primary.copy(alpha = if (isDark) 0.24f else 0.08f),
                                                ambientColor = Color.Black.copy(alpha = if (isDark) 0.35f else 0.06f)
                                            )
                                            .hazeGlassBackground(
                                                hazeState = hazeState,
                                                shape = CircleShape,
                                                borderWidth = 1.dp,
                                                borderColor = if (isDark) Color.White.copy(alpha = 0.12f) else Color(0xFFE4E6EE)
                                            )
                                            .alpha(if (canGoForward) 1f else 0.35f)
                                            .springBounceClick(enabled = canGoForward) {
                                                if (canGoForward) {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    scope.launch {
                                                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                                    }
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = "Next Slide",
                                            tint = NexusTheme.colors.textPrimary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Progress Bar Track
                                val progressTarget = if (state.slideCount > 0) {
                                    ((state.currentSlideIndex + 1).toFloat() / state.slideCount.toFloat()).coerceIn(0f, 1f)
                                } else 0f
                                val animatedProgress by animateFloatAsState(
                                    targetValue = progressTarget,
                                    animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
                                    label = "pptSlideProgress"
                                )

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(3.dp)
                                        .clip(CircleShape)
                                        .background(if (isDark) Color.White.copy(alpha = 0.12f) else Color(0xFFE1E3ED))
                                ) {
                                    if (animatedProgress > 0.001f) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(animatedProgress.coerceIn(0.001f, 1f))
                                                .fillMaxHeight()
                                                .clip(CircleShape)
                                                .background(NexusTheme.colors.primary)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // ── Floating Bottom Toolbar (5 tools) ──
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .shadow(
                                        elevation = 16.dp,
                                        shape = RoundedCornerShape(25.dp),
                                        spotColor = NexusTheme.colors.primary.copy(alpha = if (isDark) 0.28f else 0.10f),
                                        ambientColor = Color.Black.copy(alpha = if (isDark) 0.40f else 0.08f)
                                    )
                                    .hazeGlassBackground(
                                        hazeState = hazeState,
                                        shape = RoundedCornerShape(25.dp),
                                        borderWidth = 1.dp,
                                        borderColor = if (isDark) Color.White.copy(alpha = 0.12f) else Color(0xFFE6E7EF)
                                    )
                                    .padding(7.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    // 1. Slides
                                    PresentationBottomActionItem(
                                        modifier = Modifier.weight(1f),
                                        iconPainter = painterResource(id = com.nexus.core.R.drawable.ic_view_list),
                                        label = "Slides",
                                        contentDescription = "Slides",
                                        isActive = state.showThumbnails,
                                        onClick = { viewModel.toggleThumbnails() }
                                    )

                                    // 2. Grid
                                    PresentationBottomActionItem(
                                        modifier = Modifier.weight(1f),
                                        iconPainter = rememberVectorPainter(Icons.Default.GridView),
                                        label = "Grid",
                                        contentDescription = "Grid Sorter",
                                        isActive = state.isGridSorterVisible,
                                        onClick = { viewModel.toggleGridSorter() }
                                    )

                                    // 3. Notes
                                    PresentationBottomActionItem(
                                        modifier = Modifier.weight(1f),
                                        iconPainter = rememberVectorPainter(Icons.AutoMirrored.Filled.Notes),
                                        label = "Notes",
                                        contentDescription = "Speaker Notes",
                                        isActive = state.showSpeakerNotes,
                                        onClick = { viewModel.toggleSpeakerNotes() }
                                    )

                                    // 4. Play/Pause
                                    PresentationBottomActionItem(
                                        modifier = Modifier.weight(1f),
                                        iconPainter = rememberVectorPainter(
                                            if (state.isSlideshowActive) Icons.Default.Pause else Icons.Default.PlayArrow
                                        ),
                                        label = if (state.isSlideshowActive) "Pause" else "Play",
                                        contentDescription = "Slideshow",
                                        isActive = state.isSlideshowActive,
                                        activeBadgeColor = if (state.isSlideshowActive) Color(0xFF00E676).copy(alpha = 0.16f) else NexusTheme.colors.primary.copy(alpha = 0.12f),
                                        activeTint = if (state.isSlideshowActive) Color(0xFF00C853) else NexusTheme.colors.primary,
                                        onClick = { viewModel.toggleSlideshow() }
                                    )

                                    // 5. Present
                                    PresentationBottomActionItem(
                                        modifier = Modifier.weight(1f),
                                        iconPainter = painterResource(id = com.nexus.core.R.drawable.ic_maximize),
                                        label = "Present",
                                        contentDescription = "Present",
                                        isActive = false,
                                        onClick = { viewModel.toggleFullscreen() }
                                    )
                                }
                            }
                        }
                    }
                }

                // ─── Bottom Slide Thumbnail Drawer ────────────────────────────────
                AnimatedVisibility(
                    visible = state.showThumbnails && !isFullscreen,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    enter = viewerBarEnterTransition(fromTop = false),
                    exit = viewerBarExitTransition(toTop = false)
                ) {
                    NexusSurface(
                        elevation = 16.dp,
                        color = NexusTheme.colors.surface.copy(alpha = 0.98f),
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .border(
                                1.dp,
                                NexusTheme.colors.divider.copy(alpha = 0.6f),
                                RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                            )
                    ) {
                        Column(modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)) {
                            // Header Row: Title, drag handle & close button
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                NexusText(
                                    text = "Slides (${state.slideCount})",
                                    style = NexusTheme.typography.label.copy(fontWeight = FontWeight.Bold),
                                    color = NexusTheme.colors.textSecondary
                                )
                                Box(
                                    modifier = Modifier
                                        .size(36.dp, 4.dp)
                                        .clip(CircleShape)
                                        .background(NexusTheme.colors.divider)
                                )
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(NexusTheme.colors.surfaceVariant)
                                        .springBounceClick { viewModel.toggleThumbnails() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        painter = painterResource(id = com.nexus.core.R.drawable.ic_close),
                                        contentDescription = "Close thumbnails",
                                        colorFilter = ColorFilter.tint(NexusTheme.colors.textSecondary),
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                itemsIndexed(state.slides, key = { idx, _ -> idx }) { idx, slide ->
                                    val isSelected = idx == state.currentSlideIndex
                                    val safeRatio = slide.aspectRatio.takeIf { !it.isNaN() && it in 0.5f..2.5f } ?: (16f / 9f)
                                    val thumbWidth = (76.dp * safeRatio).coerceIn(60.dp, 160.dp)
                                    Box(
                                        modifier = Modifier
                                            .size(width = thumbWidth, height = 76.dp)
                                            .shadow(if (isSelected) 8.dp else 2.dp, RoundedCornerShape(8.dp))
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                slide.backgroundColorArgb?.let { Color(it) }
                                                    ?: if (isSelected) NexusTheme.colors.primary.copy(alpha = 0.16f)
                                                    else NexusTheme.colors.surfaceVariant
                                            )
                                            .border(
                                                width = if (isSelected) 2.dp else 1.dp,
                                                color = if (isSelected) NexusTheme.colors.primary else NexusTheme.colors.divider.copy(alpha = 0.6f),
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .clickable {
                                                scope.launch {
                                                    if (idx in 0 until pagerState.pageCount) {
                                                        pagerState.animateScrollToPage(idx)
                                                    }
                                                }
                                            }
                                    ) {
                                        AsyncSlideThumbnail(
                                            slide = slide,
                                            viewModel = viewModel,
                                            aspectRatio = safeRatio,
                                            modifier = Modifier.fillMaxSize()
                                        )

                                        // Selection overlay highlight
                                        if (isSelected) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(NexusTheme.colors.primary.copy(alpha = 0.10f))
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ─── Jump to Slide Dialog ──────────────────────────────────────────
                if (showJumpDialog) {
                    AlertDialog(
                        onDismissRequest = { showJumpDialog = false },
                        title = { NexusText("Jump to Slide", color = NexusTheme.colors.textPrimary) },
                        text = {
                            Column {
                                NexusText(
                                    "Enter a slide number (1 to ${state.slideCount}):",
                                    color = NexusTheme.colors.textSecondary,
                                    style = NexusTheme.typography.body
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                OutlinedTextField(
                                    value = jumpSlideInput,
                                    onValueChange = { input ->
                                        if (input.all { it.isDigit() }) jumpSlideInput = input
                                    },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                val target = jumpSlideInput.toIntOrNull()
                                if (target != null && target in 1..state.slideCount) {
                                    scope.launch {
                                        pagerState.animateScrollToPage(target - 1)
                                    }
                                }
                                showJumpDialog = false
                            }) {
                                NexusText("Jump", color = NexusTheme.colors.primary)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showJumpDialog = false }) {
                                NexusText("Cancel", color = NexusTheme.colors.textSecondary)
                            }
                        },
                        containerColor = NexusTheme.colors.surface
                    )
                }

                // ─── File Details Sheet ────────────────────────────────────────────
                if (showInfoSheet) {
                    val customDetails = listOf(
                        FileDetailItem(com.nexus.core.R.drawable.ic_view_list, "Total Slides", "${state.slideCount} slides"),
                        FileDetailItem(com.nexus.core.R.drawable.ic_maximize, "Current Slide", "Slide ${state.currentSlideIndex + 1} of ${state.slideCount}"),
                        FileDetailItem(
                            com.nexus.core.R.drawable.ic_file_ppt_new,
                            "Format",
                            if (decodedFileName.endsWith(".ppt", ignoreCase = true)) "Legacy PowerPoint (PPT)" else "Modern Presentation (PPTX)"
                        )
                    )

                    NexusFileDetailsSheet(
                        fileName = decodedFileName,
                        uri = encodedUri,
                        docType = DocumentType.PPTX,
                        customDetails = customDetails,
                        onDismissRequest = { showInfoSheet = false }
                    )
                }

                // ─── More Options Bottom Sheet ─────────────────────────────────────
                if (showMenuSheet) {
                    PresentationOptionsBottomSheet(
                        displayName = if (state.fileName.isNotBlank()) state.fileName else decodedFileName,
                        isPpt = decodedFileName.endsWith(".ppt", ignoreCase = true),
                        slideCount = state.slideCount,
                        isStarred = isStarred,
                        isLandscapeLocked = state.isLandscapeLocked,
                        onDismiss = { showMenuSheet = false },
                        onInfo = {
                            showMenuSheet = false
                            showInfoSheet = true
                        },
                        onFavorite = {
                            showMenuSheet = false
                            viewModel.toggleFavorite { nowStarred ->
                                val msg = if (nowStarred) "Added to Favorites" else "Removed from Favorites"
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        },
                        onLandscapeLock = {
                            showMenuSheet = false
                            viewModel.toggleLandscapeLock()
                        },
                        onJumpToSlide = {
                            showMenuSheet = false
                            jumpSlideInput = (state.currentSlideIndex + 1).toString()
                            showJumpDialog = true
                        },
                        onShare = {
                            showMenuSheet = false
                            sharePresentation()
                        }
                    )
                }

                // ─── 2.2 Speaker Notes Drawer Sheet ────────────────────────────────
                if (state.showSpeakerNotes) {
                    val currentSlide = state.slides.getOrNull(state.currentSlideIndex)
                    SpeakerNotesDrawer(
                        slideIndex = state.currentSlideIndex,
                        slideTitle = currentSlide?.title,
                        notes = currentSlide?.notes,
                        fontSizeSp = state.speakerNotesFontSize,
                        onIncreaseFontSize = { viewModel.increaseSpeakerNotesFontSize() },
                        onDecreaseFontSize = { viewModel.decreaseSpeakerNotesFontSize() },
                        onDismissRequest = { viewModel.toggleSpeakerNotes() }
                    )
                }

                // ─── 2.3 Lightbox Grid Sorter Sheet ────────────────────────────────
                if (state.isGridSorterVisible) {
                    SlideGridSorterSheet(
                        slides = state.slides,
                        currentSlideIndex = state.currentSlideIndex,
                        viewModel = viewModel,
                        onSelectSlide = { idx ->
                            viewModel.setCurrentSlide(idx)
                            scope.launch { pagerState.animateScrollToPage(idx) }
                            viewModel.toggleGridSorter()
                        },
                        onDismissRequest = { viewModel.toggleGridSorter() }
                    )
                }

                // ─── 2.5 Slideshow Settings Dialog ─────────────────────────────────
                if (showSlideshowSettings) {
                    val intervals = listOf(3, 5, 10, 15)
                    AlertDialog(
                        onDismissRequest = { showSlideshowSettings = false },
                        title = {
                            NexusText(
                                text = "Slideshow Settings",
                                style = NexusTheme.typography.title,
                                color = NexusTheme.colors.textPrimary
                            )
                        },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                NexusText(
                                    text = "Advance slide every:",
                                    style = NexusTheme.typography.body,
                                    color = NexusTheme.colors.textSecondary
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    for (interval in intervals) {
                                        val isSelected = (state.slideshowIntervalSeconds == interval)
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(NexusTheme.shapes.small)
                                                .background(if (isSelected) NexusTheme.colors.primary else NexusTheme.colors.surfaceVariant)
                                                .clickable { viewModel.setSlideshowInterval(interval) }
                                                .padding(vertical = 10.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            NexusText(
                                                text = "${interval}s",
                                                style = NexusTheme.typography.buttonLabel,
                                                color = if (isSelected) NexusTheme.colors.onPrimary else NexusTheme.colors.textPrimary
                                            )
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(NexusTheme.shapes.small)
                                        .clickable { viewModel.toggleSlideshowLoop() }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        NexusText(
                                            text = "Auto-Loop Presentation",
                                            style = NexusTheme.typography.body,
                                            color = NexusTheme.colors.textPrimary
                                        )
                                        NexusText(
                                            text = "Restarts from Slide 1 upon reaching the end",
                                            style = NexusTheme.typography.caption,
                                            color = NexusTheme.colors.textSecondary
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(if (state.isSlideshowLoop) NexusTheme.colors.primary else NexusTheme.colors.surfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (state.isSlideshowLoop) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                showSlideshowSettings = false
                               
                                if (!state.isSlideshowActive) {
                                    viewModel.toggleSlideshow()
                                }
                            }) {
                                NexusText(
                                    text = if (state.isSlideshowActive) "Update" else "Start Slideshow",
                                    color = NexusTheme.colors.primary
                                )
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showSlideshowSettings = false }) {
                                NexusText("Close", color = NexusTheme.colors.textSecondary)
                            }
                        },
                        containerColor = NexusTheme.colors.surface
                    )
                }
            }
        }
    }
}

@Composable
private fun PresentationBottomActionItem(
    iconPainter: Painter?,
    label: String,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    activeTint: Color = NexusTheme.colors.primary,
    inactiveTint: Color = NexusTheme.colors.textSecondary,
    isActive: Boolean = false,
    activeBadgeColor: Color = NexusTheme.colors.primary.copy(alpha = 0.12f),
    enabled: Boolean = true
) {
    val haptic = LocalHapticFeedback.current
    val animatedBgColor by animateColorAsState(
        targetValue = if (isActive) activeBadgeColor else Color.Transparent,
        animationSpec = tween(180),
        label = "presentationItemBg"
    )
    val animatedTint by animateColorAsState(
        targetValue = if (!enabled) {
            inactiveTint.copy(alpha = 0.35f)
        } else if (isActive) {
            activeTint
        } else {
            inactiveTint
        },
        animationSpec = tween(180),
        label = "presentationItemTint"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .defaultMinSize(minHeight = 58.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(animatedBgColor)
            .springBounceClick(enabled = enabled) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 2.dp, vertical = 6.dp)
    ) {
        if (iconPainter != null) {
            Image(
                painter = iconPainter,
                contentDescription = contentDescription,
                colorFilter = ColorFilter.tint(animatedTint),
                modifier = Modifier.size(21.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        NexusText(
            text = label,
            style = NexusTheme.typography.caption.copy(
                fontSize = 11.sp,
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium,
                letterSpacing = 0.1.sp
            ),
            color = animatedTint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PresentationOptionsBottomSheet(
    displayName: String,
    isPpt: Boolean,
    slideCount: Int,
    isStarred: Boolean,
    isLandscapeLocked: Boolean,
    onDismiss: () -> Unit,
    onInfo: () -> Unit,
    onFavorite: () -> Unit,
    onLandscapeLock: () -> Unit,
    onJumpToSlide: () -> Unit,
    onShare: () -> Unit
) {
    val metaText = if (slideCount > 0) {
        if (isPpt) "Legacy PowerPoint (.ppt) · $slideCount slides"
        else "PowerPoint Document (.pptx) · $slideCount slides"
    } else {
        if (isPpt) "Legacy PowerPoint (.ppt)"
        else "PowerPoint Document (.pptx)"
    }

    val group1 = listOf(
        DocumentOptionItem(
            title = "File info",
            subtitle = "View presentation properties",
            iconRes = com.nexus.core.R.drawable.ic_info,
            onClick = onInfo
        ),
        DocumentOptionItem(
            title = if (isStarred) "Starred" else "Star file",
            subtitle = "Add or remove bookmark",
            iconRes = if (isStarred) com.nexus.core.R.drawable.ic_star_filled else com.nexus.core.R.drawable.ic_star,
            isStarred = isStarred,
            showChevron = false,
            onClick = onFavorite
        ),
        DocumentOptionItem(
            title = "Share",
            subtitle = "Share this presentation",
            iconRes = com.nexus.core.R.drawable.ic_share,
            onClick = onShare
        )
    )

    val groupTools = mutableListOf(
        DocumentOptionItem(
            title = "Jump to Slide",
            subtitle = "Navigate directly to slide number",
            iconRes = com.nexus.core.R.drawable.ic_arrow_right,
            onClick = onJumpToSlide
        ),
        DocumentOptionItem(
            title = "Force Landscape",
            subtitle = if (isLandscapeLocked) "Landscape locked (Tap to unlock)" else "Lock screen orientation to landscape",
            iconRes = com.nexus.core.R.drawable.ic_rotate,
            onClick = onLandscapeLock
        )
    )

    DocumentOptionsBottomSheet(
        sheetTitle = "Document Options",
        fileName = displayName,
        fileMeta = metaText,
        docType = DocumentType.PPTX,
        groups = listOf(group1, groupTools),
        onDismiss = onDismiss
    )
}

