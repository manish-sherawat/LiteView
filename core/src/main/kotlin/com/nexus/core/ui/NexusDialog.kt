package com.nexus.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nexus.core.theme.NexusTheme

@Composable
fun NexusDialog(
    onDismissRequest: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = modifier
                .fillMaxWidth(0.9f)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            NexusSurface(
                shape = NexusTheme.shapes.large,
                elevation = 16.dp,
                color = NexusTheme.colors.surface
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    NexusText(
                        text = title,
                        style = NexusTheme.typography.h2
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    content()
                }
            }
        }
    }
}
