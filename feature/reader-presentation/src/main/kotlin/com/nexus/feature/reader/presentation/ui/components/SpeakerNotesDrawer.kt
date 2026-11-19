package com.nexus.feature.reader.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusSurface
import com.nexus.core.ui.NexusText

/**
 * Speaker & Presenter notes bottom sheet drawer with font-size scaling controls.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeakerNotesDrawer(
    slideIndex: Int,
    slideTitle: String?,
    notes: String?,
    fontSizeSp: Float,
    onIncreaseFontSize: () -> Unit,
    onDecreaseFontSize: () -> Unit,
    onDismissRequest: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = NexusTheme.colors.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
        ) {
            // Header bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(NexusTheme.colors.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notes,
                            contentDescription = null,
                            tint = NexusTheme.colors.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        NexusText(
                            text = "Speaker Notes",
                            style = NexusTheme.typography.title,
                            color = NexusTheme.colors.textPrimary
                        )
                        NexusText(
                            text = "Slide ${slideIndex + 1}${slideTitle?.let { ": $it" } ?: ""}",
                            style = NexusTheme.typography.caption,
                            color = NexusTheme.colors.textSecondary
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Font-size A- button
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(NexusTheme.colors.surfaceVariant)
                            .clickable { onDecreaseFontSize() },
                        contentAlignment = Alignment.Center
                    ) {
                        NexusText(
                            text = "A-",
                            style = NexusTheme.typography.caption.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                            color = NexusTheme.colors.textPrimary
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Font-size A+ button
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(NexusTheme.colors.surfaceVariant)
                            .clickable { onIncreaseFontSize() },
                        contentAlignment = Alignment.Center
                    ) {
                        NexusText(
                            text = "A+",
                            style = NexusTheme.typography.caption.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp),
                            color = NexusTheme.colors.textPrimary
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(onClick = onDismissRequest) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = NexusTheme.colors.textPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Notes Content Area
            NexusSurface(
                shape = RoundedCornerShape(14.dp),
                color = NexusTheme.colors.surfaceVariant.copy(alpha = 0.4f),
                borderWidth = 1.dp,
                borderColor = NexusTheme.colors.divider.copy(alpha = 0.5f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .padding(bottom = 16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (!notes.isNullOrBlank()) {
                        NexusText(
                            text = notes,
                            style = NexusTheme.typography.body.copy(
                                fontSize = fontSizeSp.sp,
                                lineHeight = (fontSizeSp * 1.45f).sp
                            ),
                            color = NexusTheme.colors.textPrimary
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(180.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Notes,
                                    contentDescription = null,
                                    tint = NexusTheme.colors.textSecondary.copy(alpha = 0.5f),
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                NexusText(
                                    text = "No presenter notes for this slide",
                                    style = NexusTheme.typography.caption,
                                    color = NexusTheme.colors.textSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
