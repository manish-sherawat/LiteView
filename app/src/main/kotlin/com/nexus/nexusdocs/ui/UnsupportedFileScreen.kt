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
import com.nexus.core.ui.components.NexusEmptyStateImage
import com.nexus.core.ui.components.NexusEmptyStateType
import com.nexus.core.ui.components.NexusTopBar
import com.nexus.core.R

val SUPPORTED_TYPES_STRING = "Supported: PDF, Word, Excel, Text, Markdown, JSON, XML, YAML, TOML, INI, CSV, Code"

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
        val config = androidx.compose.ui.platform.LocalConfiguration.current
        val isWide = config.screenWidthDp >= 600
        val isTall = config.screenHeightDp >= 800
        val maxImgHeight = if (isTall) 480.dp else if (isWide) 400.dp else 300.dp

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            NexusEmptyStateImage(
                type = NexusEmptyStateType.UNSUPPORTED,
                contentDescription = "Unsupported File",
                modifier = Modifier
                    .fillMaxWidth(if (isWide) 0.70f else 0.95f)
                    .heightIn(max = maxImgHeight),
                contentScale = androidx.compose.ui.layout.ContentScale.Fit
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            NexusButton(
                text = "Go Back",
                onClick = onBack,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
