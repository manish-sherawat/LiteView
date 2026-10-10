package com.nexus.feature.dashboard

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.core.R
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusText
import com.nexus.core.ui.animations.breathePulse
import com.nexus.core.ui.animations.springBounceClick
import com.nexus.core.ui.animations.staggeredSlideFadeIn
import com.nexus.core.ui.components.NexusButton
import com.nexus.core.ui.components.NexusEmptyStateImage
import com.nexus.core.ui.components.NexusEmptyStateType
import com.nexus.core.ui.components.NexusSearchBar
import com.nexus.core.ui.components.NexusTopBar

// ─── Tools Hub Screen ────────────────────────────────────────────────────────
// A curated, offline document conversion & utility suite.
// Features a Bento Spotlight Hero, category tabs, live search, and Apple-grade micro-physics.

@Composable
fun ToolsScreen(
    onNavigateToTool: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf(ToolCategory.ALL) }

    // Dynamic filtering
    val filteredTools = remember(searchQuery, selectedCategory) {
        toolsList.filter { tool ->
            val matchesCategory = selectedCategory == ToolCategory.ALL || tool.category == selectedCategory
            val matchesQuery = searchQuery.isBlank() ||
                tool.title.contains(searchQuery, ignoreCase = true) ||
                tool.subtitle.contains(searchQuery, ignoreCase = true) ||
                tool.description.contains(searchQuery, ignoreCase = true) ||
                (tool.badge != null && tool.badge.contains(searchQuery, ignoreCase = true))
            matchesCategory && matchesQuery
        }
    }

    val categoryCounts = remember {
        ToolCategory.entries.associateWith { cat ->
            if (cat == ToolCategory.ALL) toolsList.size
            else toolsList.count { it.category == cat }
        }
    }

    val heroTool = remember { toolsList.firstOrNull { it.isHero } ?: toolsList.first() }
    val showHeroCard = searchQuery.isBlank() && (selectedCategory == ToolCategory.ALL || selectedCategory == ToolCategory.CONVERT)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NexusTheme.colors.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ─── 1. Top Navigation Bar ──────────────────────────────────────────
            NexusTopBar(
                title = "Tools",
                navigationIcon = {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onBack()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = NexusTheme.colors.textPrimary
                        )
                    }
                },
                actions = {
                    // Security / Privacy badge pill
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .clip(CircleShape)
                            .background(NexusTheme.colors.primary.copy(alpha = 0.10f))
                            .border(
                                width = 0.8.dp,
                                color = NexusTheme.colors.primary.copy(alpha = 0.35f),
                                shape = CircleShape
                            )
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_lock),
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            colorFilter = ColorFilter.tint(NexusTheme.colors.primary)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        NexusText(
                            text = "Offline",
                            style = NexusTheme.typography.caption.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = NexusTheme.colors.primary
                        )
                    }
                }
            )

            // ─── 2. Scrollable Body with Bento Grid ──────────────────────────────
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 155.dp),
                contentPadding = PaddingValues(
                    start = NexusTheme.spacing.md,
                    end = NexusTheme.spacing.md,
                    top = 2.dp,
                    // Extra bottom padding to clear the floating bottom navigation bar
                    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 130.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // ── Section A: Live Search Bar (Full Span) ────────────────────────
                item(span = { GridItemSpan(maxLineSpan) }) {
                    NexusSearchBar(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        onClear = { searchQuery = "" },
                        placeholderText = "Search tools...",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 2.dp)
                    )
                }

                // ── Section B: Category Filter Pills (Full Span) ─────────────────
                item(span = { GridItemSpan(maxLineSpan) }) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(ToolCategory.entries.toTypedArray()) { category ->
                            val isSelected = selectedCategory == category
                            val count = categoryCounts[category] ?: 0

                            val categoryAccent = when (category) {
                                ToolCategory.ALL -> NexusTheme.colors.primary
                                ToolCategory.CONVERT -> Color(0xFF2563EB)
                                ToolCategory.ORGANIZE -> Color(0xFF8B5CF6)
                                ToolCategory.SECURITY -> Color(0xFF06B6D4)
                            }

                            val animatedBg by animateColorAsState(
                                targetValue = if (isSelected) categoryAccent.copy(alpha = 0.14f) else NexusTheme.colors.surfaceVariant.copy(alpha = 0.45f),
                                animationSpec = spring(dampingRatio = 0.85f, stiffness = 450f),
                                label = "catBg"
                            )
                            val animatedBorderColor by animateColorAsState(
                                targetValue = if (isSelected) categoryAccent.copy(alpha = 0.7f) else NexusTheme.colors.divider.copy(alpha = 0.20f),
                                animationSpec = spring(dampingRatio = 0.85f, stiffness = 450f),
                                label = "catBorder"
                            )
                            val animatedTextColor by animateColorAsState(
                                targetValue = if (isSelected) categoryAccent else NexusTheme.colors.textPrimary,
                                animationSpec = spring(dampingRatio = 0.85f, stiffness = 450f),
                                label = "catText"
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(animatedBg)
                                    .border(
                                        width = if (isSelected) 1.dp else 0.5.dp,
                                        color = animatedBorderColor,
                                        shape = CircleShape
                                    )
                                    .springBounceClick {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        selectedCategory = category
                                    }
                                    .padding(horizontal = 13.dp, vertical = 6.dp)
                            ) {
                                NexusText(
                                    text = category.title,
                                    style = NexusTheme.typography.caption.copy(
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = animatedTextColor
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(if (isSelected) categoryAccent else NexusTheme.colors.surfaceVariant)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    NexusText(
                                        text = "$count",
                                        style = NexusTheme.typography.caption.copy(
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = if (isSelected) NexusTheme.colors.onPrimary else NexusTheme.colors.textSecondary
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Section C: Featured Bento Hero Spotlight (Full Span) ─────────
                if (showHeroCard) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        ToolsFeaturedHeroCard(
                            tool = heroTool,
                            onClick = {
                                if (heroTool.isAvailable) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onNavigateToTool(heroTool.id)
                                } else {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    Toast.makeText(context, "${heroTool.title} is coming soon", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }

                // ── Section D: Search Empty State ────────────────────────────────
                if (filteredTools.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        ToolsSearchEmptyState(
                            query = searchQuery,
                            onClearSearch = {
                                searchQuery = ""
                                selectedCategory = ToolCategory.ALL
                            }
                        )
                    }
                }

                // ── Section E: Bento Tool Cards Grid ─────────────────────────────
                itemsIndexed(
                    items = filteredTools,
                    key = { _, tool -> tool.id }
                ) { index, tool ->
                    ToolBentoCard(
                        tool = tool,
                        modifier = Modifier
                            .animateItem()
                            .staggeredSlideFadeIn(index = index, baseDelayMs = 20, maxDelayMs = 200, offsetY = 14.dp),
                        onClick = {
                            if (tool.isAvailable) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onNavigateToTool(tool.id)
                            } else {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                Toast.makeText(
                                    context,
                                    "${tool.title} is coming soon",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    )
                }
            }
        }
    }
}

// ─── Featured Hero Card Component ────────────────────────────────────────────

@Composable
private fun ToolsFeaturedHeroCard(
    tool: Tool,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        tool.accentColor.copy(alpha = 0.12f),
                        NexusTheme.colors.surface,
                        NexusTheme.colors.surface
                    )
                )
            )
            .border(
                width = 0.8.dp,
                color = tool.accentColor.copy(alpha = 0.25f),
                shape = RoundedCornerShape(22.dp)
            )
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(22.dp),
                spotColor = tool.accentColor.copy(alpha = 0.15f),
                ambientColor = tool.accentColor.copy(alpha = 0.05f)
            )
            .springBounceClick(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Squircle Icon
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(tool.accentColor.copy(alpha = 0.14f))
                    .border(
                        width = 0.8.dp,
                        color = tool.accentColor.copy(alpha = 0.28f),
                        shape = RoundedCornerShape(16.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (tool.iconRes != null) {
                    Image(
                        painter = painterResource(id = tool.iconRes),
                        contentDescription = tool.title,
                        modifier = Modifier.size(28.dp)
                    )
                } else if (tool.icon != null) {
                    Icon(
                        imageVector = tool.icon,
                        contentDescription = tool.title,
                        tint = tool.accentColor,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NexusText(
                        text = tool.title,
                        style = NexusTheme.typography.title.copy(
                            fontSize = 16.5.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = NexusTheme.colors.textPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(tool.accentColor.copy(alpha = 0.12f))
                            .border(
                                width = 0.5.dp,
                                color = tool.accentColor.copy(alpha = 0.25f),
                                shape = CircleShape
                            )
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        NexusText(
                            text = "Featured",
                            style = NexusTheme.typography.caption.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = tool.accentColor
                        )
                    }
                }
                Spacer(modifier = Modifier.height(3.dp))
                NexusText(
                    text = "Convert photos & scans into clean PDF",
                    style = NexusTheme.typography.caption.copy(
                        fontSize = 12.sp
                    ),
                    color = NexusTheme.colors.textSecondary
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Trailing circular action arrow button
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(tool.accentColor.copy(alpha = 0.12f))
                    .border(
                        width = 0.6.dp,
                        color = tool.accentColor.copy(alpha = 0.25f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_arrow_right),
                    contentDescription = "Open",
                    modifier = Modifier.size(13.dp),
                    colorFilter = ColorFilter.tint(tool.accentColor)
                )
            }
        }
    }
}

// ─── Individual Bento Tool Card ──────────────────────────────────────────────

@Composable
private fun ToolBentoCard(
    tool: Tool,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .springBounceClick(onClick = onClick)
            .border(
                width = 0.8.dp,
                color = NexusTheme.colors.divider.copy(alpha = 0.35f),
                shape = RoundedCornerShape(20.dp)
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = NexusTheme.colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header Row: Squircle Icon + (Badge OR subtle circle arrow)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon Squircle Container
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (tool.isAvailable) tool.accentColor.copy(alpha = 0.12f) else tool.accentColor.copy(alpha = 0.08f))
                        .border(
                            width = 0.8.dp,
                            color = if (tool.isAvailable) tool.accentColor.copy(alpha = 0.22f) else tool.accentColor.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(14.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (tool.iconRes != null) {
                        Image(
                            painter = painterResource(id = tool.iconRes),
                            contentDescription = tool.title,
                            modifier = Modifier.size(24.dp),
                            alpha = if (tool.isAvailable) 1f else 0.8f
                        )
                    } else if (tool.icon != null) {
                        Icon(
                            imageVector = tool.icon,
                            contentDescription = tool.title,
                            tint = if (tool.isAvailable) tool.accentColor else tool.accentColor.copy(alpha = 0.8f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                if (!tool.isAvailable) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(NexusTheme.colors.surfaceVariant.copy(alpha = 0.65f))
                            .border(
                                width = 0.5.dp,
                                color = NexusTheme.colors.divider.copy(alpha = 0.40f),
                                shape = CircleShape
                            )
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        NexusText(
                            text = "Coming soon",
                            style = NexusTheme.typography.caption.copy(
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = NexusTheme.colors.textSecondary
                        )
                    }
                } else if (tool.badge != null) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(tool.accentColor.copy(alpha = 0.12f))
                            .border(
                                width = 0.6.dp,
                                color = tool.accentColor.copy(alpha = 0.25f),
                                shape = CircleShape
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        NexusText(
                            text = tool.badge,
                            style = NexusTheme.typography.caption.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = tool.accentColor
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(NexusTheme.colors.surfaceVariant.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_arrow_right),
                            contentDescription = null,
                            modifier = Modifier.size(10.dp),
                            colorFilter = ColorFilter.tint(NexusTheme.colors.textSecondary)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Tool Title
            NexusText(
                text = tool.title,
                style = NexusTheme.typography.title.copy(
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = if (tool.isAvailable) NexusTheme.colors.textPrimary else NexusTheme.colors.textPrimary.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(3.dp))

            // Punchy Subtitle
            NexusText(
                text = tool.subtitle,
                style = NexusTheme.typography.caption.copy(
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Normal
                ),
                color = NexusTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ─── Search Empty State ──────────────────────────────────────────────────────

@Composable
private fun ToolsSearchEmptyState(
    query: String,
    onClearSearch: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp, horizontal = NexusTheme.spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        NexusEmptyStateImage(
            type = NexusEmptyStateType.NOT_FOUND,
            contentDescription = "No tools found",
            modifier = Modifier
                .size(160.dp)
                .breathePulse(minScale = 0.95f, maxScale = 1.05f, durationMs = 2400)
        )

        Spacer(modifier = Modifier.height(16.dp))

        NexusText(
            text = "No tools found",
            style = NexusTheme.typography.title.copy(
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            ),
            color = NexusTheme.colors.textPrimary
        )

        Spacer(modifier = Modifier.height(6.dp))

        NexusText(
            text = if (query.isNotBlank()) "No tool matched \"$query\"" else "No tools in this category",
            style = NexusTheme.typography.caption.copy(fontSize = 13.sp),
            color = NexusTheme.colors.textSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(18.dp))

        NexusButton(
            text = "Clear Filter",
            onClick = onClearSearch
        )
    }
}
