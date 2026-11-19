package com.nexus.feature.dashboard

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.nexus.core.theme.NexusFontFamily
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusText
import com.nexus.core.ui.animations.springBounceClick
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * PDF Tools Hub screen matching the precision HTML template specification.
 * Dynamically synchronized with app settings & active theme (Light, Dark/AMOLED, Forest, Sunset, etc.).
 *
 * Highlights:
 * - Full-bleed sticky top bar with logo squircle, brand title, and "Offline" privacy pill.
 * - Pill-shaped search bar with live multi-word filtering, '/' indicator, and quick-clear.
 * - Interactive category chips with dynamic tool counts and smooth active states.
 * - Responsive 2-column card grid on phones (3-4 columns on tablet/desktop).
 * - Custom squircle tool icon containers, clear typography hierarchy, and subtle forward affordance.
 * - Seamless theme integration without hardcoded color issues or boxiness.
 * - Full clearance for floating bottom navigation bar.
 */
@Composable
fun ToolsScreen(
    onNavigateToTool: (String) -> Unit,
    onBack: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf(ToolCategory.ALL) }
    var toastMessage by remember { mutableStateOf<String?>(null) }

    fun showToast(message: String) {
        toastMessage = message
        coroutineScope.launch {
            delay(2800)
            if (toastMessage == message) {
                toastMessage = null
            }
        }
    }

    // Dynamic keyword & category filtering (multi-word matching across title, desc, short name, keywords)
    val filteredTools = remember(searchQuery, selectedCategory) {
        val q = searchQuery.trim().lowercase()
        val words = if (q.isBlank()) emptyList() else q.split(Regex("\\s+"))

        toolsList.filter { tool ->
            val matchesCategory = selectedCategory == ToolCategory.ALL || tool.category == selectedCategory
            val matchesQuery = if (words.isEmpty()) {
                true
            } else {
                val searchableText = (tool.title + " " + tool.subtitle + " " + tool.description + " " + tool.keywords + " " + tool.category.name).lowercase()
                words.all { searchableText.contains(it) }
            }
            matchesCategory && matchesQuery
        }
    }

    // Dynamic category tool counts
    val categoryCounts = remember {
        ToolCategory.entries.associateWith { cat ->
            if (cat == ToolCategory.ALL) toolsList.size
            else toolsList.count { it.category == cat }
        }
    }

    val colors = NexusTheme.colors

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        // ─── Subtle dot-grid background accent (matching HTML body::before) ───
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(480.dp)
        ) {
            val dotColor = colors.divider.copy(alpha = 0.38f)
            val dotRadius = 1.1.dp.toPx()
            val spacing = 22.dp.toPx()
            val width = size.width
            val height = size.height

            var x = spacing / 2
            while (x < width) {
                var y = spacing / 2
                while (y < height) {
                    val alphaFade = (1f - (y / height)).coerceIn(0f, 1f)
                    drawCircle(
                        color = dotColor.copy(alpha = dotColor.alpha * alphaFade),
                        radius = dotRadius,
                        center = Offset(x, y)
                    )
                    y += spacing
                }
                x += spacing
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // ─── 1. Sticky Top Bar (Full-bleed, themed blur/solid backdrop) ─────
            val isScrolled by remember { derivedStateOf { scrollState.value > 12 } }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.background.copy(alpha = 0.96f))
                    .border(
                        width = if (isScrolled) 1.dp else 0.dp,
                        color = if (isScrolled) colors.divider.copy(alpha = 0.6f) else Color.Transparent
                    )
                    .padding(
                        top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 6.dp,
                        start = 20.dp,
                        end = 20.dp,
                        bottom = 12.dp
                    )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Document Logo Squircle
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(colors.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Description,
                            contentDescription = null,
                            tint = colors.onPrimary,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    NexusText(
                        text = "Tools",
                        style = NexusTheme.typography.title.copy(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.4).sp
                        ),
                        color = colors.textPrimary
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    // Minimalist "Offline" Pill Button
                    Box(
                        modifier = Modifier
                            .springBounceClick {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                showToast("Everything runs on your device. Your files are never uploaded.")
                            }
                            .clip(CircleShape)
                            .background(colors.surface)
                            .border(1.5.dp, colors.divider, CircleShape)
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Lock,
                                contentDescription = null,
                                tint = colors.textPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                            NexusText(
                                text = "Offline",
                                style = NexusTheme.typography.caption.copy(
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = colors.textPrimary
                            )
                        }
                    }
                }
            }

            // ─── 2. Scrollable Body Content (Matching HTML Page Layout) ───────
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(
                        start = 18.dp,
                        end = 18.dp,
                        top = 14.dp,
                        // Full clearance for floating bottom navigation bar
                        bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 115.dp
                    )
            ) {
                // Search Bar (.search in HTML: pill shape, 56dp, surface fill, 1.5dp border)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(CircleShape)
                        .background(colors.surface)
                        .border(1.5.dp, colors.divider, CircleShape)
                        .padding(horizontal = 18.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = "Search",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(21.dp)
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                fontFamily = NexusFontFamily,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = colors.textPrimary
                            ),
                            decorationBox = { innerTextField ->
                                if (searchQuery.isEmpty()) {
                                    NexusText(
                                        text = "Search tools…",
                                        style = NexusTheme.typography.body.copy(
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Normal,
                                            color = colors.textSecondary.copy(alpha = 0.65f)
                                        )
                                    )
                                }
                                innerTextField()
                            },
                            modifier = Modifier.weight(1f)
                        )

                        if (searchQuery.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(colors.surfaceVariant)
                                    .clickable { searchQuery = "" },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = "Clear search",
                                    tint = colors.textPrimary,
                                    modifier = Modifier.size(13.5.dp)
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.surfaceVariant.copy(alpha = 0.5f))
                                    .border(1.dp, colors.divider, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                NexusText(
                                    text = "/",
                                    style = NexusTheme.typography.caption.copy(
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = colors.textSecondary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Category Chips (.chips in HTML: pill shape, 44dp height, count pill)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(ToolCategory.entries.toTypedArray()) { category ->
                        val isSelected = selectedCategory == category
                        val count = categoryCounts[category] ?: 0

                        Box(
                            modifier = Modifier
                                .height(44.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) colors.primary else colors.surface)
                                .border(
                                    width = 1.5.dp,
                                    color = if (isSelected) colors.primary else colors.divider,
                                    shape = CircleShape
                                )
                                .springBounceClick {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    selectedCategory = category
                                }
                                .padding(start = 16.dp, end = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                NexusText(
                                    text = category.title,
                                    style = NexusTheme.typography.body.copy(
                                        fontSize = 13.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                                    ),
                                    color = if (isSelected) colors.onPrimary else colors.textSecondary
                                )

                                Box(
                                    modifier = Modifier
                                        .height(26.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) colors.onPrimary else colors.surfaceVariant)
                                        .padding(horizontal = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    NexusText(
                                        text = "$count",
                                        style = NexusTheme.typography.caption.copy(
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = if (isSelected) colors.primary else colors.textSecondary
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Results Line (.results-line in HTML)
                val count = filteredTools.size
                val resultsText = if (searchQuery.isNotBlank() || selectedCategory != ToolCategory.ALL) {
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = colors.textPrimary)) {
                            append("$count")
                        }
                        append(" tool${if (count == 1) "" else "s"} found")
                    }
                } else {
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = colors.textPrimary)) {
                            append("${toolsList.size}")
                        }
                        append(" tools · ${toolsList.size} ready to use")
                    }
                }

                NexusText(
                    text = resultsText.text,
                    style = NexusTheme.typography.caption.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = colors.textSecondary,
                    modifier = Modifier.padding(start = 2.dp, bottom = 14.dp)
                )

                // Tool Cards: 2-Column Responsive Grid (.grid in HTML)
                if (filteredTools.isEmpty()) {
                    // Empty State (.empty in HTML: dashed border, search icon, action link)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp))
                            .background(colors.surface)
                            .border(1.5.dp, colors.divider, RoundedCornerShape(22.dp))
                            .padding(vertical = 48.dp, horizontal = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = null,
                                tint = colors.textSecondary.copy(alpha = 0.5f),
                                modifier = Modifier.size(36.dp)
                            )
                            NexusText(
                                text = "No tools match “$searchQuery”",
                                style = NexusTheme.typography.title.copy(
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = colors.textPrimary,
                                textAlign = TextAlign.Center
                            )
                            NexusText(
                                text = "Try a file type like DOCX or JPG, or a task like “merge”.",
                                style = NexusTheme.typography.bodySmall.copy(
                                    fontSize = 13.sp
                                ),
                                color = colors.textSecondary,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .springBounceClick {
                                        searchQuery = ""
                                        selectedCategory = ToolCategory.ALL
                                    }
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(colors.surfaceVariant.copy(alpha = 0.5f))
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                NexusText(
                                    text = "Show all tools",
                                    style = NexusTheme.typography.bodySmall.copy(
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        textDecoration = TextDecoration.Underline
                                    ),
                                    color = colors.textPrimary
                                )
                            }
                        }
                    }
                } else {
                    // Responsive Grid: 2 columns on phones, 3 on tablets, 4 on large screens
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val columns = when {
                            maxWidth >= 840.dp -> 4
                            maxWidth >= 600.dp -> 3
                            else -> 2
                        }
                        val gap = if (maxWidth >= 600.dp) 14.dp else 12.dp
                        val chunkedTools = remember(filteredTools, columns) {
                            filteredTools.chunked(columns)
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                            chunkedTools.forEach { rowTools ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(gap)
                                ) {
                                    rowTools.forEach { tool ->
                                        ToolGridCard(
                                            tool = tool,
                                            onNavigateToTool = onNavigateToTool,
                                            onShowToast = { showToast(it) },
                                            haptic = haptic,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    // Fill any remaining row slots
                                    repeat(columns - rowTools.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }

                // Footnote (.footnote in HTML)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = colors.textSecondary.copy(alpha = 0.5f),
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    NexusText(
                        text = "Files never leave your device. No sign-up needed.",
                        style = NexusTheme.typography.caption.copy(
                            fontSize = 12.5.sp
                        ),
                        color = colors.textSecondary.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // ─── 3. Floating Toast Notification (.toast in HTML) ─────────────────
        AnimatedVisibility(
            visible = toastMessage != null,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(350)
            ) + fadeIn(tween(250)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(250)
            ) + fadeOut(tween(200)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 85.dp,
                    start = 20.dp,
                    end = 20.dp
                )
                .zIndex(10f)
        ) {
            Box(
                modifier = Modifier
                    .shadow(16.dp, RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.primary)
                    .border(1.dp, colors.divider.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 18.dp, vertical = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = colors.onPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    NexusText(
                        text = toastMessage.orEmpty(),
                        style = NexusTheme.typography.body.copy(
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = colors.onPrimary
                    )
                }
            }
        }
    }
}

/**
 * Grid Tool Card corresponding to .tool in the HTML template.
 *
 * Structure:
 * - 22dp rounded card with subtle surface fill and 1.5dp border matching theme.
 * - Top: 54dp squircle icon container with the tool's respective icon.
 * - Bottom: Bold title + description text, padded on the right.
 * - Bottom-Right: 30dp circular forward arrow affordance.
 * - Tap feedback: Spring bounce press animation with haptic response.
 */
@Composable
private fun ToolGridCard(
    tool: Tool,
    onNavigateToTool: (String) -> Unit,
    onShowToast: (String) -> Unit,
    haptic: HapticFeedback,
    modifier: Modifier = Modifier
) {
    val colors = NexusTheme.colors

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 168.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(colors.surface)
            .border(1.5.dp, colors.divider, RoundedCornerShape(22.dp))
            .springBounceClick {
                if (tool.isAvailable) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onNavigateToTool(tool.id)
                } else {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onShowToast("${tool.title} is coming soon.")
                }
            }
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Squircle Icon Container (.t-icon in HTML: 54dp, 16dp radius, soft surface fill, 1dp border)
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.surfaceVariant.copy(alpha = 0.55f))
                    .border(1.dp, colors.divider.copy(alpha = 0.7f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (tool.iconRes != null) {
                    Image(
                        painter = painterResource(id = tool.iconRes),
                        contentDescription = tool.title,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Fit
                    )
                } else if (tool.icon != null) {
                    Icon(
                        imageVector = tool.icon,
                        contentDescription = tool.title,
                        tint = colors.textPrimary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Text Hierarchy: Title + Description (with right padding so it never clashes with the arrow)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 32.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                NexusText(
                    text = tool.title,
                    style = NexusTheme.typography.title.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.3).sp,
                        lineHeight = 19.sp
                    ),
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                NexusText(
                    text = tool.description.ifEmpty { tool.subtitle },
                    style = NexusTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        lineHeight = 15.5.sp
                    ),
                    color = colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Circular Forward Arrow Affordance (.t-arrow in HTML: 30dp circle at bottom-right)
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(30.dp)
                .clip(CircleShape)
                .background(colors.surfaceVariant)
                .border(1.dp, colors.divider.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                tint = colors.textPrimary,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}
