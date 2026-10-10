package com.nexus.feature.reader.presentation.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import com.nexus.core.ui.components.NexusTopBar
import com.nexus.feature.reader.presentation.model.PresentationUiState
import com.nexus.feature.reader.presentation.ui.components.SlideViewport
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

    var isImmersiveMode by remember { mutableStateOf(false) }
    var showJumpDialog by remember { mutableStateOf(false) }
    var showMenuSheet by remember { mutableStateOf(false) }
    var showInfoSheet by remember { mutableStateOf(false) }
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
        if (successState?.showThumbnails == true) {
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
                val pagerState = rememberPagerState(
                    initialPage = state.currentSlideIndex,
                    pageCount = { state.slideCount }
                )

                LaunchedEffect(pagerState) {
                    snapshotFlow { pagerState.currentPage }.collect { page ->
                        viewModel.setCurrentSlide(page)
                    }
                }

                // ─── Main Slide HorizontalPager Viewport ───────────────────────────
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { pageIndex ->
                    val slide = state.slides.getOrNull(pageIndex)
                    if (slide != null) {
                        SlideViewport(
                            slideInfo = slide,
                            loadSlideBitmap = { idx, w, h -> viewModel.getSlideBitmap(idx, w, h) },
                            onToggleImmersive = { isImmersiveMode = !isImmersiveMode }
                        )
                    }
                }

                // ─── Top Bar (Standard Nexus Luxury Top Bar) ─────────────────────
                AnimatedVisibility(
                    visible = !isFullscreen,
                    enter = slideInVertically { -it } + fadeIn(),
                    exit = slideOutVertically { -it } + fadeOut(),
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    NexusTopBar(
                        title = state.fileName,
                        navigationIcon = {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .springBounceClick(onClick = onNavigateBack),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = com.nexus.core.R.drawable.ic_back),
                                    contentDescription = "Back",
                                    colorFilter = ColorFilter.tint(NexusTheme.colors.textPrimary),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        },
                        actions = {
                            // Star / Favorite Action
                            Image(
                                painter = painterResource(
                                    id = if (isStarred) com.nexus.core.R.drawable.ic_star_filled else com.nexus.core.R.drawable.ic_star
                                ),
                                contentDescription = "Favorite",
                                colorFilter = ColorFilter.tint(
                                    if (isStarred) Color(0xFFFFB300) else NexusTheme.colors.textPrimary
                                ),
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .springBounceClick {
                                        viewModel.toggleFavorite { nowStarred ->
                                            val msg = if (nowStarred) "Added to Favorites" else "Removed from Favorites"
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    .padding(NexusTheme.spacing.xs)
                            )

                            // Share Action
                            Image(
                                painter = painterResource(id = com.nexus.core.R.drawable.ic_share),
                                contentDescription = "Share",
                                colorFilter = ColorFilter.tint(NexusTheme.colors.textPrimary),
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .springBounceClick { sharePresentation() }
                                    .padding(NexusTheme.spacing.xs)
                            )

                            // Fullscreen Presentation Mode Action
                            Image(
                                painter = painterResource(id = com.nexus.core.R.drawable.ic_maximize),
                                contentDescription = "Present",
                                colorFilter = ColorFilter.tint(NexusTheme.colors.textPrimary),
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .springBounceClick { viewModel.toggleFullscreen() }
                                    .padding(NexusTheme.spacing.xs)
                            )

                            // More Options Action
                            Image(
                                painter = painterResource(id = com.nexus.core.R.drawable.ic_more_vert),
                                contentDescription = "More options",
                                colorFilter = ColorFilter.tint(NexusTheme.colors.textPrimary),
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .springBounceClick { showMenuSheet = true }
                                    .padding(NexusTheme.spacing.xs)
                            )
                        }
                    )
                }

                // ─── Floating Bottom Controls & Page Badge ─────────────────────────
                AnimatedVisibility(
                    visible = !isFullscreen,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(bottom = NexusTheme.spacing.xs),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Floating Slide Badge Pill (Click to jump)
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
                                    jumpSlideInput = (state.currentSlideIndex + 1).toString()
                                    showJumpDialog = true
                                }
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
                                    text = "Slide ${state.currentSlideIndex + 1}",
                                    style = NexusTheme.typography.body.copy(fontWeight = FontWeight.Bold, fontSize = 13.5.sp),
                                    color = NexusTheme.colors.primary
                                )
                                NexusText(
                                    text = "of ${state.slideCount}",
                                    style = NexusTheme.typography.label,
                                    color = NexusTheme.colors.textSecondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Floating Dock Toolbar Container
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentWidth(Alignment.CenterHorizontally)
                                .widthIn(max = 560.dp)
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
                                // Slide Strip Toggle
                                PresentationBottomActionItem(
                                    modifier = Modifier.weight(1f),
                                    iconPainter = painterResource(id = com.nexus.core.R.drawable.ic_view_list),
                                    label = "Slides",
                                    contentDescription = "Slides",
                                    tint = NexusTheme.colors.primary,
                                    isActive = state.showThumbnails,
                                    onClick = { viewModel.toggleThumbnails() }
                                )

                                // Jump to Slide Action
                                PresentationBottomActionItem(
                                    modifier = Modifier.weight(1f),
                                    iconPainter = painterResource(id = com.nexus.core.R.drawable.ic_arrow_right),
                                    label = "Jump",
                                    contentDescription = "Jump Slide",
                                    tint = NexusTheme.colors.textPrimary,
                                    onClick = {
                                        jumpSlideInput = (state.currentSlideIndex + 1).toString()
                                        showJumpDialog = true
                                    }
                                )

                                // Favorite Action
                                PresentationBottomActionItem(
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
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )

                                // Share Action
                                PresentationBottomActionItem(
                                    modifier = Modifier.weight(1f),
                                    iconPainter = painterResource(id = com.nexus.core.R.drawable.ic_share),
                                    label = "Share",
                                    contentDescription = "Share",
                                    tint = NexusTheme.colors.textPrimary,
                                    onClick = { sharePresentation() }
                                )

                                // Fullscreen Presentation Mode
                                PresentationBottomActionItem(
                                    modifier = Modifier.weight(1f),
                                    iconPainter = painterResource(id = com.nexus.core.R.drawable.ic_maximize),
                                    label = "Present",
                                    contentDescription = "Present",
                                    tint = NexusTheme.colors.textPrimary,
                                    onClick = { viewModel.toggleFullscreen() }
                                )
                            }
                        }
                    }
                }

                // ─── Bottom Slide Thumbnail Drawer ────────────────────────────────
                AnimatedVisibility(
                    visible = state.showThumbnails && !isFullscreen,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut()
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
                        Column(modifier = Modifier.padding(top = 10.dp, bottom = 12.dp)) {
                            // Top Drag handle
                            Box(
                                modifier = Modifier
                                    .size(36.dp, 4.dp)
                                    .clip(CircleShape)
                                    .background(NexusTheme.colors.divider)
                                    .align(Alignment.CenterHorizontally)
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                itemsIndexed(state.slides) { idx, slide ->
                                    val isSelected = idx == state.currentSlideIndex
                                    Box(
                                        modifier = Modifier
                                            .height(76.dp)
                                            .aspectRatio(slide.aspectRatio)
                                            .shadow(if (isSelected) 8.dp else 2.dp, RoundedCornerShape(8.dp))
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isSelected) NexusTheme.colors.primary.copy(alpha = 0.16f)
                                                else NexusTheme.colors.surfaceVariant
                                            )
                                            .border(
                                                width = if (isSelected) 2.dp else 1.dp,
                                                color = if (isSelected) NexusTheme.colors.primary else NexusTheme.colors.divider.copy(alpha = 0.6f),
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .clickable {
                                                scope.launch {
                                                    pagerState.animateScrollToPage(idx)
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        NexusText(
                                            text = "${idx + 1}",
                                            style = NexusTheme.typography.body.copy(
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                fontSize = 14.sp
                                            ),
                                            color = if (isSelected) NexusTheme.colors.primary else NexusTheme.colors.textSecondary
                                        )
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
                    val sheetState = rememberModalBottomSheetState()
                    ModalBottomSheet(
                        onDismissRequest = { showMenuSheet = false },
                        sheetState = sheetState,
                        containerColor = NexusTheme.colors.surface,
                        contentColor = NexusTheme.colors.textPrimary,
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 12.dp)
                                .navigationBarsPadding()
                        ) {
                            NexusText(
                                text = decodedFileName,
                                style = NexusTheme.typography.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            // Details
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(NexusTheme.shapes.medium)
                                    .clickable {
                                        showMenuSheet = false
                                        showInfoSheet = true
                                    }
                                    .padding(vertical = 12.dp, horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(id = com.nexus.core.R.drawable.ic_info),
                                    contentDescription = null,
                                    tint = NexusTheme.colors.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                NexusText("Document Details", style = NexusTheme.typography.body)
                            }

                            // Jump to Slide
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(NexusTheme.shapes.medium)
                                    .clickable {
                                        showMenuSheet = false
                                        jumpSlideInput = (state.currentSlideIndex + 1).toString()
                                        showJumpDialog = true
                                    }
                                    .padding(vertical = 12.dp, horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(id = com.nexus.core.R.drawable.ic_arrow_right),
                                    contentDescription = null,
                                    tint = NexusTheme.colors.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                NexusText("Jump to Slide", style = NexusTheme.typography.body)
                            }

                            // Share
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(NexusTheme.shapes.medium)
                                    .clickable {
                                        showMenuSheet = false
                                        sharePresentation()
                                    }
                                    .padding(vertical = 12.dp, horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(id = com.nexus.core.R.drawable.ic_share),
                                    contentDescription = null,
                                    tint = NexusTheme.colors.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                NexusText("Share Presentation", style = NexusTheme.typography.body)
                            }

                            // Favorite
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(NexusTheme.shapes.medium)
                                    .clickable {
                                        showMenuSheet = false
                                        viewModel.toggleFavorite { nowStarred ->
                                            val msg = if (nowStarred) "Added to Favorites" else "Removed from Favorites"
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    .padding(vertical = 12.dp, horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(
                                        id = if (isStarred) com.nexus.core.R.drawable.ic_star_filled else com.nexus.core.R.drawable.ic_star
                                    ),
                                    contentDescription = null,
                                    tint = Color(0xFFFFB300),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                NexusText(
                                    if (isStarred) "Remove from Favorites" else "Add to Favorites",
                                    style = NexusTheme.typography.body
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
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
    tint: Color = NexusTheme.colors.textPrimary,
    isActive: Boolean = false,
    activeBadgeColor: Color = NexusTheme.colors.primary.copy(alpha = 0.14f),
    enabled: Boolean = true
) {
    val haptic = LocalHapticFeedback.current
    val animatedBadgeColor by animateColorAsState(
        targetValue = if (isActive) activeBadgeColor else Color.Transparent,
        animationSpec = tween(200),
        label = "presentationBadgeColor"
    )
    val animatedTint by animateColorAsState(
        targetValue = if (isActive) tint else if (!enabled) NexusTheme.colors.textSecondary.copy(alpha = 0.35f) else NexusTheme.colors.textPrimary.copy(alpha = 0.85f),
        animationSpec = tween(200),
        label = "presentationIconTint"
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
