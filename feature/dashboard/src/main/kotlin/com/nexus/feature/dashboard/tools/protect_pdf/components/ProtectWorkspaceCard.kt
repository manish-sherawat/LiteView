package com.nexus.feature.dashboard.tools.protect_pdf.components

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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusText
import com.nexus.core.ui.animations.springBounceClick
import com.nexus.feature.dashboard.tools.protect_pdf.*

/**
 * Enhanced workspace card with featured cover showcase card (.file-card),
 * live password strength evaluation, permissions toggles, and secure AES protection.
 */
@Composable
fun ProtectWorkspaceCard(
    docInfo: ProtectPdfDocInfo,
    thumbnail: Bitmap?,
    passwordText: String,
    onPasswordChange: (String) -> Unit,
    confirmPasswordText: String,
    onConfirmPasswordChange: (String) -> Unit,
    strength: PasswordStrength,
    permissions: ProtectPdfPermissions,
    onPermissionsChange: (ProtectPdfPermissions) -> Unit,
    onGeneratePassword: () -> Unit,
    onCopyPassword: () -> Unit,
    onBackToStart: () -> Unit,
    onExport: () -> Unit,
    isExporting: Boolean,
    errorMessage: String?,
    modifier: Modifier = Modifier
) {
    val borderColor = Color(0xFFEBEBEB)
    val textPrimary = NexusTheme.colors.textPrimary
    val mutedColor = Color(0xFF707070)
    val labelColor = Color(0xFF707070)
    val inputBg = Color(0xFFFAFAFA)
    val inputBorder = Color(0xFFEBEBEB)

    var showPassword by remember { mutableStateOf(false) }

    val canExport = passwordText.isNotBlank() && !isExporting

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
            text = "Protect PDF",
            style = NexusTheme.typography.title.copy(
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp
            ),
            color = textPrimary
        )

        Spacer(modifier = Modifier.height(2.dp))

        NexusText(
            text = "Set a password to open the file",
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
                // Vertical Stack on narrow screens
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ThumbnailBox(thumbnail = thumbnail, isNarrow = true)

                    Spacer(modifier = Modifier.height(18.dp))

                    FileInfoContent(
                        docInfo = docInfo,
                        onBackToStart = onBackToStart,
                        isCentered = true
                    )
                }
            } else {
                // Horizontal Row on tablets and wide screens
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    ThumbnailBox(thumbnail = thumbnail, isNarrow = false)

                    Box(modifier = Modifier.weight(1f)) {
                        FileInfoContent(
                            docInfo = docInfo,
                            onBackToStart = onBackToStart,
                            isCentered = false
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── Password Section ─────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NexusText(
                text = "PASSWORD",
                style = NexusTheme.typography.caption.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                ),
                color = labelColor
            )

            // "Generate strong password" Pill Button
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(1.2.dp, Color(0xFFCFCFCF), CircleShape)
                    .clickable { onGeneratePassword() }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = textPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    NexusText(
                        text = "Generate strong password",
                        style = NexusTheme.typography.caption.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = textPrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Password input field
        OutlinedTextField(
            value = passwordText,
            onValueChange = onPasswordChange,
            placeholder = {
                NexusText(
                    text = "Enter a password",
                    style = NexusTheme.typography.body.copy(fontSize = 14.sp, color = Color(0xFFA3A3A3))
                )
            },
            singleLine = true,
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        imageVector = if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = if (showPassword) "Hide password" else "Show password",
                        tint = mutedColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = inputBg,
                focusedBorderColor = textPrimary,
                unfocusedBorderColor = inputBorder
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 4-Segment Strength Meter Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            for (i in 1..4) {
                val isFilled = strength.score >= i
                val segmentColor = if (isFilled) strength.color else Color(0xFFEBEBEB)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(segmentColor)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Strength label text
        if (strength != PasswordStrength.EMPTY) {
            val strengthAnnotated = buildAnnotatedString {
                append("Strength: ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = strength.color)) {
                    append(strength.label)
                }
            }
            NexusText(
                text = strengthAnnotated.text,
                style = NexusTheme.typography.caption.copy(fontSize = 12.sp),
                color = mutedColor
            )
        } else {
            NexusText(
                text = "Use 8 or more characters with a mix of letters, numbers and symbols.",
                style = NexusTheme.typography.caption.copy(fontSize = 11.5.sp),
                color = mutedColor
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // ── Confirm Password Section ─────────────────────────────────────────
        NexusText(
            text = "CONFIRM PASSWORD",
            style = NexusTheme.typography.caption.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            ),
            color = labelColor
        )

        Spacer(modifier = Modifier.height(6.dp))

        OutlinedTextField(
            value = confirmPasswordText,
            onValueChange = onConfirmPasswordChange,
            placeholder = {
                NexusText(
                    text = "Re-enter the password",
                    style = NexusTheme.typography.body.copy(fontSize = 14.sp, color = Color(0xFFA3A3A3))
                )
            },
            singleLine = true,
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = inputBg,
                focusedBorderColor = textPrimary,
                unfocusedBorderColor = inputBorder
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        // "Copy password" Link
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            NexusText(
                text = "Copy password",
                style = NexusTheme.typography.caption.copy(
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = textPrimary,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onCopyPassword() }
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            )
        }

        Spacer(modifier = Modifier.height(18.dp))
        HorizontalDivider(color = borderColor)
        Spacer(modifier = Modifier.height(18.dp))

        // ── Permissions After Opening Section ────────────────────────────────
        NexusText(
            text = "PERMISSIONS AFTER OPENING",
            style = NexusTheme.typography.caption.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            ),
            color = labelColor
        )

        Spacer(modifier = Modifier.height(10.dp))

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Permission 1: Printing
            PermissionRow(
                title = "Allow printing",
                subtitle = "Viewers can print the document",
                isChecked = permissions.allowPrint,
                onCheckedChange = { onPermissionsChange(permissions.copy(allowPrint = it)) }
            )

            // Permission 2: Copying
            PermissionRow(
                title = "Allow copying text and images",
                subtitle = "Viewers can select and copy content",
                isChecked = permissions.allowCopy,
                onCheckedChange = { onPermissionsChange(permissions.copy(allowCopy = it)) }
            )

            // Permission 3: Editing
            PermissionRow(
                title = "Allow editing and annotating",
                subtitle = "Viewers can change pages, comment and fill forms",
                isChecked = permissions.allowEdit,
                onCheckedChange = { onPermissionsChange(permissions.copy(allowEdit = it)) }
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // ── Notice Box ───────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFFF5F5F5))
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = Icons.Rounded.Info,
                    contentDescription = null,
                    tint = Color(0xFF0A0A0A),
                    modifier = Modifier
                        .size(18.dp)
                        .padding(top = 2.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                NexusText(
                    text = "If you forget this password it cannot be recovered, so keep a copy somewhere safe. Permission limits are honoured by most PDF apps but are weaker than the password itself.",
                    style = NexusTheme.typography.caption.copy(
                        fontSize = 12.sp,
                        color = mutedColor,
                        lineHeight = 17.sp
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ── Primary Action Button ────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (canExport) Color(0xFF0A0A0A) else Color(0xFF0A0A0A).copy(alpha = 0.35f))
                .springBounceClick(enabled = canExport, onClick = onExport),
            contentAlignment = Alignment.Center
        ) {
            if (isExporting) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color.White,
                        strokeWidth = 2.5.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    NexusText(
                        text = "Protecting…",
                        style = NexusTheme.typography.buttonLabel.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color.White
                    )
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    NexusText(
                        text = "Protect and download",
                        style = NexusTheme.typography.buttonLabel.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color.White
                    )
                }
            }
        }

        // Error message
        AnimatedVisibility(
            visible = !errorMessage.isNullOrBlank(),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            errorMessage?.let {
                Spacer(modifier = Modifier.height(10.dp))
                NexusText(
                    text = it,
                    style = NexusTheme.typography.caption.copy(
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFDC2626)
                    )
                )
            }
        }
    }
}

/**
 * Featured Document Thumbnail Viewport (.thumb).
 */
@Composable
private fun ThumbnailBox(thumbnail: Bitmap?, isNarrow: Boolean) {
    val thumbWidth = if (isNarrow) 170.dp else 150.dp
    val thumbHeight = if (isNarrow) 226.dp else 200.dp

    Box(
        modifier = Modifier
            .size(width = thumbWidth, height = thumbHeight)
            .shadow(16.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .border(1.dp, Color(0xFFCFCFCF), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (thumbnail != null) {
            Image(
                bitmap = thumbnail.asImageBitmap(),
                contentDescription = "PDF Thumbnail",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = null,
                tint = Color(0xFFA3A3A3),
                modifier = Modifier.size(36.dp)
            )
        }
    }
}

/**
 * File info section with badge chip, bold title, status, and Change file button.
 */
@Composable
private fun FileInfoContent(
    docInfo: ProtectPdfDocInfo,
    onBackToStart: () -> Unit,
    isCentered: Boolean
) {
    val align = if (isCentered) Alignment.CenterHorizontally else Alignment.Start
    val textAlign = if (isCentered) TextAlign.Center else TextAlign.Start

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = align
    ) {
        // PDF Chip Pill
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(Color(0xFF0A0A0A))
                .padding(horizontal = 10.dp, vertical = 2.dp)
        ) {
            NexusText(
                text = "PDF",
                style = NexusTheme.typography.caption.copy(
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.6.sp
                ),
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // File Title
        NexusText(
            text = docInfo.name,
            style = NexusTheme.typography.title.copy(
                fontSize = 17.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp
            ),
            color = NexusTheme.colors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = textAlign
        )

        Spacer(modifier = Modifier.height(2.dp))

        // File Meta
        val pageWord = if (docInfo.pageCount == 1) "page" else "pages"
        val metaStr = "${docInfo.pageCount} $pageWord · ${ProtectPdfEngine.formatBytes(docInfo.sizeBytes)}"
        NexusText(
            text = metaStr,
            style = NexusTheme.typography.body.copy(
                fontSize = 13.5.sp,
                color = Color(0xFF707070)
            ),
            textAlign = textAlign
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Status: "Ready to be locked"
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (isCentered) Arrangement.Center else Arrangement.Start
        ) {
            Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = null,
                tint = NexusTheme.colors.textPrimary,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            NexusText(
                text = "Ready to be locked",
                style = NexusTheme.typography.caption.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = NexusTheme.colors.textPrimary
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // "Change file" Button
        Box(
            modifier = Modifier
                .height(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White)
                .border(1.2.dp, Color(0xFFCFCFCF), RoundedCornerShape(12.dp))
                .clickable { onBackToStart() }
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = null,
                    tint = NexusTheme.colors.textPrimary,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                NexusText(
                    text = "Change file",
                    style = NexusTheme.typography.buttonLabel.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = NexusTheme.colors.textPrimary
                )
            }
        }
    }
}

/**
 * Interactive permission item row with checkbox.
 */
@Composable
private fun PermissionRow(
    title: String,
    subtitle: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isChecked) Color(0xFFFAFAFA) else Color.White)
            .border(
                1.2.dp,
                if (isChecked) Color(0xFF0A0A0A) else Color(0xFFEBEBEB),
                RoundedCornerShape(12.dp)
            )
            .clickable { onCheckedChange(!isChecked) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(
                checkedColor = Color(0xFF0A0A0A),
                checkmarkColor = Color.White
            )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            NexusText(
                text = title,
                style = NexusTheme.typography.body.copy(
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = NexusTheme.colors.textPrimary
            )
            NexusText(
                text = subtitle,
                style = NexusTheme.typography.caption.copy(
                    fontSize = 11.5.sp,
                    color = Color(0xFF707070)
                )
            )
        }
    }
}
