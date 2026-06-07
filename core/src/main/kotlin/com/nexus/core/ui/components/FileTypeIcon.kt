package com.nexus.core.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nexus.core.navigation.DocumentType
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusText

data class FileTypeStyle(
    val accentColor: Color,
    val containerColor: Color
)

@Composable
fun fileTypeStyleFor(type: DocumentType): FileTypeStyle {
    val outlineColor = NexusTheme.colors.divider
    val surfaceVariantColor = NexusTheme.colors.surfaceVariant
    return when (type) {
        DocumentType.PDF -> FileTypeStyle(
            accentColor = Color(0xFFD32F2F),
            containerColor = Color(0xFFD32F2F).copy(alpha = 0.12f)
        )
        DocumentType.DOCX -> FileTypeStyle(
            accentColor = Color(0xFF1976D2),
            containerColor = Color(0xFF1976D2).copy(alpha = 0.12f)
        )
        DocumentType.XLSX -> FileTypeStyle(
            accentColor = Color(0xFF388E3C),
            containerColor = Color(0xFF388E3C).copy(alpha = 0.12f)
        )
        DocumentType.TXT -> FileTypeStyle(
            accentColor = Color(0xFF616161),
            containerColor = Color(0xFF616161).copy(alpha = 0.12f)
        )
        DocumentType.UNKNOWN -> FileTypeStyle(
            accentColor = outlineColor,
            containerColor = surfaceVariantColor
        )
    }
}

@Composable
fun FileTypeIcon(
    type: DocumentType,
    size: Dp = 40.dp,
    drawContainer: Boolean = true,
    modifier: Modifier = Modifier
) {
    val style = fileTypeStyleFor(type)
    val content = @Composable {
        val iconSize = if (drawContainer) size * 0.6f else size
        val textLabel = when (type) {
            DocumentType.PDF -> "PDF"
            DocumentType.DOCX -> "DOC"
            DocumentType.XLSX -> "XLS"
            DocumentType.TXT -> "TXT"
            else -> "FILE"
        }
        
        Box(
            modifier = Modifier.size(iconSize),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                val scaleX = size.value / 24f
                val scaleY = size.value / 24f
                val strokeWidth = 1.5f * scaleX

                // Rounded file
                drawRoundRect(
                    color = style.accentColor,
                    topLeft = androidx.compose.ui.geometry.Offset(4f * scaleX, 2f * scaleY),
                    size = androidx.compose.ui.geometry.Size(16f * scaleX, 20f * scaleY),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f * scaleX, 3f * scaleY),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
                )

                // Fold corner
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(14f * scaleX, 2f * scaleY)
                    lineTo(14f * scaleX, 7f * scaleY)
                    quadraticBezierTo(
                        14f * scaleX, 7.55f * scaleY,
                        14.45f * scaleX, 8f * scaleY
                    )
                    lineTo(15f * scaleX, 8f * scaleY)
                    lineTo(20f * scaleX, 8f * scaleY)
                }
                drawPath(
                    path = path,
                    color = style.accentColor,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
                )
            }
            
            NexusText(
                text = textLabel,
                color = style.accentColor,
                style = NexusTheme.typography.caption.copy(
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    fontSize = androidx.compose.ui.unit.TextUnit(iconSize.value * (5.5f/24f), androidx.compose.ui.unit.TextUnitType.Sp)
                ),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = iconSize * (12.5f/24f))
            )
        }
    }

    if (drawContainer) {
        val gradientBrush = Brush.linearGradient(
            colors = listOf(
                style.accentColor.copy(alpha = 0.18f),
                style.accentColor.copy(alpha = 0.04f)
            )
        )
        Box(
            modifier = modifier
                .size(size)
                .clip(NexusTheme.shapes.small)
                .background(brush = gradientBrush),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    } else {
        Box(
            modifier = modifier.size(size),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}
