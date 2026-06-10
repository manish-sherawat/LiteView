package com.nexus.feature.reader.pdf

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.runtime.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusButton
import com.nexus.core.ui.NexusSurface
import com.nexus.core.ui.NexusText
import com.nexus.core.ui.NexusTopBar
import kotlinx.coroutines.launch
import java.net.URLDecoder
import kotlin.math.roundToInt
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.Animatable

@Composable
fun PdfReaderScreen(
    encodedUri: String,
    fileName: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PdfReaderViewModel = hiltViewModel()
) {
    LaunchedEffect(encodedUri, fileName) {
        viewModel.loadPdf(encodedUri, fileName)
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val renderedPages by viewModel.renderedPages.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    
    val displayName = try { URLDecoder.decode(URLDecoder.decode(fileName, "UTF-8"), "UTF-8") } catch (_: Exception) { fileName }

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.roundToPx() }
    
    var isImmersiveMode by remember { mutableStateOf(false) }
    var isSearchMode by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var pageRotation by remember { mutableIntStateOf(0) }
    var renameText by remember { mutableStateOf("") }
    
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val searchHighlights by viewModel.searchHighlights.collectAsStateWithLifecycle()
    val currentSearchMatchIndex by viewModel.currentSearchMatchIndex.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current

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
                label = "pdfReaderState"
            ) { state ->
                when (state) {
                    is PdfReaderUiState.Loading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            NexusText("Loading PDF...", color = NexusTheme.colors.textSecondary)
                        }
                    }
                    is PdfReaderUiState.Success -> {
                        Box(modifier = Modifier.fillMaxSize()) {
                            val topPadding by animateDpAsState(targetValue = if (isImmersiveMode) 16.dp else 140.dp)
                            val bottomPadding by animateDpAsState(targetValue = if (isImmersiveMode) 16.dp else 140.dp)
                            
                            val coroutineScope = rememberCoroutineScope()
                            var scale by remember { mutableFloatStateOf(1f) }
                            var offsetX by remember { mutableFloatStateOf(0f) }
                            var offsetY by remember { mutableFloatStateOf(0f) }
                            val maxZoom = 5f
                            val screenWidthDp = configuration.screenWidthDp.dp
                            val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
                            val screenWidthPxInt = screenWidthPx
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onDoubleTap = { tapOffset ->
                                                val targetScale = if (scale > 1f) 1f else 2.5f
                                                coroutineScope.launch {
                                                    if (targetScale == 1f) {
                                                        launch { Animatable(scale).animateTo(1f) { scale = value } }
                                                        launch { Animatable(offsetX).animateTo(0f) { offsetX = value } }
                                                        launch { Animatable(offsetY).animateTo(0f) { offsetY = value } }
                                                    } else {
                                                        val zoomFactor = targetScale / scale
                                                        val newOffsetX = tapOffset.x - (tapOffset.x - offsetX) * zoomFactor
                                                        val newOffsetY = tapOffset.y - (tapOffset.y - offsetY) * zoomFactor
                                                        
                                                        launch { Animatable(scale).animateTo(targetScale) { scale = value } }
                                                        launch { Animatable(offsetX).animateTo(newOffsetX) { offsetX = value } }
                                                        launch { Animatable(offsetY).animateTo(newOffsetY) { offsetY = value } }
                                                    }
                                                }
                                            },
                                            onTap = { isImmersiveMode = !isImmersiveMode }
                                        )
                                    }
                                    .pointerInput(Unit) {
                                        awaitEachGesture {
                                            awaitFirstDown()
                                            do {
                                                val event = awaitPointerEvent()
                                                val zoomChange = event.calculateZoom()
                                                val panChange = event.calculatePan()
                                                val centroid = event.calculateCentroid()
                                                
                                                val isMultiTouch = event.changes.size > 1
                                                if (scale > 1f || isMultiTouch) {
                                                    val oldScale = scale
                                                    scale = (oldScale * zoomChange).coerceIn(1f, maxZoom)
                                                    val actualZoom = scale / oldScale
                                                    
                                                    offsetX = centroid.x - (centroid.x - offsetX) * actualZoom + panChange.x
                                                    offsetY = centroid.y - (centroid.y - offsetY) * actualZoom + panChange.y
                                                    
                                                    event.changes.forEach { it.consume() }
                                                }
                                            } while (event.changes.any { it.pressed })
                                            
                                            if (scale <= 1f) {
                                                coroutineScope.launch {
                                                    launch { Animatable(offsetX).animateTo(0f) { offsetX = value } }
                                                    launch { Animatable(offsetY).animateTo(0f) { offsetY = value } }
                                                }
                                            } else {
                                                val maxOffsetX = 0f
                                                val minOffsetX = -(screenWidthPxInt * scale - screenWidthPxInt)
                                                val clampedOffsetX = offsetX.coerceIn(minOffsetX, maxOffsetX)
                                                
                                                val maxOffsetY = 0f
                                                val minOffsetY = -(screenHeightPx * scale - screenHeightPx)
                                                val clampedOffsetY = offsetY.coerceIn(minOffsetY, maxOffsetY)
                                                
                                                if (offsetX != clampedOffsetX) coroutineScope.launch { Animatable(offsetX).animateTo(clampedOffsetX) { offsetX = value } }
                                                if (offsetY != clampedOffsetY) coroutineScope.launch { Animatable(offsetY).animateTo(clampedOffsetY) { offsetY = value } }
                                            }
                                        }
                                    }
                                    .graphicsLayer {
                                        scaleX = scale
                                        scaleY = scale
                                        translationX = offsetX
                                        translationY = offsetY
                                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                                    }
                            ) {
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier.fillMaxSize(),
                                    userScrollEnabled = scale <= 1f,
                                    contentPadding = PaddingValues(top = topPadding, start = 16.dp, end = 16.dp, bottom = bottomPadding),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    items(state.pageCount) { pageIndex ->
                                        LaunchedEffect(pageIndex) {
                                            viewModel.renderPage(pageIndex, screenWidthPx - with(density) { 32.dp.roundToPx() })
                                        }
                                    
                                    val bitmap = renderedPages[pageIndex]
                                    
                                    NexusSurface(
                                        shape = NexusTheme.shapes.medium,
                                        elevation = 4.dp,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        if (bitmap != null) {
                                            val isLandscape = pageRotation % 180 != 0
                                            Image(
                                                bitmap = bitmap.asImageBitmap(),
                                                contentDescription = null,
                                                contentScale = ContentScale.Fit,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .aspectRatio(
                                                        if (isLandscape) bitmap.height.toFloat() / bitmap.width.toFloat()
                                                        else bitmap.width.toFloat() / bitmap.height.toFloat()
                                                    )
                                                    .graphicsLayer {
                                                        rotationZ = pageRotation.toFloat()
                                                    }
                                                    .drawWithContent {
                                                        drawContent()
                                                        val highlights = searchHighlights[pageIndex]
                                                        if (highlights != null && highlights.isNotEmpty()) {
                                                            val highlightColor = Color(0x66FFEB3B) // Semi-transparent yellow
                                                            for (rect in highlights) {
                                                                val left = rect.left * this.size.width
                                                                val top = rect.top * this.size.height
                                                                val width = (rect.right - rect.left) * this.size.width
                                                                val height = (rect.bottom - rect.top) * this.size.height
                                                                drawRect(
                                                                    color = highlightColor,
                                                                    topLeft = androidx.compose.ui.geometry.Offset(left, top),
                                                                    size = androidx.compose.ui.geometry.Size(width, height)
                                                                )
                                                            }
                                                        }
                                                    }
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(400.dp)
                                                    .background(NexusTheme.colors.surfaceVariant),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                NexusText("Loading page...", color = NexusTheme.colors.textSecondary)
                                            }
                                        }
                                    }
                                    }
                                }
                            }
                            
                            val currentPage by remember(listState) {
                                derivedStateOf {
                                    val layoutInfo = listState.layoutInfo
                                    val visibleItemsInfo = layoutInfo.visibleItemsInfo
                                    if (visibleItemsInfo.isEmpty()) {
                                        1
                                    } else {
                                        val viewportCenter = layoutInfo.viewportEndOffset / 2
                                        val closestItem = visibleItemsInfo.minByOrNull { item ->
                                            val itemCenter = item.offset + item.size / 2
                                            kotlin.math.abs(itemCenter - viewportCenter)
                                        }
                                        (closestItem?.index ?: 0) + 1
                                    }
                                }
                            }
                            
                            var isDraggingSlider by remember { mutableStateOf(false) }
                            var sliderValue by remember { mutableFloatStateOf(1f) }
                            
                            LaunchedEffect(currentPage, isDraggingSlider) {
                                if (!isDraggingSlider) {
                                    sliderValue = currentPage.toFloat()
                                }
                            }
                            
                            // Right-edge Vertical Scrollbar
                            if (state.pageCount > 1) {
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = !isImmersiveMode,
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .padding(end = 8.dp, top = 64.dp, bottom = 64.dp),
                                    enter = fadeIn(),
                                    exit = fadeOut()
                                ) {
                                    BoxWithConstraints(
                                        modifier = Modifier
                                            .fillMaxHeight(0.6f)
                                            .width(32.dp)
                                            .pointerInput(state.pageCount) {
                                                awaitEachGesture {
                                                    val down = awaitFirstDown()
                                                    isDraggingSlider = true
                                                    
                                                    do {
                                                        val event = awaitPointerEvent()
                                                        val y = event.changes.first().position.y
                                                        
                                                        val trackHeight = size.height.toFloat()
                                                        val thumbHeightPx = 48.dp.toPx()
                                                        val maxScroll = (trackHeight - thumbHeightPx).coerceAtLeast(0f)
                                                        
                                                        val desiredThumbTop = y - thumbHeightPx / 2
                                                        val newOffsetPx = desiredThumbTop.coerceIn(0f, maxScroll)
                                                        
                                                        val fraction = if (maxScroll > 0) newOffsetPx / maxScroll else 0f
                                                        sliderValue = 1f + fraction * (state.pageCount - 1)
                                                        
                                                        coroutineScope.launch {
                                                            listState.scrollToItem((sliderValue.roundToInt() - 1).coerceIn(0, state.pageCount - 1))
                                                        }
                                                        
                                                        event.changes.forEach { it.consume() }
                                                    } while (event.changes.any { it.pressed })
                                                    
                                                    isDraggingSlider = false
                                                }
                                            }
                                    ) {
                                        val trackHeight = constraints.maxHeight.toFloat()
                                        val thumbHeightPx = with(density) { 48.dp.toPx() }
                                        val maxScroll = (trackHeight - thumbHeightPx).coerceAtLeast(0f)
                                        
                                        val thumbOffsetPx = if (state.pageCount > 1) {
                                            (sliderValue - 1f) / (state.pageCount - 1f) * maxScroll
                                        } else 0f
                                        
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.Center)
                                                .fillMaxHeight()
                                                .width(4.dp)
                                                .background(NexusTheme.colors.surfaceVariant.copy(alpha = 0.3f), androidx.compose.foundation.shape.CircleShape)
                                        )
                                        
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopCenter)
                                                .offset { androidx.compose.ui.unit.IntOffset(0, thumbOffsetPx.roundToInt()) }
                                                .size(width = 8.dp, height = 48.dp)
                                                .background(NexusTheme.colors.primary, androidx.compose.foundation.shape.CircleShape)
                                        )
                                    }
                                }
                            }

                            // Page indicator pill (bottom center)
                            androidx.compose.animation.AnimatedVisibility(
                                visible = !isImmersiveMode,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 32.dp),
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                        .background(NexusTheme.colors.surfaceVariant.copy(alpha = 0.9f))
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    NexusText(
                                        text = "Page ${sliderValue.roundToInt()} / ${state.pageCount}",
                                        style = NexusTheme.typography.label,
                                        color = NexusTheme.colors.textPrimary
                                    )
                                }
                            }
                        }
                    }
                is PdfReaderUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            NexusText(state.message, color = NexusTheme.colors.error)
                            Spacer(modifier = Modifier.height(16.dp))
                            NexusButton(text = "Go Back", onClick = onBack)
                        }
                    }
                }
                }
            }

            AnimatedVisibility(
                visible = !isImmersiveMode,
                modifier = Modifier.align(Alignment.TopCenter),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                NexusTopBar(
                    title = displayName,
                    titleStyle = com.nexus.core.theme.NexusTheme.typography.body,
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
                    },
                    actions = {
                        androidx.compose.material3.IconButton(onClick = { isSearchMode = !isSearchMode }) {
                            androidx.compose.material3.Icon(
                                painter = androidx.compose.ui.res.painterResource(id = com.nexus.core.R.drawable.ic_search),
                                contentDescription = "Search",
                                tint = com.nexus.core.theme.NexusTheme.colors.textPrimary
                            )
                        }
                        androidx.compose.material3.IconButton(onClick = { pageRotation = (pageRotation + 90) % 360 }) {
                            androidx.compose.material3.Icon(
                                painter = androidx.compose.ui.res.painterResource(id = com.nexus.core.R.drawable.ic_rotate),
                                contentDescription = "Rotate",
                                tint = com.nexus.core.theme.NexusTheme.colors.textPrimary
                            )
                        }
                        androidx.compose.material3.IconButton(onClick = { showMenu = true }) {
                            androidx.compose.material3.Icon(
                                painter = androidx.compose.ui.res.painterResource(id = com.nexus.core.R.drawable.ic_more_vert),
                                contentDescription = "More",
                                tint = com.nexus.core.theme.NexusTheme.colors.textPrimary
                            )
                        }
                    }
                )
            }

            AnimatedVisibility(
                visible = isSearchMode && !isImmersiveMode,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 110.dp),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                NexusSurface(
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                    shape = NexusTheme.shapes.pill,
                    elevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.foundation.text.BasicTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            modifier = Modifier.weight(1f),
                            textStyle = NexusTheme.typography.body.copy(color = NexusTheme.colors.textPrimary),
                            singleLine = true,
                            decorationBox = { innerTextField ->
                                if (searchQuery.isEmpty()) {
                                    NexusText("Search...", color = NexusTheme.colors.textSecondary)
                                }
                                innerTextField()
                            }
                        )
                        if (searchResults.isNotEmpty()) {
                            NexusText(
                                text = "${currentSearchMatchIndex + 1}/${searchResults.size}",
                                style = NexusTheme.typography.label,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            androidx.compose.material3.IconButton(
                                onClick = {
                                    viewModel.previousSearchMatch()
                                    coroutineScope.launch {
                                        kotlinx.coroutines.delay(50)
                                        listState.animateScrollToItem(viewModel.searchResults.value[viewModel.currentSearchMatchIndex.value])
                                    }
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                androidx.compose.material3.Icon(
                                    imageVector = Icons.Default.KeyboardArrowUp,
                                    contentDescription = "Previous",
                                    tint = NexusTheme.colors.textPrimary
                                )
                            }
                            androidx.compose.material3.IconButton(
                                onClick = {
                                    viewModel.nextSearchMatch()
                                    coroutineScope.launch {
                                        kotlinx.coroutines.delay(50)
                                        listState.animateScrollToItem(viewModel.searchResults.value[viewModel.currentSearchMatchIndex.value])
                                    }
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                androidx.compose.material3.Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Next",
                                    tint = NexusTheme.colors.textPrimary
                                )
                            }
                        }
                        androidx.compose.material3.IconButton(
                            onClick = {
                                viewModel.setSearchQuery("")
                                isSearchMode = false
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            androidx.compose.material3.Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = NexusTheme.colors.textPrimary
                            )
                        }
                    }
                }
            }

            if (showRenameDialog) {
                androidx.compose.material3.AlertDialog(
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
                        androidx.compose.material3.TextButton(
                            onClick = {
                                showRenameDialog = false
                                android.widget.Toast.makeText(context, "Rename involves Storage Access Framework changes for content URIs. Basic rename logged: $renameText", android.widget.Toast.LENGTH_LONG).show()
                            }
                        ) {
                            NexusText("Rename", color = NexusTheme.colors.primary)
                        }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(onClick = { showRenameDialog = false }) {
                            NexusText("Cancel", color = NexusTheme.colors.textSecondary)
                        }
                    }
                )
            }
            
            if (showMenu) {
                PdfOptionsBottomSheet(
                    onDismiss = { showMenu = false },
                    onRename = {
                        showMenu = false
                        renameText = displayName
                        showRenameDialog = true
                    },
                    onFavorite = {
                        showMenu = false
                        viewModel.toggleBookmark(0, "Favorite Document")
                        android.widget.Toast.makeText(context, "Added to Favorites", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    onPrint = {
                        showMenu = false
                        viewModel.printPdf(context, displayName)
                    }
                )
            }
        }
    }

@Composable
private fun PdfOptionsBottomSheet(
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onFavorite: () -> Unit,
    onPrint: () -> Unit
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
                    .pointerInput(Unit) {
                        var accumulatedDrag = 0f
                        detectVerticalDragGestures(
                            onDragEnd = { accumulatedDrag = 0f },
                            onDragCancel = { accumulatedDrag = 0f },
                            onVerticalDrag = { _, dragAmount ->
                                accumulatedDrag += dragAmount
                                if (accumulatedDrag > 100f) {
                                    onDismiss()
                                    accumulatedDrag = 0f
                                }
                            }
                        )
                    }
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
