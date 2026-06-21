package com.nexus.nexusdocs.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusText
import com.nexus.core.ui.components.NexusButton
import com.nexus.core.ui.components.NexusTopBar
import com.nexus.core.R

val SUPPORTED_TYPES_STRING = "Supported: PDF, DOCX, XLSX, TXT"

@Composable
fun UnsupportedFileScreen(fileName: String, onBack: () -> Unit) {
    BackHandler { onBack() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NexusTheme.colors.surface)
    ) {
        NexusTopBar(
            title = "Unsupported File",
            navigationIcon = {
                Image(
                    painter = painterResource(id = R.drawable.ic_back),
                    contentDescription = "Go Back",
                    colorFilter = ColorFilter.tint(NexusTheme.colors.textPrimary),
                    modifier = Modifier
                        .clickable { onBack() }
                        .padding(8.dp)
                        .size(24.dp)
                )
            }
        )
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(32.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_info),
                    contentDescription = "Unsupported",
                    colorFilter = ColorFilter.tint(NexusTheme.colors.primary),
                    modifier = Modifier.size(72.dp)
                )
                NexusText(
                    text = "Unsupported File Type",
                    style = NexusTheme.typography.title,
                    color = NexusTheme.colors.textPrimary
                )
                NexusText(
                    text = "\"$fileName\" cannot be opened.\n$SUPPORTED_TYPES_STRING",
                    style = NexusTheme.typography.body,
                    color = NexusTheme.colors.textSecondary,
                    textAlign = TextAlign.Center
                )
                NexusButton(
                    text = "Go Back",
                    onClick = onBack
                )
            }
        }
    }
}
