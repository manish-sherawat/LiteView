package com.nexus.feature.dashboard

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.animation.core.spring
import com.nexus.core.ui.animations.springBounceClick
import com.nexus.core.ui.animations.fadeSlideIn
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexus.core.navigation.DocumentReaderRouter
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.components.NexusButton
import com.nexus.core.ui.NexusSurface
import com.nexus.core.ui.NexusText
import com.nexus.core.ui.NexusTextField
import com.nexus.core.R

@Composable
fun DashboardScreen(
    router: DocumentReaderRouter,
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    
    var isPermissionGranted by remember { mutableStateOf(viewModel.hasStoragePermission(context)) }
    var detailsDialogDoc by remember { mutableStateOf<com.nexus.feature.dashboard.data.RecentDocument?>(null) }

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        isPermissionGranted = granted
    }

    val manageStorageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        isPermissionGranted = viewModel.hasStoragePermission(context)
    }

    LaunchedEffect(isPermissionGranted) {
        if (isPermissionGranted) {
            viewModel.scanStorage()
        } else {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                try {
                    val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = android.net.Uri.parse("package:${context.packageName}")
                    manageStorageLauncher.launch(intent)
                } catch (e: Exception) {
                    val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    manageStorageLauncher.launch(intent)
                }
            } else {
                requestPermissionLauncher.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
    }



    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NexusTheme.colors.background)
    ) {
        LazyVerticalGrid(
            columns = if (uiState.isGridView) GridCells.Fixed(2) else GridCells.Fixed(1),
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(
                top = 0.dp, 
                bottom = androidx.compose.foundation.layout.WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 100.dp
            )
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(top = 16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NexusText(
                            text = "LiteView",
                            style = NexusTheme.typography.display,
                            color = NexusTheme.colors.textPrimary
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_text_scan),
                                contentDescription = "Scan",
                                modifier = Modifier
                                    .size(48.dp)
                                    .springBounceClick { router.navigateToScanner() }
                                    .padding(8.dp),
                                colorFilter = ColorFilter.tint(NexusTheme.colors.primary)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    com.nexus.core.ui.components.NexusSearchBar(
                        query = uiState.searchQuery,
                        onQueryChange = { viewModel.setSearchQuery(it) },
                        onClear = { viewModel.setSearchQuery("") },
                        placeholderText = "Search files..."
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    com.nexus.core.ui.components.NexusTabRow(
                        options = listOf(DashboardTab.ALL, DashboardTab.RECENT, DashboardTab.STARRED),
                        selectedOption = uiState.selectedTab,
                        onOptionSelected = { viewModel.setSelectedTab(it) },
                        optionLabel = { tab ->
                            when (tab) {
                                DashboardTab.ALL -> "All"
                                DashboardTab.RECENT -> "Recent"
                                DashboardTab.STARRED -> "Starred"
                            }
                        }
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.springBounceClick { 
                            val nextSort = SortOrder.entries[(uiState.sortOrder.ordinal + 1) % SortOrder.entries.size]
                            viewModel.setSortOrder(nextSort)
                        }) {
                            NexusText(
                                text = "Sort: ${uiState.sortOrder.name.replace("BY_", "")}",
                                style = NexusTheme.typography.label,
                                color = NexusTheme.colors.textSecondary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Image(
                                painter = painterResource(id = if (uiState.sortAscending) R.drawable.ic_sort_asc else R.drawable.ic_sort_desc),
                                contentDescription = "Sort direction",
                                modifier = Modifier
                                    .size(20.dp)
                                    .springBounceClick { viewModel.toggleSortDirection() },
                                colorFilter = ColorFilter.tint(NexusTheme.colors.textSecondary)
                            )
                        }
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.springBounceClick { viewModel.toggleGridView() }.padding(4.dp)
                        ) {
                            Image(
                                painter = painterResource(id = if (uiState.isGridView) R.drawable.ic_view_list else R.drawable.ic_view_grid),
                                contentDescription = "Toggle view",
                                modifier = Modifier.size(20.dp),
                                colorFilter = ColorFilter.tint(NexusTheme.colors.textSecondary)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            NexusText(if (uiState.isGridView) "List" else "Grid", style = NexusTheme.typography.label, color = NexusTheme.colors.textSecondary)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
            
            if (uiState.isLoading) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 64.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        androidx.compose.material3.CircularProgressIndicator(
                            color = NexusTheme.colors.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        NexusText(
                            text = "Scanning storage...",
                            style = NexusTheme.typography.title,
                            color = NexusTheme.colors.textPrimary
                        )
                    }
                }
            } else if (uiState.documents.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 64.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val emptyIcon = when (uiState.selectedTab) {
                            DashboardTab.STARRED -> R.drawable.ic_star
                            DashboardTab.RECENT -> R.drawable.ic_update_progress
                            else -> R.drawable.ic_folder
                        }
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(NexusTheme.colors.primary.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = emptyIcon),
                                contentDescription = "Empty",
                                modifier = Modifier.size(48.dp),
                                colorFilter = ColorFilter.tint(NexusTheme.colors.primary)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        NexusText(
                            text = when (uiState.selectedTab) {
                                DashboardTab.STARRED -> "No starred files"
                                DashboardTab.RECENT -> "No recent files"
                                else -> "No files found"
                            },
                            style = NexusTheme.typography.title,
                            color = NexusTheme.colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        NexusText(
                            text = when (uiState.selectedTab) {
                                DashboardTab.STARRED -> "Star a file to quickly access it here."
                                DashboardTab.RECENT -> "Files you open will automatically appear here."
                                else -> "Tap Add in the nav bar to import documents."
                            },
                            style = NexusTheme.typography.body,
                            color = NexusTheme.colors.textSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                itemsIndexed(uiState.documents, key = { _, it -> it.doc.uri }) { index, uiModel ->
                    if (uiState.isGridView) {
                        FileGridItem(
                            modifier = Modifier.animateItem().fadeSlideIn(delay = index * 15),
                            doc = uiModel.doc,
                            isAccessible = uiModel.isAccessible,
                            isStarred = uiState.starredUris.contains(uiModel.doc.uri),
                            onToggleStarred = { viewModel.toggleStarred(uiModel.doc.uri) },
                            onClick = {
                                if (uiModel.isAccessible) {
                                    router.openDocument(
                                        uri = Uri.parse(uiModel.doc.uri),
                                        mimeType = uiModel.doc.mimeType,
                                        fileName = uiModel.doc.fileName
                                    )
                                }
                            },
                            onRemove = { viewModel.removeDocument(uiModel.doc.uri) },
                            onShowDetails = { detailsDialogDoc = uiModel.doc },
                            onShare = { viewModel.shareDocument(uiModel.doc.uri) },
                            onRename = { newName -> viewModel.renameDocument(uiModel.doc.uri, newName) }
                        )
                    } else {
                        FileListItem(
                            modifier = Modifier.animateItem().fadeSlideIn(delay = index * 15),
                            doc = uiModel.doc,
                            isAccessible = uiModel.isAccessible,
                            isStarred = uiState.starredUris.contains(uiModel.doc.uri),
                            onToggleStarred = { viewModel.toggleStarred(uiModel.doc.uri) },
                            onClick = {
                                if (uiModel.isAccessible) {
                                    router.openDocument(
                                        uri = Uri.parse(uiModel.doc.uri),
                                        mimeType = uiModel.doc.mimeType,
                                        fileName = uiModel.doc.fileName
                                    )
                                }
                            },
                            onRemove = { viewModel.removeDocument(uiModel.doc.uri) },
                            onShowDetails = { detailsDialogDoc = uiModel.doc },
                            onShare = { viewModel.shareDocument(uiModel.doc.uri) },
                            onRename = { newName -> viewModel.renameDocument(uiModel.doc.uri, newName) }
                        )
                    }
                }
            }
        }

        detailsDialogDoc?.let { doc ->
            com.nexus.core.ui.components.NexusDialog(
                onDismissRequest = { detailsDialogDoc = null },
                title = { NexusText("File Details", style = NexusTheme.typography.h2) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        NexusText("Name: ${doc.fileName}", style = NexusTheme.typography.body)
                        NexusText("Type: ${doc.documentType}", style = NexusTheme.typography.body)
                        NexusText("Size: ${formatFileSize(doc.fileSizeBytes)}", style = NexusTheme.typography.body)
                        NexusText("Last Opened: ${formatDate(doc.lastOpenedAt)}", style = NexusTheme.typography.body)
                        Spacer(modifier = Modifier.height(4.dp))
                        NexusText("Location: ${doc.uri}", color = NexusTheme.colors.textSecondary, style = NexusTheme.typography.caption)
                    }
                },
                confirmButton = {
                    com.nexus.core.ui.components.NexusButton(
                        text = "Close",
                        onClick = { detailsDialogDoc = null }
                    )
                }
            )
        }
    }
}

@Composable
fun TabButton(text: String, isSelected: Boolean, onClick: () -> Unit) {
    val bgColor = if (isSelected) NexusTheme.colors.primary else NexusTheme.colors.surfaceVariant
    val textColor = if (isSelected) NexusTheme.colors.onPrimary else NexusTheme.colors.textPrimary

    Box(
        modifier = Modifier
            .springBounceClick(scaleDown = 0.90f, onClick = onClick)
            .clip(NexusTheme.shapes.pill)
            .background(bgColor)
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        NexusText(
            text = text,
            color = textColor,
            style = NexusTheme.typography.label.copy(fontWeight = FontWeight.Bold)
        )
    }
}

@Composable
fun FileCard(
    doc: com.nexus.feature.dashboard.data.RecentDocument,
    isStarred: Boolean,
    onClick: () -> Unit
) {
    NexusSurface(
        shape = NexusTheme.shapes.large,
        elevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            com.nexus.core.ui.components.FileTypeIcon(
                type = runCatching { com.nexus.core.navigation.DocumentType.valueOf(doc.documentType) }.getOrDefault(com.nexus.core.navigation.DocumentType.UNKNOWN),
                size = 48.dp
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                NexusText(
                    text = doc.fileName,
                    style = NexusTheme.typography.title,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(4.dp))
                NexusText(
                    text = doc.documentType,
                    style = NexusTheme.typography.caption,
                    color = NexusTheme.colors.textSecondary
                )
            }
            
            if (isStarred) {
                Image(
                    painter = painterResource(id = R.drawable.ic_star),
                    contentDescription = "Starred",
                    modifier = Modifier.size(24.dp),
                    colorFilter = ColorFilter.tint(NexusTheme.colors.primary)
                )
            }
        }
    }
}
