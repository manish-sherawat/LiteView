package com.nexus.feature.reader.pdf

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
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
import java.net.URLDecoder

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
    
    val displayName = try { URLDecoder.decode(URLDecoder.decode(fileName, "UTF-8"), "UTF-8") } catch (_: Exception) { fileName }

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.roundToPx() }
    
    var isImmersiveMode by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NexusTheme.colors.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = !isImmersiveMode,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                NexusTopBar(
                    title = displayName,
                    navigationIcon = {
                        Box(modifier = Modifier.clickable { onBack() }.padding(12.dp)) {
                            NexusText("\u2190", style = NexusTheme.typography.h2)
                        }
                    }
                )
            }

            AnimatedContent(
                targetState = uiState,
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
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
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
                                            ZoomablePdfPage(
                                                bitmap = bitmap,
                                                onTap = { isImmersiveMode = !isImmersiveMode }
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
                            
                            val currentPage by remember { derivedStateOf { listState.firstVisibleItemIndex + 1 } }
                            androidx.compose.animation.AnimatedVisibility(
                                visible = !isImmersiveMode,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 48.dp),
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
                                        text = "Page $currentPage / ${state.pageCount}",
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
        }
    }
}

@Composable
private fun ZoomablePdfPage(bitmap: Bitmap, onTap: () -> Unit = {}) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectPdfGestures(
                    shouldConsumePan = { scale > 1f },
                    onGesture = { pan, zoom ->
                        scale = (scale * zoom).coerceIn(1f, 3f)
                        if (scale > 1f) {
                            offset += pan
                        } else {
                            offset = Offset.Zero
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        scale = if (scale > 1f) 1f else 2f
                        offset = Offset.Zero
                    },
                    onTap = { onTap() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
        )
    }
}

suspend fun PointerInputScope.detectPdfGestures(
    shouldConsumePan: () -> Boolean,
    onGesture: (pan: Offset, zoom: Float) -> Unit
) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
            val canceled = event.changes.any { it.isConsumed }
            if (!canceled) {
                val zoomChange = event.calculateZoom()
                val panChange = event.calculatePan()
                val isMultiTouch = event.changes.size > 1

                if (zoomChange != 1f || panChange != Offset.Zero) {
                    if (isMultiTouch || shouldConsumePan()) {
                        onGesture(panChange, zoomChange)
                        event.changes.forEach {
                            if (it.positionChanged()) {
                                it.consume()
                            }
                        }
                    }
                }
            }
        } while (!canceled && event.changes.any { it.pressed })
    }
}
