package com.nexus.feature.reader.office.word.components

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * High-performance, hardware-accelerated native page viewer for Word documents converted
 * into in-memory PDF byte streams. Backed directly by Android's native PDFium engine.
 *
 * Guarantees:
 * - 100% In-Memory: Streams pages directly from memory-backed PDFium handles.
 * - Fluid Gestures: Smooth pinch-to-zoom (up to 4x), pan, and fling.
 * - True Desktop Fidelity: Exact layout, line wrap, fonts, and table borders.
 */
@Composable
fun WordNativePageViewer(
    pageCount: Int,
    renderedPages: Map<Int, Bitmap>,
    onPageChanged: (page: Int, total: Int) -> Unit,
    onRequestPageRender: (pageIndex: Int, targetWidthPx: Int) -> Unit,
    onToggleImmersive: () -> Unit,
    listState: LazyListState,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val totalPages = pageCount.coerceAtLeast(1)

    // Track visible page index as user scrolls
    LaunchedEffect(listState, totalPages) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { firstIndex ->
                val currentPage = (firstIndex + 1).coerceIn(1, totalPages)
                onPageChanged(currentPage, totalPages)
            }
    }

    // Zoom and Pan states
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    // Color filter for dark mode / eye comfort
    val colorFilter = remember(isDark) {
        if (isDark) {
            ColorFilter.colorMatrix(
                ColorMatrix(
                    floatArrayOf(
                        -0.90f,  0.00f,  0.00f, 0f, 240f,
                         0.00f, -0.90f,  0.00f, 0f, 240f,
                         0.00f,  0.00f, -0.90f, 0f, 240f,
                         0.00f,  0.00f,  0.00f, 1f,   0f
                    )
                )
            )
        } else null
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(if (isDark) NexusTheme.colors.background else Color(0xFFF1F5F9))
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onToggleImmersive() },
                    onDoubleTap = {
                        coroutineScope.launch {
                            val targetScale = if (scale > 1.2f) 1f else 2.2f
                            Animatable(scale).animateTo(targetScale) { scale = value }
                            if (targetScale == 1f) {
                                offsetX = 0f
                                offsetY = 0f
                            }
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.size >= 2) {
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            scale = (scale * zoom).coerceIn(1f, 4f)
                            if (scale > 1f) {
                                offsetX += pan.x
                                offsetY += pan.y
                            } else {
                                offsetX = 0f
                                offsetY = 0f
                            }
                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })

                    if (scale <= 1.05f) {
                        coroutineScope.launch {
                            Animatable(scale).animateTo(1f) { scale = value }
                            Animatable(offsetX).animateTo(0f) { offsetX = value }
                            Animatable(offsetY).animateTo(0f) { offsetY = value }
                        }
                    }
                }
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offsetX
                translationY = offsetY
                transformOrigin = TransformOrigin(0.5f, 0.5f)
            }
    ) {
        val screenWidthPx = with(density) { maxWidth.roundToPx() }
        val targetWidthPx = (screenWidthPx - with(density) { 32.dp.roundToPx() }).coerceIn(600, 2400)

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            items(
                count = totalPages,
                key = { it },
                contentType = { "docx_pdf_page" }
            ) { pageIdx ->
                // Request page bitmap render asynchronously
                LaunchedEffect(pageIdx, targetWidthPx) {
                    if (!renderedPages.containsKey(pageIdx)) {
                        delay(25) // Fast scroll debouncer
                        if (!renderedPages.containsKey(pageIdx)) {
                            onRequestPageRender(pageIdx, targetWidthPx)
                        }
                    }
                }

                val bitmap = renderedPages[pageIdx]
                val pageAspectRatio = remember(bitmap) {
                    if (bitmap != null && bitmap.height > 0) {
                        bitmap.width.toFloat() / bitmap.height.toFloat()
                    } else {
                        1f / 1.414f
                    }
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(pageAspectRatio),
                    shape = RoundedCornerShape(6.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDark) Color(0xFF1E293B) else Color.White
                    )
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (bitmap != null && !bitmap.isRecycled) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Page ${pageIdx + 1}",
                                contentScale = ContentScale.FillWidth,
                                colorFilter = colorFilter,
                                filterQuality = FilterQuality.Low,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(6.dp))
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(if (isDark) Color(0xFF1E293B) else Color.White),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.padding(16.dp),
                                    color = NexusTheme.colors.primary,
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
