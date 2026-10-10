package com.nexus.feature.reader.text

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusSurface
import com.nexus.core.ui.NexusText
import com.nexus.core.ui.animations.shimmerEffect
import com.nexus.core.ui.animations.springBounceClick
import com.nexus.core.ui.components.NexusButton
import com.nexus.core.ui.components.NexusTopBar
import com.nexus.core.ui.components.NexusVerticalScrollbar
import com.nexus.core.ui.utils.glassBackground
import com.nexus.core.util.toUserFriendlyMessage
import kotlinx.coroutines.launch
import java.net.URLDecoder
import kotlin.math.roundToInt
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
    val readerColors = when (readerTheme) {
        "DARK" -> com.nexus.core.theme.darkNexusColors
        "SEPIA" -> com.nexus.core.theme.sepiaNexusColors
        else -> com.nexus.core.theme.lightNexusColors
    }
    val readerBackgroundColor = readerColors.background
    val readerTextColor = readerColors.textPrimary

    val keepScreenAwake by viewModel.keepScreenAwake.collectAsStateWithLifecycle()

    DisposableEffect(keepScreenAwake) {
        val window = (context as? android.app.Activity)?.window
        if (keepScreenAwake) {
            window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    var isImmersiveMode by remember { mutableStateOf(false) }
    var showLineNumbers by remember { mutableStateOf(true) }
    
    val wordWrap by viewModel.isWordWrapEnabled.collectAsStateWithLifecycle()
    val fontSizeSp by viewModel.fontSize.collectAsStateWithLifecycle()
    val matchCase by viewModel.matchCase.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val currentSearchIndex by viewModel.currentSearchIndex.collectAsStateWithLifecycle()
    val isStarred by viewModel.isStarred.collectAsStateWithLifecycle()
    
    var isSearchMode by remember { mutableStateOf(false) }
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

    LaunchedEffect(currentSearchIndex, searchResults) {
        if (currentSearchIndex in searchResults.indices) {
            val targetLine = searchResults[currentSearchIndex]
            listState.animateScrollToItem(targetLine)
        }
    }

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
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.md)) {
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
                                            SyntaxHighlighter.highlight(
                                                line = line,
                                                extension = state.fileExtension,
                                                colors = readerColors
                                            )
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
                                        .padding(top = NexusTheme.spacing.xxl, bottom = NexusTheme.spacing.xxl),
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

            // Bottom UI Container
            AnimatedVisibility(
                visible = !isImmersiveMode && uiState is TextReaderUiState.Success,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = expandVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) + fadeIn(),
                exit = shrinkVertically(animationSpec = spring(stiffness = Spring.StiffnessMedium)) + fadeOut()
            ) {
                val successState = uiState as TextReaderUiState.Success
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs)
                ) {
                    // Floating Position Viewer Pill
                    if (successState.lines.size > 1) {
                        Box(
                            modifier = Modifier
                                .shadow(
                                    elevation = 8.dp,
                                    shape = NexusTheme.shapes.pill,
                                    spotColor = NexusTheme.colors.primary.copy(alpha = 0.16f),
                                    ambientColor = Color.Black.copy(alpha = 0.10f)
                                )
                                .clip(NexusTheme.shapes.pill)
                                .background(NexusTheme.colors.surface)
                                .border(
                                    width = 1.dp,
                                    color = NexusTheme.colors.divider.copy(alpha = 0.7f),
                                    shape = NexusTheme.shapes.pill
                                )
                                .springBounceClick { showGoToLineSheet = true }
                                .padding(horizontal = NexusTheme.spacing.md, vertical = NexusTheme.spacing.xs)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs)
                            ) {
                                NexusText(
                                    text = "Line ${listState.firstVisibleItemIndex + 1}",
                                    style = NexusTheme.typography.body.copy(fontWeight = FontWeight.Bold, fontSize = 13.5.sp),
                                    color = NexusTheme.colors.primary
                                )
                                NexusText(
                                    text = "of ${successState.lines.size} ($percent%)",
                                    style = NexusTheme.typography.label,
                                    color = NexusTheme.colors.textSecondary
                                )
                            }
                        }
                    }

                    val isDarkTheme = isSystemInDarkTheme()

                    // Floating Dock Toolbar Container
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentWidth(Alignment.CenterHorizontally)
                            .widthIn(max = 560.dp)
                            .navigationBarsPadding()
                            .padding(horizontal = NexusTheme.spacing.sm, vertical = NexusTheme.spacing.xs)
                            .shadow(
                                elevation = 16.dp,
                                shape = NexusTheme.shapes.large,
                                spotColor = NexusTheme.colors.primary.copy(alpha = if (isDarkTheme) 0.28f else 0.14f),
                                ambientColor = Color.Black.copy(alpha = if (isDarkTheme) 0.40f else 0.10f)
                            )
                            .clip(NexusTheme.shapes.large)
                            .background(NexusTheme.colors.surface.copy(alpha = 0.95f))
                            .border(
                                width = 1.dp,
                                color = NexusTheme.colors.divider.copy(alpha = 0.65f),
                                shape = NexusTheme.shapes.large
                            )
                            .padding(horizontal = NexusTheme.spacing.xxs, vertical = NexusTheme.spacing.xxs)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            // Font Size Decrease (-)
                            TextBottomBarActionItem(
                                modifier = Modifier.weight(1f),
                                label = "Size -",
                                contentDescription = "Decrease Font Size",
                                tint = NexusTheme.colors.textPrimary,
                                onClick = { viewModel.decreaseFontSize() },
                                customIcon = {
                                    NexusText(
                                        text = "A-",
                                        color = NexusTheme.colors.textPrimary,
                                        style = NexusTheme.typography.body.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    )
                                }
                            )

                            // Font Size Increase (+)
                            TextBottomBarActionItem(
                                modifier = Modifier.weight(1f),
                                label = "Size +",
                                contentDescription = "Increase Font Size",
                                tint = NexusTheme.colors.textPrimary,
                                onClick = { viewModel.increaseFontSize() },
                                customIcon = {
                                    NexusText(
                                        text = "A+",
                                        color = NexusTheme.colors.textPrimary,
                                        style = NexusTheme.typography.body.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                    )
                                }
                            )

                            // Search
                            TextBottomBarActionItem(
                                modifier = Modifier.weight(1f),
                                iconPainter = androidx.compose.ui.res.painterResource(id = com.nexus.core.R.drawable.ic_search),
                                label = "Search",
                                contentDescription = "Search",
                                tint = if (isSearchMode) NexusTheme.colors.primary else NexusTheme.colors.textPrimary,
                                isActive = isSearchMode,
                                activeBadgeColor = NexusTheme.colors.primary.copy(alpha = 0.15f),
                                onClick = { isSearchMode = !isSearchMode }
                            )

                            // Word Wrap Toggle
                            TextBottomBarActionItem(
                                modifier = Modifier.weight(1f),
                                iconPainter = androidx.compose.ui.res.painterResource(id = com.nexus.core.R.drawable.ic_view_list),
                                label = "Wrap",
                                contentDescription = "Word Wrap",
                                tint = if (wordWrap) NexusTheme.colors.primary else NexusTheme.colors.textPrimary,
                                isActive = wordWrap,
                                activeBadgeColor = NexusTheme.colors.primary.copy(alpha = 0.15f),
                                onClick = { viewModel.toggleWordWrap() }
                            )

                            // Line Numbers Toggle
                            TextBottomBarActionItem(
                                modifier = Modifier.weight(1f),
                                iconPainter = androidx.compose.ui.res.painterResource(id = com.nexus.core.R.drawable.ic_view_grid),
                                label = "Lines",
                                contentDescription = "Line Numbers",
                                tint = if (showLineNumbers) NexusTheme.colors.primary else NexusTheme.colors.textPrimary,
                                isActive = showLineNumbers,
                                activeBadgeColor = NexusTheme.colors.primary.copy(alpha = 0.15f),
                                onClick = { showLineNumbers = !showLineNumbers }
                            )

                            // Theme Toggle (Light -> Sepia -> Dark)
                            TextBottomBarActionItem(
                                modifier = Modifier.weight(1f),
                                iconPainter = androidx.compose.ui.res.painterResource(
                                    id = when (readerTheme) {
                                        "DARK" -> com.nexus.core.R.drawable.ic_theme_dark
                                        else -> com.nexus.core.R.drawable.ic_theme_light
                                    }
                                ),
                                label = when (readerTheme) {
                                    "DARK" -> "Dark"
                                    "SEPIA" -> "Sepia"
                                    else -> "Light"
                                },
                                contentDescription = "Theme",
                                tint = NexusTheme.colors.textPrimary,
                                onClick = {
                                    val next = when (readerTheme) {
                                        "LIGHT" -> "SEPIA"
                                        "SEPIA" -> "DARK"
                                        else -> "LIGHT"
                                    }
                                    viewModel.setReaderTheme(next)
                                }
                            )

                            // Go to Line
                            TextBottomBarActionItem(
                                modifier = Modifier.weight(1f),
                                iconPainter = androidx.compose.ui.res.painterResource(id = com.nexus.core.R.drawable.ic_arrow_right),
                                label = "Jump",
                                contentDescription = "Go to Line",
                                tint = NexusTheme.colors.textPrimary,
                                onClick = { showGoToLineSheet = true }
                            )

                            // Encoding Sheet
                            TextBottomBarActionItem(
                                modifier = Modifier.weight(1f),
                                iconPainter = androidx.compose.ui.res.painterResource(id = com.nexus.core.R.drawable.ic_world),
                                label = "Encoding",
                                contentDescription = "Encoding",
                                tint = NexusTheme.colors.textPrimary,
                                onClick = { showEncodingSheet = true }
                            )
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
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
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
                                    colorFilter = ColorFilter.tint(
                                        if (isSearchMode) NexusTheme.colors.primary else NexusTheme.colors.textPrimary
                                    ),
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                        .springBounceClick { isSearchMode = !isSearchMode }
                                        .padding(NexusTheme.spacing.xs)
                                )
                                if (uiState is TextReaderUiState.Success && percent > 0) {
                                    NexusText(
                                        text = "$percent%",
                                        color = NexusTheme.colors.primary,
                                        style = NexusTheme.typography.label.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                                        modifier = Modifier
                                            .background(NexusTheme.colors.primary.copy(alpha = 0.15f), androidx.compose.foundation.shape.CircleShape)
                                            .padding(horizontal = NexusTheme.spacing.xs, vertical = NexusTheme.spacing.xxs)
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
                                        .padding(NexusTheme.spacing.xs)
                                )
                            }
                        )

                        com.nexus.core.ui.components.ReaderSearchBar(
                            visible = isSearchMode && !isImmersiveMode,
                            query = searchQuery,
                            onQueryChange = { viewModel.setSearchQuery(it) },
                            matchCount = searchResults.size,
                            currentMatchIndex = currentSearchIndex,
                            onPreviousMatch = { viewModel.previousSearchMatch() },
                            onNextMatch = { viewModel.nextSearchMatch() },
                            onClose = {
                                isSearchMode = false
                                viewModel.setSearchQuery("")
                            }
                        )
                    }
                }
        
        if (showEncodingSheet) {
            androidx.compose.material3.ModalBottomSheet(onDismissRequest = { showEncodingSheet = false }, containerColor = NexusTheme.colors.surface) {
                Column(modifier = Modifier.fillMaxWidth().padding(NexusTheme.spacing.md).navigationBarsPadding()) {
                    NexusText("Change Encoding", style = NexusTheme.typography.h2, modifier = Modifier.padding(bottom = NexusTheme.spacing.md))
                    listOf("UTF-8", "ISO-8859-1", "Windows-1252", "US-ASCII", "UTF-16").forEach { charset ->
                        NexusText(
                            text = charset,
                            modifier = Modifier.fillMaxWidth().clickable {
                                viewModel.reloadWithCharset(charset)
                                showEncodingSheet = false
                            }.padding(vertical = NexusTheme.spacing.sm)
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
                displayName = displayName,
                lineCount = (uiState as? TextReaderUiState.Success)?.totalLineCount ?: 0,
                isStarred = isStarred,
                onDismiss = { showMenu = false },
                onRename = {
                    showMenu = false
                    renameText = displayName
                    showRenameDialog = true
                },
                onFavorite = {
                    showMenu = false
                    viewModel.toggleFavorite { nowStarred ->
                        val msg = if (nowStarred) "Added to Favorites" else "Removed from Favorites"
                        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
                onShare = {
                    showMenu = false
                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_TEXT, (uiState as? TextReaderUiState.Success)?.lines?.joinToString("\n") ?: "")
                        putExtra(android.content.Intent.EXTRA_TITLE, displayName)
                    }
                    context.startActivity(android.content.Intent.createChooser(intent, "Share Document"))
                },
                onPrint = {
                    showMenu = false
                    viewModel.printText(context, displayName)
                },
                onInfo = {
                    showMenu = false
                    showInfoDialog = true
                }
            )
        }

        if (showInfoDialog && uiState is TextReaderUiState.Success) {
            val successState = uiState as TextReaderUiState.Success
            com.nexus.core.ui.components.NexusFileDetailsSheet(
                fileName = displayName,
                uri = encodedUri,
                docType = com.nexus.core.navigation.DocumentType.TXT,
                customDetails = listOf(
                    com.nexus.core.ui.components.FileDetailItem(
                        iconRes = com.nexus.core.R.drawable.ic_book,
                        label = "Lines",
                        value = "${successState.totalLineCount} lines"
                    ),
                    com.nexus.core.ui.components.FileDetailItem(
                        iconRes = com.nexus.core.R.drawable.ic_info,
                        label = "Encoding",
                        value = successState.charset
                    )
                ),
                onDismissRequest = { showInfoDialog = false }
            )
        }
    }
}

@Composable
private fun TextOptionsBottomSheet(
    displayName: String,
    lineCount: Int,
    isStarred: Boolean,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onFavorite: () -> Unit,
    onShare: () -> Unit,
    onPrint: () -> Unit,
    onInfo: () -> Unit
) {
    val metaText = if (lineCount > 0) "Text Document · $lineCount lines" else "Text Document · TXT"

    val group1 = listOf(
        com.nexus.core.ui.components.DocumentOptionItem(
            title = "File info",
            subtitle = "View document properties",
            iconRes = com.nexus.core.R.drawable.ic_info,
            onClick = onInfo
        ),
        com.nexus.core.ui.components.DocumentOptionItem(
            title = "Rename",
            subtitle = "Rename this file",
            iconRes = com.nexus.core.R.drawable.ic_rename,
            onClick = onRename
        ),
        com.nexus.core.ui.components.DocumentOptionItem(
            title = if (isStarred) "Starred" else "Star file",
            subtitle = "Add or remove bookmark",
            iconRes = if (isStarred) com.nexus.core.R.drawable.ic_star_filled else com.nexus.core.R.drawable.ic_star,
            isStarred = isStarred,
            showChevron = false,
            onClick = onFavorite
        )
    )

    val group2 = listOf(
        com.nexus.core.ui.components.DocumentOptionItem(
            title = "Share",
            subtitle = "Share this document",
            iconRes = com.nexus.core.R.drawable.ic_share,
            onClick = onShare
        ),
        com.nexus.core.ui.components.DocumentOptionItem(
            title = "Print",
            subtitle = "Print or save as PDF",
            iconRes = com.nexus.core.R.drawable.ic_printer,
            onClick = onPrint
        )
    )

    com.nexus.core.ui.components.DocumentOptionsBottomSheet(
        sheetTitle = "Document Options",
        fileName = displayName,
        fileMeta = metaText,
        docType = com.nexus.core.navigation.DocumentType.TXT,
        groups = listOf(group1, group2),
        onDismiss = onDismiss
    )
}

@Composable
private fun TextBottomBarActionItem(
    label: String,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconPainter: Painter? = null,
    customIcon: (@Composable () -> Unit)? = null,
    isActive: Boolean = false,
    activeBadgeColor: Color = NexusTheme.colors.primary.copy(alpha = 0.14f),
    enabled: Boolean = true
) {
    val haptic = LocalHapticFeedback.current
    val animatedBadgeColor by animateColorAsState(
        targetValue = if (isActive) activeBadgeColor else Color.Transparent,
        animationSpec = tween(200),
        label = "textBadgeColor"
    )
    val animatedTint by animateColorAsState(
        targetValue = if (isActive) tint else if (!enabled) NexusTheme.colors.textSecondary.copy(alpha = 0.35f) else NexusTheme.colors.textPrimary.copy(alpha = 0.85f),
        animationSpec = tween(200),
        label = "textIconTint"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .clip(NexusTheme.shapes.medium)
            .springBounceClick(enabled = enabled) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 1.dp, vertical = NexusTheme.spacing.xxs)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(animatedBadgeColor),
            contentAlignment = Alignment.Center
        ) {
            if (customIcon != null) {
                customIcon()
            } else if (iconPainter != null) {
                androidx.compose.foundation.Image(
                    painter = iconPainter,
                    contentDescription = contentDescription,
                    colorFilter = ColorFilter.tint(animatedTint),
                    modifier = Modifier.size(19.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        NexusText(
            text = label,
            style = NexusTheme.typography.caption.copy(
                fontSize = 10.sp,
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium,
                letterSpacing = 0.1.sp
            ),
            color = animatedTint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
