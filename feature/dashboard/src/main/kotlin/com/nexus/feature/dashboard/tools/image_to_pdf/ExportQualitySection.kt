package com.nexus.feature.dashboard.tools.image_to_pdf

import androidx.compose.ui.semantics.progressBarRangeInfo

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusText
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import com.nexus.core.ui.components.NexusDiscreteTrack
import com.nexus.core.ui.components.NexusStatBox
import kotlin.math.roundToInt

data class QualityData(val title: String, val description: String, val compression: String, val size: String)

@Composable
fun ExportQualitySection(
    totalSizeBytes: Long,
    qualityIndex: Int,
    onQualityIndexChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val baseSizeKb = if (totalSizeBytes > 0) totalSizeBytes / 1024L else 1200L 
    
    val lowSize = (baseSizeKb * 0.30).toLong().coerceAtLeast(50L)
    val medSize = (baseSizeKb * 0.60).toLong().coerceAtLeast(150L)
    val highSize = (baseSizeKb * 1.00).toLong().coerceAtLeast(300L)

    fun formatSize(kb: Long): String {
        return if (kb >= 1024) String.format("~ %.1f MB", kb / 1024f) else "~ $kb KB"
    }

    val qualityData = listOf(
        QualityData("Smaller file", "Maximum compression for sharing and saving storage space.", "High", formatSize(lowSize)),
        QualityData("Balanced quality", "A good balance between visual quality and file size.", "Balanced", formatSize(medSize)),
        QualityData("Best quality", "Preserves more detail with minimal compression.", "Low", formatSize(highSize))
    )
    val currentData = qualityData[qualityIndex]


    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(NexusTheme.shapes.extraLarge)
            .background(NexusTheme.colors.surfaceGlass)
            .border(1.dp, NexusTheme.colors.divider.copy(alpha = 0.5f), NexusTheme.shapes.extraLarge)
            .padding(NexusTheme.spacing.lg)
    ) {
        // Header
        NexusText(
            text = "Export Quality",
            style = NexusTheme.typography.titleLarge,
            color = NexusTheme.colors.textPrimary
        )
        NexusText(
            text = "Choose the balance between document quality and file size.",
            style = NexusTheme.typography.bodySmall,
            color = NexusTheme.colors.textSecondary,
            modifier = Modifier.padding(top = NexusTheme.spacing.xs, bottom = NexusTheme.spacing.lg)
        )
        
        // Slider Shell
        NexusDiscreteTrack(
            steps = 3,
            selectedIndex = qualityIndex,
            onIndexSelected = onQualityIndexChanged,
            trackColor = NexusTheme.colors.trackBackground,
            modifier = Modifier.semantics {
                contentDescription = "Export quality: ${currentData.title}"
                stateDescription = currentData.description
                progressBarRangeInfo = androidx.compose.ui.semantics.ProgressBarRangeInfo(
                    current = qualityIndex.toFloat(),
                    range = 0f..2f,
                    steps = 3
                )
                setProgress { targetValue ->
                    val newIndex = targetValue.roundToInt().coerceIn(0, 2)
                    onQualityIndexChanged(newIndex)
                    true
                }
            }
        )
        
        // Labels
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = NexusTheme.spacing.md, vertical = NexusTheme.spacing.md),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            listOf("Low", "Medium", "High").forEachIndexed { index, label ->
                val isActive = qualityIndex == index
                NexusText(
                    text = label,
                    style = NexusTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = if (isActive) NexusTheme.colors.textPrimary else NexusTheme.colors.textSecondary,
                    modifier = Modifier.graphicsLayer {
                        translationY = if (isActive) -2f else 0f
                    }
                )
            }
        }
        
        // Content
        val reduceMotion = com.nexus.core.ui.animations.AnimationConfig.RESPECT_MOTION_PREFERENCES && com.nexus.core.ui.animations.remoteMotionSettings()
        Box(modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 78.dp).padding(top = NexusTheme.spacing.xs)) {
            Crossfade(
                targetState = currentData, 
                label = "content",
                animationSpec = if (reduceMotion) snap() else tween(200)
            ) { data ->
                Column {
                    NexusText(
                        text = data.title,
                        style = NexusTheme.typography.title.copy(fontWeight = FontWeight.Bold),
                        color = NexusTheme.colors.textPrimary
                    )
                    NexusText(
                        text = data.description,
                        style = NexusTheme.typography.bodySmall,
                        color = NexusTheme.colors.textSecondary,
                        modifier = Modifier.padding(top = NexusTheme.spacing.xs)
                    )
                }
            }
        }
        
        // Info Boxes
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = NexusTheme.spacing.md),
            horizontalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs)
        ) {
            NexusStatBox(label = "COMPRESSION", value = currentData.compression, modifier = Modifier.weight(1f))
            NexusStatBox(label = "ESTIMATED SIZE", value = currentData.size, modifier = Modifier.weight(1f))
        }
        
        // Hint
        Row(
            modifier = Modifier.padding(top = NexusTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(NexusTheme.colors.textSecondary.copy(alpha = 0.5f))
            )
            NexusText(
                text = "Drag the handle to adjust quality",
                style = NexusTheme.typography.label,
                color = NexusTheme.colors.textSecondary
            )
        }
    }
}

