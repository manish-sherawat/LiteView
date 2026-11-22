package com.nexus.feature.reader.office.excel.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SheetNavigatorBottomSheet(
    sheetNames: List<String>,
    currentSheet: Int,
    onSheetSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

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
                text = "Sheets",
                style = NexusTheme.typography.title,
                modifier = Modifier.padding(horizontal = NexusTheme.spacing.lg, vertical = NexusTheme.spacing.xs)
            )
            LazyColumn(modifier = Modifier.fillMaxWidth().padding(horizontal = NexusTheme.spacing.md)) {
                itemsIndexed(sheetNames) { index, name ->
                    val isSelected = index == currentSheet
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(NexusTheme.shapes.medium)
                            .background(if (isSelected) NexusTheme.colors.primary.copy(alpha = 0.1f) else Color.Transparent)
                            .clickable { onSheetSelected(index) }
                            .padding(vertical = NexusTheme.spacing.md, horizontal = NexusTheme.spacing.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(id = com.nexus.core.R.drawable.ic_view_list),
                            contentDescription = null,
                            tint = if (isSelected) NexusTheme.colors.primary else NexusTheme.colors.textSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        NexusText(
                            text = name,
                            style = if (isSelected) NexusTheme.typography.body.copy(fontWeight = FontWeight.Bold) else NexusTheme.typography.body,
                            color = if (isSelected) NexusTheme.colors.primary else NexusTheme.colors.textPrimary
                        )
                    }
                }
            }
        }
    }
}
