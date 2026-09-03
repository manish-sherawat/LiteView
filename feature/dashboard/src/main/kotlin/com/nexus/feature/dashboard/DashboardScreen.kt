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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.ui.draw.shadow
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
import com.nexus.core.ui.components.NexusEmptyStateImage
import com.nexus.core.ui.components.NexusEmptyStateType
import com.nexus.core.ui.NexusSurface
import com.nexus.core.ui.NexusText
import com.nexus.core.ui.NexusTextField
import com.nexus.core.R
import androidx.compose.ui.graphics.asComposeRenderEffect
import android.graphics.RenderEffect
import android.graphics.Shader
import kotlinx.coroutines.launch

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
    var dismissedUpdateInSession by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val lazyGridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val searchFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        launch {
            viewModel.uiEvents.collect { message ->
                val result = snackbarHostState.showSnackbar(
                    message = message,
                    actionLabel = if (viewModel.lastDeletedDocument != null && (message.contains("deleted", ignoreCase = true) || message.contains("removed", ignoreCase = true))) "Undo" else null,
                    duration = SnackbarDuration.Short
                )
                if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                    viewModel.lastDeletedDocument?.let { doc ->
                        viewModel.restoreDocument(doc)
                    }
                }
            }
        }
        launch {
            viewModel.searchFocusEvent.collect {
                lazyGridState.animateScrollToItem(0)
                try {
                    searchFocusRequester.requestFocus()
                    keyboardController?.show()
                } catch (_: Exception) {}
            }
        }
        launch {
            viewModel.scrollToTopEvent.collect {
                lazyGridState.animateScrollToItem(0)
            }
        }
    }
    
    if (updateState is UpdateState.Available && !dismissedUpdateInSession) {
        UpdateDialog(
            updateState = updateState,
            onDismiss   = {
                dismissedUpdateInSession = true
                viewModel.dismissUpdate()
            },
            onDownload  = { downloadUrl, version ->
                android.widget.Toast.makeText(context, "Downloading update v$version...", android.widget.Toast.LENGTH_LONG).show()
                viewModel.downloadUpdate(downloadUrl, version)
                viewModel.resetUpdateState()
            },
            onRetry     = { viewModel.resetUpdateState() }
        )
    }

    var isPermissionGranted by remember { mutableStateOf(viewModel.hasStoragePermission(context)) }
    var detailsDialogDoc by remember { mutableStateOf<com.nexus.feature.dashboard.data.RecentDocument?>(null) }
    var showTagManagerScreen by remember { mutableStateOf(false) }

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

    if (showTagManagerScreen) {
        TagManagerScreen(
            tags = uiState.availableTags,
            documents = uiState.documents,
            onBack = { showTagManagerScreen = false },
            onRenameTag = { oldName, newName, colorHex, emoji ->
                viewModel.renameTagGlobally(oldName, newName, colorHex, emoji)
            },
            onUpsertTag = { name, colorHex, emoji ->
                viewModel.upsertTagDefinition(name, colorHex, emoji)
            },
            onDeleteTag = { name ->
                viewModel.deleteTagGlobally(name)
            },
            onOpenDocument = { doc ->
                showTagManagerScreen = false
                router.openDocument(
                    uri = Uri.parse(doc.uri),
                    mimeType = doc.mimeType,
                    fileName = doc.fileName
                )
            }
        )
        return
    }

    if (!isPermissionGranted && !uiState.permissionRationaleShown) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(NexusTheme.colors.background)
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    NexusEmptyStateImage(
                        type = NexusEmptyStateType.STORAGE,
                        contentDescription = "Permission needed",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit
                    )
                }
                NexusButton(
                    text = "Grant Storage Access",
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
                    modifier = Modifier.fillMaxWidth()
                )
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
                state = lazyGridState,
                columns = if (uiState.isGridView) GridCells.Adaptive(minSize = 135.dp) else GridCells.Fixed(1),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(if (uiState.isGridView) 8.dp else 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    com.nexus.core.ui.components.NexusSearchBar(
                        query = uiState.searchQuery,
                        onQueryChange = { viewModel.setSearchQuery(it) },
                        onClear = { viewModel.setSearchQuery("") },
                        placeholderText = "Search files...",
                        focusRequester = searchFocusRequester
                    )
                    
                    Spacer(modifier = Modifier.height(18.dp))
                    
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
                    
                    Spacer(modifier = Modifier.height(12.dp))

                    // ── Horizontal File Type Filter Chips ─────────────────────────
                    androidx.compose.foundation.lazy.LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(DocumentTypeFilter.entries.toTypedArray()) { filter ->
                            val isSelected = uiState.selectedFilter == filter
                            val count = uiState.filterCounts[filter] ?: 0
                            val chipColor = when (filter) {
                                DocumentTypeFilter.ALL -> NexusTheme.colors.primary
                                DocumentTypeFilter.PDF -> Color(0xFFEF4444)
                                DocumentTypeFilter.DOCX -> Color(0xFF2563EB)
                                DocumentTypeFilter.XLSX -> Color(0xFF10B981)
                                DocumentTypeFilter.TXT -> Color(0xFF8B5CF6)
                            }

                            val animatedBg by animateColorAsState(
                                targetValue = if (isSelected) chipColor.copy(alpha = 0.16f) else NexusTheme.colors.surfaceVariant.copy(alpha = 0.55f),
                                animationSpec = spring(),
                                label = "formatBg"
                            )
                            val animatedBorderColor by animateColorAsState(
                                targetValue = if (isSelected) chipColor.copy(alpha = 0.75f) else NexusTheme.colors.divider.copy(alpha = 0.25f),
                                animationSpec = spring(),
                                label = "formatBorder"
                            )
                            val animatedBorderWidth by animateDpAsState(
                                targetValue = if (isSelected) 1.2.dp else 0.6.dp,
                                animationSpec = spring(),
                                label = "formatBorderWidth"
                            )
                            val animatedTextColor by animateColorAsState(
                                targetValue = if (isSelected) chipColor else NexusTheme.colors.textPrimary,
                                animationSpec = spring(),
                                label = "formatTextColor"
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(animatedBg)
                                    .border(
                                        width = animatedBorderWidth,
                                        color = animatedBorderColor,
                                        shape = androidx.compose.foundation.shape.CircleShape
                                    )
                                    .springBounceClick { viewModel.setSelectedFilter(filter) }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                if (filter != DocumentTypeFilter.ALL) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(androidx.compose.foundation.shape.CircleShape)
                                            .background(chipColor)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                NexusText(
                                    text = filter.title,
                                    style = NexusTheme.typography.caption.copy(
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                    ),
                                    color = animatedTextColor
                                )
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = count > 0,
                                    enter = fadeIn() + expandHorizontally(),
                                    exit = fadeOut() + shrinkHorizontally()
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Spacer(modifier = Modifier.width(5.dp))
                                        NexusText(
                                            text = "$count",
                                            style = NexusTheme.typography.caption.copy(
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = if (isSelected) chipColor.copy(alpha = 0.85f) else NexusTheme.colors.textSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ── Dedicated Tags Filter Ribbon ──────────────────────────
                    val visibleTags = remember(uiState.availableTags, uiState.selectedTag) {
                        uiState.availableTags.filter { it.count > 0 || it.name == uiState.selectedTag }
                    }

                    androidx.compose.animation.AnimatedVisibility(
                        visible = visibleTags.isNotEmpty() || uiState.availableTags.isNotEmpty(),
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column {
                            Spacer(modifier = Modifier.height(8.dp))
                            androidx.compose.foundation.lazy.LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(vertical = 2.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                // ── Pinned Manage Tags Shortcut Button at the START ──────
                                item(key = "manage_tags_btn") {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(androidx.compose.foundation.shape.CircleShape)
                                            .background(NexusTheme.colors.surfaceVariant.copy(alpha = 0.8f))
                                            .border(
                                                width = 0.8.dp,
                                                color = NexusTheme.colors.primary.copy(alpha = 0.4f),
                                                shape = androidx.compose.foundation.shape.CircleShape
                                            )
                                            .springBounceClick { showTagManagerScreen = true }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Image(
                                            painter = painterResource(id = R.drawable.ic_tag),
                                            contentDescription = "Manage Tags",
                                            modifier = Modifier.size(13.dp),
                                            colorFilter = ColorFilter.tint(NexusTheme.colors.primary)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        NexusText(
                                            text = "Tags",
                                            style = NexusTheme.typography.caption.copy(
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.SemiBold
                                            ),
                                            color = NexusTheme.colors.primary
                                        )
                                    }
                                }

                                // ── Custom User Tag Filter Chips ────────────────────────
                                items(visibleTags, key = { it.name }) { tagModel ->
                                    val isSelected = uiState.selectedTag == tagModel.name
                                    val count = uiState.tagCounts[tagModel.name] ?: tagModel.count
                                    val chipColor = tagModel.color

                                    val animatedTagBg by animateColorAsState(
                                        targetValue = if (isSelected) chipColor.copy(alpha = 0.18f) else NexusTheme.colors.surfaceVariant.copy(alpha = 0.55f),
                                        animationSpec = spring(),
                                        label = "tagBg"
                                    )
                                    val animatedTagBorderColor by animateColorAsState(
                                        targetValue = if (isSelected) chipColor.copy(alpha = 0.8f) else NexusTheme.colors.divider.copy(alpha = 0.25f),
                                        animationSpec = spring(),
                                        label = "tagBorder"
                                    )
                                    val animatedTagBorderWidth by animateDpAsState(
                                        targetValue = if (isSelected) 1.2.dp else 0.6.dp,
                                        animationSpec = spring(),
                                        label = "tagBorderWidth"
                                    )
                                    val animatedTagTextColor by animateColorAsState(
                                        targetValue = if (isSelected) chipColor else NexusTheme.colors.textPrimary,
                                        animationSpec = spring(),
                                        label = "tagTextColor"
                                    )

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(androidx.compose.foundation.shape.CircleShape)
                                            .background(animatedTagBg)
                                            .border(
                                                width = animatedTagBorderWidth,
                                                color = animatedTagBorderColor,
                                                shape = androidx.compose.foundation.shape.CircleShape
                                            )
                                            .springBounceClick { viewModel.selectTag(tagModel.name) }
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        if (!tagModel.emoji.isNullOrBlank()) {
                                            NexusText(
                                                text = tagModel.emoji,
                                                style = NexusTheme.typography.caption.copy(fontSize = 12.sp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                        } else {
                                            Image(
                                                painter = painterResource(id = if (isSelected) R.drawable.ic_tag_filled else R.drawable.ic_tag),
                                                contentDescription = null,
                                                modifier = Modifier.size(13.dp),
                                                colorFilter = ColorFilter.tint(if (isSelected) chipColor else NexusTheme.colors.textSecondary)
                                            )
                                            Spacer(modifier = Modifier.width(5.dp))
                                        }
                                        NexusText(
                                            text = "#${tagModel.name}",
                                            style = NexusTheme.typography.caption.copy(
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                            ),
                                            color = animatedTagTextColor
                                        )
                                        androidx.compose.animation.AnimatedVisibility(
                                            visible = count > 0,
                                            enter = fadeIn() + expandHorizontally(),
                                            exit = fadeOut() + shrinkHorizontally()
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Spacer(modifier = Modifier.width(5.dp))
                                                NexusText(
                                                    text = "$count",
                                                    style = NexusTheme.typography.caption.copy(
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold
                                                    ),
                                                    color = if (isSelected) chipColor.copy(alpha = 0.85f) else NexusTheme.colors.textSecondary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    
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
                                    val sortLabel = when (uiState.sortOrder) {
                                        SortOrder.BY_DATE -> "Date"
                                        SortOrder.BY_NAME -> "Name"
                                        SortOrder.BY_TYPE -> "Type"
                                        SortOrder.BY_SIZE -> "Size"
                                    }
                                    NexusText(
                                        text = "Sort: $sortLabel",
                                        style = NexusTheme.typography.label.copy(fontWeight = FontWeight.Medium),
                                        color = NexusTheme.colors.textPrimary
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Image(
                                        painter = painterResource(id = R.drawable.ic_arrow_right),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(14.dp)
                                            .graphicsLayer(rotationZ = if (sortMenuExpanded) -90f else 90f),
                                        colorFilter = ColorFilter.tint(NexusTheme.colors.textSecondary)
                                    )
                                }

                                if (sortMenuExpanded) {
                                    androidx.compose.ui.window.Popup(
                                        alignment = Alignment.TopStart,
                                        offset = androidx.compose.ui.unit.IntOffset(0, 80),
                                        onDismissRequest = { sortMenuExpanded = false },
                                        properties = androidx.compose.ui.window.PopupProperties(
                                            focusable = true,
                                            dismissOnBackPress = true,
                                            dismissOnClickOutside = true
                                        )
                                    ) {
                                        NexusSurface(
                                            shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
                                            color = NexusTheme.colors.surface,
                                            modifier = Modifier
                                                .width(180.dp)
                                                .border(
                                                    width = 1.dp,
                                                    color = NexusTheme.colors.divider.copy(alpha = 0.6f),
                                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)
                                                )
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .glassBackground(
                                                        blurRadius = 24f,
                                                        fallbackColor = NexusTheme.colors.surface,
                                                        alpha = 0.9f,
                                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)
                                                    )
                                                    .padding(6.dp)
                                            ) {
                                                SortOrder.entries.forEach { order ->
                                                    val isSelected = uiState.sortOrder == order
                                                    val (title, iconRes) = when (order) {
                                                        SortOrder.BY_DATE -> Pair("Date Modified", R.drawable.ic_startup)
                                                        SortOrder.BY_NAME -> Pair("File Name", R.drawable.ic_rename)
                                                        SortOrder.BY_TYPE -> Pair("File Type", R.drawable.ic_book)
                                                        SortOrder.BY_SIZE -> Pair("File Size", R.drawable.ic_maximize)
                                                    }
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                                                            .background(
                                                                if (isSelected) NexusTheme.colors.primary.copy(alpha = 0.12f)
                                                                else Color.Transparent
                                                            )
                                                            .springBounceClick {
                                                                viewModel.setSortOrder(order)
                                                                sortMenuExpanded = false
                                                            }
                                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            modifier = Modifier.weight(1f)
                                                        ) {
                                                            Image(
                                                                painter = painterResource(id = iconRes),
                                                                contentDescription = null,
                                                                modifier = Modifier.size(16.dp),
                                                                colorFilter = ColorFilter.tint(
                                                                    if (isSelected) NexusTheme.colors.primary
                                                                    else NexusTheme.colors.textSecondary
                                                                )
                                                            )
                                                            Spacer(modifier = Modifier.width(10.dp))
                                                            NexusText(
                                                                text = title,
                                                                style = NexusTheme.typography.body.copy(
                                                                    fontSize = 13.sp,
                                                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                                                ),
                                                                color = if (isSelected) NexusTheme.colors.primary else NexusTheme.colors.textPrimary
                                                            )
                                                        }
                                                        if (isSelected) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(7.dp)
                                                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                                                    .background(NexusTheme.colors.primary)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
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
                    val config = androidx.compose.ui.platform.LocalConfiguration.current
                    val isWide = config.screenWidthDp >= 600
                    val isTall = config.screenHeightDp >= 800
                    val emptyMinHeight = if (isTall) 480.dp else if (isWide) 420.dp else 340.dp
                    val emptyMaxHeight = if (isTall) 700.dp else if (isWide) 580.dp else 560.dp
                    val imgMaxHeight = if (isTall) 440.dp else if (isWide) 380.dp else 280.dp

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = emptyMinHeight, max = emptyMaxHeight)
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        NexusEmptyStateImage(
                            type = NexusEmptyStateType.STORAGE,
                            contentDescription = "Storage access needed",
                            modifier = Modifier
                                .fillMaxWidth(if (isWide) 0.70f else 0.95f)
                                .heightIn(max = imgMaxHeight),
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit
                        )
                    }
                }
            } else if (uiState.documents.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    val config = androidx.compose.ui.platform.LocalConfiguration.current
                    val isWide = config.screenWidthDp >= 600
                    val isTall = config.screenHeightDp >= 800
                    val emptyMinHeight = if (isTall) 460.dp else if (isWide) 400.dp else 340.dp
                    val emptyMaxHeight = if (isTall) 660.dp else if (isWide) 560.dp else 520.dp
                    val imgMaxHeight = if (isTall) 420.dp else if (isWide) 360.dp else 280.dp

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = emptyMinHeight, max = emptyMaxHeight)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        val isSearching = uiState.searchQuery.isNotEmpty()
                        val emptyType = when {
                            isSearching -> NexusEmptyStateType.NOT_FOUND
                            uiState.selectedTab == DashboardTab.STARRED -> NexusEmptyStateType.FAVORITES
                            uiState.selectedTab == DashboardTab.RECENT -> NexusEmptyStateType.RECENT
                            else -> NexusEmptyStateType.STORAGE
                        }
                        
                        NexusEmptyStateImage(
                            type = emptyType,
                            contentDescription = if (isSearching) "No results found" else "Empty",
                            modifier = Modifier
                                .fillMaxWidth(if (isWide) 0.70f else 0.92f)
                                .weight(1f, fill = false)
                                .heightIn(max = imgMaxHeight),
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        if (isSearching) {
                            com.nexus.core.ui.components.NexusButton(
                                text = "Clear Search",
                                onClick = { viewModel.setSearchQuery("") }
                            )
                        } else if (uiState.selectedTag != null && uiState.selectedFilter != DocumentTypeFilter.ALL) {
                            com.nexus.core.ui.components.NexusButton(
                                text = "Clear All Filters",
                                onClick = {
                                    viewModel.setSelectedFilter(DocumentTypeFilter.ALL)
                                    viewModel.selectTag(null)
                                }
                            )
                        } else if (uiState.selectedTag != null) {
                            com.nexus.core.ui.components.NexusButton(
                                text = "Clear Tag Filter",
                                onClick = { viewModel.selectTag(null) }
                            )
                        } else if (uiState.selectedFilter != DocumentTypeFilter.ALL) {
                            com.nexus.core.ui.components.NexusButton(
                                text = "Show All Formats",
                                onClick = { viewModel.setSelectedFilter(DocumentTypeFilter.ALL) }
                            )
                        } else if (uiState.selectedTab != DashboardTab.ALL) {
                            com.nexus.core.ui.components.NexusButton(
                                text = "View All Documents",
                                onClick = { viewModel.setSelectedTab(DashboardTab.ALL) }
                            )
                        } else {
                            com.nexus.core.ui.components.NexusButton(
                                text = "Scan New Document",
                                onClick = { router.navigateToScanner() }
                            )
                        }
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
                            tags = uiModel.tags,
                            allAvailableTags = uiState.availableTags,
                            onSaveTags = { updatedTags ->
                                viewModel.setDocumentTags(uiModel.doc.uri, updatedTags)
                            },
                            onSaveTagDefinition = { name, colorHex, emoji ->
                                viewModel.upsertTagDefinition(name, colorHex, emoji)
                            },
                            onOpenTagManager = { showTagManagerScreen = true },
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
                            tags = uiModel.tags,
                            allAvailableTags = uiState.availableTags,
                            onSaveTags = { updatedTags ->
                                viewModel.setDocumentTags(uiModel.doc.uri, updatedTags)
                            },
                            onSaveTagDefinition = { name, colorHex, emoji ->
                                viewModel.upsertTagDefinition(name, colorHex, emoji)
                            },
                            onOpenTagManager = { showTagManagerScreen = true },
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
            FileDetailsSheet(
                doc = doc,
                onDismissRequest = { detailsDialogDoc = null }
            )
        }

        // Multi-Select Action Bar with Solid Opaque Background
        androidx.compose.animation.AnimatedVisibility(
            visible = uiState.isSelectionMode,
            enter = androidx.compose.animation.slideInVertically(
                initialOffsetY = { it * 2 },
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 320f)
            ) + androidx.compose.animation.fadeIn(animationSpec = tween(200)),
            exit = androidx.compose.animation.slideOutVertically(
                targetOffsetY = { it * 2 },
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 320f)
            ) + androidx.compose.animation.fadeOut(animationSpec = tween(150)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            val areAllSelected = uiState.selectedUris.size == uiState.documents.size && uiState.documents.isNotEmpty()

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 18.dp)
                    .shadow(
                        elevation = 20.dp,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
                        spotColor = Color.Black.copy(alpha = 0.35f),
                        ambientColor = Color.Black.copy(alpha = 0.2f)
                    )
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(22.dp))
                    .background(NexusTheme.colors.surface)
                    .border(
                        width = 1.dp,
                        color = NexusTheme.colors.divider.copy(alpha = 0.5f),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(NexusTheme.colors.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                NexusText(
                                    text = "${uiState.selectedUris.size}",
                                    style = NexusTheme.typography.caption.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.5.sp
                                    ),
                                    color = NexusTheme.colors.onPrimary
                                )
                            }
                            NexusText(
                                text = if (uiState.selectedUris.size == 1) "File selected" else "Files selected",
                                style = NexusTheme.typography.title.copy(fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold),
                                color = NexusTheme.colors.textPrimary
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                                .background(NexusTheme.colors.primary.copy(alpha = 0.12f))
                                .springBounceClick {
                                    if (areAllSelected) viewModel.clearSelection() else viewModel.selectAll()
                                }
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            NexusText(
                                text = if (areAllSelected) "Deselect all" else "Select all",
                                style = NexusTheme.typography.caption.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.sp
                                ),
                                color = NexusTheme.colors.primary
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Share Action
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.springBounceClick {
                                viewModel.shareSelectedDocuments(uiState.selectedUris)
                                viewModel.clearSelection()
                            }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(NexusTheme.colors.surfaceVariant)
                                    .border(0.8.dp, NexusTheme.colors.divider.copy(alpha = 0.4f), androidx.compose.foundation.shape.CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_share),
                                    contentDescription = "Share",
                                    modifier = Modifier.size(18.dp),
                                    colorFilter = ColorFilter.tint(NexusTheme.colors.primary)
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            NexusText(
                                text = "Share",
                                style = NexusTheme.typography.caption.copy(fontSize = 10.5.sp, fontWeight = FontWeight.Medium),
                                color = NexusTheme.colors.textPrimary
                            )
                        }

                        // Delete Action
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.springBounceClick { viewModel.deleteSelected() }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(NexusTheme.colors.error.copy(alpha = 0.14f))
                                    .border(0.8.dp, NexusTheme.colors.error.copy(alpha = 0.35f), androidx.compose.foundation.shape.CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_delete),
                                    contentDescription = "Delete",
                                    modifier = Modifier.size(18.dp),
                                    colorFilter = ColorFilter.tint(NexusTheme.colors.error)
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            NexusText(
                                text = "Delete",
                                style = NexusTheme.typography.caption.copy(fontSize = 10.5.sp, fontWeight = FontWeight.Medium),
                                color = NexusTheme.colors.error
                            )
                        }

                        // Close Action
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.springBounceClick { viewModel.clearSelection() }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(NexusTheme.colors.surfaceVariant)
                                    .border(0.8.dp, NexusTheme.colors.divider.copy(alpha = 0.4f), androidx.compose.foundation.shape.CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = com.nexus.core.R.drawable.ic_close),
                                    contentDescription = "Close",
                                    modifier = Modifier.size(16.dp),
                                    colorFilter = ColorFilter.tint(NexusTheme.colors.textSecondary)
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            NexusText(
                                text = "Close",
                                style = NexusTheme.typography.caption.copy(fontSize = 10.5.sp, fontWeight = FontWeight.Medium),
                                color = NexusTheme.colors.textSecondary
                            )
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = if (uiState.isSelectionMode) 130.dp else 32.dp, start = 16.dp, end = 16.dp),
            snackbar = { snackbarData ->
                val isDark = androidx.compose.foundation.isSystemInDarkTheme()
                val isUndo = snackbarData.visuals.actionLabel == "Undo"
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(NexusTheme.shapes.pill)
                        .background(
                            if (isDark) Color(0xF21A1C29) else Color(0xF22D3142)
                        )
                        .border(
                            width = 1.dp,
                            color = (if (isUndo) NexusTheme.colors.error else NexusTheme.colors.primary).copy(alpha = 0.5f),
                            shape = NexusTheme.shapes.pill
                        )
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(
                                        if (isUndo) NexusTheme.colors.error.copy(alpha = 0.2f)
                                        else NexusTheme.colors.primary.copy(alpha = 0.2f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(
                                        id = if (isUndo) R.drawable.ic_delete else com.nexus.core.R.drawable.ic_check
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    colorFilter = ColorFilter.tint(
                                        if (isUndo) NexusTheme.colors.error else NexusTheme.colors.primary
                                    )
                                )
                            }
                            
                            NexusText(
                                text = snackbarData.visuals.message,
                                style = NexusTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
                                color = Color.White
                            )
                        }

                        snackbarData.visuals.actionLabel?.let { actionText ->
                            Box(
                                modifier = Modifier
                                    .clip(NexusTheme.shapes.pill)
                                    .background(NexusTheme.colors.primary)
                                    .springBounceClick { snackbarData.performAction() }
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                NexusText(
                                    text = actionText,
                                    style = NexusTheme.typography.label.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        )
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
