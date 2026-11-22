package com.nexus.feature.reader.office.excel.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusText

@Composable
fun JumpColumnDialog(
    onDismiss: () -> Unit,
    onJump: (String) -> Unit
) {
    var jumpColumnInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { NexusText("Jump to Cell / Row / Column", color = NexusTheme.colors.textPrimary) },
        text = {
            Column {
                NexusText("Examples:\n• C150 (Cell)\n• C (Column)\n• 150 (Row)", color = NexusTheme.colors.textSecondary)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = jumpColumnInput,
                    onValueChange = { jumpColumnInput = it.uppercase() },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val input = jumpColumnInput.trim()
                if (input.isNotEmpty()) {
                    onJump(input)
                }
                onDismiss()
            }) {
                NexusText("Jump", color = NexusTheme.colors.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                NexusText("Cancel", color = NexusTheme.colors.textSecondary)
            }
        },
        containerColor = NexusTheme.colors.surface
    )
}
