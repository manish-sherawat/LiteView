package com.nexus.feature.reader.office.word.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusText
import com.nexus.core.ui.components.NexusButton
import com.nexus.feature.dashboard.data.DocumentBookmark

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordBookmarksBottomSheet(
    bookmarks: List<DocumentBookmark>,
    onAddBookmark: (String) -> Unit,
    onDeleteBookmark: (DocumentBookmark) -> Unit,
    onNavigateToBookmark: (DocumentBookmark) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var isAdding by remember { mutableStateOf(false) }
    var newLabel by remember { mutableStateOf("") }

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
                text = "Bookmarks",
                style = NexusTheme.typography.title,
                modifier = Modifier.padding(horizontal = NexusTheme.spacing.lg, vertical = NexusTheme.spacing.xs)
            )
            if (isAdding) {
                Column(modifier = Modifier.padding(horizontal = NexusTheme.spacing.lg, vertical = NexusTheme.spacing.xs)) {
                    OutlinedTextField(
                        value = newLabel,
                        onValueChange = { newLabel = it },
                        label = { NexusText("Bookmark name (e.g. Page 5, Chapter 2)", color = NexusTheme.colors.textSecondary) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(NexusTheme.spacing.md)) {
                        NexusButton(
                            text = "Cancel",
                            onClick = { isAdding = false }
                        )
                        NexusButton(
                            text = "Save",
                            onClick = {
                                if (newLabel.isNotBlank()) {
                                    onAddBookmark(newLabel)
                                    isAdding = false
                                    newLabel = ""
                                }
                            }
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = NexusTheme.spacing.lg, vertical = NexusTheme.spacing.xs),
                    horizontalArrangement = Arrangement.End
                ) {
                    NexusButton(
                        text = "+ Add Bookmark",
                        onClick = { isAdding = true }
                    )
                }
                if (bookmarks.isEmpty()) {
                    NexusText(
                        text = "No bookmarks saved for this document.",
                        style = NexusTheme.typography.body,
                        color = NexusTheme.colors.textSecondary,
                        modifier = Modifier.padding(horizontal = NexusTheme.spacing.lg, vertical = NexusTheme.spacing.md)
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().padding(horizontal = NexusTheme.spacing.md)) {
                        items(bookmarks) { bk ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(NexusTheme.shapes.medium)
                                    .clickable { onNavigateToBookmark(bk) }
                                    .padding(vertical = NexusTheme.spacing.sm, horizontal = NexusTheme.spacing.md),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(id = com.nexus.core.R.drawable.ic_tag),
                                    contentDescription = null,
                                    tint = NexusTheme.colors.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    NexusText(
                                        text = bk.label,
                                        style = NexusTheme.typography.body.copy(fontWeight = FontWeight.Bold)
                                    )
                                    NexusText(
                                        text = "Position: ${bk.pageIndex}",
                                        style = NexusTheme.typography.caption,
                                        color = NexusTheme.colors.textSecondary
                                    )
                                }
                                Icon(
                                    painter = painterResource(id = com.nexus.core.R.drawable.ic_delete),
                                    contentDescription = "Delete",
                                    tint = NexusTheme.colors.error,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clickable { onDeleteBookmark(bk) }
                                        .padding(NexusTheme.spacing.xxs)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
