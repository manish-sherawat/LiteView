package com.nexus.feature.scanner.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusText
import com.nexus.core.ui.components.NexusButton
import com.nexus.core.ui.components.NexusTopBar
import com.nexus.core.R

@Composable
fun ScannerReviewScreen(
    viewModel: ScannerViewModel,
    scannedPages: List<ScannedPage>,
    onAddMore: () -> Unit,
    onFinish: () -> Unit
) {
    if (scannedPages.isEmpty()) return
    
    var selectedPageIndex by remember { mutableIntStateOf(scannedPages.size - 1) }
    // Ensure index bounds are safe after deletions
    if (selectedPageIndex >= scannedPages.size) {
        selectedPageIndex = scannedPages.size - 1
    }
    
    val currentPage = scannedPages[selectedPageIndex]
    val ocrResult by viewModel.ocrResult.collectAsStateWithLifecycle()
    
    if (ocrResult != null) {
        Dialog(onDismissRequest = { viewModel.clearOcrResult() }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.8f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(NexusTheme.colors.surface)
                    .padding(24.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    NexusText(text = "Extracted Text", style = NexusTheme.typography.title)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Box(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        NexusText(text = ocrResult ?: "", color = NexusTheme.colors.textPrimary)
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    NexusButton(text = "Close", onClick = { viewModel.clearOcrResult() })
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NexusTheme.colors.background)
            .statusBarsPadding()
    ) {
        // Top Bar
        NexusTopBar(
            title = "Review (${selectedPageIndex + 1}/${scannedPages.size})",
            actions = {
                NexusButton(text = "Save", onClick = onFinish)
            }
        )

        // Preview
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = currentPage.croppedBitmap.asImageBitmap(),
                contentDescription = "Scanned Page",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }

        // Page Actions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ActionIcon(R.drawable.ic_delete, "Delete") {
                viewModel.deletePage(selectedPageIndex)
            }
            ActionIcon(R.drawable.ic_ocr, "OCR") {
                viewModel.extractText(selectedPageIndex)
            }
            ActionIcon(R.drawable.ic_arrow_left, "Move Left") {
                if (selectedPageIndex > 0) {
                    viewModel.movePage(selectedPageIndex, selectedPageIndex - 1)
                    selectedPageIndex -= 1
                }
            }
            ActionIcon(R.drawable.ic_arrow_right, "Move Right") {
                if (selectedPageIndex < scannedPages.size - 1) {
                    viewModel.movePage(selectedPageIndex, selectedPageIndex + 1)
                    selectedPageIndex += 1
                }
            }
        }

        // Pages thumbnail reel
        if (scannedPages.size > 1) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(scannedPages) { index, page ->
                    Box(
                        modifier = Modifier
                            .size(60.dp, 80.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (index == selectedPageIndex) NexusTheme.colors.primary else Color.Gray)
                        .clickable { selectedPageIndex = index }
                        .padding(2.dp)
                    ) {
                        Image(
                            bitmap = page.croppedBitmap.asImageBitmap(), 
                            contentDescription = "Page $index",
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
        }


    }
}

@Composable
private fun ActionIcon(iconRes: Int, label: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier.clickable { onClick() }.padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = iconRes),
            contentDescription = label,
            modifier = Modifier.size(24.dp),
            colorFilter = ColorFilter.tint(NexusTheme.colors.primary)
        )
        Spacer(modifier = Modifier.height(4.dp))
        NexusText(text = label, style = NexusTheme.typography.label, color = NexusTheme.colors.textSecondary)
    }
}
