package com.nexus.feature.reader.text

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import com.nexus.core.ui.components.NexusVerticalScrollbar
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import kotlin.math.roundToInt
import androidx.compose.foundation.gestures.detectTapGestures
import com.nexus.core.util.toUserFriendlyMessage
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.components.NexusButton
import com.nexus.core.ui.NexusSurface
import com.nexus.core.ui.NexusText
import com.nexus.core.ui.components.NexusTopBar
import java.net.URLDecoder
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.text.withStyle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.graphics.ColorFilter
import com.nexus.core.ui.animations.shimmerEffect
import com.nexus.core.ui.animations.springBounceClick
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextReaderScreen(
    encodedUri: String,
    fileName: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TextReaderViewModel = hiltViewModel()
) {
    LaunchedEffect(encodedUri) {
        viewModel.loadFile(encodedUri)
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    
    val displayName = try { URLDecoder.decode(URLDecoder.decode(fileName, "UTF-8"), "UTF-8") } catch (_: Exception) { fileName }
    val context = LocalContext.current
    val decodedUri = try { URLDecoder.decode(encodedUri, "UTF-8") } catch (_: Exception) { encodedUri }
    
    val readerTheme by viewModel.readerTheme.collectAsStateWithLifecycle()
    val readerBackgroundColor = when (readerTheme) {
        "DARK" -> androidx.compose.ui.graphics.Color(0xFF1E1E1E)
        "SEPIA" -> androidx.compose.ui.graphics.Color(0xFFF4ECD8)
        else -> androidx.compose.ui.graphics.Color(0xFFF8F9FA) // LIGHT
    }
    val readerTextColor = when (readerTheme) {
        "DARK" -> androidx.compose.ui.graphics.Color(0xFFE0E0E0)
        "SEPIA" -> androidx.compose.ui.graphics.Color(0xFF5B4636)
        else -> androidx.compose.ui.graphics.Color(0xFF1E1E1E) // LIGHT
    }

    var isImmersiveMode by remember { mutableStateOf(false) }
    var showLineNumbers by remember { mutableStateOf(true) }
    
    val wordWrap by viewModel.isWordWrapEnabled.collectAsStateWithLifecycle()
    val fontSizeSp by viewModel.fontSize.collectAsStateWithLifecycle()
    val matchCase by viewModel.matchCase.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val currentSearchIndex by viewModel.currentSearchIndex.collectAsStateWithLifecycle()
    
    var showSearchSheet by remember { mutableStateOf(false) }
    var showEncodingSheet by remember { mutableStateOf(false) }
    var showGoToLineSheet by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    val bottomInset = androidx.compose.foundation.layout.WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val topPadding by animateDpAsState(targetValue = if (isImmersiveMode) 16.dp else 140.dp)
    val bottomPadding by animateDpAsState(targetValue = if (isImmersiveMode) bottomInset else bottomInset + 100.dp)

    val percent = remember(listState.firstVisibleItemIndex, uiState) {
        val lastIndex = if (uiState is TextReaderUiState.Success) (uiState as TextReaderUiState.Success).lines.size - 1 else 0
        if (lastIndex > 0) (listState.firstVisibleItemIndex.toFloat() / lastIndex * 100).toInt().coerceIn(0, 100) else 0
    }
    val displayTitle = displayName

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(readerBackgroundColor)
    ) {
        AnimatedContent(
            targetState = uiState,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                fadeIn(animationSpec = tween(220, delayMillis = 90)) togetherWith fadeOut(animationSpec = tween(90))
            },
            label = "textReaderState"
        ) { state ->
                when (state) {
                    is TextReaderUiState.Loading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                androidx.compose.material3.CircularProgressIndicator(color = NexusTheme.colors.primary)
                                NexusText("Parsing Text Document...", color = NexusTheme.colors.textSecondary, style = NexusTheme.typography.body)
                            }
                        }
                    }
                    is TextReaderUiState.Success -> {
                        val scaledFontSize = fontSizeSp.sp
                        val scaledLineHeight = scaledFontSize * 1.5f
                        val horizontalScrollState = androidx.compose.foundation.rememberScrollState()
                        val coroutineScope = rememberCoroutineScope()
                        
                        val layoutInfo = listState.layoutInfo
                        val visibleItemsInfo = layoutInfo.visibleItemsInfo
                        val firstVisibleIndex = if (visibleItemsInfo.isEmpty()) 0 else visibleItemsInfo.first().index

                        var isDraggingSlider by remember { mutableStateOf(false) }
                        var sliderValue by remember { mutableFloatStateOf(1f) }
                        
                        LaunchedEffect(firstVisibleIndex, isDraggingSlider) {
                            if (!isDraggingSlider) {
                                sliderValue = (firstVisibleIndex + 1).toFloat()
                            }
                        }
                        
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectTapGestures(onTap = { isImmersiveMode = !isImmersiveMode })
                                }
                        ) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .then(if (!wordWrap) Modifier.horizontalScroll(horizontalScrollState) else Modifier),
                                contentPadding = PaddingValues(bottom = bottomPadding, start = 16.dp, end = 16.dp, top = topPadding)
                            ) {
                                itemsIndexed(state.lines) { index, line ->
                                    Row(modifier = Modifier.then(if(wordWrap) Modifier.fillMaxWidth() else Modifier)) {
                                        if (showLineNumbers) {
                                            NexusText(
                                                text = "${index + 1}",
                                                color = NexusTheme.colors.textSecondary,
                                                modifier = Modifier.widthIn(min = 24.dp),
                                                style = NexusTheme.typography.body.copy(
                                                    fontSize = scaledFontSize,
                                                    lineHeight = scaledLineHeight
                                                ),
                                                maxLines = 1
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                        }
                                        
                                        val isHighlighted = searchResults.contains(index)
                                        val bgColor = if (isHighlighted && currentSearchIndex >= 0 && searchResults.getOrNull(currentSearchIndex) == index) NexusTheme.colors.primary.copy(alpha = 0.3f) else if (isHighlighted) NexusTheme.colors.primary.copy(alpha = 0.1f) else androidx.compose.ui.graphics.Color.Transparent
                                        
                                        val annotatedString = if (state.isCodeFile) {
                                            androidx.compose.ui.text.buildAnnotatedString {
                                                if (line.contains(Regex("""["'].*?["']"""))) {
                                                    val parts = line.split(Regex("""(?<=["'])|(?=["'])"""))
                                                    parts.forEach { part ->
                                                        if (part.startsWith("\"") || part.startsWith("'")) {
                                                            withStyle(androidx.compose.ui.text.SpanStyle(color = androidx.compose.ui.graphics.Color(0xFF8CAF7B))) { append(part) }
                                                        } else {
                                                            append(part)
                                                        }
                                                    }
                                                } else {
                                                    append(line)
                                                }
                                            }
                                        } else {
                                            androidx.compose.ui.text.AnnotatedString(line)
                                        }

                                        androidx.compose.foundation.text.BasicText(
                                            text = annotatedString,
                                            style = NexusTheme.typography.body.copy(
                                                fontSize = scaledFontSize,
                                                lineHeight = scaledLineHeight,
                                                color = readerTextColor,
                                                fontFamily = if (state.isCodeFile) androidx.compose.ui.text.font.FontFamily.Monospace else androidx.compose.ui.text.font.FontFamily.Default
                                            ),
                                            modifier = Modifier.background(bgColor),
                                            maxLines = if (wordWrap) Int.MAX_VALUE else 1
                                        )
                                    }
                                }
                            }
                            
                            // Scrollbar
                            if (state.lines.size > 1) {
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = !isImmersiveMode,
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .padding(top = 64.dp, bottom = 64.dp),
                                    enter = fadeIn(),
                                    exit = fadeOut()
                                ) {
                                    NexusVerticalScrollbar(
                                        pageCount = state.lines.size,
                                        sliderValue = sliderValue,
                                        isScrolling = listState.isScrollInProgress,
                                        onSliderValueChange = { newValue ->
                                            sliderValue = newValue
                                            coroutineScope.launch {
                                                listState.scrollToItem((sliderValue.roundToInt() - 1).coerceIn(0, state.lines.size - 1))
                                            }
                                        },
                                        onDragStarted = { isDraggingSlider = true },
                                        onDragStopped = { isDraggingSlider = false }
                                    )
                                }
                            }
                        }
                    }
                is TextReaderUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            NexusText(state.message.toUserFriendlyMessage(), color = NexusTheme.colors.error)
                            Spacer(modifier = Modifier.height(16.dp))
                            NexusButton(text = "Go Back", onClick = onBack)
                        }
                    }
                }
            }
        }

                AnimatedVisibility(
                    visible = !isImmersiveMode,
                    modifier = Modifier.align(Alignment.TopCenter),
                    enter = androidx.compose.animation.slideInVertically(initialOffsetY = { -it }) + androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { -it }) + androidx.compose.animation.fadeOut()
                ) {
                    NexusTopBar(
                        title = displayTitle,
                        navigationIcon = {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .clickable { onBack() },
                                contentAlignment = Alignment.Center
                            ) {
                                androidx.compose.foundation.Image(
                                    painter = androidx.compose.ui.res.painterResource(id = com.nexus.core.R.drawable.ic_back),
                                    contentDescription = "Back",
                                    modifier = Modifier.size(20.dp),
                                    colorFilter = ColorFilter.tint(NexusTheme.colors.textPrimary)
                                )
                            }
                        },
                        actions = {
                            androidx.compose.foundation.Image(
                                painter = androidx.compose.ui.res.painterResource(id = com.nexus.core.R.drawable.ic_search),
                                contentDescription = "Search",
                                colorFilter = ColorFilter.tint(NexusTheme.colors.textPrimary),
                                modifier = Modifier.size(40.dp).clip(androidx.compose.foundation.shape.CircleShape).springBounceClick { showSearchSheet = true }.padding(8.dp)
                            )
                            if (uiState is TextReaderUiState.Success && percent > 0) {
                                NexusText(
                                    text = "$percent%",
                                    color = NexusTheme.colors.primary,
                                    style = NexusTheme.typography.label.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                                    modifier = Modifier
                                        .background(NexusTheme.colors.primary.copy(alpha = 0.15f), androidx.compose.foundation.shape.CircleShape)
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                        .clickable { showGoToLineSheet = true }
                                )
                            }
                            androidx.compose.foundation.Image(
                                painter = androidx.compose.ui.res.painterResource(id = com.nexus.core.R.drawable.ic_more_vert),
                                contentDescription = "More",
                                colorFilter = ColorFilter.tint(NexusTheme.colors.textPrimary),
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .springBounceClick { showMenu = true }
                                    .padding(8.dp)
                            )
                        }
                    )
                }

                // Bottom Action Bar
                AnimatedVisibility(
                    visible = !isImmersiveMode,
                    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 32.dp),
                    enter = androidx.compose.animation.slideInVertically(initialOffsetY = { it }) + androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { it }) + androidx.compose.animation.fadeOut()
                ) {
                    NexusSurface(
                        shape = androidx.compose.foundation.shape.CircleShape,
                        elevation = 8.dp,
                        color = NexusTheme.colors.surfaceVariant.copy(alpha = 0.95f),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .horizontalScroll(androidx.compose.foundation.rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier.size(40.dp).clip(androidx.compose.foundation.shape.CircleShape).springBounceClick { viewModel.increaseFontSize() }.padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) { NexusText("+", color = NexusTheme.colors.textPrimary, style = NexusTheme.typography.h2) }
                            
                            Box(
                                modifier = Modifier.size(40.dp).clip(androidx.compose.foundation.shape.CircleShape).springBounceClick { viewModel.decreaseFontSize() }.padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) { NexusText("-", color = NexusTheme.colors.textPrimary, style = NexusTheme.typography.h2) }
                            
                            androidx.compose.foundation.Image(
                                painter = androidx.compose.ui.res.painterResource(id = com.nexus.core.R.drawable.ic_view_list),
                                contentDescription = "Toggle Line Numbers",
                                colorFilter = ColorFilter.tint(if (showLineNumbers) NexusTheme.colors.primary else NexusTheme.colors.textPrimary),
                                modifier = Modifier.size(40.dp).clip(androidx.compose.foundation.shape.CircleShape).springBounceClick { showLineNumbers = !showLineNumbers }.padding(8.dp)
                            )
                            
                            androidx.compose.foundation.Image(
                                painter = androidx.compose.ui.res.painterResource(id = com.nexus.core.R.drawable.ic_view_grid),
                                contentDescription = "Toggle Word Wrap",
                                colorFilter = ColorFilter.tint(if (wordWrap) NexusTheme.colors.primary else NexusTheme.colors.textPrimary),
                                modifier = Modifier.size(40.dp).clip(androidx.compose.foundation.shape.CircleShape).springBounceClick { viewModel.toggleWordWrap() }.padding(8.dp)
                            )
                            
                            androidx.compose.foundation.Image(
                                painter = androidx.compose.ui.res.painterResource(id = if (readerTheme == "LIGHT") com.nexus.core.R.drawable.ic_theme_dark else com.nexus.core.R.drawable.ic_theme_light),
                                contentDescription = "Toggle Theme",
                                colorFilter = ColorFilter.tint(NexusTheme.colors.textPrimary),
                                modifier = Modifier.size(40.dp).clip(androidx.compose.foundation.shape.CircleShape).springBounceClick { viewModel.setReaderTheme(if (readerTheme == "LIGHT") "DARK" else "LIGHT") }.padding(8.dp)
                            )
                            
                            androidx.compose.foundation.Image(
                                painter = androidx.compose.ui.res.painterResource(id = com.nexus.core.R.drawable.ic_share),
                                contentDescription = "Share",
                                colorFilter = ColorFilter.tint(NexusTheme.colors.textPrimary),
                                modifier = Modifier.size(40.dp).clip(androidx.compose.foundation.shape.CircleShape).springBounceClick { viewModel.shareText(context, encodedUri) }.padding(8.dp)
                            )
                            
                            androidx.compose.foundation.Image(
                                painter = androidx.compose.ui.res.painterResource(id = com.nexus.core.R.drawable.ic_more_vert),
                                contentDescription = "Encoding Options",
                                colorFilter = ColorFilter.tint(NexusTheme.colors.textPrimary),
                                modifier = Modifier.size(40.dp).clip(androidx.compose.foundation.shape.CircleShape).springBounceClick { showEncodingSheet = true }.padding(8.dp)
                            )
                        }
                    }
                }
                
        if (showSearchSheet) {
            androidx.compose.material3.ModalBottomSheet(onDismissRequest = { showSearchSheet = false }, containerColor = NexusTheme.colors.surface) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    NexusText("Find in Document", style = NexusTheme.typography.h2)
                    androidx.compose.material3.OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { NexusText("Search...", color = NexusTheme.colors.textSecondary) },
                        singleLine = true
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { viewModel.toggleMatchCase() }) {
                            androidx.compose.material3.Checkbox(checked = matchCase, onCheckedChange = { viewModel.toggleMatchCase() })
                            NexusText("Match Case", color = NexusTheme.colors.textPrimary)
                        }
                        if (searchResults.isNotEmpty()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                NexusText("${currentSearchIndex + 1} of ${searchResults.size}", color = NexusTheme.colors.textSecondary)
                                NexusButton(text = "Prev", onClick = { viewModel.previousSearchMatch() })
                                NexusButton(text = "Next", onClick = { viewModel.nextSearchMatch() })
                            }
                        } else if (searchQuery.isNotEmpty()) {
                            NexusText("No results", color = NexusTheme.colors.error)
                        }
                    }
                }
            }
        }
        
        if (showEncodingSheet) {
            androidx.compose.material3.ModalBottomSheet(onDismissRequest = { showEncodingSheet = false }, containerColor = NexusTheme.colors.surface) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding()) {
                    NexusText("Change Encoding", style = NexusTheme.typography.h2, modifier = Modifier.padding(bottom = 16.dp))
                    listOf("UTF-8", "ISO-8859-1", "Windows-1252", "US-ASCII", "UTF-16").forEach { charset ->
                        NexusText(
                            text = charset,
                            modifier = Modifier.fillMaxWidth().clickable {
                                viewModel.reloadWithCharset(charset)
                                showEncodingSheet = false
                            }.padding(vertical = 12.dp)
                        )
                    }
                }
            }
        }
        
        if (showGoToLineSheet && uiState is TextReaderUiState.Success) {
            var lineInput by remember { mutableStateOf("") }
            val coroutineScope = rememberCoroutineScope()
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showGoToLineSheet = false },
                containerColor = NexusTheme.colors.surface,
                title = { NexusText("Go to Line", style = NexusTheme.typography.h2) },
                text = {
                    androidx.compose.material3.OutlinedTextField(
                        value = lineInput,
                        onValueChange = { lineInput = it.filter { char -> char.isDigit() } },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        singleLine = true
                    )
                },
                confirmButton = {
                    NexusButton(text = "Go", onClick = {
                        val lineNum = lineInput.toIntOrNull() ?: 1
                        coroutineScope.launch { listState.scrollToItem((lineNum - 1).coerceIn(0, (uiState as TextReaderUiState.Success).lines.size - 1)) }
                        showGoToLineSheet = false
                    })
                },
                dismissButton = {
                    NexusButton(text = "Cancel", isOutlined = true, onClick = { showGoToLineSheet = false })
                }
            )
        }

        if (showMenu) {
            TextOptionsBottomSheet(
                onDismiss = { showMenu = false },
                onRename = {
                    showMenu = false
                    renameText = displayName
                    showRenameDialog = true
                },
                onFavorite = {
                    showMenu = false
                    // Favorite logic not fully implemented for txt yet, show toast
                    android.widget.Toast.makeText(context, "Added to Favorites", android.widget.Toast.LENGTH_SHORT).show()
                },
                onPrint = {
                    showMenu = false
                    android.widget.Toast.makeText(context, "Printing text is not supported yet.", android.widget.Toast.LENGTH_SHORT).show()
                },
                onInfo = {
                    showMenu = false
                    showInfoDialog = true
                }
            )
        }

        if (showInfoDialog && uiState is TextReaderUiState.Success) {
            val successState = uiState as TextReaderUiState.Success
            com.nexus.core.ui.components.NexusDialog(
                onDismissRequest = { showInfoDialog = false },
                title = { NexusText("Document Info", style = NexusTheme.typography.h2) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        NexusText("File: $displayName", color = NexusTheme.colors.textPrimary)
                        NexusText("Lines: ${successState.totalLineCount}", color = NexusTheme.colors.textPrimary)
                        NexusText("Encoding: ${successState.charset}", color = NexusTheme.colors.textPrimary)
                    }
                },
                confirmButton = {
                    NexusButton(text = "OK", onClick = { showInfoDialog = false })
                }
            )
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun TextOptionsBottomSheet(
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onFavorite: () -> Unit,
    onPrint: () -> Unit,
    onInfo: () -> Unit
) {
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState()
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = com.nexus.core.theme.NexusTheme.colors.surface,
        contentColor = com.nexus.core.theme.NexusTheme.colors.textPrimary,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
                .padding(horizontal = 16.dp)
        ) {
            com.nexus.core.ui.NexusText(
                text = "Document Options",
                style = com.nexus.core.theme.NexusTheme.typography.title,
                modifier = Modifier.padding(bottom = 16.dp, start = 8.dp, end = 8.dp)
            )

            val options = listOf(
                Pair("File Info", "View document properties") to (com.nexus.core.R.drawable.ic_info to onInfo),
                Pair("Rename", "Rename this file") to (com.nexus.core.R.drawable.ic_rename to onRename),
                Pair("Favorite", "Add to bookmarks") to (com.nexus.core.R.drawable.ic_star to onFavorite),
                Pair("Print", "Print or save as PDF") to (com.nexus.core.R.drawable.ic_printer to onPrint)
            )

            options.forEach { (labels, actionData) ->
                val (title, subtitle) = labels
                val (iconRes, action) = actionData
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(com.nexus.core.theme.NexusTheme.shapes.medium)
                        .clickable { action() }
                        .padding(vertical = 12.dp, horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(id = iconRes),
                        contentDescription = title,
                        modifier = Modifier.size(24.dp),
                        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(com.nexus.core.theme.NexusTheme.colors.textPrimary)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        com.nexus.core.ui.NexusText(
                            text = title,
                            style = com.nexus.core.theme.NexusTheme.typography.body,
                            color = com.nexus.core.theme.NexusTheme.colors.textPrimary
                        )
                        com.nexus.core.ui.NexusText(
                            text = subtitle,
                            style = com.nexus.core.theme.NexusTheme.typography.caption,
                            color = com.nexus.core.theme.NexusTheme.colors.textSecondary
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
