package com.nexus.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.nexus.core.theme.NexusTheme

@Composable
fun NexusTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    singleLine: Boolean = true
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        textStyle = NexusTheme.typography.body.copy(color = NexusTheme.colors.textPrimary),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        singleLine = singleLine,
        cursorBrush = SolidColor(NexusTheme.colors.primary),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .clip(NexusTheme.shapes.pill)
                    .background(NexusTheme.colors.surfaceVariant)
                    .padding(horizontal = NexusTheme.spacing.md, vertical = NexusTheme.spacing.sm),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (leadingIcon != null) {
                        leadingIcon()
                        Box(modifier = Modifier.padding(end = NexusTheme.spacing.sm))
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        if (value.isEmpty()) {
                            NexusText(
                                text = placeholder,
                                color = NexusTheme.colors.textSecondary,
                                style = NexusTheme.typography.body
                            )
                        }
                        innerTextField()
                    }
                    if (trailingIcon != null) {
                        Box(modifier = Modifier.padding(start = NexusTheme.spacing.sm))
                        trailingIcon()
                    }
                }
            }
        }
    )
}

@Composable
fun NexusOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    singleLine: Boolean = true
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        textStyle = NexusTheme.typography.body.copy(color = NexusTheme.colors.textPrimary),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        singleLine = singleLine,
        cursorBrush = SolidColor(NexusTheme.colors.primary),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .border(1.dp, NexusTheme.colors.divider, NexusTheme.shapes.pill)
                    .clip(NexusTheme.shapes.pill)
                    .padding(horizontal = NexusTheme.spacing.md, vertical = NexusTheme.spacing.sm),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (leadingIcon != null) {
                        leadingIcon()
                        Box(modifier = Modifier.padding(end = NexusTheme.spacing.sm))
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        if (value.isEmpty()) {
                            NexusText(
                                text = placeholder,
                                color = NexusTheme.colors.textSecondary,
                                style = NexusTheme.typography.body
                            )
                        }
                        innerTextField()
                    }
                    if (trailingIcon != null) {
                        Box(modifier = Modifier.padding(start = NexusTheme.spacing.sm))
                        trailingIcon()
                    }
                }
            }
        }
    )
}
