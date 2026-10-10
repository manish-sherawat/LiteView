package com.nexus.feature.reader.presentation.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusSurface
import com.nexus.core.ui.NexusText
import com.nexus.feature.reader.presentation.engine.PresentationSlideInfo
import com.nexus.feature.reader.presentation.engine.SlideElement

@Composable
fun SlideViewport(
    slideInfo: PresentationSlideInfo,
    loadSlideBitmap: suspend (slideIndex: Int, width: Int, height: Int) -> Bitmap?,
    modifier: Modifier = Modifier,
    onToggleImmersive: () -> Unit = {}
) {
    var bitmap by remember(slideInfo.index) { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember(slideInfo.index) { mutableStateOf(true) }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 4f)
        offset = if (scale > 1f) {
            Offset(
                x = offset.x + panChange.x,
                y = offset.y + panChange.y
            )
        } else {
            Offset.Zero
        }
    }

    LaunchedEffect(slideInfo.index) {
        isLoading = true
        val targetWidth = 1920
        val targetHeight = (targetWidth / slideInfo.aspectRatio).toInt().coerceAtLeast(600)
        bitmap = loadSlideBitmap(slideInfo.index, targetWidth, targetHeight)
        isLoading = false
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NexusTheme.colors.background)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        scale = if (scale > 1.2f) 1f else 2.2f
                        if (scale == 1f) offset = Offset.Zero
                    },
                    onTap = {
                        onToggleImmersive()
                    }
                )
            }
            .transformable(state = transformState),
        contentAlignment = Alignment.Center
    ) {
        val slideCardShape = RoundedCornerShape(12.dp)
        val defaultSlideBg = NexusTheme.colors.surface

        Box(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .aspectRatio(slideInfo.aspectRatio)
                .shadow(
                    elevation = 12.dp,
                    shape = slideCardShape,
                    spotColor = NexusTheme.colors.primary.copy(alpha = 0.22f),
                    ambientColor = Color.Black.copy(alpha = 0.15f)
                )
                .clip(slideCardShape)
                .background(
                    if (slideInfo.backgroundColorArgb != null) Color(slideInfo.backgroundColorArgb)
                    else defaultSlideBg
                )
                .border(
                    width = 1.dp,
                    color = NexusTheme.colors.divider.copy(alpha = 0.6f),
                    shape = slideCardShape
                )
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
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
                NativeSlideContent(slideInfo = slideInfo)
            }
        }
    }
}

@Composable
private fun NativeSlideContent(
    slideInfo: PresentationSlideInfo,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Slide Title
            if (!slideInfo.title.isNullOrBlank()) {
                NexusText(
                    text = slideInfo.title,
                    style = NexusTheme.typography.h2.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        letterSpacing = (-0.5).sp
                    ),
                    color = NexusTheme.colors.textPrimary,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Render text elements and bullets
            for (element in slideInfo.elements) {
                when (element) {
                    is SlideElement.Text -> {
                        if (element.text != slideInfo.title) {
                            if (element.bullets.isNotEmpty()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    for (bullet in element.bullets) {
                                        Row(
                                            verticalAlignment = Alignment.Top,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .padding(top = 7.dp)
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(NexusTheme.colors.primary)
                                            )
                                            NexusText(
                                                text = bullet,
                                                style = NexusTheme.typography.body.copy(fontSize = 14.sp),
                                                color = NexusTheme.colors.textPrimary
                                            )
                                        }
                                    }
                                }
                            } else {
                                NexusText(
                                    text = element.text,
                                    style = NexusTheme.typography.body.copy(
                                        fontSize = if (element.isBold) 15.sp else 14.sp,
                                        fontWeight = if (element.isBold) FontWeight.SemiBold else FontWeight.Normal
                                    ),
                                    color = NexusTheme.colors.textPrimary,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                        }
                    }

                    is SlideElement.Image -> {
                        Spacer(modifier = Modifier.height(10.dp))
                        Image(
                            bitmap = element.bitmap.asImageBitmap(),
                            contentDescription = "Slide Image",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(170.dp)
                                .clip(NexusTheme.shapes.small)
                                .border(1.dp, NexusTheme.colors.divider.copy(alpha = 0.4f), NexusTheme.shapes.small)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    is SlideElement.Table -> {
                        Spacer(modifier = Modifier.height(10.dp))
                        NexusSurface(
                            shape = NexusTheme.shapes.small,
                            color = NexusTheme.colors.surfaceVariant.copy(alpha = 0.45f),
                            borderWidth = 1.dp,
                            borderColor = NexusTheme.colors.divider.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                for ((rowIndex, row) in element.rows.withIndex()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        for (cellText in row) {
                                            NexusText(
                                                text = cellText,
                                                style = NexusTheme.typography.caption.copy(
                                                    fontWeight = if (rowIndex == 0) FontWeight.Bold else FontWeight.Normal,
                                                    fontSize = 13.sp
                                                ),
                                                color = NexusTheme.colors.textPrimary,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    is SlideElement.Shape -> {}
                }
            }
        }
    }
}
