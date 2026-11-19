package com.nexus.feature.reader.presentation.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusText
import com.nexus.feature.reader.presentation.engine.PresentationSlideInfo
import com.nexus.feature.reader.presentation.engine.SlideElement

@Composable
fun SlideViewport(
    slideInfo: PresentationSlideInfo,
    loadSlideBitmap: suspend (slideIndex: Int, width: Int, height: Int) -> Bitmap?,
    modifier: Modifier = Modifier,
    searchQuery: String = "",
    onToggleImmersive: () -> Unit = {}
) {
    var bitmap by remember(slideInfo.index) { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember(slideInfo.index) { mutableStateOf(true) }

    var scale by remember(slideInfo.index) { mutableFloatStateOf(1f) }
    var offset by remember(slideInfo.index) { mutableStateOf(Offset.Zero) }

    LaunchedEffect(slideInfo.index) {
        isLoading = true
        val targetWidth = 1920
        val targetHeight = (targetWidth / slideInfo.aspectRatio).toInt().coerceAtLeast(600)
        bitmap = loadSlideBitmap(slideInfo.index, targetWidth, targetHeight)
        isLoading = false
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .background(NexusTheme.colors.background),
        contentAlignment = Alignment.Center
    ) {
        val maxW = maxWidth
        val maxH = maxHeight

        val density = LocalDensity.current
        val maxWPx = with(density) { maxW.toPx() }
        val maxHPx = with(density) { maxH.toPx() }

        // Dynamic padding around viewport to maximize slide area without edge clipping
        val hMargin = 8.dp
        val vMargin = 8.dp
        val availableW = (maxW - hMargin * 2).coerceAtLeast(100.dp)
        val availableH = (maxH - vMargin * 2).coerceAtLeast(100.dp)

        val targetRatio = slideInfo.aspectRatio.coerceIn(0.5f, 2.5f)

        // Dynamically compute the maximum box that fits available dimensions while preserving aspect ratio
        val (cardW, cardH) = if (availableW / targetRatio <= availableH) {
            availableW to (availableW / targetRatio)
        } else {
            (availableH * targetRatio) to availableH
        }

        val cardWPx = with(density) { cardW.toPx() }
        val cardHPx = with(density) { cardH.toPx() }

        val slideCardShape = RoundedCornerShape(8.dp)
        val slideBgColor = slideInfo.backgroundColorArgb?.let { Color(it) } ?: Color.White

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (scale > 1.2f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                scale = 2.2f
                                offset = Offset.Zero
                            }
                        },
                        onTap = {
                            onToggleImmersive()
                        }
                    )
                }
                .pointerInput(slideInfo.index, cardWPx, cardHPx) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            val isMultiTouch = event.changes.size > 1

                            if (scale > 1.01f || isMultiTouch) {
                                val zoomChange = event.calculateZoom()
                                val panChange = event.calculatePan()
                                val centroid = event.calculateCentroid()

                                if (centroid != Offset.Unspecified) {
                                    val newScale = (scale * zoomChange).coerceIn(1f, 5f)
                                    scale = newScale

                                    if (newScale > 1.01f) {
                                        val maxX = maxOf((cardWPx * (newScale - 1f)) / 2f, (cardWPx * newScale - maxWPx) / 2f).coerceAtLeast(0f)
                                        val maxY = maxOf((cardHPx * (newScale - 1f)) / 2f, (cardHPx * newScale - maxHPx) / 2f).coerceAtLeast(0f)
                                        offset = Offset(
                                            x = (offset.x + panChange.x).coerceIn(-maxX, maxX),
                                            y = (offset.y + panChange.y).coerceIn(-maxY, maxY)
                                        )
                                    } else {
                                        offset = Offset.Zero
                                    }
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        } while (event.changes.any { it.pressed })

                        if (scale <= 1.01f) {
                            scale = 1f
                            offset = Offset.Zero
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    }
                    .size(width = cardW, height = cardH)
                    .shadow(
                        elevation = 8.dp,
                        shape = slideCardShape,
                        spotColor = NexusTheme.colors.primary.copy(alpha = 0.18f),
                        ambientColor = Color.Black.copy(alpha = 0.12f)
                    )
                    .clip(slideCardShape)
                    .background(slideBgColor)
                    .border(
                        width = 1.dp,
                        color = NexusTheme.colors.divider.copy(alpha = 0.5f),
                        shape = slideCardShape
                    ),
                contentAlignment = Alignment.Center
            ) {
            val currentBitmap = bitmap
            if (currentBitmap != null) {
                Image(
                    bitmap = currentBitmap.asImageBitmap(),
                    contentDescription = slideInfo.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(38.dp)
                        .padding(4.dp),
                    color = NexusTheme.colors.primary,
                    strokeWidth = 3.dp
                )
            } else {
                NativeSlideContent(slideInfo = slideInfo, searchQuery = searchQuery)
            }

            // Search highlight bounding boxes over matching text/elements
            if (searchQuery.isNotBlank()) {
                val matchingElements = slideInfo.elements.filter { elementMatchesQuery(it, searchQuery) }
                if (matchingElements.isNotEmpty()) {
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val totalW = maxWidth.value
                        val totalH = maxHeight.value
                        for (elem in matchingElements) {
                            val b = elem.bounds
                            val leftDp = (b.left * totalW).dp
                            val topDp = (b.top * totalH).dp
                            val widthDp = (b.width() * totalW).coerceAtLeast(40f).dp
                            val heightDp = (b.height() * totalH).coerceAtLeast(24f).dp

                            Box(
                                modifier = Modifier
                                    .offset(x = leftDp, y = topDp)
                                    .size(width = widthDp, height = heightDp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFFFFEB3B).copy(alpha = 0.28f))
                                    .border(2.dp, Color(0xFFFFD600), RoundedCornerShape(4.dp))
                            )
                        }
                    }
                }
            }
            }
        }
    }
}

private fun elementMatchesQuery(element: SlideElement, query: String): Boolean {
    if (query.isBlank()) return false
    return when (element) {
        is SlideElement.Text -> {
            element.text.contains(query, ignoreCase = true) ||
            element.bullets.any { it.contains(query, ignoreCase = true) } ||
            element.paragraphs.any { p -> p.spans.any { it.text.contains(query, ignoreCase = true) } }
        }
        is SlideElement.Shape -> {
            element.textParagraphs.any { p -> p.spans.any { it.text.contains(query, ignoreCase = true) } }
        }
        is SlideElement.Table -> {
            element.rows.any { row -> row.any { cell -> cell.contains(query, ignoreCase = true) } } ||
            element.tableRows.any { row -> row.cells.any { cell -> cell.text.contains(query, ignoreCase = true) } }
        }
        else -> false
    }
}

@Composable
internal fun NativeSlideContent(
    slideInfo: PresentationSlideInfo,
    searchQuery: String = "",
    isThumbnail: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (isThumbnail) {
        ThumbnailSlidePreview(
            slideInfo = slideInfo,
            modifier = modifier
        )
        return
    }

    if (slideInfo.elements.isEmpty()) {
        if (isThumbnail) {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                NexusText(
                    text = "${slideInfo.index + 1}",
                    style = NexusTheme.typography.caption.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                    color = NexusTheme.colors.textSecondary
                )
            }
        } else {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NexusText(
                        text = slideInfo.title?.takeIf { it.isNotBlank() } ?: "Slide ${slideInfo.index + 1}",
                        style = NexusTheme.typography.h2.copy(
                            fontSize = 16.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                        ),
                        color = NexusTheme.colors.textPrimary
                    )
                    NexusText(
                        text = "Slide content not supported.",
                        style = NexusTheme.typography.caption,
                        color = NexusTheme.colors.textSecondary
                    )
                }
            }
        }
        return
    }

    // CRITICAL-17: Use spatial layout only when the majority of elements have distinct valid bounds (safe from RectF.equals quirks)
    val hasDistinctSpatialPositions = remember(slideInfo.elements) {
        if (slideInfo.elements.isEmpty()) return@remember false
        val validSpatialCount = slideInfo.elements.count { el ->
            val b = el.bounds
            val isDefaultFallback = kotlin.math.abs(b.left - 0.05f) < 0.02f &&
                    kotlin.math.abs(b.top - 0.10f) < 0.02f &&
                    kotlin.math.abs(b.right - 0.95f) < 0.02f &&
                    kotlin.math.abs(b.bottom - 0.90f) < 0.02f
            !isDefaultFallback && b.width() > 0.04f && b.height() > 0.02f
        }
        validSpatialCount >= (slideInfo.elements.size + 1) / 2
    }

    if (hasDistinctSpatialPositions) {
        SpatialSlideLayout(
            slideInfo = slideInfo,
            searchQuery = searchQuery,
            isThumbnail = isThumbnail,
            modifier = modifier
        )
    } else {
        FlowSlideLayout(
            slideInfo = slideInfo,
            searchQuery = searchQuery,
            isThumbnail = isThumbnail,
            modifier = modifier
        )
    }
}

@Composable
private fun SpatialSlideLayout(
    slideInfo: PresentationSlideInfo,
    searchQuery: String = "",
    isThumbnail: Boolean = false,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
    ) {
        val totalWidth = maxWidth
        val totalHeight = maxHeight

        // HIGH-20: Use the slide's actual point dimensions as reference canvas width and raise minimum fontScale to 0.5f (scaled appropriately for thumbnails)
        val actualCanvasWidthPoints = (slideInfo.widthTwips / 20.0f).takeIf { it > 100f }
            ?: (if (slideInfo.aspectRatio >= 1.5f) 960f else 720f)
        val fontScale = if (isThumbnail) {
            (totalWidth.value / actualCanvasWidthPoints).coerceIn(0.08f, 0.35f)
        } else {
            (totalWidth.value / actualCanvasWidthPoints).coerceIn(0.5f, 1.25f)
        }

        // Performance guard: cap element count for tiny thumbnail rendering
        val elementsToRender = if (isThumbnail) slideInfo.elements.take(25) else slideInfo.elements

        // Viewport edge margins: ensures parameter elements and slide tellers are never cut out at the edges
        val edgeMargin = 3.dp
        val safeTotalW = (totalWidth - edgeMargin * 2).coerceAtLeast(20.dp)
        val safeTotalH = (totalHeight - edgeMargin * 2).coerceAtLeast(20.dp)

        for (element in elementsToRender) {
            val isFullBg = (element.bounds.width() >= 0.90f && element.bounds.height() >= 0.90f &&
                    element.bounds.left <= 0.05f && element.bounds.top <= 0.05f &&
                    element !is SlideElement.Text)

            val safeLeft = element.bounds.left.coerceIn(0f, 0.96f)
            val safeTop = element.bounds.top.coerceIn(0f, 0.96f)
            val safeRight = element.bounds.right.coerceIn(safeLeft + 0.02f, 1f)
            val safeBottom = element.bounds.bottom.coerceIn(safeTop + 0.02f, 1f)

            val elemX = if (isFullBg) 0.dp else (edgeMargin + (safeLeft * safeTotalW.value).dp)
            val elemY = if (isFullBg) 0.dp else (edgeMargin + (safeTop * safeTotalH.value).dp)
            val rawW = if (isFullBg) totalWidth else (((safeRight - safeLeft) * safeTotalW.value).dp)
            val rawH = if (isFullBg) totalHeight else (((safeBottom - safeTop) * safeTotalH.value).dp)

            val minSizeW = if (isThumbnail) 2.dp else 12.dp
            val minSizeH = if (isThumbnail) 2.dp else 8.dp
            val maxAllowedW = (totalWidth - elemX).coerceAtLeast(minSizeW)
            val maxAllowedH = (totalHeight - elemY).coerceAtLeast(minSizeH)
            val elemW = rawW.coerceIn(minSizeW, maxAllowedW)
            val elemH = rawH.coerceIn(minSizeH, maxAllowedH)

            Box(
                modifier = Modifier
                    .offset(x = elemX, y = elemY)
                    .size(width = elemW, height = elemH)
                    .clipToBounds()
            ) {
                RenderSlideElement(
                    element = element,
                    isSpatial = true,
                    searchQuery = searchQuery,
                    fontScale = fontScale,
                    isThumbnail = isThumbnail
                )
            }
        }
    }
}

@Composable
private fun FlowSlideLayout(
    slideInfo: PresentationSlideInfo,
    searchQuery: String = "",
    isThumbnail: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (isThumbnail) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(4.dp)
                .clipToBounds()
        ) {
            if (!slideInfo.title.isNullOrBlank()) {
                NexusText(
                    text = slideInfo.title,
                    style = NexusTheme.typography.caption.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 7.sp,
                        lineHeight = 9.sp
                    ),
                    color = NexusTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
            }

            for (element in slideInfo.elements.take(6)) {
                RenderSlideElement(
                    element = element,
                    isSpatial = false,
                    searchQuery = searchQuery,
                    fontScale = 0.25f,
                    isThumbnail = true
                )
                Spacer(modifier = Modifier.height(2.dp))
            }
        }
    } else {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            // LOW-27: Remove verticalScroll to prevent gesture conflicts with HorizontalPager swipe; clip overflow instead
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
            ) {
                // Slide Title
                if (!slideInfo.title.isNullOrBlank()) {
                    NexusText(
                        text = slideInfo.title,
                        style = NexusTheme.typography.h2.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            letterSpacing = (-0.4).sp
                        ),
                        color = NexusTheme.colors.textPrimary,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                for (element in slideInfo.elements) {
                    RenderSlideElement(
                        element = element,
                        isSpatial = false,
                        searchQuery = searchQuery,
                        fontScale = 1f,
                        isThumbnail = false
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun RenderSlideElement(
    element: SlideElement,
    isSpatial: Boolean,
    searchQuery: String = "",
    fontScale: Float = 1f,
    isThumbnail: Boolean = false
) {
    when (element) {
        is SlideElement.Text -> {
            if (element.paragraphs.isNotEmpty()) {
                SlideRichText(
                    paragraphs = element.paragraphs,
                    defaultColor = element.colorArgb?.let { Color(it) } ?: NexusTheme.colors.textPrimary,
                    searchQuery = searchQuery,
                    fontScale = fontScale,
                    modifier = Modifier.fillMaxWidth()
                )
            } else if (element.bullets.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = if (isThumbnail) 0.dp else 2.dp),
                    verticalArrangement = Arrangement.spacedBy(if (isThumbnail) 1.dp else 4.dp)
                ) {
                    val bulletsToRender = if (isThumbnail) element.bullets.take(4) else element.bullets
                    for (bullet in bulletsToRender) {
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(if (isThumbnail) 2.dp else 6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(top = if (isThumbnail) 2.dp else 5.dp)
                                    .size(if (isThumbnail) 2.dp else 5.dp)
                                    .clip(CircleShape)
                                    .background(NexusTheme.colors.primary)
                            )
                            NexusText(
                                text = bullet,
                                style = NexusTheme.typography.body.copy(fontSize = (14f * fontScale).coerceIn(if (isThumbnail) 4f else 7.5f, 24f).sp),
                                color = element.colorArgb?.let { Color(it) } ?: NexusTheme.colors.textPrimary,
                                maxLines = if (isThumbnail) 1 else Int.MAX_VALUE,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            } else {
                NexusText(
                    text = element.text,
                    style = NexusTheme.typography.body.copy(
                        fontSize = (element.fontSize * fontScale).coerceIn(if (isThumbnail) 4f else 7.5f, 32f).sp,
                        fontWeight = if (element.isBold) FontWeight.SemiBold else FontWeight.Normal
                    ),
                    color = element.colorArgb?.let { Color(it) } ?: NexusTheme.colors.textPrimary,
                    modifier = Modifier.padding(vertical = if (isThumbnail) 0.dp else 1.dp),
                    maxLines = if (isThumbnail) 2 else Int.MAX_VALUE,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        is SlideElement.Shape -> {
            SlideShapeRenderer(
                shapeType = element.shapeType,
                fillInfo = element.fillInfo,
                paragraphs = element.textParagraphs,
                fontScale = fontScale,
                modifier = if (isSpatial) Modifier.fillMaxSize() else Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
            )
        }

        is SlideElement.Image -> {
            if (!element.bitmap.isRecycled && element.bitmap.width > 0 && element.bitmap.height > 0) {
                Image(
                    bitmap = element.bitmap.asImageBitmap(),
                    contentDescription = "Slide Image",
                    contentScale = ContentScale.Fit,
                    modifier = if (isSpatial) Modifier.fillMaxSize() else Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .clip(NexusTheme.shapes.small)
                )
            }
        }

        is SlideElement.Table -> {
            SlideTableRenderer(
                tableRows = element.tableRows,
                fallbackRows = element.rows,
                modifier = Modifier.fillMaxWidth()
            )
        }

        is SlideElement.Chart -> {
            SlideChartRenderer(
                chartType = element.chartType,
                categories = element.categories,
                series = element.series,
                title = element.title,
                modifier = Modifier.fillMaxWidth()
            )
        }

        is SlideElement.Media -> {
            if (isThumbnail) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            } else {
                SlideMediaRenderer(
                    mediaPath = element.mediaPath,
                    mediaType = element.mediaType,
                    title = element.title,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Dedicated lightweight, fast, crash-proof thumbnail rendering for slide drawer and sorter.
 */
@Composable
private fun ThumbnailSlidePreview(
    slideInfo: PresentationSlideInfo,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .background(
                slideInfo.backgroundColorArgb?.let { Color(it) } ?: NexusTheme.colors.surfaceVariant
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(4.dp)
        ) {
            NexusText(
                text = slideInfo.title?.takeIf { it.isNotBlank() } ?: "Slide ${slideInfo.index + 1}",
                style = NexusTheme.typography.caption.copy(
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = NexusTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            NexusText(
                text = "${slideInfo.elements.size} elements",
                style = NexusTheme.typography.caption.copy(fontSize = 7.sp),
                color = NexusTheme.colors.textSecondary
            )
        }
    }
}
