package com.nexus.feature.reader.pdf

import android.graphics.Bitmap
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.*
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import com.nexus.core.ui.utils.glassBackground
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import com.nexus.core.ui.components.NexusVerticalScrollbar
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.vectorResource
import com.nexus.feature.reader.pdf.components.*

import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.runtime.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Lock
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.nexus.core.ui.animations.shimmerEffect
import com.nexus.core.ui.animations.springBounceClick

import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.geometry.Size
import com.nexus.core.util.toUserFriendlyMessage
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.components.NexusButton
import com.nexus.core.ui.NexusSurface
import com.nexus.core.ui.NexusText
import com.nexus.core.ui.components.NexusTopBar
import com.nexus.core.ui.components.NexusViewerTopBar
import com.nexus.core.ui.components.NexusViewerActionButton
import com.nexus.core.ui.components.NexusViewerActionDivider
import com.nexus.core.ui.animations.viewerBarEnterTransition
import com.nexus.core.ui.animations.viewerBarExitTransition
import com.nexus.core.ui.components.NexusFloatingIconButton
import com.nexus.core.ui.utils.hazeGlassBackground
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch
import java.net.URLDecoder
import kotlin.math.roundToInt
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring

data class PdfAnnotationItem(
    val points: List<Offset>,
    val color: Color,
    val strokeWidth: Float,
    val tool: com.nexus.feature.reader.pdf.components.AnnotationTool,
    val shapeType: com.nexus.feature.reader.pdf.components.ShapeType? = null,
    val stampType: com.nexus.feature.reader.pdf.components.StampType? = null,
    val text: String? = null
)

@androidx.compose.animation.ExperimentalSharedTransitionApi
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
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

    var scale by remember { mutableFloatStateOf(1f) }
    var settledScale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var snapJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val maxZoom = 5f

    
    val displayName = try { URLDecoder.decode(URLDecoder.decode(fileName, "UTF-8"), "UTF-8") } catch (_: Exception) { fileName }

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.roundToPx() }
    
    val hazeState = remember { HazeState() }
    var isImmersiveMode by remember { mutableStateOf(false) }
    var isSearchMode by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showGoToPageDialog by remember { mutableStateOf(false) }
    var showThumbnailsSheet by remember { mutableStateOf(false) }
    var pageRotation by remember { mutableIntStateOf(0) }
    var renameText by remember { mutableStateOf("") }
    var goToPageText by remember { mutableStateOf("") }
    
    var backgroundMode by remember { mutableStateOf(PdfBackgroundMode.Original) }
    val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val isDarkTheme = isSystemDark || backgroundMode == PdfBackgroundMode.Inverts
    val isHorizontalLayout by viewModel.isHorizontalLayout.collectAsStateWithLifecycle()
    var isFitPage by remember { mutableStateOf(false) }

    var isAnnotationPillVisible by remember { mutableStateOf(false) }
    var activeAnnotationTool by remember { mutableStateOf<com.nexus.feature.reader.pdf.components.AnnotationTool?>(com.nexus.feature.reader.pdf.components.AnnotationTool.Pen) }
    var selectedShape by remember { mutableStateOf(com.nexus.feature.reader.pdf.components.ShapeType.Rectangle) }
    val initialAnnotationColor = if (isDarkTheme) Color(0xFFF4F4F6) else Color(0xFF141416)
    var selectedAnnotationColor by remember { mutableStateOf(initialAnnotationColor) }
    var selectedStrokeWidth by remember { mutableFloatStateOf(4f) }
    var selectedEraserFilter by remember { mutableStateOf(com.nexus.feature.reader.pdf.components.EraserTargetFilter.All) }
    var selectedStamp by remember { mutableStateOf(com.nexus.feature.reader.pdf.components.StampType.APPROVED) }
    var pendingTextPoint by remember { mutableStateOf<Pair<Int, Offset>?>(null) }
    var editingExistingTextItem by remember { mutableStateOf<Pair<Int, PdfAnnotationItem>?>(null) }
    var textEntryInput by remember { mutableStateOf("") }
    var toolbarDockPosition by remember { mutableStateOf(com.nexus.feature.reader.pdf.components.ToolbarDockPosition.Left) }
    var isToolbarCollapsed by remember { mutableStateOf(false) }
    var activePointerOffset by remember { mutableStateOf<Offset?>(null) }

    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val isStarred by viewModel.isStarred.collectAsStateWithLifecycle()
    val keepScreenAwake by viewModel.keepScreenAwake.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current

    DisposableEffect(keepScreenAwake) {
        val window = (context as? android.app.Activity)?.window
        if (keepScreenAwake) {
            window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    val searchHighlights by viewModel.searchHighlights.collectAsStateWithLifecycle()
    val currentSearchMatchIndex by viewModel.currentSearchMatchIndex.collectAsStateWithLifecycle()
    
    var isDrawMode by remember { mutableStateOf(false) }
    var currentDrawPage by remember { mutableIntStateOf(-1) }
    val drawnStrokesState by viewModel.drawnStrokesState.collectAsStateWithLifecycle()
    val drawnStrokes = remember { androidx.compose.runtime.mutableStateMapOf<Int, List<PdfAnnotationItem>>() }
    LaunchedEffect(drawnStrokesState) {
        drawnStrokesState.forEach { (page, list) ->
            drawnStrokes[page] = list
        }
    }
    val redoStrokesMap = remember { androidx.compose.runtime.mutableStateMapOf<Int, List<PdfAnnotationItem>>() }
    var showOutlineSheet by remember { mutableStateOf(false) }
    var isSavingAnnotations by remember { mutableStateOf(false) }

    val decodedUri = try { URLDecoder.decode(encodedUri, "UTF-8") } catch (_: Exception) { encodedUri }
    val outline by viewModel.outline.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()

    androidx.activity.compose.BackHandler(enabled = true) {
        if (isDrawMode) {
            isDrawMode = false
            isAnnotationPillVisible = false
        } else {
            onBack()
        }
    }

    var mainModifier = modifier
        .fillMaxSize()
        .background(PdfViewerTheme.canvasColor(isDarkTheme))

    Box(
        modifier = mainModifier
    ) {
        AnimatedContent(
            targetState = uiState,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                fadeIn(animationSpec = tween(220, delayMillis = 90)) togetherWith fadeOut(animationSpec = tween(90))
            },
                label = "pdfReaderState"
            ) { state ->
                when (state) {
                    is PdfReaderUiState.Loading -> {
                        val configuration = LocalConfiguration.current
                        val screenWidthDp = configuration.screenWidthDp.dp
                        val placeholderHeight = screenWidthDp * 1.414f
                        
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(horizontal = NexusTheme.spacing.md, vertical = NexusTheme.spacing.xxl),
                            verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.md)
                        ) {
                            items(3) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(placeholderHeight)
                                        .clip(NexusTheme.shapes.small)
                                        .shimmerEffect()
                                )
                            }
                        }
                    }
                    is PdfReaderUiState.Success -> {
                        Box(modifier = Modifier.fillMaxSize().hazeSource(hazeState)) {
                            val bottomInset = androidx.compose.foundation.layout.WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                            val topPadding by animateDpAsState(targetValue = if (isImmersiveMode) 16.dp else if (isSearchMode) 130.dp else 76.dp)
                            val bottomPadding by animateDpAsState(targetValue = if (isImmersiveMode) bottomInset + 8.dp else bottomInset + 92.dp)
                            

                            val screenWidthDp = configuration.screenWidthDp.dp
                            val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
                            val screenWidthPxInt = screenWidthPx
                            val availableHeight = (configuration.screenHeightDp.dp - topPadding - bottomPadding - 24.dp).coerceAtLeast(200.dp)
                            val basePageItemWidth = screenWidthDp - 20.dp
                            
                            // Debounce zoom settling so high-res re-rendering only runs after user finishes pinching/zooming
                            LaunchedEffect(scale) {
                                if (scale > 1.15f) {
                                    kotlinx.coroutines.delay(260)
                                    settledScale = scale
                                } else if (scale <= 1.05f && settledScale != 1f) {
                                    settledScale = 1f
                                }
                            }
                            
                            // Dynamically re-render visible pages at high resolution when settled scale changes
                            LaunchedEffect(settledScale, listState.firstVisibleItemIndex) {
                                if (settledScale > 1.15f) {
                                    val visibleItems = listState.layoutInfo.visibleItemsInfo
                                    val baseW = screenWidthPx - with(density) { 32.dp.roundToPx() }
                                    val zoomTargetW = (baseW * settledScale).toInt().coerceIn(1200, 2400)
                                    for (item in visibleItems) {
                                        val pageIdx = item.index
                                        if (pageIdx in 0 until state.pageCount) {
                                            viewModel.renderPage(pageIdx, zoomTargetW)
                                        }
                                    }
                                }
                            }
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onDoubleTap = { tapOffset ->
                                                val targetScale = if (scale > 1.05f) 1f else 2.2f
                                                snapJob?.cancel()
                                                snapJob = coroutineScope.launch {
                                                    if (targetScale == 1f) {
                                                        launch { Animatable(scale).animateTo(1f) { scale = value } }
                                                        launch { Animatable(offsetX).animateTo(0f) { offsetX = value } }
                                                        launch { Animatable(offsetY).animateTo(0f) { offsetY = value } }
                                                    } else {
                                                        val excessWidth = with(density) { (basePageItemWidth * (targetScale - 1f)).toPx() }
                                                        val maxPanX = (excessWidth / 2f).coerceAtLeast(0f)
                                                        val screenCenter = screenWidthPx / 2f
                                                        val panDelta = (screenCenter - tapOffset.x) * (targetScale - 1f)
                                                        val newOffsetX = panDelta.coerceIn(-maxPanX, maxPanX)
                                                        
                                                        launch { Animatable(scale).animateTo(targetScale) { scale = value } }
                                                        launch { Animatable(offsetX).animateTo(newOffsetX) { offsetX = value } }
                                                    }
                                                }
                                            },
                                            onTap = { isImmersiveMode = !isImmersiveMode }
                                        )
                                    }
                                    .pointerInput(Unit) {
                                        awaitEachGesture {
                                            awaitFirstDown(requireUnconsumed = false)
                                            snapJob?.cancel()
                                            do {
                                                val event = awaitPointerEvent()
                                                val isMultiTouch = event.changes.size > 1
                                                
                                                if (isMultiTouch && !isDrawMode) {
                                                    val zoomChange = event.calculateZoom()
                                                    val panChange = event.calculatePan()
                                                    
                                                    val newScale = (scale * zoomChange).coerceIn(1f, maxZoom)
                                                    scale = newScale
                                                    
                                                    if (isHorizontalLayout) {
                                                        val excessHeight = with(density) { (availableHeight * (newScale - 1f)).toPx() }
                                                        val maxPanY = (excessHeight / 2f).coerceAtLeast(0f)
                                                        offsetY = (offsetY + panChange.y).coerceIn(-maxPanY, maxPanY)
                                                        if (kotlin.math.abs(panChange.x) > 0.5f) {
                                                            coroutineScope.launch { listState.scrollBy(-panChange.x) }
                                                        }
                                                    } else {
                                                        val excessWidth = with(density) { (basePageItemWidth * (newScale - 1f)).toPx() }
                                                        val maxPanX = (excessWidth / 2f).coerceAtLeast(0f)
                                                        offsetX = (offsetX + panChange.x).coerceIn(-maxPanX, maxPanX)
                                                        if (kotlin.math.abs(panChange.y) > 0.5f) {
                                                            coroutineScope.launch { listState.scrollBy(-panChange.y) }
                                                        }
                                                    }
                                                    
                                                    event.changes.forEach { it.consume() }
                                                }
                                            } while (event.changes.any { it.pressed })
                                            
                                            if (scale <= 1.05f) {
                                                snapJob = coroutineScope.launch {
                                                    launch { Animatable(scale).animateTo(1f) { scale = value } }
                                                    launch { Animatable(offsetX).animateTo(0f) { offsetX = value } }
                                                    launch { Animatable(offsetY).animateTo(0f) { offsetY = value } }
                                                }
                                            } else {
                                                if (isHorizontalLayout) {
                                                    val excessHeight = with(density) { (availableHeight * (scale - 1f)).toPx() }
                                                    val maxPanY = (excessHeight / 2f).coerceAtLeast(0f)
                                                    val clampedY = offsetY.coerceIn(-maxPanY, maxPanY)
                                                    if (offsetY != clampedY) {
                                                        snapJob = coroutineScope.launch {
                                                            Animatable(offsetY).animateTo(clampedY, animationSpec = tween(180)) { offsetY = value }
                                                        }
                                                    }
                                                } else {
                                                    val excessWidth = with(density) { (basePageItemWidth * (scale - 1f)).toPx() }
                                                    val maxPanX = (excessWidth / 2f).coerceAtLeast(0f)
                                                    val clampedX = offsetX.coerceIn(-maxPanX, maxPanX)
                                                    if (offsetX != clampedX) {
                                                        snapJob = coroutineScope.launch {
                                                            Animatable(offsetX).animateTo(clampedX, animationSpec = tween(180)) { offsetX = value }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    .draggable(
                                        state = rememberDraggableState { delta ->
                                            if (!isHorizontalLayout && scale > 1.02f) {
                                                val excessWidth = with(density) { (basePageItemWidth * (scale - 1f)).toPx() }
                                                val maxPanX = (excessWidth / 2f).coerceAtLeast(0f)
                                                offsetX = (offsetX + delta).coerceIn(-maxPanX, maxPanX)
                                            }
                                        },
                                        orientation = Orientation.Horizontal,
                                        enabled = !isHorizontalLayout && scale > 1.02f && !isDrawMode
                                    )
                                    .draggable(
                                        state = rememberDraggableState { delta ->
                                            if (isHorizontalLayout && scale > 1.02f) {
                                                val excessHeight = with(density) { (availableHeight * (scale - 1f)).toPx() }
                                                val maxPanY = (excessHeight / 2f).coerceAtLeast(0f)
                                                offsetY = (offsetY + delta).coerceIn(-maxPanY, maxPanY)
                                            }
                                        },
                                        orientation = Orientation.Vertical,
                                        enabled = isHorizontalLayout && scale > 1.02f && !isDrawMode
                                    )
                            ) {
                                val colorFilter = remember(backgroundMode) {
                                    when (backgroundMode) {
                                        PdfBackgroundMode.Original -> null
                                        PdfBackgroundMode.EyeComfort -> ColorFilter.colorMatrix(
                                            ColorMatrix(
                                                floatArrayOf(
                                                    0.8f, 0f, 0f, 0f, 0f,
                                                    0f, 0.9f, 0f, 0f, 0f,
                                                    0f, 0f, 0.8f, 0f, 0f,
                                                    0f, 0f, 0f, 1f, 0f
                                                )
                                            )
                                        )
                                        PdfBackgroundMode.Inverts -> ColorFilter.colorMatrix(
                                            ColorMatrix(
                                                floatArrayOf(
                                                    -1f,  0f,  0f, 0f, 255f,
                                                     0f, -1f,  0f, 0f, 255f,
                                                     0f,  0f, -1f, 0f, 255f,
                                                     0f,  0f,  0f, 1f,   0f
                                                )
                                            )
                                        )
                                    }
                                }

                                val searchHighlightColor = NexusTheme.colors.searchHighlight
                                val pdfContent: LazyListScope.() -> Unit = {
                                    items(
                                        count = state.pageCount,
                                        key = { it },
                                        contentType = { "pdf_page" }
                                    ) { pageIndex ->
                                        LaunchedEffect(pageIndex) {
                                            if (!renderedPages.containsKey(pageIndex)) {
                                                kotlinx.coroutines.delay(40) // Fast debouncer for fling scroll
                                                if (!renderedPages.containsKey(pageIndex)) {
                                                    val baseW = screenWidthPx - with(density) { 32.dp.roundToPx() }
                                                    val currentTargetW = if (settledScale > 1.15f) (baseW * settledScale).toInt().coerceIn(1200, 2400) else baseW
                                                    viewModel.renderPage(pageIndex, currentTargetW)
                                                }
                                            }
                                        }
                                    
                                        val bitmap = renderedPages[pageIndex]
                                        
                                        val pageContainerColor = if (backgroundMode == PdfBackgroundMode.Inverts) 
                                            Color(0xFF1C1C1E) 
                                        else 
                                            Color.White
                                            
                                        val isLandscape = pageRotation % 180 != 0
                                        val pageRatio = if (bitmap != null && bitmap.width > 0 && bitmap.height > 0) {
                                            if (isLandscape) bitmap.width.toFloat() / bitmap.height.toFloat()
                                            else bitmap.height.toFloat() / bitmap.width.toFloat()
                                        } else {
                                            1.414f
                                        }
                                        val fitPageWidth = (availableHeight / pageRatio).coerceIn(180.dp, screenWidthDp - 20.dp)
                                        val baseWidth = if (isFitPage) fitPageWidth else (screenWidthDp - 20.dp)
                                        val pageItemWidth = if (isHorizontalLayout) (availableHeight * scale / pageRatio) else (baseWidth * scale)

                                        NexusSurface(
                                            shape = RoundedCornerShape(6.dp),
                                            elevation = 4.dp,
                                            color = pageContainerColor,
                                            modifier = Modifier
                                                .width(pageItemWidth)
                                                .shadow(elevation = 4.dp, shape = RoundedCornerShape(6.dp), clip = true)
                                        ) {
                                            Box(
                                                modifier = Modifier.graphicsLayer {
                                                    rotationZ = pageRotation.toFloat()
                                                }
                                            ) {
                                                if (bitmap != null) {
                                                    val isLandscape = pageRotation % 180 != 0
                                                    Image(
                                                        bitmap = bitmap.asImageBitmap(),
                                                        contentDescription = null,
                                                        contentScale = ContentScale.Fit,
                                                        colorFilter = colorFilter,
                                                        filterQuality = FilterQuality.Low,
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .aspectRatio(
                                                                if (isLandscape) bitmap.height.toFloat() / bitmap.width.toFloat()
                                                                else bitmap.width.toFloat() / bitmap.height.toFloat()
                                                            )
                                                            .drawWithContent {
                                                                drawContent()
                                                                val highlights = searchHighlights[pageIndex]
                                                                if (highlights != null && highlights.isNotEmpty()) {
                                                                    for (rect in highlights) {
                                                                        val left = rect.left * this.size.width
                                                                        val top = rect.top * this.size.height
                                                                        val width = (rect.right - rect.left) * this.size.width
                                                                        val height = (rect.bottom - rect.top) * this.size.height
                                                                        drawRect(
                                                                            color = searchHighlightColor.copy(alpha = 0.4f),
                                                                            topLeft = Offset(left, top),
                                                                            size = Size(width, height)
                                                                        )
                                                                        drawRect(
                                                                            color = searchHighlightColor,
                                                                            topLeft = Offset(left, top),
                                                                            size = Size(width, height),
                                                                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                    )
                                                } else {
                                                    val placeholderHeight = pageItemWidth * 1.414f
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(placeholderHeight)
                                                            .background(NexusTheme.colors.surfaceVariant),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        NexusText("Loading page...", color = NexusTheme.colors.textSecondary)
                                                    }
                                                 }
                                                 
                                                  // Canvas Overlay for Drawing & Annotations
                                                  if (isDrawMode && (currentDrawPage == -1 || currentDrawPage == pageIndex) && bitmap != null) {
                                                      var currentPath by remember { mutableStateOf<List<Offset>>(emptyList()) }
                                                      androidx.compose.foundation.Canvas(
                                                          modifier = Modifier
                                                              .matchParentSize()
                                                              .pointerInput(isDrawMode, activeAnnotationTool, selectedShape, selectedAnnotationColor, selectedStrokeWidth, selectedEraserFilter) {
                                                                  if (!isDrawMode) return@pointerInput
                                                                  awaitEachGesture {
                                                                      awaitFirstDown(requireUnconsumed = false)
                                                                      var maxPointers = 0
                                                                      
                                                                      do {
                                                                          val event = awaitPointerEvent()
                                                                          val hasStylus = event.changes.any { 
                                                                              it.type == androidx.compose.ui.input.pointer.PointerType.Stylus || 
                                                                              it.type == androidx.compose.ui.input.pointer.PointerType.Eraser 
                                                                          }
                                                                          val validChanges = if (hasStylus) event.changes.filter { 
                                                                              it.type != androidx.compose.ui.input.pointer.PointerType.Touch 
                                                                          } else event.changes
                                                                          val pressed = validChanges.filter { it.pressed }
                                                                          maxPointers = maxOf(maxPointers, pressed.size)
                                                                          
                                                                          if (maxPointers >= 2 || pressed.size >= 2) {
                                                                              // Retain toolbar visibility and draw mode during multi-touch pinch zoom
                                                                              currentPath = emptyList()
                                                                              currentDrawPage = -1
                                                                              activePointerOffset = null
                                                                              return@awaitEachGesture
                                                                          } else if (pressed.size == 1) {
                                                                              val change = pressed.first()
                                                                              activePointerOffset = change.position
                                                                              if (activeAnnotationTool == com.nexus.feature.reader.pdf.components.AnnotationTool.Eraser) {
                                                                                  val pos = change.position
                                                                                  val currentStrokes = drawnStrokes[pageIndex] ?: emptyList()
                                                                                  val targetFilter = selectedEraserFilter
                                                                                  
                                                                                  fun matchesFilter(item: PdfAnnotationItem): Boolean = when (targetFilter) {
                                                                                      com.nexus.feature.reader.pdf.components.EraserTargetFilter.All -> true
                                                                                      com.nexus.feature.reader.pdf.components.EraserTargetFilter.PenOnly -> item.tool == com.nexus.feature.reader.pdf.components.AnnotationTool.Pen
                                                                                      com.nexus.feature.reader.pdf.components.EraserTargetFilter.HighlighterOnly -> item.tool == com.nexus.feature.reader.pdf.components.AnnotationTool.Highlighter
                                                                                      com.nexus.feature.reader.pdf.components.EraserTargetFilter.ShapesOnly -> item.tool == com.nexus.feature.reader.pdf.components.AnnotationTool.Shapes
                                                                                  }

                                                                                  fun distanceToSegment(p: Offset, a: Offset, b: Offset): Float {
                                                                                      val l2 = (b.x - a.x) * (b.x - a.x) + (b.y - a.y) * (b.y - a.y)
                                                                                      if (l2 == 0f) return (p - a).getDistance()
                                                                                      val t = (((p.x - a.x) * (b.x - a.x) + (p.y - a.y) * (b.y - a.y)) / l2).coerceIn(0f, 1f)
                                                                                      val projection = Offset(a.x + t * (b.x - a.x), a.y + t * (b.y - a.y))
                                                                                      return (p - projection).getDistance()
                                                                                  }

                                                                                  val threshold = with(density) { 20.dp.toPx() }
                                                                                  val updated = currentStrokes.filterNot { item ->
                                                                                      if (!matchesFilter(item)) false
                                                                                      else {
                                                                                          val pts = item.points.map { Offset(it.x * size.width, it.y * size.height) }
                                                                                          if (item.tool == com.nexus.feature.reader.pdf.components.AnnotationTool.Shapes && pts.size >= 2) {
                                                                                              val start = pts.first()
                                                                                              val end = pts.last()
                                                                                              when (item.shapeType) {
                                                                                                  com.nexus.feature.reader.pdf.components.ShapeType.Rectangle -> {
                                                                                                      val left = kotlin.math.min(start.x, end.x)
                                                                                                      val top = kotlin.math.min(start.y, end.y)
                                                                                                      val right = kotlin.math.max(start.x, end.x)
                                                                                                      val bottom = kotlin.math.max(start.y, end.y)
                                                                                                      val d1 = distanceToSegment(pos, Offset(left, top), Offset(right, top))
                                                                                                      val d2 = distanceToSegment(pos, Offset(right, top), Offset(right, bottom))
                                                                                                      val d3 = distanceToSegment(pos, Offset(right, bottom), Offset(left, bottom))
                                                                                                      val d4 = distanceToSegment(pos, Offset(left, bottom), Offset(left, top))
                                                                                                      minOf(d1, d2, d3, d4) < threshold
                                                                                                  }
                                                                                                  com.nexus.feature.reader.pdf.components.ShapeType.Oval -> {
                                                                        val left = kotlin.math.min(start.x, end.x)
                                                                        val top = kotlin.math.min(start.y, end.y)
                                                                        val right = kotlin.math.max(start.x, end.x)
                                                                        val bottom = kotlin.math.max(start.y, end.y)
                                                                        val cx = (left + right) / 2f
                                                                        val cy = (top + bottom) / 2f
                                                                        val rx = kotlin.math.abs(right - left) / 2f
                                                                        val ry = kotlin.math.abs(bottom - top) / 2f
                                                                        if (rx == 0f || ry == 0f) (pos - Offset(cx, cy)).getDistance() < threshold
                                                                        else {
                                                                            (0 until 36).any { i ->
                                                                                val angle = (i * 10) * (Math.PI / 180.0)
                                                                                val ex = cx + rx * kotlin.math.cos(angle).toFloat()
                                                                                val ey = cy + ry * kotlin.math.sin(angle).toFloat()
                                                                                (pos - Offset(ex, ey)).getDistance() < threshold
                                                                            }
                                                                        }
                                                                    }
                                                                                                  com.nexus.feature.reader.pdf.components.ShapeType.Line, com.nexus.feature.reader.pdf.components.ShapeType.Arrow, null -> {
                                                                                                      distanceToSegment(pos, start, end) < threshold
                                                                                                  }
                                                                                              }
                                                                                          } else {
                                                                                              if (pts.size == 1) {
                                                                                                  (pts.first() - pos).getDistance() < threshold
                                                                                              } else {
                                                                                                  pts.indices.drop(1).any { i ->
                                                                                                      distanceToSegment(pos, pts[i - 1], pts[i]) < threshold
                                                                                                  }
                                                                                              }
                                                                                          }
                                                                                      }
                                                                                  }
                                                                                  if (updated.size != currentStrokes.size) {
                                                                                      val erased = currentStrokes.filter { !updated.contains(it) }
                                                                                      val rList = redoStrokesMap[pageIndex].orEmpty().toMutableList()
                                                                                      rList.addAll(erased)
                                                                                      redoStrokesMap[pageIndex] = rList.toList()
                                                                                      drawnStrokes[pageIndex] = updated
                                                                                      viewModel.updateDrawnStrokes(pageIndex, updated)
                                                                                  }
                                                                              } else {
                                                                                  if (currentPath.isEmpty()) {
                                                                                      currentDrawPage = pageIndex
                                                                                      currentPath = listOf(change.position)
                                                                                  } else {
                                                                                      currentPath = currentPath + change.position
                                                                                  }
                                                                              }
                                                                          change.consume()
                                                                          }
                                                                      } while (event.changes.any { it.pressed })
                                                                       
                                                                       if (maxPointers < 2 && currentPath.isNotEmpty() && activeAnnotationTool != com.nexus.feature.reader.pdf.components.AnnotationTool.Eraser) {
                                                                           val strokes = drawnStrokes[pageIndex]?.toMutableList() ?: mutableListOf()
                                                                           val startPt = currentPath.first()
                                                                           val endPt = currentPath.last()
                                                                           val normStart = Offset(startPt.x / size.width, startPt.y / size.height)
                                                                           val normEnd = Offset(endPt.x / size.width, endPt.y / size.height)

                                                                           val newItem = when (activeAnnotationTool) {
                                                                               com.nexus.feature.reader.pdf.components.AnnotationTool.Shapes -> {
                                                                                   PdfAnnotationItem(
                                                                                       points = listOf(normStart, normEnd),
                                                                                       color = selectedAnnotationColor,
                                                                                       strokeWidth = selectedStrokeWidth,
                                                                                       tool = com.nexus.feature.reader.pdf.components.AnnotationTool.Shapes,
                                                                                       shapeType = selectedShape
                                                                                   )
                                                                               }
                                                                                com.nexus.feature.reader.pdf.components.AnnotationTool.Text -> {
                                                                                    val existingTextItem = drawnStrokes[pageIndex]?.firstOrNull { item ->
                                                                                        if (item.tool != com.nexus.feature.reader.pdf.components.AnnotationTool.Text || item.points.isEmpty()) return@firstOrNull false
                                                                                        val pt = Offset(item.points.first().x * size.width, item.points.first().y * size.height)
                                                                                        val textStr = item.text ?: ""
                                                                                        val textScale = size.width / 400f
                                                                                        val textPaint = android.graphics.Paint().apply {
                                                                                            textSize = (item.strokeWidth * textScale * 4f).coerceIn(24f * (size.width / 800f), 64f * (size.width / 800f))
                                                                                            typeface = android.graphics.Typeface.DEFAULT_BOLD
                                                                                        }
                                                                                        val lines = textStr.split("\n")
                                                                                        val maxLineWidth = lines.maxOfOrNull { textPaint.measureText(it) } ?: 0f
                                                                                        val textHeight = lines.size * textPaint.fontSpacing
                                                                                        val pad = with(density) { 24.dp.toPx() }
                                                                                        val left = pt.x - pad
                                                                                        val top = pt.y - pad
                                                                                        val right = pt.x + maxLineWidth + pad
                                                                                        val bottom = pt.y + textHeight + pad
                                                                                        startPt.x in left..right && startPt.y in top..bottom
                                                                                    }
                                                                                    if (existingTextItem != null) {
                                                                                        textEntryInput = existingTextItem.text ?: ""
                                                                                        editingExistingTextItem = Pair(pageIndex, existingTextItem)
                                                                                        pendingTextPoint = Pair(pageIndex, existingTextItem.points.first())
                                                                                    } else {
                                                                                        textEntryInput = ""
                                                                                        editingExistingTextItem = null
                                                                                        pendingTextPoint = Pair(pageIndex, normStart)
                                                                                    }
                                                                                    null
                                                                                }
                                                                               com.nexus.feature.reader.pdf.components.AnnotationTool.Stamp -> {
                                                                                   PdfAnnotationItem(
                                                                                       points = listOf(normStart),
                                                                                       color = Color(selectedStamp.colorHex),
                                                                                       strokeWidth = selectedStrokeWidth,
                                                                                       tool = com.nexus.feature.reader.pdf.components.AnnotationTool.Stamp,
                                                                                       stampType = selectedStamp
                                                                                   )
                                                                               }
                                                                               com.nexus.feature.reader.pdf.components.AnnotationTool.Ruler -> {
                                                                                   PdfAnnotationItem(
                                                                                       points = listOf(normStart, normEnd),
                                                                                       color = selectedAnnotationColor,
                                                                                       strokeWidth = selectedStrokeWidth,
                                                                                       tool = com.nexus.feature.reader.pdf.components.AnnotationTool.Ruler
                                                                                   )
                                                                               }
                                                                               else -> {
                                                                                   val normalized = currentPath.map { Offset(it.x / size.width, it.y / size.height) }
                                                                                   PdfAnnotationItem(
                                                                                       points = normalized,
                                                                                       color = selectedAnnotationColor,
                                                                                       strokeWidth = selectedStrokeWidth,
                                                                                       tool = activeAnnotationTool ?: com.nexus.feature.reader.pdf.components.AnnotationTool.Pen
                                                                                   )
                                                                               }
                                                                           }
                                                                           if (newItem != null) {
                                                                               strokes.add(newItem)
                                                                               drawnStrokes[pageIndex] = strokes
                                                                               viewModel.updateDrawnStrokes(pageIndex, strokes)
                                                                               redoStrokesMap.remove(pageIndex)
                                                                           }
                                                                       }
                                                                       currentPath = emptyList()
                                                                       currentDrawPage = -1
                                                                       activePointerOffset = null
                                                                   }
                                                               }
                                                       ) {
                                                          // ── Render Saved Annotations ──
                                                          drawnStrokes[pageIndex]?.forEach { item ->
                                                              val denormalized = item.points.map { Offset(it.x * size.width, it.y * size.height) }
                                                              val strokeStyle = androidx.compose.ui.graphics.drawscope.Stroke(
                                                                  width = item.strokeWidth.dp.toPx(),
                                                                  cap = StrokeCap.Round,
                                                                  join = StrokeJoin.Round
                                                              )
                                                              val drawColor = if (item.tool == com.nexus.feature.reader.pdf.components.AnnotationTool.Highlighter) item.color.copy(alpha = 0.45f) else item.color

                                                              when (item.tool) {
                                                                  com.nexus.feature.reader.pdf.components.AnnotationTool.Shapes -> {
                                                                      if (denormalized.size >= 2) {
                                                                          val start = denormalized.first()
                                                                          val end = denormalized.last()
                                                                          val left = kotlin.math.min(start.x, end.x)
                                                                          val top = kotlin.math.min(start.y, end.y)
                                                                          val w = kotlin.math.abs(end.x - start.x)
                                                                          val h = kotlin.math.abs(end.y - start.y)

                                                                          when (item.shapeType) {
                                                                              com.nexus.feature.reader.pdf.components.ShapeType.Rectangle -> {
                                                                                  drawRect(color = drawColor, topLeft = Offset(left, top), size = Size(w, h), style = strokeStyle)
                                                                              }
                                                                              com.nexus.feature.reader.pdf.components.ShapeType.Oval -> {
                                                                                  drawOval(color = drawColor, topLeft = Offset(left, top), size = Size(w, h), style = strokeStyle)
                                                                              }
                                                                              com.nexus.feature.reader.pdf.components.ShapeType.Line -> {
                                                                                  drawLine(color = drawColor, start = start, end = end, strokeWidth = strokeStyle.width, cap = StrokeCap.Round)
                                                                              }
                                                                              com.nexus.feature.reader.pdf.components.ShapeType.Arrow, null -> {
                                                                                   drawLine(color = drawColor, start = start, end = end, strokeWidth = strokeStyle.width, cap = StrokeCap.Round)
                                                                                   val angle = kotlin.math.atan2((end.y - start.y).toDouble(), (end.x - start.x).toDouble())
                                                                                   val arrowLen = with(density) { 16.dp.toPx() }
                                                                                   val arrowAngle = Math.toRadians(25.0)
                                                                                   val p1 = Offset((end.x - arrowLen * kotlin.math.cos(angle - arrowAngle)).toFloat(), (end.y - arrowLen * kotlin.math.sin(angle - arrowAngle)).toFloat())
                                                                                   val p2 = Offset((end.x - arrowLen * kotlin.math.cos(angle + arrowAngle)).toFloat(), (end.y - arrowLen * kotlin.math.sin(angle + arrowAngle)).toFloat())
                                                                                   drawLine(color = drawColor, start = end, end = p1, strokeWidth = strokeStyle.width, cap = StrokeCap.Round)
                                                                                   drawLine(color = drawColor, start = end, end = p2, strokeWidth = strokeStyle.width, cap = StrokeCap.Round)
                                                                               }
                                                                          }
                                                                      }
                                                                  }
                                                                  com.nexus.feature.reader.pdf.components.AnnotationTool.Text -> {
                                                                        val textStr = item.text ?: ""
                                                                        if (denormalized.isNotEmpty() && textStr.isNotEmpty()) {
                                                                            val pt = denormalized.first()
                                                                            val textScale = size.width / 400f
                                                                            val textPaint = android.graphics.Paint().apply {
                                                                                color = item.color.toArgb()
                                                                                textSize = (item.strokeWidth * textScale * 4f).coerceIn(24f * (size.width / 800f), 64f * (size.width / 800f))
                                                                                isAntiAlias = true
                                                                                typeface = android.graphics.Typeface.DEFAULT_BOLD
                                                                            }
                                                                            val lines = textStr.split("\n")
                                                                            val lineHeight = textPaint.fontSpacing
                                                                            val startY = pt.y - textPaint.fontMetrics.ascent
                                                                            lines.forEachIndexed { idx, line ->
                                                                                drawContext.canvas.nativeCanvas.drawText(
                                                                                    line,
                                                                                    pt.x,
                                                                                    startY + (idx * lineHeight),
                                                                                    textPaint
                                                                                )
                                                                            }
                                                                        }
                                                                  }
                                                                  com.nexus.feature.reader.pdf.components.AnnotationTool.Stamp -> {
                                                                       if (denormalized.isNotEmpty()) {
                                                                           val center = denormalized.first()
                                                                           val stamp = item.stampType ?: com.nexus.feature.reader.pdf.components.StampType.APPROVED
                                                                           val stampColor = Color(stamp.colorHex)
                                                                           val badgeW = with(density) { 130.dp.toPx() }
                                                                           val badgeH = with(density) { 42.dp.toPx() }
                                                                           val left = center.x - badgeW / 2f
                                                                           val top = center.y - badgeH / 2f

                                                                           drawRoundRect(
                                                                               color = stampColor.copy(alpha = 0.16f),
                                                                               topLeft = Offset(left, top),
                                                                               size = Size(badgeW, badgeH),
                                                                               cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f)
                                                                           )
                                                                           drawRoundRect(
                                                                               color = stampColor,
                                                                               topLeft = Offset(left, top),
                                                                               size = Size(badgeW, badgeH),
                                                                               cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f),
                                                                               style = androidx.compose.ui.graphics.drawscope.Stroke(width = with(density) { 2.5.dp.toPx() })
                                                                           )
                                                                           val stampPaint = android.graphics.Paint().apply {
                                                                               color = stampColor.toArgb()
                                                                               textSize = with(density) { 13.sp.toPx() }
                                                                               isAntiAlias = true
                                                                               typeface = android.graphics.Typeface.DEFAULT_BOLD
                                                                               textAlign = android.graphics.Paint.Align.CENTER
                                                                           }
                                                                           drawContext.canvas.nativeCanvas.drawText(stamp.label, center.x, center.y + with(density) { 4.5.dp.toPx() }, stampPaint)
                                                                       }
                                                                  }
                                                                  com.nexus.feature.reader.pdf.components.AnnotationTool.Ruler -> {
                                                                       if (denormalized.size >= 2) {
                                                                           val start = denormalized.first()
                                                                           val end = denormalized.last()
                                                                           drawLine(color = item.color, start = start, end = end, strokeWidth = strokeStyle.width, cap = StrokeCap.Round)
                                                                           
                                                                           val angle = kotlin.math.atan2((end.y - start.y).toDouble(), (end.x - start.x).toDouble())
                                                                           val perpAngle = angle + Math.PI / 2
                                                                           val tickLen = with(density) { 8.dp.toPx() }
                                                                           val p1 = Offset((start.x + tickLen * kotlin.math.cos(perpAngle)).toFloat(), (start.y + tickLen * kotlin.math.sin(perpAngle)).toFloat())
                                                                           val p2 = Offset((start.x - tickLen * kotlin.math.cos(perpAngle)).toFloat(), (start.y - tickLen * kotlin.math.sin(perpAngle)).toFloat())
                                                                           val p3 = Offset((end.x + tickLen * kotlin.math.cos(perpAngle)).toFloat(), (end.y + tickLen * kotlin.math.sin(perpAngle)).toFloat())
                                                                           val p4 = Offset((end.x - tickLen * kotlin.math.cos(perpAngle)).toFloat(), (end.y - tickLen * kotlin.math.sin(perpAngle)).toFloat())
                                                                           drawLine(color = item.color, start = p1, end = p2, strokeWidth = strokeStyle.width)
                                                                           drawLine(color = item.color, start = p3, end = p4, strokeWidth = strokeStyle.width)

                                                                           val normX = (end.x - start.x) / size.width
                                                                           val normY = (end.y - start.y) / size.height
                                                                           val normDist = kotlin.math.hypot(normX.toDouble(), normY.toDouble()).toFloat()
                                                                           val cm = (normDist * 595.28f) * (2.54f / 72f)
                                                                           val mid = Offset((start.x + end.x) / 2f, (start.y + end.y) / 2f - with(density) { 8.dp.toPx() })
                                                                           val labelPaint = android.graphics.Paint().apply {
                                                                               color = item.color.toArgb()
                                                                               textSize = with(density) { 13.sp.toPx() }
                                                                               isAntiAlias = true
                                                                               typeface = android.graphics.Typeface.DEFAULT_BOLD
                                                                               textAlign = android.graphics.Paint.Align.CENTER
                                                                           }
                                                                           drawContext.canvas.nativeCanvas.drawText(String.format(java.util.Locale.US, "%.1f cm", cm), mid.x, mid.y, labelPaint)
                                                                       }
                                                                  }
                                                                  else -> {
                                                                      if (denormalized.size == 1) {
                                                                          drawCircle(
                                                                              color = drawColor,
                                                                              radius = (strokeStyle.width / 2f).coerceAtLeast(with(density) { 3.dp.toPx() }),
                                                                              center = denormalized.first()
                                                                          )
                                                                      } else if (denormalized.size > 1) {
                                                                          drawPath(
                                                                              path = androidx.compose.ui.graphics.Path().apply {
                                                                                  moveTo(denormalized.first().x, denormalized.first().y)
                                                                                  for (i in 1 until denormalized.size) lineTo(denormalized[i].x, denormalized[i].y)
                                                                              },
                                                                              color = drawColor,
                                                                              style = strokeStyle
                                                                          )
                                                                      }
                                                                  }
                                                              }
                                                          }

                                                          // ── Render Active Live Preview ──
                                                          if (currentPath.isNotEmpty() && activeAnnotationTool != com.nexus.feature.reader.pdf.components.AnnotationTool.Eraser) {
                                                              val liveStrokeStyle = androidx.compose.ui.graphics.drawscope.Stroke(
                                                                  width = selectedStrokeWidth.dp.toPx(),
                                                                  cap = StrokeCap.Round,
                                                                  join = StrokeJoin.Round
                                                              )
                                                              val liveColor = if (activeAnnotationTool == com.nexus.feature.reader.pdf.components.AnnotationTool.Highlighter) selectedAnnotationColor.copy(alpha = 0.45f) else selectedAnnotationColor

                                                               if (activeAnnotationTool == com.nexus.feature.reader.pdf.components.AnnotationTool.Ruler && currentPath.size >= 2) {
                                                                   val start = currentPath.first()
                                                                   val end = currentPath.last()
                                                                   drawLine(color = liveColor, start = start, end = end, strokeWidth = liveStrokeStyle.width, cap = StrokeCap.Round)
                                                                   
                                                                   val angle = kotlin.math.atan2((end.y - start.y).toDouble(), (end.x - start.x).toDouble())
                                                                   val perpAngle = angle + Math.PI / 2
                                                                   val tickLen = with(density) { 8.dp.toPx() }
                                                                   val p1 = Offset((start.x + tickLen * kotlin.math.cos(perpAngle)).toFloat(), (start.y + tickLen * kotlin.math.sin(perpAngle)).toFloat())
                                                                   val p2 = Offset((start.x - tickLen * kotlin.math.cos(perpAngle)).toFloat(), (start.y - tickLen * kotlin.math.sin(perpAngle)).toFloat())
                                                                   val p3 = Offset((end.x + tickLen * kotlin.math.cos(perpAngle)).toFloat(), (end.y + tickLen * kotlin.math.sin(perpAngle)).toFloat())
                                                                   val p4 = Offset((end.x - tickLen * kotlin.math.cos(perpAngle)).toFloat(), (end.y - tickLen * kotlin.math.sin(perpAngle)).toFloat())
                                                                   drawLine(color = liveColor, start = p1, end = p2, strokeWidth = liveStrokeStyle.width)
                                                                   drawLine(color = liveColor, start = p3, end = p4, strokeWidth = liveStrokeStyle.width)

                                                                   val normX = (end.x - start.x) / size.width
                                                                   val normY = (end.y - start.y) / size.height
                                                                   val normDist = kotlin.math.hypot(normX.toDouble(), normY.toDouble()).toFloat()
                                                                   val cm = (normDist * 595.28f) * (2.54f / 72f)
                                                                   val mid = Offset((start.x + end.x) / 2f, (start.y + end.y) / 2f - with(density) { 8.dp.toPx() })
                                                                   val labelPaint = android.graphics.Paint().apply {
                                                                       color = liveColor.toArgb()
                                                                       textSize = with(density) { 13.sp.toPx() }
                                                                       isAntiAlias = true
                                                                       typeface = android.graphics.Typeface.DEFAULT_BOLD
                                                                       textAlign = android.graphics.Paint.Align.CENTER
                                                                   }
                                                                   drawContext.canvas.nativeCanvas.drawText(String.format(java.util.Locale.US, "%.1f cm", cm), mid.x, mid.y, labelPaint)
                                                               } else if (activeAnnotationTool == com.nexus.feature.reader.pdf.components.AnnotationTool.Stamp && currentPath.isNotEmpty()) {
                                                                   val center = currentPath.last()
                                                                   val stampColor = Color(selectedStamp.colorHex).copy(alpha = 0.6f)
                                                                   val badgeW = with(density) { 130.dp.toPx() }
                                                                   val badgeH = with(density) { 42.dp.toPx() }
                                                                   drawRoundRect(
                                                                       color = stampColor,
                                                                       topLeft = Offset(center.x - badgeW / 2f, center.y - badgeH / 2f),
                                                                       size = Size(badgeW, badgeH),
                                                                       cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f),
                                                                       style = androidx.compose.ui.graphics.drawscope.Stroke(width = with(density) { 2.dp.toPx() })
                                                                   )
                                                               } else if (activeAnnotationTool == com.nexus.feature.reader.pdf.components.AnnotationTool.Shapes && currentPath.size >= 2) {
                                                                  val start = currentPath.first()
                                                                  val end = currentPath.last()
                                                                  val left = kotlin.math.min(start.x, end.x)
                                                                  val top = kotlin.math.min(start.y, end.y)
                                                                  val w = kotlin.math.abs(end.x - start.x)
                                                                  val h = kotlin.math.abs(end.y - start.y)

                                                                  when (selectedShape) {
                                                                      com.nexus.feature.reader.pdf.components.ShapeType.Rectangle -> {
                                                                          drawRect(color = liveColor, topLeft = Offset(left, top), size = Size(w, h), style = liveStrokeStyle)
                                                                      }
                                                                      com.nexus.feature.reader.pdf.components.ShapeType.Oval -> {
                                                                          drawOval(color = liveColor, topLeft = Offset(left, top), size = Size(w, h), style = liveStrokeStyle)
                                                                      }
                                                                      com.nexus.feature.reader.pdf.components.ShapeType.Line -> {
                                                                          drawLine(color = liveColor, start = start, end = end, strokeWidth = liveStrokeStyle.width, cap = StrokeCap.Round)
                                                                      }
                                                                      com.nexus.feature.reader.pdf.components.ShapeType.Arrow -> {
                                                                          drawLine(color = liveColor, start = start, end = end, strokeWidth = liveStrokeStyle.width, cap = StrokeCap.Round)
                                                                          val angle = kotlin.math.atan2((end.y - start.y).toDouble(), (end.x - start.x).toDouble())
                                                                          val arrowLen = with(density) { 16.dp.toPx() }
                                                                          val arrowAngle = Math.toRadians(25.0)
                                                                          val p1 = Offset((end.x - arrowLen * kotlin.math.cos(angle - arrowAngle)).toFloat(), (end.y - arrowLen * kotlin.math.sin(angle - arrowAngle)).toFloat())
                                                                          val p2 = Offset((end.x - arrowLen * kotlin.math.cos(angle + arrowAngle)).toFloat(), (end.y - arrowLen * kotlin.math.sin(angle + arrowAngle)).toFloat())
                                                                          drawLine(color = liveColor, start = end, end = p1, strokeWidth = liveStrokeStyle.width, cap = StrokeCap.Round)
                                                                          drawLine(color = liveColor, start = end, end = p2, strokeWidth = liveStrokeStyle.width, cap = StrokeCap.Round)
                                                                      }
                                                                  }
                                                              } else {
                                                                  if (currentPath.size == 1) {
                                                                      drawCircle(
                                                                          color = liveColor,
                                                                          radius = (liveStrokeStyle.width / 2f).coerceAtLeast(with(density) { 3.dp.toPx() }),
                                                                          center = currentPath.first()
                                                                      )
                                                                  } else if (currentPath.size > 1) {
                                                                      drawPath(
                                                                          path = androidx.compose.ui.graphics.Path().apply {
                                                                              moveTo(currentPath.first().x, currentPath.first().y)
                                                                              for (i in 1 until currentPath.size) lineTo(currentPath[i].x, currentPath[i].y)
                                                                          },
                                                                          color = liveColor,
                                                                          style = liveStrokeStyle
                                                                      )
                                                                  }
                                                              }
                                                              // ── Render Active Tool Real-Time Precision Glowing Reticle Cursor ──
                                                           activePointerOffset?.let { pos ->
                                                               val ringRadius = (selectedStrokeWidth.dp.toPx() / 2f).coerceIn(with(density) { 6.dp.toPx() }, with(density) { 24.dp.toPx() })
                                                               // Outer soft aura
                                                               drawCircle(
                                                                   color = selectedAnnotationColor.copy(alpha = 0.22f),
                                                                   radius = ringRadius + with(density) { 4.dp.toPx() },
                                                                   center = pos
                                                               )
                                                               // Sharp ring
                                                               drawCircle(
                                                                   color = selectedAnnotationColor,
                                                                   radius = ringRadius,
                                                                   center = pos,
                                                                   style = androidx.compose.ui.graphics.drawscope.Stroke(width = with(density) { 1.5.dp.toPx() })
                                                               )
                                                               // Center precision pin-dot
                                                               drawCircle(
                                                                   color = selectedAnnotationColor,
                                                                   radius = with(density) { 1.8.dp.toPx() },
                                                                   center = pos
                                                               )
                                                           }
                                                          }

                                                      }
                                                  }
                                             }
                                         }
                                     }
                                }

                                if (isHorizontalLayout) {
                                    LazyRow(
                                        state = listState,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .graphicsLayer {
                                                translationY = offsetY
                                            },
                                        userScrollEnabled = !isDrawMode,
                                        contentPadding = PaddingValues(top = topPadding, start = 8.dp, end = 8.dp, bottom = bottomPadding),
                                        horizontalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        pdfContent()
                                    }
                                } else {
                                    LazyColumn(
                                        state = listState,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .graphicsLayer {
                                                translationX = offsetX
                                            },
                                        userScrollEnabled = !isDrawMode,
                                        contentPadding = PaddingValues(top = topPadding, start = 8.dp, end = 8.dp, bottom = bottomPadding),
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        pdfContent()
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

                            // Prefetch adjacent pages to ensure zero flicker when scrolling
                            LaunchedEffect(currentPage, state.pageCount) {
                                val currentIdx = currentPage - 1
                                val baseW = screenWidthPx - with(density) { 32.dp.roundToPx() }
                                val targetW = if (settledScale > 1.15f) (baseW * settledScale).toInt().coerceIn(1200, 2400) else baseW
                                val nextIdx = currentIdx + 1
                                if (nextIdx in 0 until state.pageCount && !renderedPages.containsKey(nextIdx)) {
                                    viewModel.renderPage(nextIdx, targetW)
                                }
                                val prevIdx = currentIdx - 1
                                if (prevIdx in 0 until state.pageCount && !renderedPages.containsKey(prevIdx)) {
                                    viewModel.renderPage(prevIdx, targetW)
                                }
                            }
                            
                            // Right-edge Vertical Scrollbar
                            if (state.pageCount > 1) {
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = !isImmersiveMode,
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .padding(top = NexusTheme.spacing.xxl, bottom = NexusTheme.spacing.xxl),
                                    enter = fadeIn(),
                                    exit = fadeOut()
                                ) {
                                    NexusVerticalScrollbar(
                                        pageCount = state.pageCount,
                                        sliderValue = sliderValue,
                                        isScrolling = listState.isScrollInProgress,
                                        onSliderValueChange = { newValue ->
                                            sliderValue = newValue
                                            coroutineScope.launch {
                                                listState.scrollToItem((sliderValue.roundToInt() - 1).coerceIn(0, state.pageCount - 1))
                                            }
                                        },
                                        onDragStarted = { isDraggingSlider = true },
                                        onDragStopped = { isDraggingSlider = false }
                                    )
                                }
                            }
                             val pageIdx = (currentPage - 1).coerceAtLeast(0)
                             val pageStrokes = drawnStrokes[pageIdx] ?: emptyList()
                             val redoStrokes = redoStrokesMap[pageIdx] ?: emptyList()

                                 if (pendingTextPoint != null) {
                                     val textFocusRequester = remember { FocusRequester() }
                                     val isEditing = editingExistingTextItem != null

                                     LaunchedEffect(Unit) {
                                         kotlinx.coroutines.delay(120)
                                         try {
                                             textFocusRequester.requestFocus()
                                         } catch (_: Exception) {}
                                     }

                                     androidx.compose.material3.AlertDialog(
                                         onDismissRequest = {
                                             pendingTextPoint = null
                                             editingExistingTextItem = null
                                         },
                                         title = {
                                             NexusText(
                                                 text = if (isEditing) "Edit Text Annotation" else "Insert Text Box",
                                                 style = NexusTheme.typography.title
                                             )
                                         },
                                         text = {
                                             Column(
                                                 verticalArrangement = Arrangement.spacedBy(10.dp),
                                                 modifier = Modifier
                                                     .fillMaxWidth()
                                                     .widthIn(min = 300.dp, max = 540.dp)
                                             ) {
                                                 androidx.compose.material3.OutlinedTextField(
                                                     value = textEntryInput,
                                                     onValueChange = { textEntryInput = it },
                                                     label = { NexusText("Enter annotation text") },
                                                     singleLine = false,
                                                     maxLines = 6,
                                                     textStyle = NexusTheme.typography.body.copy(color = NexusTheme.colors.textPrimary),
                                                     modifier = Modifier
                                                         .fillMaxWidth()
                                                         .focusRequester(textFocusRequester)
                                                 )
                                             }
                                         },
                                         confirmButton = {
                                             Row(
                                                 horizontalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs),
                                                 verticalAlignment = Alignment.CenterVertically
                                             ) {
                                                 if (isEditing) {
                                                     NexusButton(
                                                         text = "Delete",
                                                         onClick = {
                                                             val itemInfo = editingExistingTextItem
                                                             if (itemInfo != null) {
                                                                 val (pIdx, oldItem) = itemInfo
                                                                 val list = drawnStrokes[pIdx]?.toMutableList() ?: mutableListOf()
                                                                 list.remove(oldItem)
                                                                 drawnStrokes[pIdx] = list
                                                                 viewModel.updateDrawnStrokes(pIdx, list)
                                                             }
                                                             textEntryInput = ""
                                                             pendingTextPoint = null
                                                             editingExistingTextItem = null
                                                         }
                                                     )
                                                 }
                                                 NexusButton(
                                                     text = if (isEditing) "Update" else "Insert",
                                                     onClick = {
                                                         val target = pendingTextPoint
                                                         if (target != null && textEntryInput.isNotBlank()) {
                                                             val (pIdx, normPt) = target
                                                             val list = drawnStrokes[pIdx]?.toMutableList() ?: mutableListOf()
                                                             if (editingExistingTextItem != null) {
                                                                 val oldItem = editingExistingTextItem!!.second
                                                                 list.remove(oldItem)
                                                             }
                                                             list.add(
                                                                 PdfAnnotationItem(
                                                                     points = listOf(normPt),
                                                                     color = selectedAnnotationColor,
                                                                     strokeWidth = selectedStrokeWidth,
                                                                     tool = com.nexus.feature.reader.pdf.components.AnnotationTool.Text,
                                                                     text = textEntryInput
                                                                 )
                                                             )
                                                             drawnStrokes[pIdx] = list
                                                             viewModel.updateDrawnStrokes(pIdx, list)
                                                         }
                                                         textEntryInput = ""
                                                         pendingTextPoint = null
                                                         editingExistingTextItem = null
                                                     }
                                                 )
                                             }
                                         },
                                         dismissButton = {
                                             NexusButton(
                                                 text = "Cancel",
                                                 onClick = {
                                                     textEntryInput = ""
                                                     pendingTextPoint = null
                                                     editingExistingTextItem = null
                                                 }
                                             )
                                         }
                                     )
                                 }

                            // Floating Modern Annotation Bar (when user toggles annotate)
                            PdfModernAnnotationBar(
                                visible = isAnnotationPillVisible && !isImmersiveMode,
                                isHighlighter = activeAnnotationTool == com.nexus.feature.reader.pdf.components.AnnotationTool.Highlighter,
                                onSelectPen = {
                                    activeAnnotationTool = com.nexus.feature.reader.pdf.components.AnnotationTool.Pen
                                    isDrawMode = true
                                },
                                onSelectHighlighter = {
                                    activeAnnotationTool = com.nexus.feature.reader.pdf.components.AnnotationTool.Highlighter
                                    isDrawMode = true
                                },
                                selectedColor = selectedAnnotationColor,
                                onSelectColor = { color ->
                                    selectedAnnotationColor = color
                                    isDrawMode = true
                                    if (activeAnnotationTool != com.nexus.feature.reader.pdf.components.AnnotationTool.Pen &&
                                        activeAnnotationTool != com.nexus.feature.reader.pdf.components.AnnotationTool.Highlighter) {
                                        activeAnnotationTool = com.nexus.feature.reader.pdf.components.AnnotationTool.Pen
                                    }
                                },
                                onUndo = {
                                    val current = pageStrokes.toMutableList()
                                    if (current.isNotEmpty()) {
                                        val removed = current.removeAt(current.size - 1)
                                        val updatedList = current.toList()
                                        drawnStrokes[pageIdx] = updatedList
                                        viewModel.updateDrawnStrokes(pageIdx, updatedList)
                                        val rList = redoStrokes.toMutableList()
                                        rList.add(removed)
                                        redoStrokesMap[pageIdx] = rList.toList()
                                    }
                                },
                                onClear = {
                                    drawnStrokes[pageIdx] = emptyList()
                                    viewModel.updateDrawnStrokes(pageIdx, emptyList())
                                },
                                onSave = {
                                    isSavingAnnotations = true
                                    viewModel.saveAnnotationsToFile(encodedUri, drawnStrokes.toMap()) { success ->
                                        isSavingAnnotations = false
                                        isDrawMode = false
                                        isAnnotationPillVisible = false
                                        android.widget.Toast.makeText(context, "Annotations saved to file", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                },
                                canUndo = pageStrokes.isNotEmpty(),
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = bottomInset + 72.dp),
                                isDark = isDarkTheme
                            )

                            // Floating Modern Bottom Dock
                            AnimatedVisibility(
                                visible = !isImmersiveMode,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = bottomInset + 12.dp),
                                enter = slideInVertically(animationSpec = tween(220)) { it } + fadeIn(animationSpec = tween(200)),
                                exit = slideOutVertically(animationSpec = tween(180)) { it } + fadeOut(animationSpec = tween(150))
                            ) {
                                PdfModernBottomDock(
                                    currentPage = currentPage,
                                    totalPages = state.pageCount,
                                    zoomPercent = (scale * 100).roundToInt(),
                                    isAnnotating = isAnnotationPillVisible,
                                    onToggleAnnotate = {
                                        isAnnotationPillVisible = !isAnnotationPillVisible
                                        isDrawMode = isAnnotationPillVisible
                                        if (isAnnotationPillVisible && activeAnnotationTool == null) {
                                            activeAnnotationTool = com.nexus.feature.reader.pdf.components.AnnotationTool.Pen
                                        }
                                    },
                                    onOpenThumbnails = { showThumbnailsSheet = true },
                                    onPreviousPage = {
                                        if (currentPage > 1) {
                                            coroutineScope.launch { listState.animateScrollToItem(currentPage - 2) }
                                        }
                                    },
                                    onNextPage = {
                                        if (currentPage < state.pageCount) {
                                            coroutineScope.launch { listState.animateScrollToItem(currentPage) }
                                        }
                                    },
                                    onJumpToPage = {
                                        goToPageText = currentPage.toString()
                                        showGoToPageDialog = true
                                    },
                                    onZoomIn = {
                                        val newScale = (scale * 1.25f).coerceAtMost(maxZoom)
                                        coroutineScope.launch {
                                            Animatable(scale).animateTo(newScale) { scale = value }
                                        }
                                    },
                                    onZoomOut = {
                                        val newScale = (scale / 1.25f).coerceAtLeast(1f)
                                        coroutineScope.launch {
                                            Animatable(scale).animateTo(newScale) { scale = value }
                                            if (newScale == 1f) {
                                                launch { Animatable(offsetX).animateTo(0f) { offsetX = value } }
                                                launch { Animatable(offsetY).animateTo(0f) { offsetY = value } }
                                            }
                                        }
                                    },
                                    onResetZoom = {
                                        coroutineScope.launch {
                                            launch { Animatable(scale).animateTo(1f) { scale = value } }
                                            launch { Animatable(offsetX).animateTo(0f) { offsetX = value } }
                                            launch { Animatable(offsetY).animateTo(0f) { offsetY = value } }
                                        }
                                    },
                                    isDark = isDarkTheme
                                )
                            }
                        }
                    }
                is PdfReaderUiState.PasswordRequired -> {
                    var password by remember { mutableStateOf("") }
                    var isVisible by remember { mutableStateOf(false) }
                    
                    LaunchedEffect(Unit) {
                        isVisible = true
                    }
                    LaunchedEffect(state.isError) {
                        if (state.isError) {
                            password = ""
                            isVisible = true
                        }
                    }
                    
                    Box(modifier = Modifier.fillMaxSize().background(NexusTheme.colors.background), contentAlignment = Alignment.Center) {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = isVisible,
                            enter = androidx.compose.animation.scaleIn(animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.7f, stiffness = 300f)) + androidx.compose.animation.fadeIn(),
                            exit = androidx.compose.animation.scaleOut() + androidx.compose.animation.fadeOut()
                        ) {
                            NexusSurface(
                                shape = NexusTheme.shapes.large,
                                elevation = 8.dp,
                                modifier = Modifier.padding(NexusTheme.spacing.xl).fillMaxWidth()
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(NexusTheme.spacing.lg)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "Lock",
                                        modifier = Modifier.size(48.dp),
                                        tint = NexusTheme.colors.primary
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    NexusText("Protected PDF", color = NexusTheme.colors.textPrimary, style = NexusTheme.typography.title)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    NexusText("Enter password to open", color = NexusTheme.colors.textSecondary, style = NexusTheme.typography.body)
                                    Spacer(modifier = Modifier.height(24.dp))
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
                                    Row(horizontalArrangement = Arrangement.spacedBy(NexusTheme.spacing.md), modifier = Modifier.fillMaxWidth()) {
                                        Box(modifier = Modifier.weight(1f)) {
                                            NexusButton(text = "Cancel", onClick = onBack, modifier = Modifier.fillMaxWidth())
                                        }
                                        Box(modifier = Modifier.weight(1f)) {
                                            NexusButton(text = "Open", onClick = {
                                                if (password.isNotEmpty()) {
                                                    viewModel.loadPdf(state.encodedUri, state.encodedFileName, password)
                                                }
                                            }, modifier = Modifier.fillMaxWidth())
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                is PdfReaderUiState.Error -> {
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

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
            ) {
                AnimatedVisibility(
                    visible = !isImmersiveMode,
                    enter = slideInVertically(animationSpec = tween(220)) { -it } + fadeIn(animationSpec = tween(200)),
                    exit = slideOutVertically(animationSpec = tween(180)) { -it } + fadeOut(animationSpec = tween(150))
                ) {
                    PdfModernTopBar(
                        title = displayName,
                        pageCount = (uiState as? PdfReaderUiState.Success)?.pageCount ?: 0,
                        isSearchActive = isSearchMode,
                        isDark = isDarkTheme,
                        onBack = onBack,
                        onToggleSearch = { isSearchMode = !isSearchMode },
                        onOpenMore = { showMenu = true }
                    )
                }

                PdfModernSearchBar(
                    visible = isSearchMode && !isImmersiveMode,
                    query = searchQuery,
                    matchCount = searchResults.size,
                    currentMatchIndex = currentSearchMatchIndex,
                    isDark = isDarkTheme,
                    onQueryChange = { viewModel.setSearchQuery(it) },
                    onPreviousMatch = {
                        viewModel.previousSearchMatch()
                        coroutineScope.launch {
                            kotlinx.coroutines.delay(50)
                            listState.animateScrollToItem(viewModel.searchResults.value[viewModel.currentSearchMatchIndex.value])
                        }
                    },
                    onNextMatch = {
                        viewModel.nextSearchMatch()
                        coroutineScope.launch {
                            kotlinx.coroutines.delay(50)
                            listState.animateScrollToItem(viewModel.searchResults.value[viewModel.currentSearchMatchIndex.value])
                        }
                    },
                    onClose = {
                        viewModel.setSearchQuery("")
                        isSearchMode = false
                    }
                )
            }

            PdfRenameBottomSheet(
                visible = showRenameDialog,
                onDismiss = { showRenameDialog = false },
                currentName = renameText.ifEmpty { displayName.removeSuffix(".pdf") },
                onConfirmRename = { newName ->
                    viewModel.renameFile(encodedUri, newName) { success, msg ->
                        showRenameDialog = false
                        if (success) {
                            android.widget.Toast.makeText(context, "Renamed successfully", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            android.widget.Toast.makeText(context, "Rename failed: $msg", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                },
                isDark = isDarkTheme
            )

            if (showGoToPageDialog && uiState is PdfReaderUiState.Success) {
                val successState = uiState as PdfReaderUiState.Success
                PdfGoToPageDialog(
                    currentPage = currentPage,
                    pageCount = successState.pageCount,
                    isDark = isDarkTheme,
                    onDismiss = { showGoToPageDialog = false },
                    onJumpToPage = { targetPage ->
                        coroutineScope.launch {
                            listState.scrollToItem(targetPage - 1)
                        }
                    }
                )
            }

            if (uiState is PdfReaderUiState.Success) {
                val successState = uiState as PdfReaderUiState.Success
                PdfFileInfoBottomSheet(
                    visible = showInfoDialog,
                    onDismiss = { showInfoDialog = false },
                    fileName = displayName,
                    fileSize = successState.fileSize,
                    pageCount = successState.pageCount,
                    lastModified = successState.lastModified,
                    isDark = isDarkTheme
                )

                PdfThumbnailsBottomSheet(
                    visible = showThumbnailsSheet,
                    onDismiss = { showThumbnailsSheet = false },
                    pageCount = successState.pageCount,
                    currentPage = currentPage,
                    renderedPages = renderedPages,
                    onRequestPage = { pageIdx ->
                        coroutineScope.launch {
                            viewModel.renderPage(pageIdx, 300)
                        }
                    },
                    onSelectPage = { pageIdx ->
                        coroutineScope.launch {
                            listState.scrollToItem(pageIdx)
                        }
                    },
                    isDark = isDarkTheme
                )
            }

            if (showOutlineSheet) {
                androidx.compose.material3.ModalBottomSheet(
                    onDismissRequest = { showOutlineSheet = false },
                    containerColor = PdfViewerTheme.surfaceSolid(isDarkTheme)
                ) {
                    if (outline.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().padding(NexusTheme.spacing.xl), contentAlignment = Alignment.Center) {
                            NexusText("No Outline Available", color = PdfViewerTheme.mutedColor(isDarkTheme))
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                            items(outline) { item ->
                                NexusText(
                                    text = item.title,
                                    style = NexusTheme.typography.body.copy(color = PdfViewerTheme.inkColor(isDarkTheme)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .springBounceClick {
                                            showOutlineSheet = false
                                            coroutineScope.launch { listState.scrollToItem(item.pageIndex.coerceAtLeast(0)) }
                                        }
                                        .padding(vertical = 12.dp)
                                )
                            }
                        }
                    }
                }
            }

            PdfMoreOptionsBottomSheet(
                visible = showMenu,
                onDismiss = { showMenu = false },
                isStarred = isStarred,
                onToggleFavorite = {
                    showMenu = false
                    viewModel.toggleFavorite { nowStarred ->
                        val msg = if (nowStarred) "Added to Favorites" else "Removed from Favorites"
                        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
                onRename = {
                    showMenu = false
                    renameText = displayName.removeSuffix(".pdf")
                    showRenameDialog = true
                },
                onFileInfo = {
                    showMenu = false
                    showInfoDialog = true
                },
                onFitWidth = {
                    showMenu = false
                    isFitPage = false
                    coroutineScope.launch {
                        val pageIdx = (currentPage - 1).coerceAtLeast(0)
                        launch { listState.animateScrollToItem(pageIdx) }
                        launch { Animatable(scale).animateTo(1f) { scale = value } }
                        launch { Animatable(offsetX).animateTo(0f) { offsetX = value } }
                        launch { Animatable(offsetY).animateTo(0f) { offsetY = value } }
                    }
                },
                onFitPage = {
                    showMenu = false
                    isFitPage = true
                    coroutineScope.launch {
                        val pageIdx = (currentPage - 1).coerceAtLeast(0)
                        launch { listState.animateScrollToItem(pageIdx) }
                        launch { Animatable(scale).animateTo(1f) { scale = value } }
                        launch { Animatable(offsetX).animateTo(0f) { offsetX = value } }
                        launch { Animatable(offsetY).animateTo(0f) { offsetY = value } }
                    }
                },
                onRotate90 = {
                    showMenu = false
                    pageRotation = (pageRotation + 90) % 360
                },
                isNightReading = backgroundMode == PdfBackgroundMode.Inverts,
                onToggleNightReading = {
                    backgroundMode = if (backgroundMode == PdfBackgroundMode.Inverts) PdfBackgroundMode.Original else PdfBackgroundMode.Inverts
                },
                keepScreenAwake = keepScreenAwake,
                onToggleKeepAwake = {
                    viewModel.setKeepScreenAwake(!keepScreenAwake)
                },
                onPrint = {
                    showMenu = false
                    viewModel.printPdf(context, displayName)
                },
                onShare = {
                    showMenu = false
                    viewModel.sharePdf(context, encodedUri)
                },
                isDark = isDarkTheme
            )
        }
    }

enum class PdfBackgroundMode(val label: String) {
    Original("Original Color"),
    EyeComfort("Eye Comfort (Forest)"),
    Inverts("Inverts")
}
