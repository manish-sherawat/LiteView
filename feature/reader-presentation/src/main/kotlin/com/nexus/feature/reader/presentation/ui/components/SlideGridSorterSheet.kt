package com.nexus.feature.reader.presentation.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusSurface
import com.nexus.core.ui.NexusText
import com.nexus.feature.reader.presentation.engine.PresentationSlideInfo
import com.nexus.feature.reader.presentation.ui.PresentationReaderViewModel

/**
 * Lightbox / Slide Grid Sorter modal view for visual scanning across the entire presentation deck.
 * Uses an asynchronous downsampled thumbnail caching layer to guarantee 60fps performance and zero crashes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SlideGridSorterSheet(
    slides: List<PresentationSlideInfo>,
    currentSlideIndex: Int,
    viewModel: PresentationReaderViewModel,
    onSelectSlide: (Int) -> Unit,
    onDismissRequest: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = NexusTheme.colors.background,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
        ) {
            // Header bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.GridView,
                        contentDescription = null,
                        tint = NexusTheme.colors.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.size(10.dp))
                    Column {
                        NexusText(
                            text = "Slide Sorter",
                            style = NexusTheme.typography.title,
                            color = NexusTheme.colors.textPrimary
                        )
                        NexusText(
                            text = "${slides.size} slides in presentation",
                            style = NexusTheme.typography.caption,
                            color = NexusTheme.colors.textSecondary
                        )
                    }
                }

                IconButton(onClick = onDismissRequest) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = NexusTheme.colors.textPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Multi-column grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(slides, key = { idx, _ -> idx }) { index, slide ->
                    val isSelected = (index == currentSlideIndex)
                    val cardShape = RoundedCornerShape(12.dp)
                    val safeRatio = slide.aspectRatio.takeIf { !it.isNaN() && it in 0.5f..2.5f } ?: (16f / 9f)

                    NexusSurface(
                        shape = cardShape,
                        color = if (isSelected) NexusTheme.colors.primary.copy(alpha = 0.12f) else NexusTheme.colors.surface,
                        borderWidth = if (isSelected) 2.dp else 1.dp,
                        borderColor = if (isSelected) NexusTheme.colors.primary else NexusTheme.colors.divider.copy(alpha = 0.6f),
                        elevation = if (isSelected) 6.dp else 2.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelectSlide(index)
                                onDismissRequest()
                            }
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            // Asynchronous downsampled thumbnail preview
                            AsyncSlideThumbnail(
                                slide = slide,
                                viewModel = viewModel,
                                aspectRatio = safeRatio,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(safeRatio)
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Slide Title
                            NexusText(
                                text = slide.title?.takeIf { it.isNotBlank() } ?: "Slide ${index + 1}",
                                style = NexusTheme.typography.caption.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.sp
                                ),
                                color = if (isSelected) NexusTheme.colors.primary else NexusTheme.colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * High-performance, crash-free asynchronous thumbnail component.
 * Decodes downsampled bitmaps off the UI thread and reads from LRU memory cache.
 * Eliminates all SubcomposeLayout / BoxWithConstraints intrinsic measurement crashes in Lazy Grids and Bottom Sheets.
 */
@Composable
fun AsyncSlideThumbnail(
    slide: PresentationSlideInfo,
    viewModel: PresentationReaderViewModel,
    aspectRatio: Float,
    modifier: Modifier = Modifier
) {
    var thumbnailBitmap by remember(slide.index) {
        mutableStateOf<Bitmap?>(viewModel.getCachedThumbnail(slide.index))
    }

    LaunchedEffect(slide.index) {
        if (thumbnailBitmap == null) {
            val targetW = 320
            val targetH = (targetW / aspectRatio).toInt().coerceIn(120, 240)
            thumbnailBitmap = viewModel.getSlideThumbnail(slide.index, targetW, targetH)
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                slide.backgroundColorArgb?.let { Color(it) } ?: NexusTheme.colors.surfaceVariant
            )
            .border(0.5.dp, NexusTheme.colors.divider.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        val bmp = thumbnailBitmap
        if (bmp != null && !bmp.isRecycled) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Slide ${slide.index + 1}",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Elegant placeholder card
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(6.dp)
            ) {
                NexusText(
                    text = slide.title?.takeIf { it.isNotBlank() } ?: "Slide ${slide.index + 1}",
                    style = NexusTheme.typography.caption.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = NexusTheme.colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Slide Number Pill Badge
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(6.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.Black.copy(alpha = 0.65f))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            NexusText(
                text = "${slide.index + 1}",
                style = NexusTheme.typography.caption.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = Color.White
            )
        }
    }
}
