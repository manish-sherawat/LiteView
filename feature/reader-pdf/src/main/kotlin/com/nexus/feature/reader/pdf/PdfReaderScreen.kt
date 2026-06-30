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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import com.nexus.core.ui.components.NexusVerticalScrollbar
import androidx.compose.ui.input.pointer.pointerInput

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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.components.NexusButton
import com.nexus.core.ui.NexusSurface
import com.nexus.core.ui.NexusText
import com.nexus.core.ui.components.NexusPillTopBar
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
    var showInfoDialog by remember { mutableStateOf(false) }
    var showGoToPageDialog by remember { mutableStateOf(false) }
    var pageRotation by remember { mutableIntStateOf(0) }
    var renameText by remember { mutableStateOf("") }
    var goToPageText by remember { mutableStateOf("") }
    
    var showViewModal by remember { mutableStateOf(false) }
    var backgroundMode by remember { mutableStateOf(PdfBackgroundMode.Original) }
    val isHorizontalLayout by viewModel.isHorizontalLayout.collectAsStateWithLifecycle()

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
                            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 90.dp),
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
                                                    }
                                                    
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
                                                        .graphicsLayer {
                                                            rotationZ = pageRotation.toFloat()
                                                        }
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
                                        }
                                    }
                                }

                                if (isHorizontalLayout) {
                                    LazyRow(
                                        state = listState,
                                        modifier = Modifier.fillMaxSize(),
                                        userScrollEnabled = scale <= 1f,
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
                                        userScrollEnabled = scale <= 1f,
                                        contentPadding = PaddingValues(top = topPadding, start = 8.dp, end = 8.dp, bottom = bottomPadding),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {

                                    NexusSurface(
                                        shape = CircleShape,
                                        elevation = 8.dp,
                                        color = NexusTheme.colors.surfaceVariant.copy(alpha = 0.95f),
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
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
                    NexusPillTopBar(
                        title = displayName,
                        titleStyle = com.nexus.core.theme.NexusTheme.typography.body,
                        outerVerticalPadding = 4.dp,
                        innerVerticalPadding = 8.dp,
                        iconSize = 40.dp,
                        navigationIcon = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
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
                    title = { NexusText("Go to Page (1 - ${successState.pageCount})", style = NexusTheme.typography.h2) },
                    text = {
                        androidx.compose.foundation.text.BasicTextField(
                            value = goToPageText,
                            onValueChange = { if (it.isEmpty() || it.all { char -> char.isDigit() }) goToPageText = it },
                            textStyle = NexusTheme.typography.body.copy(color = NexusTheme.colors.textPrimary),
                            modifier = Modifier.fillMaxWidth().padding(8.dp).background(NexusTheme.colors.surfaceVariant, NexusTheme.shapes.small).padding(16.dp),
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                        )
                    },
                    confirmButton = {
                        NexusButton(
                            text = "Go",
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

@Composable
private fun PdfOptionsBottomSheet(
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onFavorite: () -> Unit,
    onPrint: () -> Unit,
    onInfo: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
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
                        MenuOption("File Info", "View document properties", com.nexus.core.R.drawable.ic_info, onInfo),
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
                                .clickable { if (isHorizontalLayout) onToggleHorizontalLayout() },
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
                                .clickable { if (!isHorizontalLayout) onToggleHorizontalLayout() },
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
                                    .clickable { onBackgroundModeChange(mode) },
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
