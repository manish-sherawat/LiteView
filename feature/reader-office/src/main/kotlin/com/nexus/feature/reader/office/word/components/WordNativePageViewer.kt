package com.nexus.feature.reader.office.word.components

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.nexus.core.theme.NexusTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * High-performance, hardware-accelerated native page viewer for documents converted
 * by LibreOfficeKit. Eliminates WebViews, DOM reflows, and mobile viewport compression.
 */
@Composable
fun WordNativePageViewer(
    pdfFilePath: String,
    pageCount: Int,
    onPageChanged: (page: Int, total: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val listState = rememberLazyListState()

    // Manage PdfRenderer lifecycle
    val pfdAndRenderer = remember(pdfFilePath) {
        try {
            val file = File(pdfFilePath)
            if (file.exists() && file.length() > 0) {
                val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(pfd)
                Pair(pfd, renderer)
            } else null
        } catch (_: Exception) {
            null
        }
    }

    DisposableEffect(pfdAndRenderer) {
        onDispose {
            try {
                pfdAndRenderer?.second?.close()
                pfdAndRenderer?.first?.close()
            } catch (_: Exception) {}
        }
    }

    val renderer = pfdAndRenderer?.second
    val totalPages = renderer?.pageCount ?: pageCount.coerceAtLeast(1)

    // Track visible page index
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { firstIndex ->
                onPageChanged(firstIndex + 1, totalPages)
            }
    }

    // Zoom state
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    val transformState = rememberTransformableState { zoomChange, offsetChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 3.5f)
        if (scale > 1f) {
            offsetX += offsetChange.x
            offsetY += offsetChange.y
        } else {
            offsetX = 0f
            offsetY = 0f
        }
    }

    // LRU Bitmap Cache
    val pageBitmapCache = remember { mutableStateMapOf<Int, Bitmap>() }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(NexusTheme.colors.background)
            .transformable(state = transformState)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offsetX
                translationY = offsetY
            }
    ) {
        val targetWidthPx = with(density) { (maxWidth - 32.dp).roundToPx().coerceAtLeast(400) }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(totalPages) { pageIdx ->
                var pageBitmap by remember(pageIdx) { mutableStateOf<Bitmap?>(pageBitmapCache[pageIdx]) }
                var pageAspectRatio by remember(pageIdx) { mutableFloatStateOf(1f / 1.414f) } // Default A4 ratio

                LaunchedEffect(pageIdx, renderer, targetWidthPx) {
                    if (pageBitmap == null && renderer != null) {
                        val bmp = withContext(Dispatchers.IO) {
                            try {
                                synchronized(renderer) {
                                    val page = renderer.openPage(pageIdx)
                                    val ratio = page.height.toFloat() / page.width.toFloat().coerceAtLeast(1f)
                                    pageAspectRatio = 1f / ratio

                                    val targetHeightPx = (targetWidthPx * ratio).toInt().coerceAtLeast(400)
                                    val b = Bitmap.createBitmap(targetWidthPx, targetHeightPx, Bitmap.Config.ARGB_8888)
                                    // Pre-fill with pure paper white
                                    b.eraseColor(android.graphics.Color.WHITE)
                                    page.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                    page.close()
                                    b
                                }
                            } catch (_: Exception) {
                                null
                            }
                        }
                        if (bmp != null) {
                            pageBitmap = bmp
                            pageBitmapCache[pageIdx] = bmp
                        }
                    }
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(pageAspectRatio),
                    shape = RoundedCornerShape(8.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        val currentBmp = pageBitmap
                        if (currentBmp != null) {
                            Image(
                                bitmap = currentBmp.asImageBitmap(),
                                contentDescription = "Page ${pageIdx + 1}",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(8.dp))
                            )
                        } else {
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
