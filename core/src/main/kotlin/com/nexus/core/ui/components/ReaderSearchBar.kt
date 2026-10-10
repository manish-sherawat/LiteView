package com.nexus.core.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusSurface
import com.nexus.core.ui.NexusText
import com.nexus.core.ui.animations.springBounceClick

@Composable
fun ReaderSearchBar(
    visible: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    matchCount: Int = 0,
    currentMatchIndex: Int = 0,
    onPreviousMatch: () -> Unit,
    onNextMatch: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    placeholderText: String = "Search..."
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier.padding(top = 8.dp),
        enter = expandHorizontally() + fadeIn(),
        exit = shrinkHorizontally() + fadeOut()
    ) {
        NexusSurface(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
            shape = NexusTheme.shapes.pill,
            elevation = 4.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.weight(1f),
                    textStyle = NexusTheme.typography.body.copy(color = NexusTheme.colors.textPrimary),
                    singleLine = true,
                    decorationBox = { innerTextField ->
                        if (query.isEmpty()) {
                            NexusText(placeholderText, color = NexusTheme.colors.textSecondary)
                        }
                        innerTextField()
                    }
                )

                if (matchCount > 0) {
                    NexusText(
                        text = "${currentMatchIndex + 1}/$matchCount",
                        style = NexusTheme.typography.label,
                        color = NexusTheme.colors.textSecondary,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    Image(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Previous",
                        colorFilter = ColorFilter.tint(NexusTheme.colors.textPrimary),
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .springBounceClick(onClick = onPreviousMatch)
                            .padding(4.dp)
                    )

                    Image(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Next",
                        colorFilter = ColorFilter.tint(NexusTheme.colors.textPrimary),
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .springBounceClick(onClick = onNextMatch)
                            .padding(4.dp)
                    )
                }

                Image(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    colorFilter = ColorFilter.tint(NexusTheme.colors.textPrimary),
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .springBounceClick(onClick = onClose)
                        .padding(4.dp)
                )
            }
        }
    }
}
