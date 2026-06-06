package com.nexus.core.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
        val painter = when (type) {
            DocumentType.PDF -> painterResource(id = com.nexus.core.R.drawable.ic_pdf)
            DocumentType.DOCX -> painterResource(id = com.nexus.core.R.drawable.ic_doc)
            DocumentType.XLSX -> painterResource(id = com.nexus.core.R.drawable.ic_excel)
            DocumentType.TXT -> painterResource(id = com.nexus.core.R.drawable.ic_txt)
            else -> null
        }
        if (painter != null) {
            Image(
                painter = painter,
                contentDescription = type.name,
                modifier = Modifier.size(if (drawContainer) size * 0.6f else size)
            )
        } else {
            NexusText(
                text = "?",
                color = style.accentColor,
                style = NexusTheme.typography.title
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
