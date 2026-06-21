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
import com.nexus.core.ui.components.NexusPillTopBar
import java.net.URLDecoder
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.graphics.ColorFilter
import com.nexus.core.ui.animations.shimmerEffect
import com.nexus.core.ui.animations.springBounceClick
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

    var isImmersiveMode by remember { mutableStateOf(false) }
    var fontSizeMultiplier by remember { mutableFloatStateOf(1f) }
    var showLineNumbers by remember { mutableStateOf(true) }
    var wordWrap by remember { mutableStateOf(true) }
    val bottomInset = androidx.compose.foundation.layout.WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val topPadding by animateDpAsState(targetValue = if (isImmersiveMode) 16.dp else 140.dp)
    val bottomPadding by animateDpAsState(targetValue = if (isImmersiveMode) bottomInset else bottomInset + 100.dp)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NexusTheme.colors.background)
    ) {
        AnimatedContent(
            targetState = uiState,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
            },
            label = "textReaderState"
        ) { state ->
                when (state) {
                    is TextReaderUiState.Loading -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(15) { index ->
                                val widthFraction = if (index % 3 == 0) 0.9f else if (index % 3 == 1) 0.7f else 1f
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(widthFraction)
                                        .height(16.dp)
                                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                                        .shimmerEffect()
                                )
                            }
                        }
                    }
                    is TextReaderUiState.Success -> {
                        val scaledFontSize = 14.sp * fontSizeMultiplier
                        val scaledLineHeight = scaledFontSize * 1.5f
                        val horizontalScrollState = androidx.compose.foundation.rememberScrollState()
                        
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
                                                modifier = Modifier.width(48.dp),
                                                style = NexusTheme.typography.body.copy(
                                                    fontSize = scaledFontSize,
                                                    lineHeight = scaledLineHeight
                                                ),
                                                maxLines = 1
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                        }
                                        NexusText(
                                            text = line,
                                            color = NexusTheme.colors.textPrimary,
                                            style = NexusTheme.typography.body.copy(
                                                fontSize = scaledFontSize,
                                                lineHeight = scaledLineHeight
                                            ),
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
                                        .padding(end = 8.dp, top = 64.dp, bottom = 64.dp),
                                    enter = fadeIn(),
                                    exit = fadeOut()
                                ) {
                                    val layoutInfo = listState.layoutInfo
                                    val visibleItemsInfo = layoutInfo.visibleItemsInfo
                                    val firstVisibleIndex = if (visibleItemsInfo.isEmpty()) 0 else visibleItemsInfo.first().index
                                    val fraction = if (state.lines.size > 1) firstVisibleIndex.toFloat() / (state.lines.size - 1) else 0f
                                    
                                    BoxWithConstraints(
                                        modifier = Modifier
                                            .fillMaxHeight(0.6f)
                                            .width(32.dp)
                                    ) {
                                        val trackHeight = constraints.maxHeight.toFloat()
                                        val thumbHeightPx = with(androidx.compose.ui.platform.LocalDensity.current) { 48.dp.toPx() }
                                        val maxScroll = (trackHeight - thumbHeightPx).coerceAtLeast(0f)
                                        val thumbOffsetPx = fraction * maxScroll
                                        
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.Center)
                                                .fillMaxHeight()
                                                .width(4.dp)
                                                .background(NexusTheme.colors.surfaceVariant.copy(alpha = 0.3f), androidx.compose.foundation.shape.CircleShape)
                                        )
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopCenter)
                                                .offset { androidx.compose.ui.unit.IntOffset(0, thumbOffsetPx.toInt()) }
                                                .size(width = 24.dp, height = 48.dp)
                                                .background(NexusTheme.colors.primary, androidx.compose.foundation.shape.CircleShape)
                                        )
                                    }
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
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    NexusPillTopBar(
                        title = displayName,
                        outerVerticalPadding = 4.dp,
                        innerVerticalPadding = 8.dp,
                        iconSize = 40.dp,
                        navigationIcon = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
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
                        }
                    )
                }

                // Bottom Action Bar
                AnimatedVisibility(
                    visible = !isImmersiveMode,
                    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 32.dp),
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    NexusSurface(
                        shape = androidx.compose.foundation.shape.CircleShape,
                        elevation = 8.dp,
                        color = NexusTheme.colors.surfaceVariant.copy(alpha = 0.95f),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .springBounceClick { fontSizeMultiplier += 0.1f }
                                    .padding(12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                NexusText("+", color = NexusTheme.colors.textPrimary, style = NexusTheme.typography.h2)
                            }
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .springBounceClick { if(fontSizeMultiplier > 0.5f) fontSizeMultiplier -= 0.1f }
                                    .padding(12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                NexusText("-", color = NexusTheme.colors.textPrimary, style = NexusTheme.typography.h2)
                            }
                            androidx.compose.foundation.Image(
                                painter = androidx.compose.ui.res.painterResource(id = com.nexus.core.R.drawable.ic_view_list),
                                contentDescription = "Toggle Line Numbers",
                                colorFilter = ColorFilter.tint(if (showLineNumbers) NexusTheme.colors.primary else NexusTheme.colors.textPrimary),
                                modifier = Modifier.size(48.dp).clip(androidx.compose.foundation.shape.CircleShape).springBounceClick { showLineNumbers = !showLineNumbers }.padding(12.dp)
                            )
                            androidx.compose.foundation.Image(
                                painter = androidx.compose.ui.res.painterResource(id = com.nexus.core.R.drawable.ic_view_grid),
                                contentDescription = "Toggle Word Wrap",
                                colorFilter = ColorFilter.tint(if (wordWrap) NexusTheme.colors.primary else NexusTheme.colors.textPrimary),
                                modifier = Modifier.size(48.dp).clip(androidx.compose.foundation.shape.CircleShape).springBounceClick { wordWrap = !wordWrap }.padding(12.dp)
                            )
                        }
                    }
                }
    }
}
