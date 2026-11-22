package com.nexus.feature.reader.office.word.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusText
import com.nexus.feature.reader.office.word.model.DocxHeading

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordOutlineBottomSheet(
    headings: List<DocxHeading>,
    onHeadingSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = NexusTheme.colors.surface,
        contentColor = NexusTheme.colors.textPrimary,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = NexusTheme.spacing.xl)
        ) {
            NexusText(
                text = "Table of Contents",
                style = NexusTheme.typography.title,
                modifier = Modifier.padding(horizontal = NexusTheme.spacing.lg, vertical = NexusTheme.spacing.xs)
            )
            if (headings.isEmpty()) {
                NexusText(
                    text = "No outline available for this document.",
                    style = NexusTheme.typography.body,
                    color = NexusTheme.colors.textSecondary,
                    modifier = Modifier.padding(horizontal = NexusTheme.spacing.lg, vertical = NexusTheme.spacing.md)
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().padding(horizontal = NexusTheme.spacing.md)) {
                    items(headings) { heading ->
                        val indentPadding = ((heading.level - 1) * 16).dp
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(NexusTheme.shapes.medium)
                                .clickable { onHeadingSelected(heading.anchorId) }
                                .padding(vertical = NexusTheme.spacing.sm, horizontal = NexusTheme.spacing.md)
                                .padding(start = indentPadding),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            NexusText(
                                text = heading.text,
                                style = NexusTheme.typography.body.copy(
                                    fontWeight = if (heading.level == 1) FontWeight.Bold else FontWeight.Normal
                               ),
                                color = NexusTheme.colors.textPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}
