package com.nexus.feature.dashboard.tools.word_to_pdf

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

import java.util.UUID
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.nexus.core.R
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusText
import com.nexus.core.ui.animations.springBounceClick
import com.nexus.core.ui.components.NexusButton
import com.nexus.core.ui.components.NexusTopBar
import com.nexus.core.utils.PdfConverter

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class ProcessState {
    IDLE, PROCESSING, SUCCESS, ERROR
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordToPdfScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var selectedDocs by remember { mutableStateOf<List<SelectedDoc>>(emptyList()) }
    
    var showBottomSheet by remember { mutableStateOf(false) }
    var outputFileName by remember { mutableStateOf("Word_To_PDF_${System.currentTimeMillis()}") }
    var qualityIndex by remember { mutableStateOf(1) }
    
    var processState by remember { mutableStateOf(ProcessState.IDLE) }
    var errorMessage by remember { mutableStateOf("") }
    
    var totalImagesSize by remember { mutableLongStateOf(0L) }
    
    LaunchedEffect(selectedDocs) {
        var size = 0L
        for (img in selectedDocs) {
            context.contentResolver.query(img.uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    if (sizeIndex != -1) {
                        size += cursor.getLong(sizeIndex)
                    }
                }
            }
        }
        totalImagesSize = size
    }

    val docPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            selectedDocs = selectedDocs + uris.map { SelectedDoc(uri = it) }
        }
    }

    val createDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { destUri ->
        if (destUri != null && selectedDocs.isNotEmpty()) {
            processState = ProcessState.PROCESSING
            showBottomSheet = false
            coroutineScope.launch {
                try {
                    context.contentResolver.openOutputStream(destUri)?.use { outputStream ->
                        PdfConverter.convertWordToPdf(
                            context = context, 
                            docUris = selectedDocs.map { it.uri }, 
                            outputStream = outputStream,
                            quality = when(qualityIndex) {
                                0 -> 50  // Smaller size
                                1 -> 80  // Balanced size
                                2 -> 100 // Best quality
                                else -> 80
                            }
                        )
                    }
                    delay(500) // Small delay for smoother UI transition
                    processState = ProcessState.SUCCESS
                } catch (e: Exception) {
                    errorMessage = e.message ?: "An unknown error occurred"
                    processState = ProcessState.ERROR
                }
            }
        }
    }

    Scaffold(
        topBar = {
            NexusTopBar(
                title = "Word to PDF",
                navigationIcon = {
                    Image(
                        painter = painterResource(id = R.drawable.ic_back),
                        contentDescription = "Back",
                        modifier = Modifier
                            .clip(CircleShape)
                            .springBounceClick { onBack() }
                            .padding(NexusTheme.spacing.sm)
                            .size(24.dp),
                        colorFilter = ColorFilter.tint(NexusTheme.colors.textPrimary)
                    )
                }
            )
        },
        bottomBar = {
            if (selectedDocs.isNotEmpty() && processState == ProcessState.IDLE) {
                Surface(
                    color = NexusTheme.colors.background,
                    shadowElevation = 16.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(NexusTheme.spacing.md),
                        horizontalArrangement = Arrangement.spacedBy(NexusTheme.spacing.md)
                    ) {
                        NexusButton(
                            text = "Cancel",
                            onClick = { selectedDocs = emptyList() },
                            modifier = Modifier.weight(1f).height(56.dp),
                            containerColor = NexusTheme.colors.surfaceVariant,
                            contentColor = NexusTheme.colors.textPrimary
                        )
                        NexusButton(
                            text = "Next",
                            onClick = { showBottomSheet = true },
                            modifier = Modifier.weight(1f).height(56.dp)
                        )
                    }
                }
            }
        },
        containerColor = NexusTheme.colors.background
    ) { paddingValues ->
        Crossfade(targetState = processState, label = "state_transition", modifier = modifier.padding(paddingValues)) { state ->
            when (state) {
                ProcessState.IDLE -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(NexusTheme.spacing.lg),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
            if (selectedDocs.isEmpty()) {
                // Upload Area
                val strokeColor = NexusTheme.colors.primary
                val infiniteTransition = rememberInfiniteTransition(label = "dash_anim")
                val phase by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 40f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1500, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "dash_phase"
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .clip(NexusTheme.shapes.large)
                        .background(NexusTheme.colors.primary.copy(alpha = 0.05f))
                        .drawBehind {
                            drawRoundRect(
                                color = strokeColor.copy(alpha = 0.5f),
                                style = Stroke(
                                    width = 2.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 20f), phase)
                                ),
                                cornerRadius = CornerRadius(24.dp.toPx(), 24.dp.toPx())
                            )
                        }
                        .clickable { 
                            docPickerLauncher.launch(arrayOf("application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")) 
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(NexusTheme.spacing.lg)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.upload),
                            contentDescription = "Upload Illustration",
                            modifier = Modifier
                                .size(100.dp)
                                .padding(bottom = NexusTheme.spacing.md)
                        )
                        NexusText(
                            text = "Tap to select documents",
                            style = NexusTheme.typography.title,
                            color = NexusTheme.colors.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        NexusText(
                            text = "You can select multiple Word documents",
                            style = NexusTheme.typography.caption,
                            color = NexusTheme.colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(NexusTheme.spacing.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FormatBadge("DOC")
                            FormatBadge("DOCX")
                            
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        NexusText(
                            text = "Max 50 documents per PDF • High Quality Export",
                            style = NexusTheme.typography.caption,
                            color = NexusTheme.colors.textSecondary
                        )
                    }
                }
            } else {
                // Preview & Reorder Area
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = NexusTheme.spacing.xxs),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NexusText(
                        text = "Selected Documents (${selectedDocs.size})",
                        style = NexusTheme.typography.title
                    )
                    
                    IconButton(
                        onClick = { docPickerLauncher.launch(arrayOf("application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")) },
                        modifier = Modifier.background(NexusTheme.colors.primary.copy(alpha = 0.1f), CircleShape)
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = "Add More", tint = NexusTheme.colors.primary)
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Track dragging state
                var draggedIndex by remember { mutableStateOf<Int?>(null) }
                var dragOffset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
                val gridState = rememberLazyGridState()

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 140.dp),
                    state = gridState,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(NexusTheme.spacing.md),
                    verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.md)
                ) {
                    itemsIndexed(
                        items = selectedDocs,
                        key = { _, image -> image.id }
                    ) { index, image ->
                        val isDragged = draggedIndex == index
                        val zIndex = if (isDragged) 1f else 0f
                        
                        Box(
                            modifier = Modifier
                                .animateItem()
                                .zIndex(zIndex)
                                .graphicsLayer {
                                    if (isDragged) {
                                        translationX = dragOffset.x
                                        translationY = dragOffset.y
                                        scaleX = 1.05f
                                        scaleY = 1.05f
                                        shadowElevation = 8.dp.toPx()
                                        alpha = 0.9f
                                    }
                                }
                                .aspectRatio(0.75f)
                                .clip(NexusTheme.shapes.medium)
                                .background(NexusTheme.colors.surface)
                                .border(
                                    if (isDragged) 2.dp else 1.dp,
                                    if (isDragged) NexusTheme.colors.primary else NexusTheme.colors.divider,
                                    NexusTheme.shapes.medium
                                )
                                .pointerInput(Unit) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { offset ->
                                            draggedIndex = index
                                            dragOffset = androidx.compose.ui.geometry.Offset.Zero
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            dragOffset += dragAmount
                                            
                                            val currentIndex = draggedIndex ?: return@detectDragGesturesAfterLongPress
                                            
                                            val layoutInfo = gridState.layoutInfo
                                            val draggedItemInfo = layoutInfo.visibleItemsInfo.find { it.index == currentIndex }
                                            
                                            if (draggedItemInfo != null) {
                                                val draggedCenter = androidx.compose.ui.geometry.Offset(
                                                    draggedItemInfo.offset.x + draggedItemInfo.size.width / 2f + dragOffset.x,
                                                    draggedItemInfo.offset.y + draggedItemInfo.size.height / 2f + dragOffset.y
                                                )
                                                
                                                val targetItem = layoutInfo.visibleItemsInfo.find { item ->
                                                    val rect = androidx.compose.ui.geometry.Rect(
                                                        left = item.offset.x.toFloat(),
                                                        top = item.offset.y.toFloat(),
                                                        right = (item.offset.x + item.size.width).toFloat(),
                                                        bottom = (item.offset.y + item.size.height).toFloat()
                                                    )
                                                    rect.contains(draggedCenter) && item.index != currentIndex
                                                }
                                                
                                                if (targetItem != null) {
                                                    val targetIndex = targetItem.index
                                                    val mutableList = selectedDocs.toMutableList()
                                                    val temp = mutableList[currentIndex]
                                                    mutableList.removeAt(currentIndex)
                                                    mutableList.add(targetIndex, temp)
                                                    selectedDocs = mutableList
                                                    
                                                    draggedIndex = targetIndex
                                                    
                                                    val dx = targetItem.offset.x - draggedItemInfo.offset.x
                                                    val dy = targetItem.offset.y - draggedItemInfo.offset.y
                                                    dragOffset -= androidx.compose.ui.geometry.Offset(dx.toFloat(), dy.toFloat())
                                                }
                                            }
                                        },
                                        onDragEnd = {
                                            draggedIndex = null
                                            dragOffset = androidx.compose.ui.geometry.Offset.Zero
                                        },
                                        onDragCancel = {
                                            draggedIndex = null
                                            dragOffset = androidx.compose.ui.geometry.Offset.Zero
                                        }
                                    )
                                }
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_file_word_new),
                                contentDescription = "Selected Document",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                            
                            // Delete button (Top Right)
                            IconButton(
                                onClick = {
                                    val mutableList = selectedDocs.toMutableList()
                                    mutableList.removeAt(index)
                                    selectedDocs = mutableList
                                },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(NexusTheme.spacing.xxs)
                                    .size(32.dp)
                                    .background(NexusTheme.colors.surface.copy(alpha = 0.8f), CircleShape)
                            ) {
                                Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = NexusTheme.colors.error, modifier = Modifier.size(16.dp))
                            }
                            
                            // Badge index
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(NexusTheme.spacing.xs)
                                    .background(NexusTheme.colors.primary, CircleShape)
                                    .padding(horizontal = NexusTheme.spacing.xs, vertical = NexusTheme.spacing.xxs)
                            ) {
                                NexusText(text = "${index + 1}", color = Color.White, style = NexusTheme.typography.caption)
                            }
                        }
                    }
                }
                
                NexusText(
                    text = "💡 Tip: Long-press and drag an image to reorder",
                    style = NexusTheme.typography.caption,
                    color = NexusTheme.colors.textSecondary,
                    modifier = Modifier.padding(top = NexusTheme.spacing.sm),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
                }
                ProcessState.PROCESSING -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(NexusTheme.spacing.lg),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            color = NexusTheme.colors.primary,
                            strokeWidth = 6.dp,
                            modifier = Modifier.size(72.dp)
                        )
                        Spacer(modifier = Modifier.height(32.dp))
                        NexusText(
                            text = "Creating your PDF...",
                            style = NexusTheme.typography.titleLarge,
                            color = NexusTheme.colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        NexusText(
                            text = "Please wait while we stitch your images together.",
                            style = NexusTheme.typography.body,
                            color = NexusTheme.colors.textSecondary
                        )
                    }
                }
                ProcessState.SUCCESS -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(NexusTheme.spacing.lg),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .clip(CircleShape)
                                .background(NexusTheme.colors.success.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            com.nexus.core.ui.animations.AnimatedSuccessCheck(
                                modifier = Modifier.size(48.dp),
                                color = NexusTheme.colors.success,
                                strokeWidth = 10f
                            )
                        }
                        Spacer(modifier = Modifier.height(32.dp))
                        NexusText(
                            text = "Conversion Complete!",
                            style = NexusTheme.typography.display2,
                            color = NexusTheme.colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        NexusText(
                            text = "Your new PDF has been saved successfully.",
                            style = NexusTheme.typography.body,
                            color = NexusTheme.colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(48.dp))
                        NexusButton(
                            text = "Back to Home",
                            onClick = onBack,
                            modifier = Modifier.fillMaxWidth().height(56.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        NexusButton(
                            text = "Create Another",
                            onClick = {
                                selectedDocs = emptyList()
                                processState = ProcessState.IDLE
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            containerColor = NexusTheme.colors.surfaceVariant,
                            contentColor = NexusTheme.colors.textPrimary
                        )
                    }
                }
                ProcessState.ERROR -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(NexusTheme.spacing.lg),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .clip(CircleShape)
                                .background(NexusTheme.colors.error.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ErrorOutline,
                                contentDescription = "Error",
                                tint = NexusTheme.colors.error,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(32.dp))
                        NexusText(
                            text = "Conversion Failed",
                            style = NexusTheme.typography.display2,
                            color = NexusTheme.colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        NexusText(
                            text = errorMessage,
                            style = NexusTheme.typography.body,
                            color = NexusTheme.colors.error,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(48.dp))
                        NexusButton(
                            text = "Try Again",
                            onClick = { processState = ProcessState.IDLE },
                            modifier = Modifier.fillMaxWidth().height(56.dp)
                        )
                    }
                }
            }
        }
    }

    if (showBottomSheet) {
        val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            sheetState = bottomSheetState,
            containerColor = NexusTheme.colors.surface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(NexusTheme.spacing.lg)
            ) {
                NexusText(
                    text = "Save PDF",
                    style = NexusTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = NexusTheme.spacing.xs)
                )
                NexusText(
                    text = "Name your document before saving.",
                    style = NexusTheme.typography.body,
                    color = NexusTheme.colors.textSecondary,
                    modifier = Modifier.padding(bottom = NexusTheme.spacing.lg)
                )
                
                OutlinedTextField(
                    value = outputFileName,
                    onValueChange = { outputFileName = it },
                    label = { NexusText("Output File Name", color = NexusTheme.colors.textSecondary) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NexusTheme.colors.primary,
                        unfocusedBorderColor = NexusTheme.colors.divider,
                        focusedTextColor = NexusTheme.colors.textPrimary,
                        unfocusedTextColor = NexusTheme.colors.textPrimary
                    )
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                NexusButton(
                    text = "Save PDF",
                    onClick = { createDocLauncher.launch("$outputFileName.pdf") },
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                )
                
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun FormatBadge(text: String) {
    Box(
        modifier = Modifier
            .background(NexusTheme.colors.surfaceVariant, NexusTheme.shapes.small)
            .padding(horizontal = NexusTheme.spacing.sm, vertical = NexusTheme.spacing.xs)
    ) {
        NexusText(
            text = text,
            style = NexusTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold),
            color = NexusTheme.colors.textPrimary
        )
    }
}

data class SelectedDoc(
    val id: String = UUID.randomUUID().toString(),
    val uri: Uri
)
