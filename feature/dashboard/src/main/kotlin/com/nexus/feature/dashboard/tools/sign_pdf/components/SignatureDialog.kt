package com.nexus.feature.dashboard.tools.sign_pdf.components

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint as AndroidPaint
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusText
import com.nexus.core.ui.animations.springBounceClick
import com.nexus.feature.dashboard.tools.sign_pdf.PenWeight
import com.nexus.feature.dashboard.tools.sign_pdf.ScriptFont
import com.nexus.feature.dashboard.tools.sign_pdf.SignPdfEngine
import com.nexus.feature.dashboard.tools.sign_pdf.SignatureInputMode
import com.nexus.feature.dashboard.tools.sign_pdf.SignatureItem
import kotlinx.coroutines.launch

// Data class to store recorded stroke segments for smooth vector rendering on Compose Canvas
private data class DrawnStroke(
    val points: List<Offset>,
    val strokeWidth: Float
)

@Composable
fun SignatureDialog(
    isOpen: Boolean,
    savedSignatures: List<SignatureItem>,
    onDismiss: () -> Unit,
    onUseExistingSignature: (SignatureItem) -> Unit,
    onConfirmNewSignature: (Bitmap) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isOpen) return

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var activeMode by remember { mutableStateOf(SignatureInputMode.DRAW) }
    var penWeight by remember { mutableStateOf(PenWeight.MEDIUM) }

    // ── Drawing Pad State ───────────────────────────────────────────────────
    val strokes = remember { mutableStateListOf<DrawnStroke>() }
    var currentPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }

    // ── Type Mode State ─────────────────────────────────────────────────────
    var typedName by remember { mutableStateOf("") }
    var selectedFont by remember { mutableStateOf(ScriptFont.DANCING_SCRIPT) }

    // ── Upload Mode State ───────────────────────────────────────────────────
    var uploadedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var makeTransparent by remember { mutableStateOf(true) }

    // Error message inside dialog
    var dialogError by remember { mutableStateOf<String?>(null) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                val decoded = SignPdfEngine.decodeImageFromUri(context, uri)
                if (decoded != null) {
                    uploadedBitmap = decoded
                    dialogError = null
                } else {
                    dialogError = "That image couldn't be opened."
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth(0.94f)
                .wrapContentHeight()
                .padding(vertical = 20.dp),
            shape = RoundedCornerShape(24.dp),
            color = NexusTheme.colors.surface,
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // ── Dialog Header ───────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NexusText(
                        text = "Add signature",
                        style = NexusTheme.typography.titleLarge.copy(
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.4).sp
                        ),
                        color = NexusTheme.colors.textPrimary
                    )

                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .border(1.5.dp, NexusTheme.colors.divider, CircleShape)
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close",
                            modifier = Modifier.size(17.dp),
                            tint = NexusTheme.colors.textPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ── Saved Signatures Chips Row (if any) ─────────────────────
                if (savedSignatures.isNotEmpty()) {
                    NexusText(
                        text = "USE A SAVED SIGNATURE",
                        style = NexusTheme.typography.label.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = NexusTheme.colors.textSecondary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(savedSignatures) { sig ->
                            Box(
                                modifier = Modifier
                                    .size(width = 110.dp, height = 58.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(NexusTheme.colors.surfaceVariant)
                                    .border(1.5.dp, NexusTheme.colors.divider, RoundedCornerShape(12.dp))
                                    .clickable {
                                        onUseExistingSignature(sig)
                                    }
                                    .padding(6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    bitmap = sig.bitmap.asImageBitmap(),
                                    contentDescription = "Saved signature",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                // ── Segmented Mode Tabs (Draw | Type | Upload) ───────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(NexusTheme.colors.surfaceVariant.copy(alpha = 0.5f))
                        .border(1.dp, NexusTheme.colors.divider.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    SignatureInputMode.values().forEach { mode ->
                        val isSelected = mode == activeMode
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (isSelected) NexusTheme.colors.primary else Color.Transparent)
                                .clickable {
                                    activeMode = mode
                                    dialogError = null
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            NexusText(
                                text = mode.name.lowercase().replaceFirstChar { it.uppercase() },
                                style = NexusTheme.typography.buttonLabel.copy(
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = if (isSelected) NexusTheme.colors.onPrimary else NexusTheme.colors.textSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ── Body content by mode ─────────────────────────────────────
                when (activeMode) {
                    SignatureInputMode.DRAW -> {
                        DrawSignatureTab(
                            penWeight = penWeight,
                            onPenWeightChange = { penWeight = it },
                            strokes = strokes,
                            currentPoints = currentPoints,
                            onPointsChange = { currentPoints = it },
                            onStrokeFinished = { stroke -> strokes.add(stroke) },
                            onClear = {
                                strokes.clear()
                                currentPoints = emptyList()
                            }
                        )
                    }

                    SignatureInputMode.TYPE -> {
                        TypeSignatureTab(
                            typedText = typedName,
                            onTextChange = { typedName = it },
                            selectedFont = selectedFont,
                            onFontSelect = { selectedFont = it }
                        )
                    }

                    SignatureInputMode.UPLOAD -> {
                        UploadSignatureTab(
                            uploadedBitmap = uploadedBitmap,
                            makeTransparent = makeTransparent,
                            onMakeTransparentChange = { makeTransparent = it },
                            onPickImage = { imagePickerLauncher.launch("image/*") }
                        )
                    }
                }

                if (!dialogError.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    NexusText(
                        text = dialogError ?: "",
                        style = NexusTheme.typography.caption.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = NexusTheme.colors.error
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ── Footer Actions ──────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Cancel Ghost Button
                    Box(
                        modifier = Modifier
                            .height(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.2.dp, NexusTheme.colors.divider, RoundedCornerShape(12.dp))
                            .clickable { onDismiss() }
                            .padding(horizontal = 18.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        NexusText(
                            text = "Cancel",
                            style = NexusTheme.typography.buttonLabel.copy(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = NexusTheme.colors.textPrimary
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // Use Signature Primary Button
                    Box(
                        modifier = Modifier
                            .height(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(NexusTheme.colors.primary)
                            .springBounceClick {
                                when (activeMode) {
                                    SignatureInputMode.DRAW -> {
                                        if (strokes.isEmpty() && currentPoints.isEmpty()) {
                                            dialogError = "Draw your signature first."
                                            return@springBounceClick
                                        }

                                        // Render drawn strokes to Bitmap (700 x 260)
                                        val canvasW = 700
                                        val canvasH = 260
                                        val rawBitmap = Bitmap.createBitmap(canvasW, canvasH, Bitmap.Config.ARGB_8888)
                                        val nativeCanvas = AndroidCanvas(rawBitmap)
                                        val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                                            color = AndroidColor.BLACK
                                            style = AndroidPaint.Style.STROKE
                                            strokeCap = AndroidPaint.Cap.ROUND
                                            strokeJoin = AndroidPaint.Join.ROUND
                                        }

                                        // Replay all strokes
                                        val allStrokes = strokes + if (currentPoints.isNotEmpty()) listOf(DrawnStroke(currentPoints, penWeight.strokeWidth)) else emptyList()
                                        for (s in allStrokes) {
                                            if (s.points.size < 2) continue
                                            paint.strokeWidth = s.strokeWidth * 2f
                                            val path = android.graphics.Path().apply {
                                                moveTo(s.points[0].x, s.points[0].y)
                                                for (i in 1 until s.points.size) {
                                                    lineTo(s.points[i].x, s.points[i].y)
                                                }
                                            }
                                            nativeCanvas.drawPath(path, paint)
                                        }

                                        val trimmed = SignPdfEngine.trimBitmap(rawBitmap)
                                        if (trimmed != null) {
                                            onConfirmNewSignature(trimmed)
                                        } else {
                                            dialogError = "Nothing visible to use. Try again."
                                        }
                                    }

                                    SignatureInputMode.TYPE -> {
                                        val text = typedName.trim()
                                        if (text.isEmpty()) {
                                            dialogError = "Type your name first."
                                            return@springBounceClick
                                        }
                                        val raw = SignPdfEngine.generateTypedSignature(text, selectedFont)
                                        val trimmed = SignPdfEngine.trimBitmap(raw)
                                        if (trimmed != null) {
                                            onConfirmNewSignature(trimmed)
                                        } else {
                                            dialogError = "Nothing visible to use. Try again."
                                        }
                                    }

                                    SignatureInputMode.UPLOAD -> {
                                        val bmp = uploadedBitmap
                                        if (bmp == null) {
                                            dialogError = "Choose an image first."
                                            return@springBounceClick
                                        }
                                        val processed = if (makeTransparent) {
                                            SignPdfEngine.removeWhiteBackground(bmp)
                                        } else {
                                            bmp
                                        }
                                        val trimmed = SignPdfEngine.trimBitmap(processed)
                                        if (trimmed != null) {
                                            onConfirmNewSignature(trimmed)
                                        } else {
                                            dialogError = "Nothing visible to use. Try again."
                                        }
                                    }
                                }
                            }
                            .padding(horizontal = 22.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        NexusText(
                            text = "Use signature",
                            style = NexusTheme.typography.buttonLabel.copy(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = NexusTheme.colors.onPrimary
                        )
                    }
                }
            }
        }
    }
}

// ── Draw Tab Component ──────────────────────────────────────────────────────
@Composable
private fun DrawSignatureTab(
    penWeight: PenWeight,
    onPenWeightChange: (PenWeight) -> Unit,
    strokes: List<DrawnStroke>,
    currentPoints: List<Offset>,
    onPointsChange: (List<Offset>) -> Unit,
    onStrokeFinished: (DrawnStroke) -> Unit,
    onClear: () -> Unit
) {
    val borderColor = Color(0xFFCFCFCF)
    val hasInk = strokes.isNotEmpty() || currentPoints.isNotEmpty()

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(700f / 260f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFFFAFAFA))
                .border(1.5.dp, borderColor, RoundedCornerShape(16.dp))
                .pointerInput(penWeight) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            onPointsChange(listOf(offset))
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            onPointsChange(currentPoints + change.position)
                        },
                        onDragEnd = {
                            if (currentPoints.isNotEmpty()) {
                                onStrokeFinished(DrawnStroke(currentPoints, penWeight.strokeWidth))
                                onPointsChange(emptyList())
                            }
                        },
                        onDragCancel = {
                            onPointsChange(emptyList())
                        }
                    )
                }
        ) {
            // Dashed baseline guide at bottom 26%
            Canvas(modifier = Modifier.fillMaxSize()) {
                val guideY = size.height * 0.74f
                drawLine(
                    color = Color(0xFFCFCFCF),
                    start = Offset(size.width * 0.06f, guideY),
                    end = Offset(size.width * 0.94f, guideY),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                )
            }

            // Placeholder text when empty
            if (!hasInk) {
                NexusText(
                    text = "Sign here with your finger or stylus",
                    style = NexusTheme.typography.body.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = Color(0xFFA3A3A3),
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            // Render existing and current drawn strokes
            Canvas(modifier = Modifier.fillMaxSize()) {
                for (s in strokes) {
                    if (s.points.size < 2) continue
                    val path = Path().apply {
                        moveTo(s.points[0].x, s.points[0].y)
                        for (i in 1 until s.points.size) {
                            lineTo(s.points[i].x, s.points[i].y)
                        }
                    }
                    drawPath(
                        path = path,
                        color = Color(0xFF0A0A0A),
                        style = Stroke(
                            width = s.strokeWidth.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }

                if (currentPoints.size >= 2) {
                    val activePath = Path().apply {
                        moveTo(currentPoints[0].x, currentPoints[0].y)
                        for (i in 1 until currentPoints.size) {
                            lineTo(currentPoints[i].x, currentPoints[i].y)
                        }
                    }
                    drawPath(
                        path = activePath,
                        color = Color(0xFF0A0A0A),
                        style = Stroke(
                            width = penWeight.strokeWidth.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Pen width options & Clear button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Pen size segmented control
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFF5F5F5))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                PenWeight.values().forEach { w ->
                    val isSelected = w == penWeight
                    Box(
                        modifier = Modifier
                            .height(34.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (isSelected) Color(0xFF0A0A0A) else Color.Transparent)
                            .clickable { onPenWeightChange(w) }
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        NexusText(
                            text = w.label,
                            style = NexusTheme.typography.caption.copy(
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = if (isSelected) Color.White else Color(0xFF707070)
                        )
                    }
                }
            }

            // Clear button
            Box(
                modifier = Modifier
                    .height(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, Color(0xFFCFCFCF), RoundedCornerShape(10.dp))
                    .clickable { onClear() }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                NexusText(
                    text = "Clear",
                    style = NexusTheme.typography.caption.copy(
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = Color(0xFF0A0A0A)
                )
            }
        }
    }
}

// ── Type Tab Component ──────────────────────────────────────────────────────
@Composable
private fun TypeSignatureTab(
    typedText: String,
    onTextChange: (String) -> Unit,
    selectedFont: ScriptFont,
    onFontSelect: (ScriptFont) -> Unit
) {
    val displayPreview = if (typedText.trim().isNotEmpty()) typedText.trim() else "Your Name"

    Column(modifier = Modifier.fillMaxWidth()) {
        // Text Input Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFFAFAFA))
                .border(1.5.dp, Color(0xFFEBEBEB), RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (typedText.isEmpty()) {
                NexusText(
                    text = "Type your name",
                    style = NexusTheme.typography.body.copy(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = Color(0xFFA3A3A3)
                )
            }
            BasicTextField(
                value = typedText,
                onValueChange = { if (it.length <= 40) onTextChange(it) },
                singleLine = true,
                textStyle = TextStyle(
                    color = Color(0xFF0A0A0A),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 2x2 Fonts Grid
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val fonts = ScriptFont.values()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FontOptionCard(
                    font = fonts[0],
                    displayText = displayPreview,
                    isSelected = selectedFont == fonts[0],
                    onClick = { onFontSelect(fonts[0]) },
                    modifier = Modifier.weight(1f)
                )
                FontOptionCard(
                    font = fonts[1],
                    displayText = displayPreview,
                    isSelected = selectedFont == fonts[1],
                    onClick = { onFontSelect(fonts[1]) },
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FontOptionCard(
                    font = fonts[2],
                    displayText = displayPreview,
                    isSelected = selectedFont == fonts[2],
                    onClick = { onFontSelect(fonts[2]) },
                    modifier = Modifier.weight(1f)
                )
                FontOptionCard(
                    font = fonts[3],
                    displayText = displayPreview,
                    isSelected = selectedFont == fonts[3],
                    onClick = { onFontSelect(fonts[3]) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun FontOptionCard(
    font: ScriptFont,
    displayText: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fontStyle = when (font) {
        ScriptFont.DANCING_SCRIPT -> FontStyle.Italic
        ScriptFont.GREAT_VIBES -> FontStyle.Normal
        ScriptFont.ALLURA -> FontStyle.Italic
        ScriptFont.CAVEAT -> FontStyle.Italic
    }
    val fontFamily = when (font) {
        ScriptFont.DANCING_SCRIPT, ScriptFont.GREAT_VIBES -> FontFamily.Cursive
        ScriptFont.ALLURA -> FontFamily.Serif
        ScriptFont.CAVEAT -> FontFamily.SansSerif
    }

    Box(
        modifier = modifier
            .height(68.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .border(
                width = if (isSelected) 2.dp else 1.5.dp,
                color = if (isSelected) Color(0xFF0A0A0A) else Color(0xFFEBEBEB),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        NexusText(
            text = displayText,
            style = TextStyle(
                fontFamily = fontFamily,
                fontStyle = fontStyle,
                fontSize = 24.sp,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center
            ),
            color = Color(0xFF0A0A0A),
            maxLines = 1
        )
    }
}

// ── Upload Tab Component ────────────────────────────────────────────────────
@Composable
private fun UploadSignatureTab(
    uploadedBitmap: Bitmap?,
    makeTransparent: Boolean,
    onMakeTransparentChange: (Boolean) -> Unit,
    onPickImage: () -> Unit
) {
    val borderColor = Color(0xFFCFCFCF)

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFFFAFAFA))
                .drawBehind {
                    drawRoundRect(
                        color = borderColor,
                        style = Stroke(
                            width = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f)
                        ),
                        cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx())
                    )
                }
                .clickable { onPickImage() }
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            if (uploadedBitmap != null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Image(
                        bitmap = uploadedBitmap.asImageBitmap(),
                        contentDescription = "Uploaded signature",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    NexusText(
                        text = "Tap to choose a different image",
                        style = NexusTheme.typography.caption.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = Color(0xFF707070)
                    )
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Image,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = Color(0xFFA3A3A3)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    NexusText(
                        text = "Choose a photo or scan of your signature",
                        style = NexusTheme.typography.body.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = Color(0xFF707070),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Checkbox: Make the white background transparent
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onMakeTransparentChange(!makeTransparent) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = makeTransparent,
                onCheckedChange = { onMakeTransparentChange(it) },
                colors = CheckboxDefaults.colors(
                    checkedColor = Color(0xFF0A0A0A),
                    uncheckedColor = Color(0xFF707070)
                )
            )
            Spacer(modifier = Modifier.width(6.dp))
            NexusText(
                text = "Make the white background transparent",
                style = NexusTheme.typography.body.copy(
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = Color(0xFF0A0A0A)
            )
        }
    }
}
