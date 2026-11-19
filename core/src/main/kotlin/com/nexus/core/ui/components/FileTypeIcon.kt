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

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.graphicsLayer

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
        DocumentType.PPTX -> FileTypeStyle(
            accentColor = Color(0xFFF57C00),
            containerColor = Color(0xFFF57C00).copy(alpha = 0.12f)
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
    animateOnMount: Boolean = true,
    modifier: Modifier = Modifier
) {
    val style = fileTypeStyleFor(type)

    var isAppeared by remember { mutableStateOf(!animateOnMount) }
    LaunchedEffect(type) {
        if (animateOnMount) {
            isAppeared = true
        }
    }

    val animScale by animateFloatAsState(
        targetValue = if (isAppeared) 1f else 0.72f,
        animationSpec = spring(
            dampingRatio = 0.62f,
            stiffness = 400f
        ),
        label = "iconScale"
    )

    val animRotation by animateFloatAsState(
        targetValue = if (isAppeared) 0f else -10f,
        animationSpec = spring(
            dampingRatio = 0.68f,
            stiffness = 350f
        ),
        label = "iconRotation"
    )

    val content = @Composable {
        val iconSize = if (drawContainer) size * 0.6f else size
        val imageRes = when (type) {
            DocumentType.PDF -> com.nexus.core.R.drawable.ic_file_pdf_new
            DocumentType.DOCX -> com.nexus.core.R.drawable.ic_file_word_new
            DocumentType.XLSX -> com.nexus.core.R.drawable.ic_file_sheet_new
            DocumentType.PPTX -> com.nexus.core.R.drawable.ic_file_ppt_new
            DocumentType.TXT -> com.nexus.core.R.drawable.ic_file_txt_new
            else -> com.nexus.core.R.drawable.ic_file_gen_new
        }
        
        Box(
            modifier = Modifier
                .size(iconSize)
                .graphicsLayer {
                    scaleX = animScale
                    scaleY = animScale
                    rotationZ = animRotation
                },
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = imageRes),
                contentDescription = "${type.name} File",
                modifier = Modifier.fillMaxSize()
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
