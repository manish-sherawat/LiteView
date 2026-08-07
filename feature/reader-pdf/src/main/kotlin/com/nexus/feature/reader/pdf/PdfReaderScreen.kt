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

import androidx.compose.ui.draw.drawWithContent
import androidx.compose.runtime.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.nexus.core.ui.animations.shimmerEffect
import com.nexus.core.ui.animations.springBounceClick

import androidx.compose.ui.graphics.asImageBitmap
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
import kotlinx.coroutines.launch
import java.net.URLDecoder
import kotlin.math.roundToInt
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.Animatable

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
    
    val displayName = try { URLDecoder.decode(URLDecoder.decode(fileName, "UTF-8"), "UTF-8") } catch (_: Exception) { fileName }

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.roundToPx() }
    
    var isImmersiveMode by remember { mutableStateOf(false) }
    var isSearchMode by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showGoToPageDialog by remember { mutableStateOf(false) }
    var pageRotation by remember { mutableIntStateOf(0) }
    var renameText by remember { mutableStateOf("") }
    var goToPageText by remember { mutableStateOf("") }
    
    var showViewModal by remember { mutableStateOf(false) }
    var backgroundMode by remember { mutableStateOf(PdfBackgroundMode.Original) }
    val isHorizontalLayout by viewModel.isHorizontalLayout.collectAsStateWithLifecycle()

    var isAnnotationPillVisible by remember { mutableStateOf(false) }
    var activeAnnotationTool by remember { mutableStateOf<com.nexus.feature.reader.pdf.components.AnnotationTool?>(com.nexus.feature.reader.pdf.components.AnnotationTool.Pen) }
    var selectedShape by remember { mutableStateOf(com.nexus.feature.reader.pdf.components.ShapeType.Rectangle) }
    var selectedAnnotationColor by remember { mutableStateOf(Color(0xFFFFEB3B)) }
    var selectedStrokeWidth by remember { mutableFloatStateOf(4f) }
    var selectedEraserFilter by remember { mutableStateOf(com.nexus.feature.reader.pdf.components.EraserTargetFilter.All) }
    var selectedStamp by remember { mutableStateOf(com.nexus.feature.reader.pdf.components.StampType.APPROVED) }
    var pendingTextPoint by remember { mutableStateOf<Pair<Int, Offset>?>(null) }
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
    val sharedScope = com.nexus.core.navigation.LocalSharedTransitionScope.current
    val animatedScope = com.nexus.core.navigation.LocalAnimatedVisibilityScope.current
    val outline by viewModel.outline.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()

    var mainModifier = modifier
        .fillMaxSize()
        .background(NexusTheme.colors.background)
        
    var sharedElementModifier: Modifier = Modifier
    if (sharedScope != null && animatedScope != null) {
        with(sharedScope) {
            @OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
            sharedElementModifier = Modifier.sharedElement(
                state = rememberSharedContentState(key = "thumb_$decodedUri"),
                animatedVisibilityScope = animatedScope,
                boundsTransform = { _, _ -> tween(300) }
            )
        }
    }

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
                            modifier = Modifier.fillMaxSize().then(sharedElementModifier).padding(horizontal = 16.dp, vertical = 90.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
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
                        Box(modifier = Modifier.fillMaxSize()) {
                            val bottomInset = androidx.compose.foundation.layout.WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                            val topPadding by animateDpAsState(targetValue = if (isImmersiveMode) 16.dp else if (isSearchMode) 160.dp else 90.dp)
                            val bottomPadding by animateDpAsState(targetValue = if (isImmersiveMode) bottomInset else bottomInset + 80.dp)
                            
                            val coroutineScope = rememberCoroutineScope()
                            var scale by remember { mutableFloatStateOf(1f) }
                            var offsetX by remember { mutableFloatStateOf(0f) }
                            var offsetY by remember { mutableFloatStateOf(0f) }
                            var snapJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
                            val maxZoom = 5f
                            val screenWidthDp = configuration.screenWidthDp.dp
                            val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
                            val screenWidthPxInt = screenWidthPx
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .then(sharedElementModifier)
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onDoubleTap = { tapOffset ->
                                                val targetScale = if (scale > 1f) 1f else 2.5f
                                                snapJob?.cancel()
                                                snapJob = coroutineScope.launch {
                                                    if (targetScale == 1f) {
                                                        launch { Animatable(scale).animateTo(1f) { scale = value } }
                                                        launch { Animatable(offsetX).animateTo(0f) { offsetX = value } }
                                                        launch { Animatable(offsetY).animateTo(0f) { offsetY = value } }
                                                    } else {
                                                        val zoomFactor = targetScale / scale
                                                        val rawOffsetX = tapOffset.x - (tapOffset.x - offsetX) * zoomFactor
                                                        val rawOffsetY = tapOffset.y - (tapOffset.y - offsetY) * zoomFactor
                                                        
                                                        val maxOffsetX = 0f
                                                        val minOffsetX = -(screenWidthPxInt * targetScale - screenWidthPxInt)
                                                        val maxOffsetY = 0f
                                                        val minOffsetY = -(screenHeightPx * targetScale - screenHeightPx)
                                                        
                                                        val newOffsetX = rawOffsetX.coerceIn(minOffsetX, maxOffsetX)
                                                        val newOffsetY = rawOffsetY.coerceIn(minOffsetY, maxOffsetY)
                                                        
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
                                            snapJob?.cancel()
                                            do {
                                                val event = awaitPointerEvent()
                                                val zoomChange = event.calculateZoom()
                                                val panChange = event.calculatePan()
                                                val centroid = event.calculateCentroid()
                                                
                                                val isMultiTouch = event.changes.size > 1
                                                if ((scale > 1f && !isDrawMode) || isMultiTouch) {
                                                    if (centroid != Offset.Unspecified) {
                                                        val oldScale = scale
                                                        scale = (oldScale * zoomChange).coerceIn(1f, maxZoom)
                                                        val actualZoom = scale / oldScale
                                                        
                                                        val rawOffsetX = centroid.x - (centroid.x - offsetX) * actualZoom + panChange.x
                                                        val rawOffsetY = centroid.y - (centroid.y - offsetY) * actualZoom + panChange.y
                                                        
                                                        val maxOffsetX = 0f
                                                        val minOffsetX = -(screenWidthPxInt * scale - screenWidthPxInt)
                                                        val maxOffsetY = 0f
                                                        val minOffsetY = -(screenHeightPx * scale - screenHeightPx)
                                                        
                                                        val shouldConsume = if (isMultiTouch) true else {
                                                            if (!isHorizontalLayout) {
                                                                !(rawOffsetY > maxOffsetY && panChange.y > 0) && !(rawOffsetY < minOffsetY && panChange.y < 0)
                                                            } else {
                                                                !(rawOffsetX > maxOffsetX && panChange.x > 0) && !(rawOffsetX < minOffsetX && panChange.x < 0)
                                                            }
                                                        }

                                                        offsetX = if (rawOffsetX > maxOffsetX) {
                                                            maxOffsetX + (rawOffsetX - maxOffsetX) * 0.3f
                                                        } else if (rawOffsetX < minOffsetX) {
                                                            minOffsetX + (rawOffsetX - minOffsetX) * 0.3f
                                                        } else {
                                                            rawOffsetX
                                                        }
                                                        
                                                        offsetY = if (rawOffsetY > maxOffsetY) {
                                                            maxOffsetY + (rawOffsetY - maxOffsetY) * 0.3f
                                                        } else if (rawOffsetY < minOffsetY) {
                                                            minOffsetY + (rawOffsetY - minOffsetY) * 0.3f
                                                        } else {
                                                            rawOffsetY
                                                        }
                                                        
                                                        if (shouldConsume || scale != oldScale) {
                                                            event.changes.forEach { it.consume() }
                                                        }
                                                    }
                                                }
                                            } while (event.changes.any { it.pressed })
                                            
                                            if (scale <= 1f) {
                                                snapJob = coroutineScope.launch {
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
                                                
                                                if (offsetX != clampedOffsetX || offsetY != clampedOffsetY) {
                                                    snapJob = coroutineScope.launch {
                                                        if (offsetX != clampedOffsetX) launch { Animatable(offsetX).animateTo(clampedOffsetX) { offsetX = value } }
                                                        if (offsetY != clampedOffsetY) launch { Animatable(offsetY).animateTo(clampedOffsetY) { offsetY = value } }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    .graphicsLayer {
                                        scaleX = scale
                                        scaleY = scale
                                        translationX = offsetX
                                        translationY = offsetY
                                        transformOrigin = TransformOrigin(0f, 0f)
                                    }
                            ) {
                                val colorFilter = remember(backgroundMode) {
                                    when (backgroundMode) {
                                        PdfBackgroundMode.Original -> null
                                        PdfBackgroundMode.Paper -> ColorFilter.colorMatrix(
                                            ColorMatrix(
                                                floatArrayOf(
                                                    0.393f, 0.769f, 0.189f, 0f, 0f,
                                                    0.349f, 0.686f, 0.168f, 0f, 0f,
                                                    0.272f, 0.534f, 0.131f, 0f, 0f,
                                                    0f, 0f, 0f, 1f, 0f
                                                )
                                            )
                                        )
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
                                    items(state.pageCount) { pageIndex ->
                                        LaunchedEffect(pageIndex) {
                                            kotlinx.coroutines.delay(100) // Render debouncer
                                            viewModel.renderPage(pageIndex, screenWidthPx - with(density) { 32.dp.roundToPx() })
                                        }
                                    
                                        val bitmap = renderedPages[pageIndex]
                                        
                                        val pageContainerColor = if (backgroundMode == PdfBackgroundMode.Inverts) 
                                            NexusTheme.colors.background 
                                        else 
                                            NexusTheme.colors.surface
                                            
                                        NexusSurface(
                                            shape = androidx.compose.ui.graphics.RectangleShape,
                                            elevation = 4.dp,
                                            color = pageContainerColor,
                                            modifier = Modifier.width(screenWidthDp - 32.dp)
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
                                                    val placeholderHeight = (screenWidthDp - 32.dp) * 1.414f
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
                                                                          val pressed = event.changes.filter { it.pressed }
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

                                                                                  val threshold = 24.dp.toPx()
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
                                                                                                          val dx = (pos.x - cx) / rx
                                                                                                          val dy = (pos.y - cy) / ry
                                                                                                          val norm = kotlin.math.sqrt(dx * dx + dy * dy)
                                                                                                          kotlin.math.abs(norm - 1f) * minOf(rx, ry) < threshold
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
                                                                                   pendingTextPoint = Pair(pageIndex, normStart)
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
                                                                                  val arrowLen = 30f
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
                                                                          drawContext.canvas.nativeCanvas.drawText(
                                                                              textStr,
                                                                              pt.x,
                                                                              pt.y,
                                                                              android.graphics.Paint().apply {
                                                                                  color = item.color.toArgb()
                                                                                  textSize = (item.strokeWidth * 4f).coerceIn(24f, 64f)
                                                                                  isAntiAlias = true
                                                                                  typeface = android.graphics.Typeface.DEFAULT_BOLD
                                                                              }
                                                                          )
                                                                      }
                                                                  }
                                                                  com.nexus.feature.reader.pdf.components.AnnotationTool.Stamp -> {
                                                                      if (denormalized.isNotEmpty()) {
                                                                          val center = denormalized.first()
                                                                          val stamp = item.stampType ?: com.nexus.feature.reader.pdf.components.StampType.APPROVED
                                                                          val stampColor = Color(stamp.colorHex)
                                                                          val badgeW = 150f
                                                                          val badgeH = 46f
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
                                                                              style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.5f)
                                                                          )
                                                                          val stampPaint = android.graphics.Paint().apply {
                                                                              color = stampColor.toArgb()
                                                                              textSize = 18f
                                                                              isAntiAlias = true
                                                                              typeface = android.graphics.Typeface.DEFAULT_BOLD
                                                                              textAlign = android.graphics.Paint.Align.CENTER
                                                                          }
                                                                          drawContext.canvas.nativeCanvas.drawText(stamp.label, center.x, center.y + 6f, stampPaint)
                                                                      }
                                                                  }
                                                                  com.nexus.feature.reader.pdf.components.AnnotationTool.Ruler -> {
                                                                      if (denormalized.size >= 2) {
                                                                          val start = denormalized.first()
                                                                          val end = denormalized.last()
                                                                          drawLine(color = item.color, start = start, end = end, strokeWidth = strokeStyle.width, cap = StrokeCap.Round)
                                                                          
                                                                          val angle = kotlin.math.atan2((end.y - start.y).toDouble(), (end.x - start.x).toDouble())
                                                                          val perpAngle = angle + Math.PI / 2
                                                                          val tickLen = 14f
                                                                          val p1 = Offset((start.x + tickLen * kotlin.math.cos(perpAngle)).toFloat(), (start.y + tickLen * kotlin.math.sin(perpAngle)).toFloat())
                                                                          val p2 = Offset((start.x - tickLen * kotlin.math.cos(perpAngle)).toFloat(), (start.y - tickLen * kotlin.math.sin(perpAngle)).toFloat())
                                                                          val p3 = Offset((end.x + tickLen * kotlin.math.cos(perpAngle)).toFloat(), (end.y + tickLen * kotlin.math.sin(perpAngle)).toFloat())
                                                                          val p4 = Offset((end.x - tickLen * kotlin.math.cos(perpAngle)).toFloat(), (end.y - tickLen * kotlin.math.sin(perpAngle)).toFloat())
                                                                          drawLine(color = item.color, start = p1, end = p2, strokeWidth = strokeStyle.width)
                                                                          drawLine(color = item.color, start = p3, end = p4, strokeWidth = strokeStyle.width)

                                                                          val lengthPx = (end - start).getDistance()
                                                                          val cm = lengthPx / (density.density * 160f / 2.54f)
                                                                          val mid = Offset((start.x + end.x) / 2f, (start.y + end.y) / 2f - 10f)
                                                                          val labelPaint = android.graphics.Paint().apply {
                                                                              color = item.color.toArgb()
                                                                              textSize = 22f
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
                                                                              radius = (strokeStyle.width / 2f).coerceAtLeast(3f),
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
                                                                  val lengthPx = (end - start).getDistance()
                                                                  val cm = lengthPx / (density.density * 160f / 2.54f)
                                                                  val mid = Offset((start.x + end.x) / 2f, (start.y + end.y) / 2f - 10f)
                                                                  val labelPaint = android.graphics.Paint().apply {
                                                                      color = liveColor.toArgb()
                                                                      textSize = 22f
                                                                      isAntiAlias = true
                                                                      typeface = android.graphics.Typeface.DEFAULT_BOLD
                                                                      textAlign = android.graphics.Paint.Align.CENTER
                                                                  }
                                                                  drawContext.canvas.nativeCanvas.drawText(String.format(java.util.Locale.US, "%.1f cm", cm), mid.x, mid.y, labelPaint)
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
                                                                          val arrowLen = 30f
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
                                                                          radius = (liveStrokeStyle.width / 2f).coerceAtLeast(3f),
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
                                                          }

                                                          // ── Render Active Tool Real-Time Brush Ring Cursor ──
                                                          activePointerOffset?.let { pos ->
                                                              val ringRadius = (selectedStrokeWidth.dp.toPx() / 2f).coerceAtLeast(8f)
                                                              drawCircle(
                                                                  color = selectedAnnotationColor.copy(alpha = 0.30f),
                                                                  radius = ringRadius + 4f,
                                                                  center = pos
                                                              )
                                                              drawCircle(
                                                                  color = selectedAnnotationColor,
                                                                  radius = ringRadius,
                                                                  center = pos,
                                                                  style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
                                                              )
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
                                        modifier = Modifier.fillMaxSize(),
                                        userScrollEnabled = !isDrawMode,
                                        contentPadding = PaddingValues(top = topPadding, start = 8.dp, end = 8.dp, bottom = bottomPadding),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        pdfContent()
                                    }
                                } else {
                                    LazyColumn(
                                        state = listState,
                                        modifier = Modifier.fillMaxSize(),
                                        userScrollEnabled = !isDrawMode,
                                        contentPadding = PaddingValues(top = topPadding, start = 8.dp, end = 8.dp, bottom = bottomPadding),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        pdfContent()
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
                                        .padding(top = 64.dp, bottom = 64.dp),
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

                             // Dockable & Modular PDF Annotation Tool Pill
                             com.nexus.feature.reader.pdf.components.PdfAnnotationPill(
                                 isVisible = isAnnotationPillVisible && !isImmersiveMode,
                                 activeTool = activeAnnotationTool,
                                 onToolSelect = { tool ->
                                     activeAnnotationTool = tool
                                 },
                                 selectedShape = selectedShape,
                                 onShapeSelect = { shape -> selectedShape = shape },
                                 selectedColor = selectedAnnotationColor,
                                 onColorSelect = { color -> selectedAnnotationColor = color },
                                 strokeWidth = selectedStrokeWidth,
                                 onStrokeWidthSelect = { width -> selectedStrokeWidth = width },
                                 selectedEraserFilter = selectedEraserFilter,
                                 onEraserFilterSelect = { filter -> selectedEraserFilter = filter },
                                 selectedStamp = selectedStamp,
                                 onStampSelect = { stamp -> selectedStamp = stamp },
                                 dockPosition = toolbarDockPosition,
                                 onDockPositionChange = { toolbarDockPosition = it },
                                 isCollapsed = isToolbarCollapsed,
                                 onToggleCollapse = { isToolbarCollapsed = !isToolbarCollapsed },
                                 canUndo = pageStrokes.isNotEmpty(),
                                 onUndo = {
                                     val current = pageStrokes.toMutableList()
                                     if (current.isNotEmpty()) {
                                         val removed = current.removeAt(current.size - 1)
                                         drawnStrokes[pageIdx] = current.toList()
                                         val rList = redoStrokes.toMutableList()
                                         rList.add(removed)
                                         redoStrokesMap[pageIdx] = rList.toList()
                                     }
                                 },
                                 canRedo = redoStrokes.isNotEmpty(),
                                 onRedo = {
                                     val rList = redoStrokes.toMutableList()
                                     if (rList.isNotEmpty()) {
                                         val restored = rList.removeAt(rList.size - 1)
                                         redoStrokesMap[pageIdx] = rList.toList()
                                         val current = pageStrokes.toMutableList()
                                         current.add(restored)
                                         drawnStrokes[pageIdx] = current.toList()
                                     }
                                 },
                                 modifier = Modifier
                                     .align(
                                         when (toolbarDockPosition) {
                                             com.nexus.feature.reader.pdf.components.ToolbarDockPosition.Left -> Alignment.CenterStart
                                             com.nexus.feature.reader.pdf.components.ToolbarDockPosition.Right -> Alignment.CenterEnd
                                             com.nexus.feature.reader.pdf.components.ToolbarDockPosition.Top -> Alignment.TopCenter
                                             com.nexus.feature.reader.pdf.components.ToolbarDockPosition.Bottom -> Alignment.BottomCenter
                                         }
                                     )
                                     .padding(
                                          when (toolbarDockPosition) {
                                              com.nexus.feature.reader.pdf.components.ToolbarDockPosition.Left -> PaddingValues(start = 12.dp)
                                              com.nexus.feature.reader.pdf.components.ToolbarDockPosition.Right -> PaddingValues(end = 12.dp)
                                              com.nexus.feature.reader.pdf.components.ToolbarDockPosition.Top -> PaddingValues(top = if (isSearchMode) 175.dp else 76.dp)
                                              com.nexus.feature.reader.pdf.components.ToolbarDockPosition.Bottom -> PaddingValues(bottom = 155.dp)
                                          }
                                      )
                             )

                              if (pendingTextPoint != null) {
                                  androidx.compose.material3.AlertDialog(
                                      onDismissRequest = { pendingTextPoint = null },
                                      title = { NexusText("Insert Text Box", style = NexusTheme.typography.title) },
                                      text = {
                                          androidx.compose.material3.OutlinedTextField(
                                              value = textEntryInput,
                                              onValueChange = { textEntryInput = it },
                                              label = { NexusText("Enter annotation text") },
                                              singleLine = true,
                                              modifier = Modifier.fillMaxWidth()
                                          )
                                      },
                                      confirmButton = {
                                           NexusButton(
                                               text = "Insert",
                                               onClick = {
                                                   val target = pendingTextPoint
                                                   if (target != null && textEntryInput.isNotBlank()) {
                                                       val (pIdx, normPt) = target
                                                       val list = drawnStrokes[pIdx]?.toMutableList() ?: mutableListOf()
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
                                               }
                                           )
                                       },
                                       dismissButton = {
                                           NexusButton(
                                               text = "Cancel",
                                               onClick = {
                                                   textEntryInput = ""
                                                   pendingTextPoint = null
                                               }
                                           )
                                       }
                                  )
                              }

                             // Bottom UI Container
                            AnimatedVisibility(
                                visible = !isImmersiveMode,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .navigationBarsPadding()
                                    .padding(bottom = 32.dp),
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // Floating Page Number Viewer Pill
                                    if (state.pageCount > 1) {
                                        Box(
                                            modifier = Modifier
                                                .clip(NexusTheme.shapes.pill)
                                                .background(NexusTheme.colors.surface)
                                                .border(
                                                    0.8.dp,
                                                    NexusTheme.colors.divider.copy(alpha = 0.6f),
                                                    NexusTheme.shapes.pill
                                                )
                                                .springBounceClick {
                                                    goToPageText = currentPage.toString()
                                                    showGoToPageDialog = true
                                                }
                                                .padding(horizontal = 16.dp, vertical = 7.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                NexusText(
                                                    text = "Page $currentPage",
                                                    style = NexusTheme.typography.body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                                                    color = NexusTheme.colors.primary
                                                )
                                                NexusText(
                                                    text = "of ${state.pageCount}",
                                                    style = NexusTheme.typography.caption,
                                                    color = NexusTheme.colors.textSecondary
                                                )
                                            }
                                        }
                                    }

                                    Box(
                                        modifier = Modifier
                                            .padding(horizontal = 16.dp)
                                            .clip(CircleShape)
                                            .glassBackground(blurRadius = 40f, alpha = 0.85f, fallbackColor = NexusTheme.colors.surfaceVariant)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .horizontalScroll(androidx.compose.foundation.rememberScrollState())
                                                .padding(horizontal = 24.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                            Image(
                                                painter = androidx.compose.ui.res.painterResource(id = com.nexus.core.R.drawable.ic_view_list),
                                                contentDescription = "Outline",
                                                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(if (outline.isNotEmpty()) NexusTheme.colors.textPrimary else NexusTheme.colors.textSecondary.copy(alpha = 0.5f)),
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .clip(CircleShape)
                                                    .springBounceClick { if (outline.isNotEmpty()) showOutlineSheet = true }
                                                    .padding(12.dp)
                                            )
                                            Image(
                                                painter = androidx.compose.ui.res.painterResource(id = com.nexus.core.R.drawable.ic_rename),
                                                contentDescription = "Annotate",
                                                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(if (isAnnotationPillVisible) NexusTheme.colors.primary else NexusTheme.colors.textPrimary),
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .clip(CircleShape)
                                                    .springBounceClick {
                                                        isAnnotationPillVisible = !isAnnotationPillVisible
                                                        isDrawMode = isAnnotationPillVisible
                                                        if (isAnnotationPillVisible) {
                                                            if (activeAnnotationTool == null) {
                                                                activeAnnotationTool = com.nexus.feature.reader.pdf.components.AnnotationTool.Pen
                                                            }
                                                        } else {
                                                            activeAnnotationTool = null
                                                        }
                                                    }
                                                    .padding(12.dp)
                                            )
                                            Image(
                                                painter = androidx.compose.ui.res.painterResource(
                                                    id = if (isStarred) com.nexus.core.R.drawable.ic_star_filled else com.nexus.core.R.drawable.ic_star
                                                ),
                                                contentDescription = "Favorite",
                                                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(if (isStarred) Color(0xFFFFB300) else NexusTheme.colors.textPrimary),
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .clip(CircleShape)
                                                    .springBounceClick {
                                                        viewModel.toggleFavorite { nowStarred ->
                                                            val msg = if (nowStarred) "Added to Favorites" else "Removed from Favorites"
                                                            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                    .padding(12.dp)
                                            )
                                            val context = androidx.compose.ui.platform.LocalContext.current
                                            Image(
                                                imageVector = rememberLayoutDashboardIcon(),
                                                contentDescription = "View Settings",
                                                colorFilter = ColorFilter.tint(NexusTheme.colors.textPrimary),
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .clip(CircleShape)
                                                    .springBounceClick { showViewModal = true }
                                                    .padding(12.dp)
                                            )
                                            Image(
                                                painter = androidx.compose.ui.res.painterResource(
                                                    id = if (backgroundMode == PdfBackgroundMode.Inverts) com.nexus.core.R.drawable.ic_theme_light else com.nexus.core.R.drawable.ic_theme_dark
                                                ),
                                                contentDescription = "Toggle Dark Mode",
                                                colorFilter = ColorFilter.tint(NexusTheme.colors.textPrimary),
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .clip(CircleShape)
                                                    .springBounceClick {
                                                        backgroundMode = if (backgroundMode == PdfBackgroundMode.Inverts) PdfBackgroundMode.Original else PdfBackgroundMode.Inverts
                                                    }
                                                    .padding(12.dp)
                                            )
                                            Image(
                                                painter = androidx.compose.ui.res.painterResource(id = com.nexus.core.R.drawable.ic_share),
                                                contentDescription = "Share Document",
                                                colorFilter = ColorFilter.tint(NexusTheme.colors.textPrimary),
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .clip(CircleShape)
                                                    .springBounceClick {
                                                        viewModel.sharePdf(context, encodedUri)
                                                    }
                                                    .padding(12.dp)
                                            )
                                            Image(
                                                painter = androidx.compose.ui.res.painterResource(id = com.nexus.core.R.drawable.ic_file_search),
                                                contentDescription = "Go to Page",
                                                colorFilter = ColorFilter.tint(NexusTheme.colors.textPrimary),
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .clip(CircleShape)
                                                    .springBounceClick { showGoToPageDialog = true }
                                                    .padding(12.dp)
                                            )
                                        }
                                    }
                                    
                                    AnimatedVisibility(
                                        visible = isDrawMode && drawnStrokes.values.any { it.isNotEmpty() },
                                        enter = slideInVertically(animationSpec = tween(220)) { it } + fadeIn(animationSpec = tween(200)),
                                        exit = slideOutVertically(animationSpec = tween(180)) { it } + fadeOut(animationSpec = tween(150))
                                    ) {
                                        NexusButton(
                                            text = if (isSavingAnnotations) "Saving..." else "Save Annotations",
                                            leadingIcon = androidx.compose.ui.graphics.vector.ImageVector.vectorResource(id = com.nexus.core.R.drawable.ic_save),
                                            isLoading = isSavingAnnotations,
                                            enabled = !isSavingAnnotations,
                                            onClick = {
                                                isSavingAnnotations = true
                                                viewModel.saveAnnotationsToFile(encodedUri, drawnStrokes.toMap()) { success ->
                                                    isSavingAnnotations = false
                                                    isDrawMode = false
                                                    android.widget.Toast.makeText(context, "Annotations saved to file", android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        )
                                    }
                                    

                                }
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
                                modifier = Modifier.padding(32.dp).fillMaxWidth()
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(24.dp)
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
                                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
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

            Column(
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()
            ) {
                AnimatedVisibility(
                    visible = !isImmersiveMode,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    NexusTopBar(
                        title = displayName,
                        titleStyle = com.nexus.core.theme.NexusTheme.typography.body,
                        navigationIcon = {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
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
                            Image(
                                painter = androidx.compose.ui.res.painterResource(id = com.nexus.core.R.drawable.ic_search),
                                contentDescription = "Search",
                                colorFilter = ColorFilter.tint(com.nexus.core.theme.NexusTheme.colors.textPrimary),
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .springBounceClick { isSearchMode = !isSearchMode }
                                    .padding(8.dp)
                            )
                            Image(
                                painter = androidx.compose.ui.res.painterResource(id = com.nexus.core.R.drawable.ic_rotate),
                                contentDescription = "Rotate",
                                colorFilter = ColorFilter.tint(com.nexus.core.theme.NexusTheme.colors.textPrimary),
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .springBounceClick { pageRotation = (pageRotation + 90) % 360 }
                                    .padding(8.dp)
                            )
                            Image(
                                painter = androidx.compose.ui.res.painterResource(id = com.nexus.core.R.drawable.ic_more_vert),
                                contentDescription = "More",
                                colorFilter = ColorFilter.tint(com.nexus.core.theme.NexusTheme.colors.textPrimary),
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .springBounceClick { showMenu = true }
                                    .padding(8.dp)
                            )
                        }
                    )
                }

                AnimatedVisibility(
                    visible = isSearchMode && !isImmersiveMode,
                    modifier = Modifier.padding(top = 8.dp),
                    enter = androidx.compose.animation.expandHorizontally() + androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.shrinkHorizontally() + androidx.compose.animation.fadeOut()
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
                                Image(
                                    imageVector = Icons.Default.KeyboardArrowUp,
                                    contentDescription = "Previous",
                                    colorFilter = ColorFilter.tint(NexusTheme.colors.textPrimary),
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .springBounceClick {
                                            viewModel.previousSearchMatch()
                                            coroutineScope.launch {
                                                kotlinx.coroutines.delay(50)
                                                listState.animateScrollToItem(viewModel.searchResults.value[viewModel.currentSearchMatchIndex.value])
                                            }
                                        }
                                        .padding(4.dp)
                                )
                                Image(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Next",
                                    colorFilter = ColorFilter.tint(NexusTheme.colors.textPrimary),
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .springBounceClick {
                                            viewModel.nextSearchMatch()
                                            coroutineScope.launch {
                                                kotlinx.coroutines.delay(50)
                                                listState.animateScrollToItem(viewModel.searchResults.value[viewModel.currentSearchMatchIndex.value])
                                            }
                                        }
                                        .padding(4.dp)
                                )
                            }
                            Image(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                colorFilter = ColorFilter.tint(NexusTheme.colors.textPrimary),
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .springBounceClick {
                                        viewModel.setSearchQuery("")
                                        isSearchMode = false
                                    }
                                    .padding(4.dp)
                            )
                        }
                    }
                }
            }

            if (showRenameDialog) {
                com.nexus.core.ui.components.NexusDialog(
                    onDismissRequest = { showRenameDialog = false },
                    title = { NexusText("Rename File", style = NexusTheme.typography.h2) },
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
                        NexusButton(
                            text = "Rename",
                            onClick = {
                                viewModel.renameFile(encodedUri, renameText) { success, msg ->
                                    showRenameDialog = false
                                    if (success) {
                                        android.widget.Toast.makeText(context, "Renamed successfully", android.widget.Toast.LENGTH_SHORT).show()
                                    } else {
                                        android.widget.Toast.makeText(context, "Rename failed: $msg", android.widget.Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        )
                    },
                    dismissButton = {
                        NexusButton(text = "Cancel", isOutlined = true, onClick = { showRenameDialog = false })
                    }
                )
            }

            if (showGoToPageDialog && uiState is PdfReaderUiState.Success) {
                val successState = uiState as PdfReaderUiState.Success
                com.nexus.core.ui.components.NexusDialog(
                    onDismissRequest = { showGoToPageDialog = false },
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            NexusText("Go to Page", style = NexusTheme.typography.h2)
                            Box(
                                modifier = Modifier
                                    .clip(NexusTheme.shapes.pill)
                                    .background(NexusTheme.colors.primary.copy(alpha = 0.12f))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                NexusText(
                                    text = "1 - ${successState.pageCount}",
                                    style = NexusTheme.typography.caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                                    color = NexusTheme.colors.primary
                                )
                            }
                        }
                    },
                    text = {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        ) {
                            // Stepper Row: - button, input, + button
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(NexusTheme.colors.surfaceVariant)
                                        .springBounceClick {
                                            val currentVal = goToPageText.toIntOrNull() ?: 1
                                            if (currentVal > 1) goToPageText = (currentVal - 1).toString()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    NexusText("-", style = NexusTheme.typography.h1, color = NexusTheme.colors.textPrimary)
                                }

                                androidx.compose.foundation.text.BasicTextField(
                                    value = goToPageText,
                                    onValueChange = { input ->
                                        if (input.isEmpty() || input.all { char -> char.isDigit() }) {
                                            val num = input.toIntOrNull()
                                            if (num == null || num <= successState.pageCount) {
                                                goToPageText = input
                                            }
                                        }
                                    },
                                    textStyle = NexusTheme.typography.title.copy(
                                        color = NexusTheme.colors.textPrimary,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        fontSize = 20.sp,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(NexusTheme.colors.surfaceVariant, NexusTheme.shapes.medium)
                                        .padding(vertical = 12.dp, horizontal = 16.dp),
                                    singleLine = true,
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                                    )
                                )

                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(NexusTheme.colors.surfaceVariant)
                                        .springBounceClick {
                                            val currentVal = goToPageText.toIntOrNull() ?: 1
                                            if (currentVal < successState.pageCount) goToPageText = (currentVal + 1).toString()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    NexusText("+", style = NexusTheme.typography.h2, color = NexusTheme.colors.textPrimary)
                                }
                            }

                            // Interactive Page Slider
                            val sliderPos = (goToPageText.toFloatOrNull() ?: 1f).coerceIn(1f, successState.pageCount.toFloat())
                            androidx.compose.material3.Slider(
                                value = sliderPos,
                                onValueChange = { newPos ->
                                    goToPageText = newPos.roundToInt().toString()
                                },
                                valueRange = 1f..successState.pageCount.toFloat(),
                                colors = androidx.compose.material3.SliderDefaults.colors(
                                    thumbColor = NexusTheme.colors.primary,
                                    activeTrackColor = NexusTheme.colors.primary,
                                    inactiveTrackColor = NexusTheme.colors.surfaceVariant
                                )
                            )
                        }
                    },
                    confirmButton = {
                        NexusButton(
                            text = "Jump to Page",
                            onClick = {
                                val pageNum = goToPageText.toIntOrNull()
                                if (pageNum != null && pageNum in 1..successState.pageCount) {
                                    coroutineScope.launch {
                                        listState.scrollToItem(pageNum - 1)
                                    }
                                    showGoToPageDialog = false
                                    goToPageText = ""
                                } else {
                                    android.widget.Toast.makeText(context, "Invalid page number", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    },
                    dismissButton = {
                        NexusButton(text = "Cancel", isOutlined = true, onClick = { showGoToPageDialog = false })
                    }
                )
            }

            if (showInfoDialog && uiState is PdfReaderUiState.Success) {
                val successState = uiState as PdfReaderUiState.Success
                com.nexus.core.ui.components.NexusDialog(
                    onDismissRequest = { showInfoDialog = false },
                    title = { NexusText("Document Info", style = NexusTheme.typography.h2) },
                    text = {
                        val formattedSize = if (successState.fileSize > 0) android.text.format.Formatter.formatShortFileSize(context, successState.fileSize) else "Unknown"
                        val formattedDate = if (successState.lastModified > 0) java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(successState.lastModified)) else "Unknown"
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            NexusText("File: $displayName", color = NexusTheme.colors.textPrimary)
                            NexusText("Pages: ${successState.pageCount}", color = NexusTheme.colors.textPrimary)
                            NexusText("Size: $formattedSize", color = NexusTheme.colors.textPrimary)
                            NexusText("Last Modified: $formattedDate", color = NexusTheme.colors.textPrimary)
                        }
                    },
                    confirmButton = {
                        NexusButton(text = "OK", onClick = { showInfoDialog = false })
                    }
                )

            }
            
            if (showOutlineSheet) {
                androidx.compose.material3.ModalBottomSheet(
                    onDismissRequest = { showOutlineSheet = false },
                    containerColor = NexusTheme.colors.surface
                ) {
                    if (outline.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            NexusText("No Outline Available", color = NexusTheme.colors.textSecondary)
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                            items(outline) { item ->
                                NexusText(
                                    text = item.title,
                                    modifier = Modifier.fillMaxWidth().springBounceClick {
                                        showOutlineSheet = false
                                        coroutineScope.launch { listState.scrollToItem(item.pageIndex.coerceAtLeast(0)) }
                                    }.padding(vertical = 12.dp)
                                )
                            }
                        }
                    }
                }
            }
            
            if (showMenu) {
                PdfOptionsBottomSheet(
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
                        viewModel.printPdf(context, displayName)
                    },
                    onInfo = {
                        showMenu = false
                        showInfoDialog = true
                    }
                )
            }

            if (showViewModal) {
                ViewSettingsModal(
                    backgroundMode = backgroundMode,
                    onBackgroundModeChange = { backgroundMode = it },
                    isHorizontalLayout = isHorizontalLayout,
                    onToggleHorizontalLayout = { viewModel.toggleHorizontalLayout() },
                    onDismiss = { showViewModal = false }
                )
            }
        }
    }

@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
private fun PdfOptionsBottomSheet(
    isStarred: Boolean,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onFavorite: () -> Unit,
    onPrint: () -> Unit,
    onInfo: () -> Unit
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
                .padding(bottom = 32.dp)
                .padding(horizontal = 16.dp)
        ) {
            com.nexus.core.ui.NexusText(
                text = "Document Options",
                style = com.nexus.core.theme.NexusTheme.typography.title,
                modifier = Modifier.padding(bottom = 16.dp, start = 8.dp, end = 8.dp)
            )

            val options = listOf(
                MenuOption("File Info", "View document properties", com.nexus.core.R.drawable.ic_info, onInfo),
                MenuOption("Rename", "Rename this file", com.nexus.core.R.drawable.ic_rename, onRename),
                MenuOption(if (isStarred) "Unstar file" else "Star file", "Add or remove bookmark", if (isStarred) com.nexus.core.R.drawable.ic_star_filled else com.nexus.core.R.drawable.ic_star, onFavorite),
                MenuOption("Print", "Print or save as PDF", com.nexus.core.R.drawable.ic_printer, onPrint)
            )

            options.forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(com.nexus.core.theme.NexusTheme.shapes.medium)
                        .springBounceClick { option.action() }
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

private data class MenuOption(
    val label: String,
    val subtitle: String,
    val icon: Int,
    val action: () -> Unit
)

enum class PdfBackgroundMode(val label: String) {
    Original("Original Color"),
    Paper("Paper (Sepia)"),
    EyeComfort("Eye Comfort (Forest)"),
    Inverts("Inverts")
}

@Composable
fun rememberLayoutDashboardIcon(): ImageVector {
    return remember {
        ImageVector.Builder(
            name = "layout-dashboard",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            val strokeColor = SolidColor(Color.Black)
            addPath(
                pathData = PathParser().parsePathString("M5 4h4a1 1 0 0 1 1 1v6a1 1 0 0 1 -1 1h-4a1 1 0 0 1 -1 -1v-6a1 1 0 0 1 1 -1").toNodes(),
                stroke = strokeColor,
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
            addPath(
                pathData = PathParser().parsePathString("M5 16h4a1 1 0 0 1 1 1v2a1 1 0 0 1 -1 1h-4a1 1 0 0 1 -1 -1v-2a1 1 0 0 1 1 -1").toNodes(),
                stroke = strokeColor,
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
            addPath(
                pathData = PathParser().parsePathString("M15 12h4a1 1 0 0 1 1 1v6a1 1 0 0 1 -1 1h-4a1 1 0 0 1 -1 -1v-6a1 1 0 0 1 1 -1").toNodes(),
                stroke = strokeColor,
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
            addPath(
                pathData = PathParser().parsePathString("M15 4h4a1 1 0 0 1 1 1v2a1 1 0 0 1 -1 1h-4a1 1 0 0 1 -1 -1v-2a1 1 0 0 1 1 -1").toNodes(),
                stroke = strokeColor,
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
        }.build()
    }
}

@Composable
fun ViewSettingsModal(
    backgroundMode: PdfBackgroundMode,
    onBackgroundModeChange: (PdfBackgroundMode) -> Unit,
    isHorizontalLayout: Boolean,
    onToggleHorizontalLayout: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
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
                    .padding(16.dp)
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                        onClick = {} 
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
                    NexusText(
                        text = "Reading Direction",
                        style = com.nexus.core.theme.NexusTheme.typography.title,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        com.nexus.core.ui.NexusSurface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(com.nexus.core.theme.NexusTheme.shapes.pill)
                                .springBounceClick { if (isHorizontalLayout) onToggleHorizontalLayout() },
                            shape = com.nexus.core.theme.NexusTheme.shapes.pill,
                            color = if (!isHorizontalLayout) com.nexus.core.theme.NexusTheme.colors.primary else com.nexus.core.theme.NexusTheme.colors.surfaceVariant,
                            elevation = 0.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = rememberArrowsVerticalIcon(),
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = if (!isHorizontalLayout) com.nexus.core.theme.NexusTheme.colors.onPrimary else com.nexus.core.theme.NexusTheme.colors.textPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                NexusText(
                                    text = "Vertical",
                                    color = if (!isHorizontalLayout) com.nexus.core.theme.NexusTheme.colors.onPrimary else com.nexus.core.theme.NexusTheme.colors.textPrimary,
                                    style = com.nexus.core.theme.NexusTheme.typography.label
                                )
                            }
                        }
                        com.nexus.core.ui.NexusSurface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(com.nexus.core.theme.NexusTheme.shapes.pill)
                                .springBounceClick { if (!isHorizontalLayout) onToggleHorizontalLayout() },
                            shape = com.nexus.core.theme.NexusTheme.shapes.pill,
                            color = if (isHorizontalLayout) com.nexus.core.theme.NexusTheme.colors.primary else com.nexus.core.theme.NexusTheme.colors.surfaceVariant,
                            elevation = 0.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = rememberArrowsHorizontalIcon(),
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = if (isHorizontalLayout) com.nexus.core.theme.NexusTheme.colors.onPrimary else com.nexus.core.theme.NexusTheme.colors.textPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                NexusText(
                                    text = "Horizontal",
                                    color = if (isHorizontalLayout) com.nexus.core.theme.NexusTheme.colors.onPrimary else com.nexus.core.theme.NexusTheme.colors.textPrimary,
                                    style = com.nexus.core.theme.NexusTheme.typography.label
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    NexusText(
                        text = "Background",
                        style = com.nexus.core.theme.NexusTheme.typography.title,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PdfBackgroundMode.values().forEach { mode ->
                            com.nexus.core.ui.NexusSurface(
                                modifier = Modifier
                                    .clip(com.nexus.core.theme.NexusTheme.shapes.medium)
                                    .springBounceClick { onBackgroundModeChange(mode) },
                                shape = com.nexus.core.theme.NexusTheme.shapes.medium,
                                color = if (backgroundMode == mode) com.nexus.core.theme.NexusTheme.colors.primary.copy(alpha = 0.1f) else com.nexus.core.theme.NexusTheme.colors.surfaceVariant,
                                elevation = 0.dp
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(
                                                when(mode) {
                                                    PdfBackgroundMode.Original -> Color.White
                                                    PdfBackgroundMode.Paper -> Color(0xFFF4EAD5)
                                                    PdfBackgroundMode.EyeComfort -> Color(0xFFD5F4D9)
                                                    PdfBackgroundMode.Inverts -> Color.Black
                                                }
                                            )
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    NexusText(
                                        text = mode.label,
                                        style = com.nexus.core.theme.NexusTheme.typography.body,
                                        color = if (backgroundMode == mode) com.nexus.core.theme.NexusTheme.colors.primary else com.nexus.core.theme.NexusTheme.colors.textPrimary
                                    )
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
fun rememberArrowsVerticalIcon(): ImageVector {
    return remember {
        ImageVector.Builder(
            name = "arrows-vertical",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            val strokeColor = SolidColor(Color.Black)
            addPath(
                pathData = PathParser().parsePathString("M8 7l4 -4l4 4").toNodes(),
                stroke = strokeColor, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            )
            addPath(
                pathData = PathParser().parsePathString("M8 17l4 4l4 -4").toNodes(),
                stroke = strokeColor, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            )
            addPath(
                pathData = PathParser().parsePathString("M12 3l0 18").toNodes(),
                stroke = strokeColor, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            )
        }.build()
    }
}

@Composable
fun rememberArrowsHorizontalIcon(): ImageVector {
    return remember {
        ImageVector.Builder(
            name = "arrows-horizontal",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            val strokeColor = SolidColor(Color.Black)
            addPath(
                pathData = PathParser().parsePathString("M7 8l-4 4l4 4").toNodes(),
                stroke = strokeColor, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            )
            addPath(
                pathData = PathParser().parsePathString("M17 8l4 4l-4 4").toNodes(),
                stroke = strokeColor, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            )
            addPath(
                pathData = PathParser().parsePathString("M3 12l18 0").toNodes(),
                stroke = strokeColor, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            )
        }.build()
    }
}

@Composable
fun ConfettiEffect(modifier: Modifier = Modifier) {
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition()
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = tween(2000, easing = androidx.compose.animation.core.LinearEasing)
        )
    )
    
    val particles = remember {
        List(50) {
            ConfettiParticle(
                x = kotlin.random.Random.nextFloat(),
                y = kotlin.random.Random.nextFloat() * 1.5f - 0.5f,
                speed = kotlin.random.Random.nextFloat() * 0.5f + 0.5f,
                color = listOf(Color.Red, Color.Blue, Color.Green, Color.Yellow, Color.Magenta).random(),
                size = kotlin.random.Random.nextFloat() * 20f + 10f
            )
        }
    }
    
    androidx.compose.foundation.Canvas(modifier = modifier) {
        particles.forEach { p ->
            val currentY = (p.y + progress * p.speed) % 1.5f
            if (currentY > -0.2f && currentY < 1.2f) {
                drawRect(
                    color = p.color,
                    topLeft = Offset(p.x * size.width, currentY * size.height),
                    size = Size(p.size, p.size)
                )
            }
        }
    }
}

data class ConfettiParticle(val x: Float, val y: Float, val speed: Float, val color: Color, val size: Float)
