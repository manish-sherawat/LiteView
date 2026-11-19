package com.nexus.feature.dashboard

import android.net.Uri
import androidx.compose.animation.core.tween
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.animation.core.spring
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import com.nexus.core.theme.NexusFontFamily
import com.nexus.core.ui.animations.springBounceClick
import com.nexus.core.ui.animations.fadeSlideIn
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import com.nexus.core.ui.utils.glassBackground
import androidx.compose.foundation.border
import androidx.compose.runtime.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import com.nexus.core.navigation.DocumentType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexus.core.navigation.DocumentReaderRouter
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.components.NexusButton
import com.nexus.core.ui.components.NexusEmptyStateImage
import com.nexus.core.ui.components.NexusEmptyStateType
import com.nexus.core.ui.NexusSurface
import com.nexus.core.ui.NexusText
import com.nexus.core.ui.NexusTextField
import com.nexus.core.R
import androidx.compose.ui.graphics.Brush
import com.nexus.core.ui.animations.*
import androidx.compose.ui.graphics.asComposeRenderEffect
import android.graphics.RenderEffect
import android.graphics.Shader
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    router: DocumentReaderRouter,
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    
    val snackbarHostState = remember { SnackbarHostState() }
    val lazyGridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val searchFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    var isSearchOpen by rememberSaveable { mutableStateOf(false) }
    var searchTextFieldValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(uiState.searchQuery, TextRange(uiState.searchQuery.length)))
    }

    // Intercept back press when search bar is open to close search instead of exiting app
    androidx.activity.compose.BackHandler(enabled = isSearchOpen) {
        isSearchOpen = false
        searchTextFieldValue = TextFieldValue("", TextRange.Zero)
        viewModel.setSearchQuery("")
        keyboardController?.hide()
    }

    LaunchedEffect(isSearchOpen) {
        if (isSearchOpen) {
            lazyGridState.animateScrollToItem(0)
            kotlinx.coroutines.delay(120)
            try {
                searchFocusRequester.requestFocus()
                keyboardController?.show()
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(uiState.searchQuery) {
        if (uiState.searchQuery.isNotEmpty()) {
            isSearchOpen = true
            if (searchTextFieldValue.text.isEmpty()) {
                searchTextFieldValue = TextFieldValue(
                    text = uiState.searchQuery,
                    selection = TextRange(uiState.searchQuery.length)
                )
            }
        } else if (searchTextFieldValue.text.isNotEmpty()) {
            searchTextFieldValue = TextFieldValue("", TextRange.Zero)
        }
    }

    LaunchedEffect(Unit) {
        launch {
            viewModel.uiEvents.collect { message ->
                val isDeleteMessage = message.contains("Recycle Bin", ignoreCase = true) ||
                                     message.contains("deleted", ignoreCase = true) ||
                                     message.contains("removed", ignoreCase = true)
                val canUndo = isDeleteMessage && (viewModel.lastTrashedDocs.isNotEmpty() || viewModel.lastDeletedDocument != null)
                val result = snackbarHostState.showSnackbar(
                    message = message,
                    actionLabel = if (canUndo) "Undo" else null,
                    duration = SnackbarDuration.Short
                )
                if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                    viewModel.undoLastDelete()
                }
            }
        }
        launch {
            viewModel.searchFocusEvent.collect {
                isSearchOpen = true
                lazyGridState.animateScrollToItem(0)
                try {
                    searchFocusRequester.requestFocus()
                    keyboardController?.show()
                } catch (_: Exception) {}
            }
        }
        launch {
            viewModel.scrollToTopEvent.collect {
                lazyGridState.animateScrollToItem(0)
            }
        }
    }
    

    var isPermissionGranted by remember { mutableStateOf(viewModel.hasStoragePermission(context)) }
    var detailsDialogDoc by remember { mutableStateOf<com.nexus.feature.dashboard.data.RecentDocument?>(null) }
    var showTagManagerScreen by remember { mutableStateOf(false) }
    var tagsExpanded by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(uiState.selectedTag) {
        if (uiState.selectedTag != null) {
            tagsExpanded = true
        }
    }

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        isPermissionGranted = granted
    }

    val manageStorageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        isPermissionGranted = viewModel.hasStoragePermission(context)
    }

    LaunchedEffect(isPermissionGranted) {
        if (isPermissionGranted) {
            viewModel.scanStorage()
        }
    }

    if (showTagManagerScreen) {
        TagManagerScreen(
            tags = uiState.availableTags,
            documents = uiState.documents,
            onBack = { showTagManagerScreen = false },
            onRenameTag = { oldName, newName, colorHex, emoji ->
                viewModel.renameTagGlobally(oldName, newName, colorHex, emoji)
            },
            onUpsertTag = { name, colorHex, emoji ->
                viewModel.upsertTagDefinition(name, colorHex, emoji)
            },
            onDeleteTag = { name ->
                viewModel.deleteTagGlobally(name)
            },
            onOpenDocument = { doc ->
                showTagManagerScreen = false
                router.openDocument(
                    uri = Uri.parse(doc.uri),
                    mimeType = doc.mimeType,
                    fileName = doc.fileName
                )
            }
        )
        return
    }

    if (!isPermissionGranted && !uiState.permissionRationaleShown) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(NexusTheme.colors.background)
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = NexusTheme.spacing.lg, vertical = NexusTheme.spacing.lg),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    NexusEmptyStateImage(
                        type = NexusEmptyStateType.STORAGE,
                        contentDescription = "Permission needed",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit
                    )
                }
                NexusButton(
                    text = "Grant Storage Access",
                    onClick = {
                        viewModel.setPermissionRationaleShown()
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                            try {
                                val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                                intent.data = android.net.Uri.parse("package:${context.packageName}")
                                manageStorageLauncher.launch(intent)
                            } catch (e: Exception) {
                                val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                manageStorageLauncher.launch(intent)
                            }
                        } else {
                            requestPermissionLauncher.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        return
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NexusTheme.colors.background)
    ) {
        val pullToRefreshState = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()
        
        // ── Undo Notice & Countdown State (Task 33) ──────────────────────────
        var undoNotice by remember { mutableStateOf<String?>(null) }
        var countdownFraction by remember { mutableFloatStateOf(1f) }

        LaunchedEffect(Unit) {
            viewModel.uiEvents.collect { message ->
                if (message.contains("Recycle Bin", ignoreCase = true) || message.contains("Deleted", ignoreCase = true)) {
                    undoNotice = message
                    countdownFraction = 1f
                }
            }
        }

        LaunchedEffect(undoNotice) {
            if (undoNotice != null) {
                val startTime = System.currentTimeMillis()
                val totalDuration = 5000L
                while (true) {
                    val elapsed = System.currentTimeMillis() - startTime
                    countdownFraction = (1f - (elapsed.toFloat() / totalDuration)).coerceIn(0f, 1f)
                    if (elapsed >= totalDuration) {
                        undoNotice = null
                        break
                    }
                    kotlinx.coroutines.delay(40)
                }
            }
        }

        PullToRefreshBox(
            state = pullToRefreshState,
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.refreshDocuments() },
            modifier = Modifier.fillMaxSize(),
            indicator = {
                val fraction = pullToRefreshState.distanceFraction.coerceIn(0f, 1f)
                val rotation by animateFloatAsState(
                    targetValue = if (uiState.isRefreshing) 1440f else fraction * 180f,
                    animationSpec = if (uiState.isRefreshing) infiniteRepeatable(
                        animation = tween(900, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ) else spring(),
                    label = "ptrRotation"
                )
                val scale by animateFloatAsState(
                    targetValue = if (fraction > 0.05f || uiState.isRefreshing) 1f else 0f,
                    animationSpec = spring(dampingRatio = 0.75f, stiffness = 400f),
                    label = "ptrScale"
                )

                if (scale > 0f) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                            .padding(top = 10.dp)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationY = fraction * 48.dp.toPx()
                            }
                            .clip(NexusTheme.shapes.pill)
                            .background(NexusTheme.colors.surface)
                            .border(1.dp, NexusTheme.colors.primary.copy(alpha = 0.35f), NexusTheme.shapes.pill)
                            .shadow(
                                elevation = 8.dp,
                                shape = NexusTheme.shapes.pill,
                                spotColor = NexusTheme.colors.primary.copy(alpha = 0.35f)
                            )
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_startup),
                                contentDescription = "Refreshing",
                                modifier = Modifier
                                    .size(16.dp)
                                    .graphicsLayer { rotationZ = rotation },
                                colorFilter = ColorFilter.tint(NexusTheme.colors.primary)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            NexusText(
                                text = if (uiState.isRefreshing) "Refreshing..." else "Pull to refresh",
                                style = NexusTheme.typography.caption.copy(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = NexusTheme.colors.textPrimary
                            )
                        }
                    }
                }
            }
        ) {
            LazyVerticalGrid(
                state = lazyGridState,
                columns = if (uiState.isGridView) GridCells.Adaptive(minSize = 135.dp) else GridCells.Fixed(1),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = NexusTheme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(if (uiState.isGridView) 8.dp else 4.dp),
                horizontalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs),
                contentPadding = PaddingValues(
                    top = 0.dp, 
                    bottom = androidx.compose.foundation.layout.WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 100.dp
                )
            ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(top = NexusTheme.spacing.md)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(end = 96.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.graphicsLayer {
                                if (lazyGridState.firstVisibleItemIndex == 0) {
                                    translationY = (lazyGridState.firstVisibleItemScrollOffset * 0.45f).coerceAtMost(100f)
                                    alpha = (1f - (lazyGridState.firstVisibleItemScrollOffset / 550f)).coerceIn(0f, 1f)
                                }
                            }
                        ) {
                            NexusText(
                                text = "LiteView",
                                style = NexusTheme.typography.display,
                                color = NexusTheme.colors.textPrimary
                            )
                            val totalSize = uiState.documents.sumOf { it.doc.fileSizeBytes }
                            NexusText(
                                text = "${uiState.documents.size} documents · ${formatFileSize(totalSize)}",
                                style = NexusTheme.typography.caption,
                                color = NexusTheme.colors.textSecondary
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(14.dp))

                    // ── Dedicated Tags Filter Data ──────────────────────────
                    val visibleTags = remember(uiState.availableTags, uiState.selectedTag) {
                        uiState.availableTags.filter { it.count > 0 || it.name == uiState.selectedTag }
                    }
                    val hasTags = visibleTags.isNotEmpty() || uiState.availableTags.isNotEmpty()

                    // ── Horizontal File Type Filter Chips ─────────────────────────
                    androidx.compose.foundation.lazy.LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs),
                        contentPadding = PaddingValues(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(DocumentTypeFilter.entries.toTypedArray()) { filter ->
                            val isSelected = uiState.selectedFilter == filter
                            val count = uiState.filterCounts[filter] ?: 0
                            val chipColor = when (filter) {
                                DocumentTypeFilter.ALL -> NexusTheme.colors.primary
                                DocumentTypeFilter.PDF -> com.nexus.core.theme.DocumentAccentColors.PDF
                                DocumentTypeFilter.DOCX -> com.nexus.core.theme.DocumentAccentColors.DOCX
                                DocumentTypeFilter.PPT -> com.nexus.core.theme.DocumentAccentColors.PPTX
                                DocumentTypeFilter.XLSX -> com.nexus.core.theme.DocumentAccentColors.XLSX
                                DocumentTypeFilter.TXT -> com.nexus.core.theme.DocumentAccentColors.TXT
                            }

                            val animatedBg by animateColorAsState(
                                targetValue = if (isSelected) chipColor.copy(alpha = 0.16f) else NexusTheme.colors.surfaceVariant.copy(alpha = 0.55f),
                                animationSpec = spring(),
                                label = "formatBg"
                            )
                            val animatedBorderColor by animateColorAsState(
                                targetValue = if (isSelected) chipColor.copy(alpha = 0.75f) else NexusTheme.colors.divider.copy(alpha = 0.25f),
                                animationSpec = spring(),
                                label = "formatBorder"
                            )
                            val animatedBorderWidth by animateDpAsState(
                                targetValue = if (isSelected) 1.2.dp else 0.6.dp,
                                animationSpec = spring(),
                                label = "formatBorderWidth"
                            )
                            val animatedTextColor by animateColorAsState(
                                targetValue = if (isSelected) chipColor else NexusTheme.colors.textPrimary,
                                animationSpec = spring(),
                                label = "formatTextColor"
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(animatedBg)
                                    .border(
                                        width = animatedBorderWidth,
                                        color = animatedBorderColor,
                                        shape = androidx.compose.foundation.shape.CircleShape
                                    )
                                    .springBounceClick { viewModel.setSelectedFilter(filter) }
                                    .padding(horizontal = NexusTheme.spacing.sm, vertical = NexusTheme.spacing.xs)
                            ) {
                                if (filter != DocumentTypeFilter.ALL) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(androidx.compose.foundation.shape.CircleShape)
                                            .background(chipColor)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                NexusText(
                                    text = filter.title,
                                    style = NexusTheme.typography.caption.copy(
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                    ),
                                    color = animatedTextColor
                                )
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = count > 0,
                                    enter = fadeIn() + expandHorizontally(),
                                    exit = fadeOut() + shrinkHorizontally()
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Spacer(modifier = Modifier.width(5.dp))
                                        AnimatedContent(
                                            targetState = count,
                                            transitionSpec = {
                                                if (targetState > initialState) {
                                                    (slideInVertically { it } + fadeIn()).togetherWith(
                                                        slideOutVertically { -it } + fadeOut()
                                                    )
                                                } else {
                                                    (slideInVertically { -it } + fadeIn()).togetherWith(
                                                        slideOutVertically { it } + fadeOut()
                                                    )
                                                }
                                            },
                                            label = "formatCountTicker"
                                        ) { targetCount ->
                                            NexusText(
                                                text = "$targetCount",
                                                style = NexusTheme.typography.caption.copy(
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                ),
                                                color = if (isSelected) chipColor.copy(alpha = 0.85f) else NexusTheme.colors.textSecondary
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // ── Expand Chevron & Separator for Tags ─────────────────
                        if (hasTags) {
                            item(key = "tag_filter_separator") {
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 2.dp)
                                        .height(18.dp)
                                        .width(1.dp)
                                        .background(NexusTheme.colors.divider.copy(alpha = 0.5f))
                                )
                            }

                            item(key = "tags_expand_toggle") {
                                val isTagActive = uiState.selectedTag != null
                                val chevronRotation by animateFloatAsState(
                                    targetValue = if (tagsExpanded) 270f else 90f,
                                    animationSpec = spring(),
                                    label = "tagChevronRotation"
                                )
                                val toggleBg by animateColorAsState(
                                    targetValue = if (isTagActive || tagsExpanded)
                                        NexusTheme.colors.primary.copy(alpha = 0.16f)
                                    else
                                        NexusTheme.colors.surfaceVariant.copy(alpha = 0.55f),
                                    animationSpec = spring(),
                                    label = "tagsToggleBg"
                                )
                                val toggleBorderColor by animateColorAsState(
                                    targetValue = if (isTagActive || tagsExpanded)
                                        NexusTheme.colors.primary.copy(alpha = 0.75f)
                                    else
                                        NexusTheme.colors.divider.copy(alpha = 0.25f),
                                    animationSpec = spring(),
                                    label = "tagsToggleBorder"
                                )
                                val toggleBorderWidth by animateDpAsState(
                                    targetValue = if (isTagActive || tagsExpanded) 1.2.dp else 0.6.dp,
                                    animationSpec = spring(),
                                    label = "tagsToggleBorderWidth"
                                )
                                val toggleTextColor by animateColorAsState(
                                    targetValue = if (isTagActive || tagsExpanded)
                                        NexusTheme.colors.primary
                                    else
                                        NexusTheme.colors.textPrimary,
                                    animationSpec = spring(),
                                    label = "tagsToggleTextColor"
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                        .background(toggleBg)
                                        .border(
                                            width = toggleBorderWidth,
                                            color = toggleBorderColor,
                                            shape = androidx.compose.foundation.shape.CircleShape
                                        )
                                        .springBounceClick { tagsExpanded = !tagsExpanded }
                                        .padding(horizontal = NexusTheme.spacing.sm, vertical = NexusTheme.spacing.xs)
                                ) {
                                    Image(
                                        painter = painterResource(id = if (isTagActive) R.drawable.ic_tag_filled else R.drawable.ic_tag),
                                        contentDescription = "Tags filter",
                                        modifier = Modifier.size(13.dp),
                                        colorFilter = ColorFilter.tint(toggleTextColor)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    NexusText(
                                        text = if (isTagActive) "#${uiState.selectedTag}" else "Tags",
                                        style = NexusTheme.typography.caption.copy(
                                            fontSize = 12.sp,
                                            fontWeight = if (isTagActive || tagsExpanded) FontWeight.SemiBold else FontWeight.Normal
                                        ),
                                        color = toggleTextColor
                                    )
                                    if (!isTagActive && uiState.availableTags.isNotEmpty()) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        NexusText(
                                            text = "${uiState.availableTags.size}",
                                            style = NexusTheme.typography.caption.copy(
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = toggleTextColor.copy(alpha = 0.85f)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Image(
                                        painter = painterResource(id = R.drawable.ic_arrow_right),
                                        contentDescription = if (tagsExpanded) "Collapse tags" else "Expand tags",
                                        modifier = Modifier
                                            .size(10.dp)
                                            .graphicsLayer(rotationZ = chevronRotation),
                                        colorFilter = ColorFilter.tint(toggleTextColor)
                                    )
                                }
                            }
                        }
                    }

                    // ── Dedicated Tags Filter Ribbon ──────────────────────────
                    androidx.compose.animation.AnimatedVisibility(
                        visible = tagsExpanded && hasTags,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column {
                            Spacer(modifier = Modifier.height(8.dp))
                            androidx.compose.foundation.lazy.LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs),
                                contentPadding = PaddingValues(vertical = 2.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                // ── Pinned Manage Tags Shortcut Button at the START ──────
                                item(key = "manage_tags_btn") {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(androidx.compose.foundation.shape.CircleShape)
                                            .background(NexusTheme.colors.surfaceVariant.copy(alpha = 0.8f))
                                            .border(
                                                width = 0.8.dp,
                                                color = NexusTheme.colors.primary.copy(alpha = 0.4f),
                                                shape = androidx.compose.foundation.shape.CircleShape
                                            )
                                            .springBounceClick { showTagManagerScreen = true }
                                            .padding(horizontal = NexusTheme.spacing.xs, vertical = NexusTheme.spacing.xs)
                                    ) {
                                        Image(
                                            painter = painterResource(id = R.drawable.ic_tag),
                                            contentDescription = "Manage Tags",
                                            modifier = Modifier.size(13.dp),
                                            colorFilter = ColorFilter.tint(NexusTheme.colors.primary)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        NexusText(
                                            text = "Tags",
                                            style = NexusTheme.typography.caption.copy(
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.SemiBold
                                            ),
                                            color = NexusTheme.colors.primary
                                        )
                                    }
                                }

                                // ── Custom User Tag Filter Chips ────────────────────────
                                items(visibleTags, key = { it.name }) { tagModel ->
                                    val isSelected = uiState.selectedTag == tagModel.name
                                    val count = uiState.tagCounts[tagModel.name] ?: tagModel.count
                                    val chipColor = tagModel.color

                                    val animatedTagBg by animateColorAsState(
                                        targetValue = if (isSelected) chipColor.copy(alpha = 0.18f) else NexusTheme.colors.surfaceVariant.copy(alpha = 0.55f),
                                        animationSpec = spring(),
                                        label = "tagBg"
                                    )
                                    val animatedTagBorderColor by animateColorAsState(
                                        targetValue = if (isSelected) chipColor.copy(alpha = 0.8f) else NexusTheme.colors.divider.copy(alpha = 0.25f),
                                        animationSpec = spring(),
                                        label = "tagBorder"
                                    )
                                    val animatedTagBorderWidth by animateDpAsState(
                                        targetValue = if (isSelected) 1.2.dp else 0.6.dp,
                                        animationSpec = spring(),
                                        label = "tagBorderWidth"
                                    )
                                    val animatedTagTextColor by animateColorAsState(
                                        targetValue = if (isSelected) chipColor else NexusTheme.colors.textPrimary,
                                        animationSpec = spring(),
                                        label = "tagTextColor"
                                    )

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(androidx.compose.foundation.shape.CircleShape)
                                            .background(animatedTagBg)
                                            .border(
                                                width = animatedTagBorderWidth,
                                                color = animatedTagBorderColor,
                                                shape = androidx.compose.foundation.shape.CircleShape
                                            )
                                            .springBounceClick { viewModel.selectTag(tagModel.name) }
                                            .padding(horizontal = NexusTheme.spacing.sm, vertical = NexusTheme.spacing.xs)
                                    ) {
                                        if (!tagModel.emoji.isNullOrBlank()) {
                                            NexusText(
                                                text = tagModel.emoji,
                                                style = NexusTheme.typography.caption
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                        } else {
                                            Image(
                                                painter = painterResource(id = if (isSelected) R.drawable.ic_tag_filled else R.drawable.ic_tag),
                                                contentDescription = "Tag: ${tagModel.name}",
                                                modifier = Modifier.size(13.dp),
                                                colorFilter = ColorFilter.tint(if (isSelected) chipColor else NexusTheme.colors.textSecondary)
                                            )
                                            Spacer(modifier = Modifier.width(5.dp))
                                        }
                                        NexusText(
                                            text = "#${tagModel.name}",
                                            style = NexusTheme.typography.caption.copy(
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                            ),
                                            color = animatedTagTextColor
                                        )
                                        androidx.compose.animation.AnimatedVisibility(
                                            visible = count > 0,
                                            enter = fadeIn() + expandHorizontally(),
                                            exit = fadeOut() + shrinkHorizontally()
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Spacer(modifier = Modifier.width(5.dp))
                                                AnimatedContent(
                                                    targetState = count,
                                                    transitionSpec = {
                                                        if (targetState > initialState) {
                                                            (slideInVertically { it } + fadeIn()).togetherWith(
                                                                slideOutVertically { -it } + fadeOut()
                                                            )
                                                        } else {
                                                            (slideInVertically { -it } + fadeIn()).togetherWith(
                                                                slideOutVertically { it } + fadeOut()
                                                            )
                                                        }
                                                    },
                                                    label = "tagCountTicker"
                                                ) { targetCount ->
                                                    NexusText(
                                                        text = "$targetCount",
                                                        style = NexusTheme.typography.caption.copy(
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Bold
                                                        ),
                                                        color = if (isSelected) chipColor.copy(alpha = 0.85f) else NexusTheme.colors.textSecondary
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        var sortMenuExpanded by remember { mutableStateOf(false) }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(NexusTheme.shapes.pill)
                                        .background(NexusTheme.colors.surfaceVariant)
                                        .springBounceClick { sortMenuExpanded = true }
                                        .padding(horizontal = NexusTheme.spacing.sm, vertical = NexusTheme.spacing.xs)
                                ) {
                                    val sortLabel = when (uiState.sortOrder) {
                                        SortOrder.BY_DATE -> "Date"
                                        SortOrder.BY_NAME -> "Name"
                                        SortOrder.BY_TYPE -> "Type"
                                        SortOrder.BY_SIZE -> "Size"
                                    }
                                    NexusText(
                                        text = "Sort: $sortLabel",
                                        style = NexusTheme.typography.label.copy(fontWeight = FontWeight.Medium),
                                        color = NexusTheme.colors.textPrimary
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Image(
                                        painter = painterResource(id = R.drawable.ic_arrow_right),
                                        contentDescription = if (sortMenuExpanded) "Collapse sort options" else "Expand sort options",
                                        modifier = Modifier
                                            .size(14.dp)
                                            .graphicsLayer(rotationZ = if (sortMenuExpanded) -90f else 90f),
                                        colorFilter = ColorFilter.tint(NexusTheme.colors.textSecondary)
                                    )
                                }

                                if (sortMenuExpanded) {
                                    androidx.compose.ui.window.Popup(
                                        alignment = Alignment.TopStart,
                                        offset = androidx.compose.ui.unit.IntOffset(0, 80),
                                        onDismissRequest = { sortMenuExpanded = false },
                                        properties = androidx.compose.ui.window.PopupProperties(
                                            focusable = true,
                                            dismissOnBackPress = true,
                                            dismissOnClickOutside = true
                                        )
                                    ) {
                                        NexusSurface(
                                            shape = com.nexus.core.theme.NexusTheme.shapes.medium,
                                            color = NexusTheme.colors.surface,
                                            modifier = Modifier
                                                .width(180.dp)
                                                .border(
                                                    width = 1.dp,
                                                    color = NexusTheme.colors.divider.copy(alpha = 0.6f),
                                                    shape = com.nexus.core.theme.NexusTheme.shapes.medium
                                                )
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .glassBackground(
                                                        blurRadius = 24f,
                                                        fallbackColor = NexusTheme.colors.surface,
                                                        alpha = 0.9f,
                                                        shape = com.nexus.core.theme.NexusTheme.shapes.medium
                                                    )
                                                    .padding(NexusTheme.spacing.xs)
                                            ) {
                                                SortOrder.entries.forEach { order ->
                                                    val isSelected = uiState.sortOrder == order
                                                    val (title, iconRes) = when (order) {
                                                        SortOrder.BY_DATE -> Pair("Date Modified", R.drawable.ic_startup)
                                                        SortOrder.BY_NAME -> Pair("File Name", R.drawable.ic_rename)
                                                        SortOrder.BY_TYPE -> Pair("File Type", R.drawable.ic_book)
                                                        SortOrder.BY_SIZE -> Pair("File Size", R.drawable.ic_maximize)
                                                    }
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clip(com.nexus.core.theme.NexusTheme.shapes.medium)
                                                            .background(
                                                                if (isSelected) NexusTheme.colors.primary.copy(alpha = 0.12f)
                                                                else Color.Transparent
                                                            )
                                                            .springBounceClick {
                                                                viewModel.setSortOrder(order)
                                                                sortMenuExpanded = false
                                                            }
                                                            .padding(horizontal = NexusTheme.spacing.sm, vertical = NexusTheme.spacing.xs),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            modifier = Modifier.weight(1f)
                                                        ) {
                                                            Image(
                                                                painter = painterResource(id = iconRes),
                                                                contentDescription = title,
                                                                modifier = Modifier.size(16.dp),
                                                                colorFilter = ColorFilter.tint(
                                                                    if (isSelected) NexusTheme.colors.primary
                                                                    else NexusTheme.colors.textSecondary
                                                                )
                                                            )
                                                            Spacer(modifier = Modifier.width(10.dp))
                                                            NexusText(
                                                                text = title,
                                                                style = NexusTheme.typography.body.copy(
                                                                    fontSize = 13.sp,
                                                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                                                ),
                                                                color = if (isSelected) NexusTheme.colors.primary else NexusTheme.colors.textPrimary
                                                            )
                                                        }
                                                        if (isSelected) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(7.dp)
                                                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                                                    .background(NexusTheme.colors.primary)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Image(
                                painter = painterResource(id = if (uiState.sortAscending) R.drawable.ic_sort_asc else R.drawable.ic_sort_desc),
                                contentDescription = "Sort direction",
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(NexusTheme.colors.surfaceVariant)
                                    .springBounceClick { viewModel.toggleSortDirection() }
                                    .padding(NexusTheme.spacing.xs),
                                colorFilter = ColorFilter.tint(NexusTheme.colors.textSecondary)
                            )
                        }
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(NexusTheme.shapes.pill)
                                .background(NexusTheme.colors.surfaceVariant)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(if (!uiState.isGridView) NexusTheme.colors.primary.copy(alpha = 0.2f) else Color.Transparent)
                                    .springBounceClick { if (uiState.isGridView) viewModel.toggleGridView() }
                                    .padding(NexusTheme.spacing.xs)
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_view_list),
                                    contentDescription = "List View",
                                    modifier = Modifier.size(20.dp),
                                    colorFilter = ColorFilter.tint(if (!uiState.isGridView) NexusTheme.colors.primary else NexusTheme.colors.textSecondary)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(if (uiState.isGridView) NexusTheme.colors.primary.copy(alpha = 0.2f) else Color.Transparent)
                                    .springBounceClick { if (!uiState.isGridView) viewModel.toggleGridView() }
                                    .padding(NexusTheme.spacing.xs)
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_view_grid),
                                    contentDescription = "Grid View",
                                    modifier = Modifier.size(20.dp),
                                    colorFilter = ColorFilter.tint(if (uiState.isGridView) NexusTheme.colors.primary else NexusTheme.colors.textSecondary)
                                )
                            }
                        }
                    }
                    
                    if (uiState.isRefreshing) {
                        androidx.compose.material3.LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = NexusTheme.colors.primary,
                            trackColor = NexusTheme.colors.surfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

            if (uiState.isLoading) {
                items(10, span = { if (!uiState.isGridView) GridItemSpan(maxLineSpan) else GridItemSpan(1) }) {
                    if (uiState.isGridView) {
                        FileGridItemShimmer(modifier = Modifier.fillMaxWidth())
                    } else {
                        FileListItemShimmer(modifier = Modifier.fillMaxWidth())
                    }
                }
            } else if (!isPermissionGranted) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    val config = androidx.compose.ui.platform.LocalConfiguration.current
                    val isWide = config.screenWidthDp >= 600
                    val isTall = config.screenHeightDp >= 800
                    val emptyMinHeight = if (isTall) 480.dp else if (isWide) 420.dp else 340.dp
                    val emptyMaxHeight = if (isTall) 700.dp else if (isWide) 580.dp else 560.dp
                    val imgMaxHeight = if (isTall) 440.dp else if (isWide) 380.dp else 280.dp

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = emptyMinHeight, max = emptyMaxHeight)
                            .padding(NexusTheme.spacing.md),
                        contentAlignment = Alignment.Center
                    ) {
                        NexusEmptyStateImage(
                            type = NexusEmptyStateType.STORAGE,
                            contentDescription = "Storage access needed",
                            modifier = Modifier
                                .fillMaxWidth(if (isWide) 0.70f else 0.95f)
                                .heightIn(max = imgMaxHeight)
                                .breathePulse(minScale = 0.96f, maxScale = 1.04f, durationMs = 2800),
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit
                        )
                    }
                }
            } else if (uiState.documents.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    val config = androidx.compose.ui.platform.LocalConfiguration.current
                    val isWide = config.screenWidthDp >= 600
                    val isTall = config.screenHeightDp >= 800
                    val emptyMinHeight = if (isTall) 460.dp else if (isWide) 400.dp else 340.dp
                    val emptyMaxHeight = if (isTall) 660.dp else if (isWide) 560.dp else 520.dp
                    val imgMaxHeight = if (isTall) 420.dp else if (isWide) 360.dp else 280.dp

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = emptyMinHeight, max = emptyMaxHeight)
                            .padding(NexusTheme.spacing.md),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        val isSearching = uiState.searchQuery.isNotEmpty()
                        val (emptyType, emptyTitle, emptySubtitle) = when {
                            isSearching -> Triple(
                                NexusEmptyStateType.NOT_FOUND,
                                "No documents found",
                                "We couldn't find anything matching \"${uiState.searchQuery}\". Try a different search term or check spelling."
                            )
                            uiState.selectedTab == DashboardTab.STARRED -> Triple(
                                NexusEmptyStateType.FAVORITES,
                                "No starred documents",
                                "Tap the star icon on any document card to bookmark it here for instant access."
                            )
                            uiState.selectedTab == DashboardTab.RECENT -> Triple(
                                NexusEmptyStateType.RECENT,
                                "No recently opened files",
                                "Documents you view or edit will automatically appear here."
                            )
                            uiState.selectedTag != null -> Triple(
                                NexusEmptyStateType.EMPTY,
                                "No files with tag '${uiState.selectedTag}'",
                                "No files currently have this tag assigned. You can tag files using the tag manager."
                            )
                            uiState.selectedFilter != DocumentTypeFilter.ALL -> Triple(
                                NexusEmptyStateType.EMPTY,
                                "No ${uiState.selectedFilter.title} files found",
                                "Your device doesn't have any files in the ${uiState.selectedFilter.title} format yet."
                            )
                            else -> Triple(
                                NexusEmptyStateType.EMPTY,
                                "Your library is empty",
                                "Scan your storage or tap Browse to discover and view documents."
                            )
                        }
                        
                        NexusEmptyStateImage(
                            type = emptyType,
                            contentDescription = emptyTitle,
                            modifier = Modifier
                                .fillMaxWidth(if (isWide) 0.65f else 0.88f)
                                .weight(1f, fill = false)
                                .heightIn(max = imgMaxHeight)
                                .breathePulse(minScale = 0.96f, maxScale = 1.04f, durationMs = 2800),
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        NexusText(
                            text = emptyTitle,
                            style = NexusTheme.typography.title.copy(
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.3).sp
                            ),
                            color = NexusTheme.colors.textPrimary,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        NexusText(
                            text = emptySubtitle,
                            style = NexusTheme.typography.body.copy(fontSize = 13.sp),
                            color = NexusTheme.colors.textSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        if (isSearching) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                com.nexus.core.ui.components.NexusButton(
                                    text = "Clear Search",
                                    onClick = {
                                        searchTextFieldValue = TextFieldValue("", TextRange.Zero)
                                        viewModel.setSearchQuery("")
                                    }
                                )
                                if (uiState.selectedFilter != DocumentTypeFilter.ALL || uiState.selectedTag != null) {
                                    com.nexus.core.ui.components.NexusButton(
                                        text = "Search All Files",
                                        onClick = {
                                            viewModel.setSelectedFilter(DocumentTypeFilter.ALL)
                                            viewModel.selectTag(null)
                                        }
                                    )
                                }
                            }
                        } else if (uiState.selectedTag != null && uiState.selectedFilter != DocumentTypeFilter.ALL) {
                            com.nexus.core.ui.components.NexusButton(
                                text = "Clear All Filters",
                                onClick = {
                                    viewModel.setSelectedFilter(DocumentTypeFilter.ALL)
                                    viewModel.selectTag(null)
                                }
                            )
                        } else if (uiState.selectedTag != null) {
                            com.nexus.core.ui.components.NexusButton(
                                text = "Clear Tag Filter",
                                onClick = { viewModel.selectTag(null) }
                            )
                        } else if (uiState.selectedFilter != DocumentTypeFilter.ALL) {
                            com.nexus.core.ui.components.NexusButton(
                                text = "Show All Formats",
                                onClick = { viewModel.setSelectedFilter(DocumentTypeFilter.ALL) }
                            )
                        } else if (uiState.selectedTab != DashboardTab.ALL) {
                            com.nexus.core.ui.components.NexusButton(
                                text = "View All Documents",
                                onClick = { viewModel.setSelectedTab(DashboardTab.ALL) }
                            )
                        } else {
                            com.nexus.core.ui.components.NexusButton(
                                text = "Browse Files",
                                onClick = { router.navigateToScanner() }
                            )
                        }
                    }
                }
            } else {
                itemsIndexed(uiState.documents, key = { _, it -> it.doc.uri }) { index, uiModel ->
                    val itemModifier = Modifier
                        .animateItem()
                        .staggeredSlideFadeIn(index = index, baseDelayMs = 20, maxDelayMs = 200, offsetY = 14.dp)

                    if (uiState.isGridView) {
                        FileGridItem(
                            modifier = itemModifier,
                            doc = uiModel.doc,
                            isAccessible = uiModel.isAccessible,
                            searchQuery = uiState.searchQuery,
                            isStarred = uiState.starredUris.contains(uiModel.doc.uri),
                            isSelected = uiState.selectedUris.contains(uiModel.doc.uri),
                            tags = uiModel.tags,
                            allAvailableTags = uiState.availableTags,
                            onSaveTags = { updatedTags ->
                                viewModel.setDocumentTags(uiModel.doc.uri, updatedTags)
                            },
                            onSaveTagDefinition = { name, colorHex, emoji ->
                                viewModel.upsertTagDefinition(name, colorHex, emoji)
                            },
                            onOpenTagManager = { showTagManagerScreen = true },
                            onToggleStarred = { viewModel.toggleStarred(uiModel.doc.uri) },
                            onClick = {
                                if (uiState.isSelectionMode) {
                                    viewModel.toggleSelection(uiModel.doc.uri)
                                } else if (uiModel.isAccessible) {
                                    router.openDocument(
                                        uri = Uri.parse(uiModel.doc.uri),
                                        mimeType = uiModel.doc.mimeType,
                                        fileName = uiModel.doc.fileName
                                    )
                                }
                            },
                            onLongClick = {
                                viewModel.toggleSelection(uiModel.doc.uri)
                            },
                            onRemove = { 
                                viewModel.removeDocument(uiModel.doc.uri)
                            },
                            onShowDetails = { detailsDialogDoc = uiModel.doc },
                            onShare = { viewModel.shareDocument(uiModel.doc.uri) },
                            onRename = { newName -> viewModel.renameDocument(uiModel.doc.uri, newName) }
                        )
                    } else {
                        FileListItem(
                            modifier = itemModifier,
                            doc = uiModel.doc,
                            isAccessible = uiModel.isAccessible,
                            searchQuery = uiState.searchQuery,
                            isStarred = uiState.starredUris.contains(uiModel.doc.uri),
                            isSelected = uiState.selectedUris.contains(uiModel.doc.uri),
                            tags = uiModel.tags,
                            allAvailableTags = uiState.availableTags,
                            onSaveTags = { updatedTags ->
                                viewModel.setDocumentTags(uiModel.doc.uri, updatedTags)
                            },
                            onSaveTagDefinition = { name, colorHex, emoji ->
                                viewModel.upsertTagDefinition(name, colorHex, emoji)
                            },
                            onOpenTagManager = { showTagManagerScreen = true },
                            onToggleStarred = { viewModel.toggleStarred(uiModel.doc.uri) },
                            onClick = {
                                if (uiState.isSelectionMode) {
                                    viewModel.toggleSelection(uiModel.doc.uri)
                                } else if (uiModel.isAccessible) {
                                    router.openDocument(
                                        uri = Uri.parse(uiModel.doc.uri),
                                        mimeType = uiModel.doc.mimeType,
                                        fileName = uiModel.doc.fileName
                                    )
                                }
                            },
                            onLongClick = {
                                viewModel.toggleSelection(uiModel.doc.uri)
                            },
                            onRemove = { 
                                viewModel.removeDocument(uiModel.doc.uri)
                            },
                            onShowDetails = { detailsDialogDoc = uiModel.doc },
                            onShare = { viewModel.shareDocument(uiModel.doc.uri) },
                            onRename = { newName -> viewModel.renameDocument(uiModel.doc.uri, newName) }
                        )
                    }
                } // End of itemsIndexed block
            } // End of else block
            } // End of LazyVerticalGrid block
        } // End of PullToRefreshBox block

        detailsDialogDoc?.let { doc ->
            FileDetailsSheet(
                doc = doc,
                onDismissRequest = { detailsDialogDoc = null },
                onOpen = {
                    detailsDialogDoc = null
                    router.openDocument(
                        uri = Uri.parse(doc.uri),
                        mimeType = doc.mimeType,
                        fileName = doc.fileName
                    )
                }
            )
        }

        // ── Floating Search Bar (When Search is Opened) ───────────────────────
        androidx.compose.animation.AnimatedVisibility(
            visible = isSearchOpen && !uiState.isSelectionMode,
            enter = fadeIn(tween(200)) + slideInVertically(tween(250)) { -it },
            exit = fadeOut(tween(150)) + slideOutVertically(tween(200)) { -it },
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 16.dp, end = 98.dp, top = 12.dp)
                .zIndex(25f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .shadow(8.dp, CircleShape, spotColor = Color.Black.copy(alpha = 0.15f))
                    .clip(CircleShape)
                    .background(NexusTheme.colors.surface)
                    .border(1.dp, NexusTheme.colors.divider.copy(alpha = 0.7f), CircleShape)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_search),
                        contentDescription = "Search",
                        modifier = Modifier.size(16.dp),
                        colorFilter = ColorFilter.tint(NexusTheme.colors.textSecondary)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    BasicTextField(
                        value = searchTextFieldValue,
                        onValueChange = { newValue ->
                            searchTextFieldValue = newValue
                            viewModel.setSearchQuery(newValue.text)
                        },
                        singleLine = true,
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(NexusTheme.colors.primary),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            imeAction = androidx.compose.ui.text.input.ImeAction.Search
                        ),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                            onSearch = { keyboardController?.hide() }
                        ),
                        textStyle = TextStyle(
                            fontFamily = NexusFontFamily,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Normal,
                            color = NexusTheme.colors.textPrimary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(searchFocusRequester),
                        decorationBox = { innerTextField ->
                            if (searchTextFieldValue.text.isEmpty()) {
                                NexusText(
                                    text = "Search files by name, type, or tag...",
                                    style = NexusTheme.typography.body.copy(
                                        fontSize = 14.sp,
                                        color = NexusTheme.colors.textSecondary.copy(alpha = 0.7f)
                                    )
                                )
                            }
                            innerTextField()
                        }
                    )
                    if (searchTextFieldValue.text.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(NexusTheme.colors.surfaceVariant)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    searchTextFieldValue = TextFieldValue("", TextRange.Zero)
                                    viewModel.setSearchQuery("")
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_close),
                                contentDescription = "Clear",
                                modifier = Modifier.size(12.dp),
                                colorFilter = ColorFilter.tint(NexusTheme.colors.textPrimary)
                            )
                        }
                    }
                }
            }
        }

        // ── Fixed Floating Top-Right Action Controls (Combined Pill Bar: Star & Search) ────────
        androidx.compose.animation.AnimatedVisibility(
            visible = !uiState.isSelectionMode,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(150)),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 14.dp, end = 16.dp)
                .zIndex(30f)
        ) {
            val isStarred = uiState.selectedTab == DashboardTab.STARRED

            // Unified Pill Shape Bar
            Box(
                modifier = Modifier
                    .shadow(
                        elevation = 3.dp,
                        shape = CircleShape,
                        spotColor = Color.Black.copy(alpha = 0.12f)
                    )
                    .clip(CircleShape)
                    .background(NexusTheme.colors.surface.copy(alpha = 0.94f))
                    .border(
                        width = 1.dp,
                        color = NexusTheme.colors.divider.copy(alpha = 0.65f),
                        shape = CircleShape
                    )
                    .padding(3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // ── Star (Favorites) Button ────────────────────────
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(
                                if (isStarred) Color(0xFFF59E0B).copy(alpha = 0.18f)
                                else Color.Transparent
                            )
                            .springBounceClick {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.setSelectedTab(if (isStarred) DashboardTab.ALL else DashboardTab.STARRED)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = if (isStarred) R.drawable.ic_star_filled else R.drawable.ic_star),
                            contentDescription = if (isStarred) "Show All Files" else "Show Starred Files",
                            modifier = Modifier.size(17.dp),
                            colorFilter = ColorFilter.tint(if (isStarred) Color(0xFFF59E0B) else NexusTheme.colors.textSecondary)
                        )
                    }

                    // ── Subtle Vertical Divider ───────────────────────
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(16.dp)
                            .background(NexusTheme.colors.divider.copy(alpha = 0.6f))
                    )

                    // ── Search / Close Button ──────────────────────────
                    val searchBtnBg by animateColorAsState(
                        targetValue = if (isSearchOpen) NexusTheme.colors.primary.copy(alpha = 0.16f) else Color.Transparent,
                        animationSpec = spring(),
                        label = "searchBtnBg"
                    )
                    val searchBtnBorderColor by animateColorAsState(
                        targetValue = if (isSearchOpen) NexusTheme.colors.primary.copy(alpha = 0.35f) else Color.Transparent,
                        animationSpec = spring(),
                        label = "searchBtnBorder"
                    )
                    val searchBtnTint by animateColorAsState(
                        targetValue = if (isSearchOpen) NexusTheme.colors.primary else NexusTheme.colors.textSecondary,
                        animationSpec = spring(),
                        label = "searchBtnTint"
                    )

                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(searchBtnBg)
                            .border(
                                width = if (isSearchOpen) 1.dp else 0.dp,
                                color = searchBtnBorderColor,
                                shape = CircleShape
                            )
                            .springBounceClick {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                isSearchOpen = !isSearchOpen
                                if (!isSearchOpen) {
                                    searchTextFieldValue = TextFieldValue("", TextRange.Zero)
                                    viewModel.setSearchQuery("")
                                    keyboardController?.hide()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = if (isSearchOpen) R.drawable.ic_close else R.drawable.ic_search),
                            contentDescription = if (isSearchOpen) "Close Search" else "Search Files",
                            modifier = Modifier.size(if (isSearchOpen) 18.dp else 16.dp),
                            colorFilter = ColorFilter.tint(searchBtnTint)
                        )
                    }
                }
            }
        }

        // Multi-Select Action Bar with Solid Opaque Background
        androidx.compose.animation.AnimatedVisibility(
            visible = uiState.isSelectionMode,
            enter = androidx.compose.animation.slideInVertically(
                initialOffsetY = { it * 2 },
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 320f)
            ) + androidx.compose.animation.fadeIn(animationSpec = tween(200)),
            exit = androidx.compose.animation.slideOutVertically(
                targetOffsetY = { it * 2 },
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 320f)
            ) + androidx.compose.animation.fadeOut(animationSpec = tween(150)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            val areAllSelected = uiState.selectedUris.size == uiState.documents.size && uiState.documents.isNotEmpty()

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = NexusTheme.spacing.md, vertical = NexusTheme.spacing.md)
                    .shadow(
                        elevation = 20.dp,
                        shape = com.nexus.core.theme.NexusTheme.shapes.large,
                        spotColor = Color.Black.copy(alpha = 0.35f),
                        ambientColor = Color.Black.copy(alpha = 0.2f)
                    )
                    .clip(com.nexus.core.theme.NexusTheme.shapes.large)
                    .background(NexusTheme.colors.surface)
                    .border(
                        width = 1.dp,
                        color = NexusTheme.colors.divider.copy(alpha = 0.5f),
                        shape = com.nexus.core.theme.NexusTheme.shapes.large
                    )
                    .padding(horizontal = NexusTheme.spacing.md, vertical = NexusTheme.spacing.sm)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xxs)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(NexusTheme.colors.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                NexusText(
                                    text = "${uiState.selectedUris.size}",
                                    style = NexusTheme.typography.caption.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.5.sp
                                    ),
                                    color = NexusTheme.colors.onPrimary
                                )
                            }
                            NexusText(
                                text = if (uiState.selectedUris.size == 1) "File selected" else "Files selected",
                                style = NexusTheme.typography.buttonLabel,
                                color = NexusTheme.colors.textPrimary
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(com.nexus.core.theme.NexusTheme.shapes.small)
                                .background(NexusTheme.colors.primary.copy(alpha = 0.12f))
                                .springBounceClick {
                                    if (areAllSelected) viewModel.clearSelection() else viewModel.selectAll()
                                }
                                .padding(horizontal = NexusTheme.spacing.xs, vertical = NexusTheme.spacing.xxs)
                        ) {
                            NexusText(
                                text = if (areAllSelected) "Deselect all" else "Select all",
                                style = NexusTheme.typography.caption.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.sp
                                ),
                                color = NexusTheme.colors.primary
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(NexusTheme.spacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Share Action
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.springBounceClick {
                                viewModel.shareSelectedDocuments(uiState.selectedUris)
                                viewModel.clearSelection()
                            }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(NexusTheme.colors.surfaceVariant)
                                    .border(0.8.dp, NexusTheme.colors.divider.copy(alpha = 0.4f), androidx.compose.foundation.shape.CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_share),
                                    contentDescription = "Share",
                                    modifier = Modifier.size(18.dp),
                                    colorFilter = ColorFilter.tint(NexusTheme.colors.primary)
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            NexusText(
                                text = "Share",
                                style = NexusTheme.typography.labelXs,
                                color = NexusTheme.colors.textPrimary
                            )
                        }

                        // Delete Action
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.springBounceClick { viewModel.deleteSelected() }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(NexusTheme.colors.error.copy(alpha = 0.14f))
                                    .border(0.8.dp, NexusTheme.colors.error.copy(alpha = 0.35f), androidx.compose.foundation.shape.CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_delete),
                                    contentDescription = "Delete",
                                    modifier = Modifier.size(18.dp),
                                    colorFilter = ColorFilter.tint(NexusTheme.colors.error)
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            NexusText(
                                text = "Delete",
                                style = NexusTheme.typography.labelXs,
                                color = NexusTheme.colors.error
                            )
                        }

                        // Close Action
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.springBounceClick { viewModel.clearSelection() }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(NexusTheme.colors.surfaceVariant)
                                    .border(0.8.dp, NexusTheme.colors.divider.copy(alpha = 0.4f), androidx.compose.foundation.shape.CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = com.nexus.core.R.drawable.ic_close),
                                    contentDescription = "Close",
                                    modifier = Modifier.size(16.dp),
                                    colorFilter = ColorFilter.tint(NexusTheme.colors.textSecondary)
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            NexusText(
                                text = "Close",
                                style = NexusTheme.typography.labelXs,
                                color = NexusTheme.colors.textSecondary
                            )
                        }
                    }
                }
            }
        }

        // ── Bouncy FAB on First Launch (Task 23) ─────────────────────────
        var fabVisible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(250)
            fabVisible = true
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = fabVisible && !uiState.isSelectionMode,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 20.dp, bottom = 92.dp),
            enter = scaleIn(
                initialScale = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ) + fadeIn(animationSpec = tween(250)),
            exit = scaleOut(
                targetScale = 0f,
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f)
            ) + fadeOut(animationSpec = tween(150))
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(NexusTheme.colors.primaryGradient)
                    .shadow(
                        elevation = 10.dp,
                        shape = CircleShape,
                        spotColor = NexusTheme.colors.primary.copy(alpha = 0.5f),
                        ambientColor = NexusTheme.colors.primary.copy(alpha = 0.25f)
                    )
                    .springBounceClick { router.navigateToScanner() },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_text_scan),
                    contentDescription = "Scan Document",
                    modifier = Modifier.size(24.dp),
                    colorFilter = ColorFilter.tint(NexusTheme.colors.onPrimary)
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = if (uiState.isSelectionMode) 130.dp else 32.dp, start = 16.dp, end = 16.dp),
            snackbar = { snackbarData ->
                val isDark = androidx.compose.foundation.isSystemInDarkTheme()
                val isUndo = snackbarData.visuals.actionLabel == "Undo"

                var countdownFraction by remember { mutableFloatStateOf(1f) }
                LaunchedEffect(snackbarData) {
                    if (isUndo) {
                        val startTime = System.currentTimeMillis()
                        val duration = 4000L
                        while (true) {
                            val elapsed = System.currentTimeMillis() - startTime
                            countdownFraction = (1f - (elapsed.toFloat() / duration)).coerceIn(0f, 1f)
                            if (elapsed >= duration) break
                            kotlinx.coroutines.delay(30)
                        }
                    }
                }
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(NexusTheme.shapes.medium)
                        .background(
                            if (isDark) NexusTheme.colors.surfaceVariant else NexusTheme.colors.surfaceVariant
                        )
                        .border(
                            width = 1.dp,
                            color = (if (isUndo) NexusTheme.colors.error else NexusTheme.colors.primary).copy(alpha = 0.5f),
                            shape = NexusTheme.shapes.medium
                        )
                        .shadow(12.dp, NexusTheme.shapes.medium)
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = NexusTheme.spacing.md, vertical = NexusTheme.spacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(NexusTheme.spacing.sm),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                        .background(
                                            if (isUndo) NexusTheme.colors.error.copy(alpha = 0.2f)
                                            else NexusTheme.colors.primary.copy(alpha = 0.2f)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        painter = painterResource(
                                            id = if (isUndo) R.drawable.ic_delete else com.nexus.core.R.drawable.ic_check
                                        ),
                                        contentDescription = if (isUndo) "Deleted" else "Success",
                                        modifier = Modifier.size(18.dp),
                                        colorFilter = ColorFilter.tint(
                                            if (isUndo) NexusTheme.colors.error else NexusTheme.colors.primary
                                        )
                                    )
                                }
                                
                                NexusText(
                                    text = snackbarData.visuals.message,
                                    style = NexusTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
                                    color = Color.White
                                )
                            }

                            snackbarData.visuals.actionLabel?.let { actionText ->
                                Box(
                                    modifier = Modifier
                                        .clip(NexusTheme.shapes.pill)
                                        .background(NexusTheme.colors.primary)
                                        .springBounceClick { snackbarData.performAction() }
                                        .padding(horizontal = NexusTheme.spacing.md, vertical = NexusTheme.spacing.xs)
                                ) {
                                    NexusText(
                                        text = actionText,
                                        style = NexusTheme.typography.label.copy(fontWeight = FontWeight.Bold),
                                        color = Color.White
                                    )
                                }
                            }
                        }

                        // Countdown Timer Line (Task 33)
                        if (isUndo) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(countdownFraction)
                                    .height(2.5.dp)
                                    .background(NexusTheme.colors.error)
                            )
                        }
                    }
                }
            }
        )
    }
}

@Composable
fun TabButton(text: String, isSelected: Boolean, onClick: () -> Unit) {
    val bgColor = if (isSelected) NexusTheme.colors.primary else NexusTheme.colors.surfaceVariant
    val textColor = if (isSelected) NexusTheme.colors.onPrimary else NexusTheme.colors.textPrimary

    Box(
        modifier = Modifier
            .springBounceClick(scaleDown = 0.90f, onClick = onClick)
            .clip(NexusTheme.shapes.pill)
            .background(bgColor)
            .padding(horizontal = NexusTheme.spacing.lg, vertical = NexusTheme.spacing.sm)
    ) {
        NexusText(
            text = text,
            color = textColor,
            style = NexusTheme.typography.label.copy(fontWeight = FontWeight.Bold)
        )
    }
}

@Composable
fun FileCard(
    doc: com.nexus.feature.dashboard.data.RecentDocument,
    isStarred: Boolean,
    onClick: () -> Unit
) {
    NexusSurface(
        shape = NexusTheme.shapes.large,
        elevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .springBounceClick { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(NexusTheme.spacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            com.nexus.core.ui.components.FileTypeIcon(
                type = runCatching { com.nexus.core.navigation.DocumentType.valueOf(doc.documentType) }.getOrDefault(com.nexus.core.navigation.DocumentType.UNKNOWN),
                size = 48.dp
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                NexusText(
                    text = doc.fileName,
                    style = NexusTheme.typography.title,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(4.dp))
                NexusText(
                    text = doc.documentType,
                    style = NexusTheme.typography.caption,
                    color = NexusTheme.colors.textSecondary
                )
            }
            
            if (isStarred) {
                Image(
                    painter = painterResource(id = R.drawable.ic_star),
                    contentDescription = "Starred",
                    modifier = Modifier.size(24.dp),
                    colorFilter = ColorFilter.tint(NexusTheme.colors.primary)
                )
            }
        }
    }
}
