package com.nexus.feature.dashboard.tools.repair_pdf.components

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusText
import com.nexus.core.ui.animations.springBounceClick
import com.nexus.feature.dashboard.tools.repair_pdf.*

/**
 * Workspace card for Repair PDF.
 * Features preview showcase (.file-card), health status, repair options checkboxes,
 * execution button with progress bar, and comprehensive repair result report.
 */
@Composable
fun RepairWorkspaceCard(
    docInfo: RepairPdfDocInfo,
    thumbnail: Bitmap?,
    options: RepairPdfOptions,
    onOptionsChange: (RepairPdfOptions) -> Unit,
    isRepairing: Boolean,
    repairProgress: Float,
    statusMessage: String?,
    result: RepairPdfResult?,
    onRepairClick: () -> Unit,
    onChangeFile: () -> Unit,
    onDownloadClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = Color(0xFFEBEBEB)
    val textPrimary = NexusTheme.colors.textPrimary
    val mutedColor = Color(0xFF707070)
    val labelColor = Color(0xFF707070)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .background(NexusTheme.colors.surface)
            .border(1.dp, borderColor, RoundedCornerShape(24.dp))
            .padding(24.dp)
    ) {
        // ── Card Header ──────────────────────────────────────────────────────
        NexusText(
            text = "Repair PDF",
            style = NexusTheme.typography.title.copy(
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp
            ),
            color = textPrimary
        )

        Spacer(modifier = Modifier.height(2.dp))

        NexusText(
            text = "We rebuild the file structure and recover every page we can",
            style = NexusTheme.typography.body.copy(
                fontSize = 13.5.sp,
                color = mutedColor
            )
        )

        Spacer(modifier = Modifier.height(20.dp))

        // ── Featured File Showcase Card (.file-card) ─────────────────────────
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFFFAFAFA))
                .border(1.dp, borderColor, RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            val isNarrow = maxWidth < 480.dp

            if (isNarrow) {
                // Vertical Stack for narrow mobile screens
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ThumbnailContainer(thumbnail = thumbnail)

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        FileCardDetails(
                            docInfo = docInfo,
                            isCentered = true,
                            onChangeFile = onChangeFile
                        )
                    }
                }
            } else {
                // Horizontal Row for tablets and wide screens
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    ThumbnailContainer(thumbnail = thumbnail)

                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.Start,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        FileCardDetails(
                            docInfo = docInfo,
                            isCentered = false,
                            onChangeFile = onChangeFile
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── Repair Options Header ────────────────────────────────────────────
        NexusText(
            text = "REPAIR OPTIONS",
            style = NexusTheme.typography.caption.copy(
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            ),
            color = labelColor
        )

        Spacer(modifier = Modifier.height(12.dp))

        // ── Option 1: Rebuild unreadable pages as images ─────────────────────
        RepairOptionRow(
            title = "Rebuild unreadable pages as images",
            subtitle = "If a page can’t be copied, it is drawn as a picture so it isn’t lost. Its text won’t be selectable.",
            checked = options.rebuildAsImages,
            onCheckedChange = { onOptionsChange(options.copy(rebuildAsImages = it)) }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // ── Option 2: Optimize the repaired file ─────────────────────────────
        RepairOptionRow(
            title = "Optimize the repaired file",
            subtitle = "Compress the file structure for a smaller, cleaner PDF",
            checked = options.optimizeFile,
            onCheckedChange = { onOptionsChange(options.copy(optimizeFile = it)) }
        )

        Spacer(modifier = Modifier.height(24.dp))

        // ── Action Button: Repair PDF ────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .springBounceClick(
                    enabled = !isRepairing,
                    onClick = onRepairClick
                )
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (!isRepairing) Color(0xFF0A0A0A) else Color(0xFF555555)
                ),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (isRepairing) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Build,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                NexusText(
                    text = when {
                        isRepairing -> "Repairing…"
                        result != null -> "Repair again"
                        else -> "Repair PDF"
                    },
                    style = NexusTheme.typography.title.copy(
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color.White
                )
            }
        }

        // ── Progress Bar ─────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = isRepairing,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Column(modifier = Modifier.padding(top = 14.dp)) {
                LinearProgressIndicator(
                    progress = { repairProgress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(6.dp)),
                    color = Color(0xFF0A0A0A),
                    trackColor = Color(0xFFEBEBEB)
                )
            }
        }

        // ── Status / Error Message ───────────────────────────────────────────
        AnimatedVisibility(
            visible = !statusMessage.isNullOrBlank(),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            NexusText(
                text = statusMessage.orEmpty(),
                style = NexusTheme.typography.bodySmall.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = if (statusMessage?.contains("password", ignoreCase = true) == true ||
                    statusMessage?.contains("error", ignoreCase = true) == true ||
                    statusMessage?.contains("damaged", ignoreCase = true) == true
                ) {
                    Color(0xFFE5484D)
                } else {
                    mutedColor
                },
                modifier = Modifier.padding(top = 12.dp)
            )
        }

        // ── Result Section (.result) ─────────────────────────────────────────
        AnimatedVisibility(
            visible = result != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            if (result != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp)
                ) {
                    // Divider
                    HorizontalDivider(
                        color = Color(0xFFEBEBEB),
                        thickness = 1.dp
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Result Header
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    if (result.unrecoverable > 0) Color(0xFFC77D00) else Color(0xFF17A34A)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (result.unrecoverable > 0) Icons.Rounded.Warning else Icons.Rounded.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Column {
                            NexusText(
                                text = if (result.unrecoverable > 0) "Partly repaired" else "Repair complete",
                                style = NexusTheme.typography.title.copy(
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = (-0.3).sp
                                ),
                                color = textPrimary
                            )
                            val recoveredCount = result.recoveredIntact + result.rebuiltAsImages
                            val totalText = "${recoveredCount} of ${result.totalFound} page${if (result.totalFound == 1) "" else "s"} recovered · ${RepairPdfEngine.formatBytes(result.outputFileSizeBytes)}"
                            NexusText(
                                text = totalText,
                                style = NexusTheme.typography.bodySmall.copy(
                                    fontSize = 13.sp,
                                    color = mutedColor
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // 4 Stat Cards
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val isNarrowStats = maxWidth < 460.dp

                        if (isNarrowStats) {
                            // 2x2 Grid
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    StatCard(
                                        number = result.totalFound.toString(),
                                        label = "Pages found",
                                        numberColor = textPrimary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    StatCard(
                                        number = result.recoveredIntact.toString(),
                                        label = "Recovered intact",
                                        numberColor = Color(0xFF17A34A),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    StatCard(
                                        number = result.rebuiltAsImages.toString(),
                                        label = "Rebuilt as images",
                                        numberColor = Color(0xFFC77D00),
                                        modifier = Modifier.weight(1f)
                                    )
                                    StatCard(
                                        number = result.unrecoverable.toString(),
                                        label = "Unrecoverable",
                                        numberColor = Color(0xFFE5484D),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        } else {
                            // 4 across
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                StatCard(
                                    number = result.totalFound.toString(),
                                    label = "Pages found",
                                    numberColor = textPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                                StatCard(
                                    number = result.recoveredIntact.toString(),
                                    label = "Recovered intact",
                                    numberColor = Color(0xFF17A34A),
                                    modifier = Modifier.weight(1f)
                                )
                                StatCard(
                                    number = result.rebuiltAsImages.toString(),
                                    label = "Rebuilt as images",
                                    numberColor = Color(0xFFC77D00),
                                    modifier = Modifier.weight(1f)
                                )
                                StatCard(
                                    number = result.unrecoverable.toString(),
                                    label = "Unrecoverable",
                                    numberColor = Color(0xFFE5484D),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Notes list
                    if (result.notes.isNotEmpty()) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            result.notes.forEach { note ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFFF5F5F5))
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    NexusText(
                                        text = "•",
                                        style = NexusTheme.typography.bodySmall.copy(
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        ),
                                        color = textPrimary
                                    )
                                    NexusText(
                                        text = note,
                                        style = NexusTheme.typography.bodySmall.copy(
                                            fontSize = 13.sp,
                                            lineHeight = 18.sp
                                        ),
                                        color = mutedColor
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))
                    }

                    // Download Repaired PDF Button
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .springBounceClick(onClick = onDownloadClick)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF0A0A0A)),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Download,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            NexusText(
                                text = "Download repaired PDF",
                                style = NexusTheme.typography.buttonLabel.copy(
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Thumbnail container with subtle border and drop shadow.
 */
@Composable
private fun ThumbnailContainer(thumbnail: Bitmap?) {
    Box(
        modifier = Modifier
            .size(width = 136.dp, height = 182.dp)
            .shadow(6.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .border(1.dp, Color(0xFFCFCFCF), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (thumbnail != null) {
            Image(
                bitmap = thumbnail.asImageBitmap(),
                contentDescription = "Document first page preview",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.BrokenImage,
                    contentDescription = null,
                    tint = Color(0xFFA3A3A3),
                    modifier = Modifier.size(36.dp)
                )
                NexusText(
                    text = "No preview",
                    style = NexusTheme.typography.caption.copy(
                        fontSize = 11.sp,
                        color = Color(0xFFA3A3A3)
                    )
                )
            }
        }
    }
}

/**
 * File card metadata and action buttons.
 */
@Composable
private fun FileCardDetails(
    docInfo: RepairPdfDocInfo,
    isCentered: Boolean,
    onChangeFile: () -> Unit
) {
    val textPrimary = NexusTheme.colors.textPrimary
    val mutedColor = Color(0xFF707070)

    // "PDF" Chip Badge
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0xFF0A0A0A))
            .padding(horizontal = 9.dp, vertical = 2.dp)
    ) {
        NexusText(
            text = "PDF",
            style = NexusTheme.typography.caption.copy(
                fontSize = 10.5.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.5.sp
            ),
            color = Color.White
        )
    }

    Spacer(modifier = Modifier.height(2.dp))

    // File name
    NexusText(
        text = docInfo.name,
        style = NexusTheme.typography.title.copy(
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.3).sp
        ),
        color = textPrimary,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        textAlign = if (isCentered) TextAlign.Center else TextAlign.Start
    )

    // File metadata (pages + size)
    val pageCountText = docInfo.pageCount?.let { "$it page${if (it == 1) "" else "s"} · " }.orEmpty()
    val sizeText = RepairPdfEngine.formatBytes(docInfo.sizeBytes)
    NexusText(
        text = "$pageCountText$sizeText",
        style = NexusTheme.typography.bodySmall.copy(
            fontSize = 13.sp,
            color = mutedColor
        ),
        textAlign = if (isCentered) TextAlign.Center else TextAlign.Start
    )

    Spacer(modifier = Modifier.height(4.dp))

    // Health status row
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = if (docInfo.healthStatus.isWarning) Icons.Rounded.Warning else Icons.Rounded.CheckCircle,
            contentDescription = null,
            tint = docInfo.healthStatus.indicatorColor,
            modifier = Modifier.size(15.dp)
        )
        NexusText(
            text = docInfo.healthStatus.description,
            style = NexusTheme.typography.bodySmall.copy(
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = docInfo.healthStatus.indicatorColor
        )
    }

    Spacer(modifier = Modifier.height(6.dp))

    // Change file button
    Box(
        modifier = Modifier
            .springBounceClick(onClick = onChangeFile)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .border(1.dp, Color(0xFFCFCFCF), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = null,
                tint = textPrimary,
                modifier = Modifier.size(14.dp)
            )
            NexusText(
                text = "Change file",
                style = NexusTheme.typography.caption.copy(
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = textPrimary
            )
        }
    }
}

/**
 * Checkbox option card with custom styling.
 */
@Composable
private fun RepairOptionRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val borderColor = if (checked) Color(0xFF0A0A0A) else Color(0xFFEBEBEB)
    val bgColor = if (checked) Color(0xFFFAFAFA) else Color.White

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .border(1.5.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(
                checkedColor = Color(0xFF0A0A0A),
                uncheckedColor = Color(0xFFCFCFCF),
                checkmarkColor = Color.White
            ),
            modifier = Modifier.size(20.dp)
        )

        Column(modifier = Modifier.weight(1f)) {
            NexusText(
                text = title,
                style = NexusTheme.typography.body.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = NexusTheme.colors.textPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            NexusText(
                text = subtitle,
                style = NexusTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    color = Color(0xFF707070),
                    lineHeight = 16.sp
                )
            )
        }
    }
}

/**
 * Stat display card for repair results.
 */
@Composable
private fun StatCard(
    number: String,
    label: String,
    numberColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFFAFAFA))
            .border(1.5.dp, Color(0xFFEBEBEB), RoundedCornerShape(14.dp))
            .padding(vertical = 12.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        NexusText(
            text = number,
            style = NexusTheme.typography.titleLarge.copy(
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp
            ),
            color = numberColor
        )
        Spacer(modifier = Modifier.height(2.dp))
        NexusText(
            text = label,
            style = NexusTheme.typography.caption.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            ),
            color = Color(0xFF707070)
        )
    }
}

