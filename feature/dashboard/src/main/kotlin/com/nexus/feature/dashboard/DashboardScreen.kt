package com.nexus.feature.dashboard

import android.net.Uri
import androidx.compose.animation.core.tween
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
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.graphics.graphicsLayer
import com.nexus.core.ui.utils.glassBackground
import androidx.compose.foundation.border
import androidx.compose.runtime.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import com.nexus.core.updater.UpdateState
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
import androidx.compose.ui.graphics.asComposeRenderEffect
import android.graphics.RenderEffect
import android.graphics.Shader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    router: DocumentReaderRouter,
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(updateState) {
        if (updateState is UpdateState.Error) {
            val error = updateState as UpdateState.Error
            snackbarHostState.showSnackbar(
                message = "Update check failed: ${error.message}",
                actionLabel = "Dismiss",
                duration = SnackbarDuration.Short
            )
            viewModel.resetUpdateState()
        }
    }
    
    LaunchedEffect(Unit) {
        viewModel.uiEvents.collect { message ->
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short
            )
        }
    }
    
    if (updateState is UpdateState.Available) {
        val available = updateState as UpdateState.Available
        AlertDialog(
            onDismissRequest = { viewModel.dismissUpdate() },
            title = { NexusText(text = "Update Available", style = NexusTheme.typography.title, color = NexusTheme.colors.textPrimary) },
            text = { 
                Column {
                    NexusText(text = "Version ${available.version} is available.", style = NexusTheme.typography.body, color = NexusTheme.colors.textPrimary)
                    Spacer(modifier = Modifier.height(8.dp))
                    NexusText(text = available.releaseNotes, style = NexusTheme.typography.body, color = NexusTheme.colors.textSecondary)
                }
            },
            confirmButton = {
                androidx.compose.material3.Button(
                    onClick = { viewModel.downloadUpdate(available.downloadUrl, available.version) }
                ) {
                    NexusText(text = "Update Now", style = NexusTheme.typography.label, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissUpdate() }) {
                    NexusText(text = "Later", style = NexusTheme.typography.label, color = NexusTheme.colors.primary)
                }
            },
            containerColor = NexusTheme.colors.surface
        )
    }

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
        }
    }



    if (!isPermissionGranted && !uiState.permissionRationaleShown) {
        Box(
            modifier = modifier.fillMaxSize().background(NexusTheme.colors.background),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_folder),
                    contentDescription = "Permission needed",
                    modifier = Modifier.size(80.dp),
                    colorFilter = ColorFilter.tint(NexusTheme.colors.primary)
                )
                Spacer(modifier = Modifier.height(24.dp))
                NexusText(
                    text = "Storage Access Required",
                    style = NexusTheme.typography.h2,
                    color = NexusTheme.colors.textPrimary
                )
                Spacer(modifier = Modifier.height(16.dp))
                NexusText(
                    text = "LiteView needs storage access to scan and display your documents.",
                    style = NexusTheme.typography.body,
                    color = NexusTheme.colors.textSecondary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(32.dp))
                androidx.compose.material3.Button(
                    onClick = {
                        viewModel.setPermissionRationaleShown()
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
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = NexusTheme.colors.primary)
                ) {
                    NexusText("Grant Access", style = NexusTheme.typography.label, color = Color.White)
                }
            }
        }
        return
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NexusTheme.colors.background)
    ) {
        val pullToRefreshState = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()
        PullToRefreshBox(
            state = pullToRefreshState,
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.refreshDocuments() },
            modifier = Modifier.fillMaxSize()
        ) {
            LazyVerticalGrid(
                columns = if (uiState.isGridView) GridCells.Fixed(2) else GridCells.Fixed(1),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
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
                        Column {
                            NexusText(
                                text = "LiteView",
                                style = NexusTheme.typography.display,
                                color = NexusTheme.colors.textPrimary
                            )
                            val totalSize = uiState.documents.sumOf { it.doc.fileSizeBytes }
                            NexusText(
                                text = "${uiState.documents.size} documents · ${formatFileSize(totalSize)}",
                                style = NexusTheme.typography.caption,
                                color = NexusTheme.colors.textSecondary
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(NexusTheme.shapes.pill)
                                .background(NexusTheme.colors.primary)
                                .springBounceClick { router.navigateToScanner() }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_text_scan),
                                contentDescription = "Scan",
                                modifier = Modifier.size(20.dp),
                                colorFilter = ColorFilter.tint(NexusTheme.colors.onPrimary)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            NexusText(
                                text = "Scan",
                                style = NexusTheme.typography.label,
                                color = NexusTheme.colors.onPrimary
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
                        var sortMenuExpanded by remember { mutableStateOf(false) }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(NexusTheme.shapes.pill)
                                        .background(NexusTheme.colors.surfaceVariant)
                                        .springBounceClick { sortMenuExpanded = true }
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    NexusText(
                                        text = "Sort: ${uiState.sortOrder.name.replace("BY_", "")}",
                                        style = NexusTheme.typography.label,
                                        color = NexusTheme.colors.textSecondary
                                    )
                                    androidx.compose.material3.DropdownMenu(
                                        expanded = sortMenuExpanded,
                                        onDismissRequest = { sortMenuExpanded = false }
                                    ) {
                                        SortOrder.entries.forEach { order ->
                                            androidx.compose.material3.DropdownMenuItem(
                                                text = { NexusText(order.name.replace("BY_", "")) },
                                                onClick = {
                                                    viewModel.setSortOrder(order)
                                                    sortMenuExpanded = false
                                                },
                                                colors = androidx.compose.material3.MenuDefaults.itemColors(
                                                    textColor = if (uiState.sortOrder == order) NexusTheme.colors.primary else NexusTheme.colors.textPrimary
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Image(
                                painter = painterResource(id = if (uiState.sortAscending) R.drawable.ic_sort_asc else R.drawable.ic_sort_desc),
                                contentDescription = "Sort direction",
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(NexusTheme.colors.surfaceVariant)
                                    .springBounceClick { viewModel.toggleSortDirection() }
                                    .padding(8.dp),
                                colorFilter = ColorFilter.tint(NexusTheme.colors.textSecondary)
                            )
                        }
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(NexusTheme.shapes.pill)
                                .background(NexusTheme.colors.surfaceVariant)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(if (!uiState.isGridView) NexusTheme.colors.primary.copy(alpha = 0.2f) else Color.Transparent)
                                    .springBounceClick { if (uiState.isGridView) viewModel.toggleGridView() }
                                    .padding(8.dp)
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_view_list),
                                    contentDescription = "List View",
                                    modifier = Modifier.size(20.dp),
                                    colorFilter = ColorFilter.tint(if (!uiState.isGridView) NexusTheme.colors.primary else NexusTheme.colors.textSecondary)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(if (uiState.isGridView) NexusTheme.colors.primary.copy(alpha = 0.2f) else Color.Transparent)
                                    .springBounceClick { if (!uiState.isGridView) viewModel.toggleGridView() }
                                    .padding(8.dp)
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_view_grid),
                                    contentDescription = "Grid View",
                                    modifier = Modifier.size(20.dp),
                                    colorFilter = ColorFilter.tint(if (uiState.isGridView) NexusTheme.colors.primary else NexusTheme.colors.textSecondary)
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
            
            if (uiState.isLoading) {
                items(10, span = { if (!uiState.isGridView) GridItemSpan(maxLineSpan) else GridItemSpan(1) }) {
                    if (uiState.isGridView) {
                        FileGridItemShimmer(modifier = Modifier.fillMaxWidth())
                    } else {
                        FileListItemShimmer(modifier = Modifier.fillMaxWidth())
                    }
                }
            } else if (!isPermissionGranted) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 64.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(120.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(NexusTheme.colors.surfaceVariant.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_folder),
                                contentDescription = "Empty",
                                modifier = Modifier.size(56.dp),
                                colorFilter = ColorFilter.tint(NexusTheme.colors.primary.copy(alpha = 0.8f))
                            )
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        NexusText(
                            text = "Storage access needed",
                            style = NexusTheme.typography.title,
                            color = NexusTheme.colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        NexusText(
                            text = "Please grant storage permission in Settings to see your files.",
                            style = NexusTheme.typography.body,
                            color = NexusTheme.colors.textSecondary,
                            textAlign = TextAlign.Center
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
                                .size(120.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(NexusTheme.colors.surfaceVariant.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = emptyIcon),
                                contentDescription = "Empty",
                                modifier = Modifier.size(56.dp),
                                colorFilter = ColorFilter.tint(NexusTheme.colors.primary.copy(alpha = 0.8f))
                            )
                        }
                        Spacer(modifier = Modifier.height(24.dp))
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
                                DashboardTab.STARRED -> "Files you star will appear here for quick access."
                                DashboardTab.RECENT -> "Recently opened files will appear here."
                                else -> "Tap the Scan button or open a document to get started."
                            },
                            style = NexusTheme.typography.body,
                            color = NexusTheme.colors.textSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                itemsIndexed(uiState.documents, key = { _, it -> it.doc.uri }) { index, uiModel ->
                    var isVisible by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        kotlinx.coroutines.delay(index.toLong().coerceAtMost(10L) * 10L)
                        isVisible = true
                    }
                    val alpha by androidx.compose.animation.core.animateFloatAsState(
                        targetValue = if (isVisible) 1f else 0f, 
                        animationSpec = tween(150),
                        label = "alpha"
                    )
                    val translationY by androidx.compose.animation.core.animateFloatAsState(
                        targetValue = if (isVisible) 0f else 20f, 
                        animationSpec = tween(150),
                        label = "translateY"
                    )

                    val itemModifier = Modifier.animateItem().graphicsLayer {
                        this.alpha = alpha
                        this.translationY = translationY
                    }

                    if (uiState.isGridView) {
                        FileGridItem(
                            modifier = itemModifier,
                            doc = uiModel.doc,
                            isAccessible = uiModel.isAccessible,
                            isStarred = uiState.starredUris.contains(uiModel.doc.uri),
                            isSelected = uiState.selectedUris.contains(uiModel.doc.uri),
                            onToggleStarred = { viewModel.toggleStarred(uiModel.doc.uri) },
                            onClick = {
                                if (uiState.isSelectionMode) {
                                    viewModel.toggleSelection(uiModel.doc.uri)
                                } else if (uiModel.isAccessible) {
                                    router.openDocument(
                                        uri = Uri.parse(uiModel.doc.uri),
                                        mimeType = uiModel.doc.mimeType,
                                        fileName = uiModel.doc.fileName
                                    )
                                }
                            },
                            onLongClick = {
                                viewModel.toggleSelection(uiModel.doc.uri)
                            },
                            onRemove = { 
                                viewModel.removeDocument(uiModel.doc.uri)
                                android.widget.Toast.makeText(context, "File deleted successfully", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            onShowDetails = { detailsDialogDoc = uiModel.doc },
                            onShare = { viewModel.shareDocument(uiModel.doc.uri) },
                            onRename = { newName -> viewModel.renameDocument(uiModel.doc.uri, newName) }
                        )
                    } else {
                        FileListItem(
                            modifier = itemModifier,
                            doc = uiModel.doc,
                            isAccessible = uiModel.isAccessible,
                            isStarred = uiState.starredUris.contains(uiModel.doc.uri),
                            isSelected = uiState.selectedUris.contains(uiModel.doc.uri),
                            onToggleStarred = { viewModel.toggleStarred(uiModel.doc.uri) },
                            onClick = {
                                if (uiState.isSelectionMode) {
                                    viewModel.toggleSelection(uiModel.doc.uri)
                                } else if (uiModel.isAccessible) {
                                    router.openDocument(
                                        uri = Uri.parse(uiModel.doc.uri),
                                        mimeType = uiModel.doc.mimeType,
                                        fileName = uiModel.doc.fileName
                                    )
                                }
                            },
                            onLongClick = {
                                viewModel.toggleSelection(uiModel.doc.uri)
                            },
                            onRemove = { 
                                viewModel.removeDocument(uiModel.doc.uri)
                                android.widget.Toast.makeText(context, "File deleted successfully", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            onShowDetails = { detailsDialogDoc = uiModel.doc },
                            onShare = { viewModel.shareDocument(uiModel.doc.uri) },
                            onRename = { newName -> viewModel.renameDocument(uiModel.doc.uri, newName) }
                        )
                    }
                } // End of itemsIndexed block
            } // End of else block
            } // End of LazyVerticalGrid block
        } // End of PullToRefreshBox block

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
        
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp) // above nav bar
        )

        // Multi-Select Action Bar with Glassmorphism
        androidx.compose.animation.AnimatedVisibility(
            visible = uiState.isSelectionMode,
            enter = androidx.compose.animation.slideInVertically(initialOffsetY = { it }) + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { it }) + androidx.compose.animation.fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            val isDark = androidx.compose.foundation.isSystemInDarkTheme()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 24.dp)
                    .clip(NexusTheme.shapes.large)
                    .glassBackground(
                        blurRadius = 40f,
                        fallbackColor = NexusTheme.colors.surface,
                        alpha = if (isDark) 0.4f else 0.55f,
                        shape = NexusTheme.shapes.large
                    )
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        NexusText("${uiState.selectedUris.size} selected", style = NexusTheme.typography.title, color = NexusTheme.colors.textPrimary)
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .clip(NexusTheme.shapes.small)
                                .background(NexusTheme.colors.primary.copy(alpha = 0.1f))
                                .springBounceClick { viewModel.selectAll() }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            NexusText("Select all", style = NexusTheme.typography.label, color = NexusTheme.colors.primary)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.springBounceClick { viewModel.shareSelectedDocuments(uiState.selectedUris); viewModel.clearSelection() }) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_share),
                                contentDescription = "Share",
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(NexusTheme.colors.surfaceVariant.copy(alpha = 0.5f))
                                    .padding(12.dp),
                                colorFilter = ColorFilter.tint(NexusTheme.colors.textPrimary)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            NexusText("Share", style = NexusTheme.typography.caption, color = NexusTheme.colors.textPrimary)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.springBounceClick { viewModel.deleteSelected() }) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_delete),
                                contentDescription = "Delete",
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(NexusTheme.colors.error.copy(alpha = 0.15f))
                                    .padding(12.dp),
                                colorFilter = ColorFilter.tint(NexusTheme.colors.error)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            NexusText("Delete", style = NexusTheme.typography.caption, color = NexusTheme.colors.error)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.springBounceClick { viewModel.clearSelection() }) {
                            Image(
                                painter = painterResource(id = com.nexus.core.R.drawable.ic_close),
                                contentDescription = "Close",
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(NexusTheme.colors.surfaceVariant.copy(alpha = 0.5f))
                                    .padding(12.dp),
                                colorFilter = ColorFilter.tint(NexusTheme.colors.textPrimary)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            NexusText("Close", style = NexusTheme.typography.caption, color = NexusTheme.colors.textPrimary)
                        }
                    }
                }
            }
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
            .springBounceClick { onClick() }
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
