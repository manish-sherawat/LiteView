package com.nexus.core.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.core.R
import com.nexus.core.navigation.DocumentType
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusText
import com.nexus.core.ui.animations.fadeSlideIn
import com.nexus.core.ui.animations.springBounceClick
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class FileDetailItem(
    val iconRes: Int,
    val label: String,
    val value: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NexusFileDetailsSheet(
    fileName: String,
    uri: String,
    fileSizeBytes: Long = 0L,
    lastOpenedAt: Long = 0L,
    docType: DocumentType = DocumentType.UNKNOWN,
    customDetails: List<FileDetailItem> = emptyList(),
    onDismissRequest: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var isCopied by remember { mutableStateOf(false) }

    val lowerName = fileName.lowercase()
    val (formatIconRes, formatLabel) = when {
        lowerName.endsWith(".pdf") || docType == DocumentType.PDF -> Pair(R.drawable.ic_file_pdf_new, "PDF Document")
        lowerName.endsWith(".docx") || lowerName.endsWith(".doc") || docType == DocumentType.DOCX -> Pair(R.drawable.ic_file_word_new, "Word Document")
        lowerName.endsWith(".xlsx") || lowerName.endsWith(".xls") || docType == DocumentType.XLSX -> Pair(R.drawable.ic_file_sheet_new, "Excel Spreadsheet")
        lowerName.endsWith(".pptx") || lowerName.endsWith(".ppt") || docType == DocumentType.PPTX -> Pair(R.drawable.ic_file_ppt_new, "PowerPoint Presentation")
        lowerName.endsWith(".txt") || docType == DocumentType.TXT -> Pair(R.drawable.ic_file_txt_new, "Text Document")
        else -> Pair(R.drawable.ic_file_gen_new, "${docType.name} File")
    }

    val displayPath = remember(uri) {
        try {
            val parsedUri = Uri.parse(uri)
            if (parsedUri.scheme == "file") parsedUri.path ?: uri
            else Uri.decode(uri)
        } catch (_: Exception) {
            uri
        }
    }

    LaunchedEffect(isCopied) {
        if (isCopied) {
            delay(1800)
            isCopied = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = NexusTheme.colors.surface,
        contentColor = NexusTheme.colors.textPrimary,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 12.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(NexusTheme.colors.divider.copy(alpha = 0.8f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                NexusText(
                    text = "File Details",
                    style = NexusTheme.typography.title.copy(
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.3).sp
                    ),
                    color = NexusTheme.colors.textPrimary
                )

                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(NexusTheme.colors.surfaceVariant.copy(alpha = 0.85f))
                        .border(1.dp, NexusTheme.colors.divider.copy(alpha = 0.5f), CircleShape)
                        .springBounceClick(onClick = onDismissRequest),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_close),
                        contentDescription = "Close",
                        modifier = Modifier.size(15.dp),
                        colorFilter = ColorFilter.tint(NexusTheme.colors.textPrimary)
                    )
                }
            }

            // File Identity Card
            Row(
                modifier = Modifier
                    .fadeSlideIn(delay = 50)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(NexusTheme.colors.surfaceVariant.copy(alpha = 0.45f))
                    .border(1.dp, NexusTheme.colors.divider.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(NexusTheme.colors.surfaceVariant.copy(alpha = 0.9f))
                        .border(1.dp, NexusTheme.colors.divider.copy(alpha = 0.5f), RoundedCornerShape(11.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = formatIconRes),
                        contentDescription = formatLabel,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(13.dp))

                Column(modifier = Modifier.weight(1f)) {
                    NexusText(
                        text = fileName,
                        style = NexusTheme.typography.body.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = NexusTheme.colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    val sizeString = if (fileSizeBytes > 0) formatCoreFileSize(fileSizeBytes) else null
                    val subtitle = if (sizeString != null) "$formatLabel · $sizeString" else formatLabel
                    NexusText(
                        text = subtitle,
                        style = NexusTheme.typography.caption.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = NexusTheme.colors.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Detail Rows Card
            val detailsList = remember(fileName, fileSizeBytes, lastOpenedAt, docType, customDetails) {
                val list = mutableListOf<FileDetailItem>()
                if (lastOpenedAt > 0L) {
                    val dateFormatted = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(lastOpenedAt))
                    list.add(FileDetailItem(R.drawable.ic_startup, "Last modified", dateFormatted))
                }
                list.add(FileDetailItem(R.drawable.ic_book, "File type", if (docType != DocumentType.UNKNOWN) docType.name else formatLabel))
                if (fileSizeBytes > 0L) {
                    list.add(FileDetailItem(R.drawable.ic_maximize, "File size", formatCoreFileSize(fileSizeBytes)))
                }
                list.addAll(customDetails)
                list
            }

            if (detailsList.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fadeSlideIn(delay = 100)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(NexusTheme.colors.surfaceVariant.copy(alpha = 0.35f))
                        .border(1.dp, NexusTheme.colors.divider.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
                ) {
                    detailsList.forEachIndexed { index, item ->
                        if (index > 0) {
                            HorizontalDivider(
                                color = NexusTheme.colors.divider.copy(alpha = 0.45f),
                                thickness = 0.8.dp
                            )
                        }
                        DetailRow(
                            iconRes = item.iconRes,
                            label = item.label,
                            value = item.value
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Location Card with Copy Button
            if (displayPath.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fadeSlideIn(delay = 150)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(NexusTheme.colors.surfaceVariant.copy(alpha = 0.45f))
                        .border(1.dp, NexusTheme.colors.divider.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_folder),
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                colorFilter = ColorFilter.tint(NexusTheme.colors.textSecondary)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            NexusText(
                                text = "LOCATION",
                                style = NexusTheme.typography.caption.copy(
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.4.sp
                                ),
                                color = NexusTheme.colors.textSecondary
                            )
                        }

                        // Copy Button
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (isCopied) NexusTheme.colors.primary
                                    else NexusTheme.colors.surface
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isCopied) NexusTheme.colors.primary
                                    else NexusTheme.colors.divider.copy(alpha = 0.7f),
                                    shape = RoundedCornerShape(50)
                                )
                                .springBounceClick {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("File Path", displayPath)
                                    clipboard.setPrimaryClip(clip)
                                    isCopied = true
                                    Toast.makeText(context, "Path copied to clipboard", Toast.LENGTH_SHORT).show()
                                }
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Image(
                                    painter = painterResource(id = if (isCopied) R.drawable.ic_check else R.drawable.ic_save),
                                    contentDescription = null,
                                    modifier = Modifier.size(11.dp),
                                    colorFilter = ColorFilter.tint(
                                        if (isCopied) NexusTheme.colors.onPrimary else NexusTheme.colors.textPrimary
                                    )
                                )
                                NexusText(
                                    text = if (isCopied) "Copied" else "Copy",
                                    style = NexusTheme.typography.caption.copy(
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = if (isCopied) NexusTheme.colors.onPrimary else NexusTheme.colors.textPrimary
                                )
                            }
                        }
                    }

                    // Path Text
                    NexusText(
                        text = displayPath,
                        style = NexusTheme.typography.caption.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.5.sp,
                            lineHeight = 16.5.sp
                        ),
                        color = NexusTheme.colors.textSecondary,
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))
            }

            // Footer: Close Button
            Box(
                modifier = Modifier
                    .fadeSlideIn(delay = 200)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50))
                    .background(NexusTheme.colors.primary)
                    .springBounceClick(onClick = onDismissRequest)
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                NexusText(
                    text = "Close",
                    style = NexusTheme.typography.body.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.5.sp
                    ),
                    color = NexusTheme.colors.onPrimary
                )
            }
        }
    }
}

@Composable
private fun DetailRow(
    iconRes: Int,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f, fill = false),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(NexusTheme.colors.surfaceVariant.copy(alpha = 0.8f)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    colorFilter = ColorFilter.tint(NexusTheme.colors.textPrimary)
                )
            }

            Spacer(modifier = Modifier.width(11.dp))

            NexusText(
                text = label,
                style = NexusTheme.typography.body.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = NexusTheme.colors.textSecondary
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        NexusText(
            text = value,
            style = NexusTheme.typography.body.copy(
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold
            ),
            color = NexusTheme.colors.textPrimary
        )
    }
}

private fun formatCoreFileSize(bytes: Long): String {
    if (bytes <= 0L) return "—"
    return when {
        bytes < 1_024          -> "$bytes B"
        bytes < 1_048_576      -> "${"%.1f".format(bytes / 1_024f)} KB"
        bytes < 1_073_741_824  -> "${"%.1f".format(bytes / 1_048_576f)} MB"
        else                   -> "${"%.2f".format(bytes / 1_073_741_824f)} GB"
    }
}
