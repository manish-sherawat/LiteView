package com.nexus.feature.reader.pdf.components

import kotlin.math.roundToInt
import android.graphics.Bitmap
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.core.ui.NexusText
import com.nexus.core.ui.animations.springBounceClick
import com.nexus.core.ui.utils.hazeGlassBackground
import dev.chrisbanes.haze.HazeState

// ─── Design Tokens matching the Web Prototype ──────────────────────────────────

object PdfViewerTheme {
    val canvasLight = Color(0xFFE5E7EC)
    val canvasDark = Color(0xFF0A0A0C)

    val surfaceLight = Color(0xDCFFFFFF)
    val surfaceDark = Color(0xD61A1A1E)

    val surfaceSolidLight = Color(0xFFFFFFFF)
    val surfaceSolidDark = Color(0xFF1A1A1E)

    val lineLight = Color(0x17000000)
    val lineDark = Color(0x1CFFFFFF)

    val inkLight = Color(0xFF141416)
    val inkDark = Color(0xFFF4F4F6)

    val mutedLight = Color(0xFF656974)
    val mutedDark = Color(0xFF989CA6)

    val activeSurfaceLight = Color(0xFF141416)
    val activeSurfaceDark = Color(0xFFF4F4F6)

    val activeInkLight = Color(0xFFFFFFFF)
    val activeInkDark = Color(0xFF111113)

    val hoverLight = Color(0x0F000000)
    val hoverDark = Color(0x17FFFFFF)

    @Composable
    fun canvasColor(isDark: Boolean = isSystemInDarkTheme()) = if (isDark) canvasDark else canvasLight

    @Composable
    fun surfaceColor(isDark: Boolean = isSystemInDarkTheme()) = if (isDark) surfaceDark else surfaceLight

    @Composable
    fun surfaceSolid(isDark: Boolean = isSystemInDarkTheme()) = if (isDark) surfaceSolidDark else surfaceSolidLight

    @Composable
    fun lineColor(isDark: Boolean = isSystemInDarkTheme()) = if (isDark) lineDark else lineLight

    @Composable
    fun inkColor(isDark: Boolean = isSystemInDarkTheme()) = if (isDark) inkDark else inkLight

    @Composable
    fun mutedColor(isDark: Boolean = isSystemInDarkTheme()) = if (isDark) mutedDark else mutedLight

    @Composable
    fun activeSurface(isDark: Boolean = isSystemInDarkTheme()) = if (isDark) activeSurfaceDark else activeSurfaceLight

    @Composable
    fun activeInk(isDark: Boolean = isSystemInDarkTheme()) = if (isDark) activeInkDark else activeInkLight
}

// ─── 1. Floating 3-Pill Top Bar ────────────────────────────────────────────────

@Composable
fun PdfModernTopBar(
    title: String,
    pageCount: Int,
    onBack: () -> Unit,
    isSearchActive: Boolean,
    onToggleSearch: () -> Unit,
    onOpenMore: () -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    isDark: Boolean = isSystemInDarkTheme()
) {
    val surface = PdfViewerTheme.surfaceColor(isDark)
    val border = PdfViewerTheme.lineColor(isDark)
    val ink = PdfViewerTheme.inkColor(isDark)
    val muted = PdfViewerTheme.mutedColor(isDark)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Pill 1: Back Navigation Pill
        Box(
            modifier = Modifier
                .size(44.dp)
                .shadow(elevation = 6.dp, shape = CircleShape, ambientColor = Color.Black.copy(alpha = 0.08f), spotColor = Color.Black.copy(alpha = 0.12f))
                .clip(CircleShape)
                .background(surface)
                .border(1.dp, border, CircleShape)
                .springBounceClick(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back",
                modifier = Modifier.size(20.dp),
                tint = ink
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Pill 2: Document Title & Page Count Pill
        Box(
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
                .shadow(elevation = 6.dp, shape = CircleShape, ambientColor = Color.Black.copy(alpha = 0.08f), spotColor = Color.Black.copy(alpha = 0.12f))
                .clip(CircleShape)
                .background(surface)
                .border(1.dp, border, CircleShape)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = title,
                    style = TextStyle(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        letterSpacing = (-0.14).sp,
                        color = ink
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                if (pageCount > 0) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(14.dp)
                            .background(border)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "$pageCount pgs",
                        style = TextStyle(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.5.sp,
                            color = muted
                        ),
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Pill 3: Search + More Actions Pill
        Box(
            modifier = Modifier
                .height(44.dp)
                .shadow(elevation = 6.dp, shape = CircleShape, ambientColor = Color.Black.copy(alpha = 0.08f), spotColor = Color.Black.copy(alpha = 0.12f))
                .clip(CircleShape)
                .background(surface)
                .border(1.dp, border, CircleShape)
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Search Toggle Button
                val searchActiveBg = if (isSearchActive) PdfViewerTheme.activeSurface(isDark) else Color.Transparent
                val searchActiveTint = if (isSearchActive) PdfViewerTheme.activeInk(isDark) else ink

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(searchActiveBg)
                        .springBounceClick(onClick = onToggleSearch),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "Find in document",
                        modifier = Modifier.size(19.dp),
                        tint = searchActiveTint
                    )
                }

                // More Menu Button
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .springBounceClick(onClick = onOpenMore),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "More options",
                        modifier = Modifier.size(19.dp),
                        tint = ink
                    )
                }
            }
        }
    }
}

// ─── 2. Floating Find Pill ────────────────────────────────────────────────────

@Composable
fun PdfModernSearchBar(
    visible: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    matchCount: Int,
    currentMatchIndex: Int,
    onPreviousMatch: () -> Unit,
    onNextMatch: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    isDark: Boolean = isSystemInDarkTheme()
) {
    val surface = PdfViewerTheme.surfaceColor(isDark)
    val border = PdfViewerTheme.lineColor(isDark)
    val ink = PdfViewerTheme.inkColor(isDark)
    val muted = PdfViewerTheme.mutedColor(isDark)
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(visible) {
        if (visible) {
            focusRequester.requestFocus()
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(animationSpec = tween(180)) { -it } + fadeIn(animationSpec = tween(180)),
        exit = slideOutVertically(animationSpec = tween(160)) { -it } + fadeOut(animationSpec = tween(160)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .wrapContentWidth(Alignment.CenterHorizontally)
                .widthIn(max = 480.dp)
                .height(46.dp)
                .shadow(elevation = 10.dp, shape = CircleShape, ambientColor = Color.Black.copy(alpha = 0.10f), spotColor = Color.Black.copy(alpha = 0.14f))
                .clip(CircleShape)
                .background(surface)
                .border(1.dp, border, CircleShape)
                .padding(start = 12.dp, end = 6.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = muted
                )

                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Normal,
                        color = ink
                    ),
                    cursorBrush = SolidColor(ink),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onNextMatch() }),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    decorationBox = { innerTextField ->
                        if (query.isEmpty()) {
                            Text(
                                text = "Find in document…",
                                style = TextStyle(fontSize = 15.sp, color = muted)
                            )
                        }
                        innerTextField()
                    }
                )

                // Match count badge
                if (query.isNotEmpty()) {
                    val countText = if (matchCount == 0) "0 results" else "${currentMatchIndex + 1}/$matchCount"
                    Text(
                        text = countText,
                        style = TextStyle(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = muted
                        ),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }

                // Prev Result Button
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .springBounceClick(onClick = onPreviousMatch),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowUp,
                        contentDescription = "Previous result",
                        modifier = Modifier.size(18.dp),
                        tint = ink
                    )
                }

                // Next Result Button
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .springBounceClick(onClick = onNextMatch),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Next result",
                        modifier = Modifier.size(18.dp),
                        tint = ink
                    )
                }

                // Close Button
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .springBounceClick(onClick = onClose),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close search",
                        modifier = Modifier.size(18.dp),
                        tint = muted
                    )
                }
            }
        }
    }
}

// ─── 3. Floating Responsive Bottom Dock ───────────────────────────────────────

@Composable
fun PdfModernBottomDock(
    currentPage: Int,
    totalPages: Int,
    zoomPercent: Int,
    isAnnotating: Boolean,
    onToggleAnnotate: () -> Unit,
    onOpenThumbnails: () -> Unit,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    onJumpToPage: (Int) -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onResetZoom: () -> Unit,
    modifier: Modifier = Modifier,
    isDark: Boolean = isSystemInDarkTheme()
) {
    val surface = PdfViewerTheme.surfaceColor(isDark)
    val border = PdfViewerTheme.lineColor(isDark)
    val ink = PdfViewerTheme.inkColor(isDark)
    val muted = PdfViewerTheme.mutedColor(isDark)

    var showJumpDialog by remember { mutableStateOf(false) }
    var jumpInput by remember { mutableStateOf("") }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 12.dp)
            .wrapContentWidth(Alignment.CenterHorizontally)
            .height(48.dp)
            .shadow(elevation = 12.dp, shape = CircleShape, ambientColor = Color.Black.copy(alpha = 0.10f), spotColor = Color.Black.copy(alpha = 0.16f))
            .clip(CircleShape)
            .background(surface)
            .border(1.dp, border, CircleShape)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            // Thumbnails Button
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .springBounceClick(onClick = onOpenThumbnails),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.GridView,
                    contentDescription = "Pages thumbnails",
                    modifier = Modifier.size(19.dp),
                    tint = ink
                )
            }

            // Annotate Button
            val annotActiveBg = if (isAnnotating) PdfViewerTheme.activeSurface(isDark) else Color.Transparent
            val annotActiveTint = if (isAnnotating) PdfViewerTheme.activeInk(isDark) else ink

            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(annotActiveBg)
                    .springBounceClick(onClick = onToggleAnnotate),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = "Annotate PDF",
                    modifier = Modifier.size(19.dp),
                    tint = annotActiveTint
                )
            }

            // Separator
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(20.dp)
                    .background(border)
            )

            // Previous Page Button
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .springBounceClick(onClick = onPreviousPage),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                    contentDescription = "Previous page",
                    modifier = Modifier.size(20.dp),
                    tint = if (currentPage > 0) ink else muted.copy(alpha = 0.4f)
                )
            }

            // Page Number Indicator / Stepper Pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(PdfViewerTheme.hoverLight)
                    .border(1.dp, border, RoundedCornerShape(999.dp))
                    .clickable {
                        jumpInput = (currentPage + 1).toString()
                        showJumpDialog = true
                    }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = "${currentPage + 1}",
                        style = TextStyle(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = ink
                        )
                    )
                    Text(
                        text = "/ $totalPages",
                        style = TextStyle(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                            color = muted
                        )
                    )
                }
            }

            // Next Page Button
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .springBounceClick(onClick = onNextPage),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = "Next page",
                    modifier = Modifier.size(20.dp),
                    tint = if (currentPage < totalPages - 1) ink else muted.copy(alpha = 0.4f)
                )
            }

            // Separator
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(20.dp)
                    .background(border)
            )

            // Zoom Out Button
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .springBounceClick(onClick = onZoomOut),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Remove,
                    contentDescription = "Zoom out",
                    modifier = Modifier.size(19.dp),
                    tint = ink
                )
            }

            // Zoom Percent Button (1-tap to reset)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .springBounceClick(onClick = onResetZoom)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$zoomPercent%",
                    style = TextStyle(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ink
                    )
                )
            }

            // Zoom In Button
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .springBounceClick(onClick = onZoomIn),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "Zoom in",
                    modifier = Modifier.size(19.dp),
                    tint = ink
                )
            }
        }
    }

    // Quick Jump Dialog
    if (showJumpDialog) {
        AlertDialog(
            onDismissRequest = { showJumpDialog = false },
            title = {
                Text("Go to page", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp, color = ink))
            },
            text = {
                OutlinedTextField(
                    value = jumpInput,
                    onValueChange = { jumpInput = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    label = { Text("Page (1 - $totalPages)") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val page = jumpInput.toIntOrNull()
                    if (page != null && page in 1..totalPages) {
                        onJumpToPage(page - 1)
                    }
                    showJumpDialog = false
                }) {
                    Text("Go", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showJumpDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// ─── 4. Floating Annotation Toolbar ───────────────────────────────────────────

@Composable
fun PdfModernAnnotationBar(
    visible: Boolean,
    isHighlighter: Boolean,
    onSelectPen: () -> Unit,
    onSelectHighlighter: () -> Unit,
    selectedColor: Color,
    onSelectColor: (Color) -> Unit,
    onUndo: () -> Unit,
    onClear: () -> Unit,
    onSave: () -> Unit,
    canUndo: Boolean,
    modifier: Modifier = Modifier,
    isDark: Boolean = isSystemInDarkTheme()
) {
    val surface = PdfViewerTheme.surfaceColor(isDark)
    val border = PdfViewerTheme.lineColor(isDark)
    val ink = PdfViewerTheme.inkColor(isDark)

    // Palette: Black/Ink, Red, Blue, Yellow
    val palette = remember(isDark) {
        listOf(
            if (isDark) Color(0xFFF4F4F6) else Color(0xFF141416),
            Color(0xFFE11D48),
            Color(0xFF2563EB),
            Color(0xFFEAB308)
        )
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(animationSpec = tween(180)) { it } + fadeIn(animationSpec = tween(180)),
        exit = slideOutVertically(animationSpec = tween(160)) { it } + fadeOut(animationSpec = tween(160)),
        modifier = modifier
    ) {
        val activeToolBg = if (isDark) Color(0xFFF4F4F6) else Color(0xFF141416)
        val activeToolInk = if (isDark) Color(0xFF111113) else Color(0xFFFFFFFF)

        Box(
            modifier = Modifier
                .wrapContentWidth(Alignment.CenterHorizontally)
                .height(44.dp)
                .shadow(elevation = 10.dp, shape = CircleShape, ambientColor = Color.Black.copy(alpha = 0.10f), spotColor = Color.Black.copy(alpha = 0.14f))
                .clip(CircleShape)
                .background(surface)
                .border(1.dp, border, CircleShape)
                .padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Pen Tool
                val penActive = !isHighlighter
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(if (penActive) activeToolBg else Color.Transparent)
                        .springBounceClick(onClick = onSelectPen),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = rememberPenIcon(isFilled = penActive),
                        contentDescription = "Pen",
                        modifier = Modifier.size(17.dp),
                        tint = if (penActive) activeToolInk else ink
                    )
                }

                // Highlighter Tool
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(if (isHighlighter) activeToolBg else Color.Transparent)
                        .springBounceClick(onClick = onSelectHighlighter),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = rememberHighlighterIcon(isFilled = isHighlighter),
                        contentDescription = "Highlighter",
                        modifier = Modifier.size(17.dp),
                        tint = if (isHighlighter) activeToolInk else ink
                    )
                }

                // Separator
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(20.dp)
                        .background(border)
                        .padding(horizontal = 2.dp)
                )

                // Color Swatches
                palette.forEach { c ->
                    val isSelected = selectedColor == c
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .border(
                                width = if (isSelected) 2.dp else 0.dp,
                                color = if (isSelected) ink else Color.Transparent,
                                shape = CircleShape
                            )
                            .springBounceClick { onSelectColor(c) },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(c)
                                .border(0.5.dp, Color.Black.copy(alpha = 0.15f), CircleShape)
                        )
                    }
                }

                // Separator
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(20.dp)
                        .background(border)
                        .padding(horizontal = 2.dp)
                )

                // Undo Button
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .springBounceClick(onClick = onUndo),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = rememberUndoIcon(),
                        contentDescription = "Undo",
                        modifier = Modifier.size(17.dp),
                        tint = if (canUndo) ink else ink.copy(alpha = 0.35f)
                    )
                }

                // Clear Page Button
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .springBounceClick(onClick = onClear),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = "Clear page",
                        modifier = Modifier.size(17.dp),
                        tint = ink
                    )
                }

                // Save Annotations Button
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .springBounceClick(onClick = onSave),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = com.nexus.core.R.drawable.ic_save),
                        contentDescription = "Save annotations",
                        modifier = Modifier.size(16.dp),
                        tint = ink
                    )
                }
            }
        }
    }
}

// ─── 5. Floating Page Indicator Pill ──────────────────────────────────────────

@Composable
fun PdfModernPagePill(
    visible: Boolean,
    currentPage: Int,
    totalPages: Int,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(150)),
        exit = fadeOut(animationSpec = tween(300)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 76.dp)
                .wrapContentWidth(Alignment.CenterHorizontally)
                .clip(CircleShape)
                .background(Color(0xD1141416))
                .padding(horizontal = 12.dp, vertical = 5.dp)
        ) {
            Text(
                text = "${currentPage + 1} / $totalPages",
                style = TextStyle(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
            )
        }
    }
}

// ─── 6. Thumbnails Bottom Sheet ───────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfThumbnailsBottomSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    pageCount: Int,
    currentPage: Int,
    renderedPages: Map<Int, Bitmap>,
    onRequestPage: (Int) -> Unit,
    onSelectPage: (Int) -> Unit,
    isDark: Boolean = isSystemInDarkTheme()
) {
    if (!visible) return

    val surface = PdfViewerTheme.surfaceSolid(isDark)
    val ink = PdfViewerTheme.inkColor(isDark)
    val border = PdfViewerTheme.lineColor(isDark)
    val muted = PdfViewerTheme.mutedColor(isDark)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = surface,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(border)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Pages",
                    style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = ink)
                )
                Text(
                    text = "$pageCount total",
                    style = TextStyle(fontSize = 13.sp, color = muted)
                )
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 108.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .padding(bottom = 24.dp)
            ) {
                items(pageCount) { index ->
                    val isSelected = index == currentPage
                    val bitmap = renderedPages[index]

                    LaunchedEffect(index) {
                        if (bitmap == null) {
                            onRequestPage(index)
                        }
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) PdfViewerTheme.hoverLight else Color.Transparent)
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) ink else border,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                onSelectPage(index)
                                onDismiss()
                            }
                            .padding(6.dp)
                    ) {
                        // Thumbnail Preview
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.White)
                                .shadow(2.dp, RoundedCornerShape(6.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (bitmap != null) {
                                androidx.compose.foundation.Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Page ${index + 1}",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Text(
                                    text = "...",
                                    style = TextStyle(fontSize = 12.sp, color = Color.Gray)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "${index + 1}",
                            style = TextStyle(
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) ink else muted
                            )
                        )
                    }
                }
            }
        }
    }
}

// ─── 7. More Options Bottom Sheet ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfMoreOptionsBottomSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    isStarred: Boolean,
    onToggleFavorite: () -> Unit,
    onRename: () -> Unit,
    onFileInfo: () -> Unit,
    onFitWidth: () -> Unit,
    onFitPage: () -> Unit,
    onRotate90: () -> Unit,
    isNightReading: Boolean,
    onToggleNightReading: () -> Unit,
    keepScreenAwake: Boolean,
    onToggleKeepAwake: () -> Unit,
    onPrint: () -> Unit,
    onShare: () -> Unit,
    isDark: Boolean = isSystemInDarkTheme()
) {
    if (!visible) return

    val surface = PdfViewerTheme.surfaceSolid(isDark)
    val ink = PdfViewerTheme.inkColor(isDark)
    val border = PdfViewerTheme.lineColor(isDark)
    val muted = PdfViewerTheme.mutedColor(isDark)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = surface,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(border)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp)
                .padding(bottom = 20.dp)
        ) {
            // Favorite Item
            PdfMenuItemRow(
                icon = if (isStarred) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                label = "Favorite",
                meta = if (isStarred) "Saved" else null,
                ink = ink,
                muted = muted,
                onClick = {
                    onToggleFavorite()
                    onDismiss()
                }
            )

            // Rename Item
            PdfMenuItemRow(
                icon = Icons.Rounded.Edit,
                label = "Rename",
                ink = ink,
                muted = muted,
                onClick = {
                    onDismiss()
                    onRename()
                }
            )

            // File Info Item
            PdfMenuItemRow(
                icon = Icons.Rounded.Info,
                label = "File info",
                ink = ink,
                muted = muted,
                onClick = {
                    onDismiss()
                    onFileInfo()
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = border)

            // Fit Width
            PdfMenuItemRow(
                icon = Icons.Rounded.OpenWith,
                label = "Fit width",
                ink = ink,
                muted = muted,
                onClick = {
                    onFitWidth()
                    onDismiss()
                }
            )

            // Fit Page
            PdfMenuItemRow(
                icon = Icons.Rounded.CropFree,
                label = "Fit page",
                ink = ink,
                muted = muted,
                onClick = {
                    onFitPage()
                    onDismiss()
                }
            )

            // Rotate 90°
            PdfMenuItemRow(
                icon = Icons.Rounded.RotateRight,
                label = "Rotate 90°",
                ink = ink,
                muted = muted,
                onClick = {
                    onRotate90()
                    onDismiss()
                }
            )

            // Night Reading Mode
            PdfMenuItemRow(
                icon = Icons.Rounded.Brightness4,
                label = "Night reading",
                meta = if (isNightReading) "On" else "Off",
                ink = ink,
                muted = muted,
                onClick = {
                    onToggleNightReading()
                    onDismiss()
                }
            )

            // Keep Screen Awake
            PdfMenuItemRow(
                icon = Icons.Rounded.Visibility,
                label = "Keep screen awake",
                meta = if (keepScreenAwake) "On" else "Off",
                ink = ink,
                muted = muted,
                onClick = {
                    onToggleKeepAwake()
                    onDismiss()
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = border)

            // Print
            PdfMenuItemRow(
                icon = Icons.Rounded.Print,
                label = "Print",
                ink = ink,
                muted = muted,
                onClick = {
                    onPrint()
                    onDismiss()
                }
            )

            // Share / Save
            PdfMenuItemRow(
                icon = Icons.Rounded.Share,
                label = "Share file",
                ink = ink,
                muted = muted,
                onClick = {
                    onShare()
                    onDismiss()
                }
            )
        }
    }
}

@Composable
private fun PdfMenuItemRow(
    icon: ImageVector,
    label: String,
    meta: String? = null,
    ink: Color,
    muted: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(20.dp),
            tint = ink
        )

        Spacer(modifier = Modifier.width(14.dp))

        Text(
            text = label,
            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = ink),
            modifier = Modifier.weight(1f)
        )

        if (meta != null) {
            Text(
                text = meta,
                style = TextStyle(fontSize = 12.5.sp, color = muted)
            )
        }
    }
}

// ─── 8. File Info Bottom Sheet ────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfFileInfoBottomSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    fileName: String,
    fileSize: Long,
    pageCount: Int,
    lastModified: Long,
    isDark: Boolean = isSystemInDarkTheme()
) {
    if (!visible) return

    val surface = PdfViewerTheme.surfaceSolid(isDark)
    val ink = PdfViewerTheme.inkColor(isDark)
    val border = PdfViewerTheme.lineColor(isDark)
    val muted = PdfViewerTheme.mutedColor(isDark)

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        if (bytes < 1024) return "$bytes B"
        if (bytes < 1048576) return String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0)
        return String.format(java.util.Locale.US, "%.2f MB", bytes / 1048576.0)
    }

    val formattedDate = remember(lastModified) {
        if (lastModified > 0) {
            val sdf = java.text.SimpleDateFormat("MMM dd, yyyy · HH:mm", java.util.Locale.getDefault())
            sdf.format(java.util.Date(lastModified))
        } else "—"
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = surface,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(border)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 6.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                text = "File info",
                style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = ink),
                modifier = Modifier.padding(bottom = 14.dp)
            )

            val rows = listOf(
                "File name" to fileName,
                "File size" to formatBytes(fileSize),
                "Pages" to "$pageCount",
                "Format" to "PDF Document",
                "Last modified" to formattedDate
            )

            rows.forEachIndexed { i, (k, v) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = k, style = TextStyle(fontSize = 14.sp, color = muted))
                    Text(
                        text = v,
                        style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = ink),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
                if (i < rows.lastIndex) {
                    HorizontalDivider(color = border)
                }
            }
        }
    }
}

// ─── 9. Rename Bottom Sheet ───────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfRenameBottomSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    currentName: String,
    onConfirmRename: (String) -> Unit,
    isDark: Boolean = isSystemInDarkTheme()
) {
    if (!visible) return

    val surface = PdfViewerTheme.surfaceSolid(isDark)
    val ink = PdfViewerTheme.inkColor(isDark)
    val border = PdfViewerTheme.lineColor(isDark)
    val muted = PdfViewerTheme.mutedColor(isDark)

    val cleanBase = remember(currentName) { currentName.removeSuffix(".pdf") }
    var newName by remember { mutableStateOf(cleanBase) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100)
        focusRequester.requestFocus()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = surface,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(border)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 6.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                text = "Rename file",
                style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = ink),
                modifier = Modifier.padding(bottom = 14.dp)
            )

            // Rename Field
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(PdfViewerTheme.hoverLight)
                    .border(1.dp, border, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = ink
                    ),
                    cursorBrush = SolidColor(ink),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                )

                Text(
                    text = ".pdf",
                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = muted)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PdfViewerTheme.hoverLight,
                        contentColor = ink
                    ),
                    shape = CircleShape,
                    modifier = Modifier.height(40.dp)
                ) {
                    Text("Cancel", fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = {
                        val trimmed = newName.trim()
                        if (trimmed.isNotBlank()) {
                            onConfirmRename(if (trimmed.endsWith(".pdf", ignoreCase = true)) trimmed else "$trimmed.pdf")
                            onDismiss()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PdfViewerTheme.activeSurface(isDark),
                        contentColor = PdfViewerTheme.activeInk(isDark)
                    ),
                    shape = CircleShape,
                    modifier = Modifier.height(40.dp)
                ) {
                    Text("Save", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun PdfGoToPageDialog(
    currentPage: Int,
    pageCount: Int,
    isDark: Boolean,
    onDismiss: () -> Unit,
    onJumpToPage: (Int) -> Unit
) {
    var pageInput by remember { mutableStateOf(currentPage.toString()) }
    val ink = PdfViewerTheme.inkColor(isDark)
    val muted = PdfViewerTheme.mutedColor(isDark)
    val surface = PdfViewerTheme.surfaceSolid(isDark)
    val border = PdfViewerTheme.lineColor(isDark)

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(surface)
                .border(1.dp, border, RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Jump to page",
                        style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = ink)
                    )
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(PdfViewerTheme.activeSurface(isDark).copy(alpha = 0.12f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "1 - $pageCount",
                            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = ink)
                        )
                    }
                }

                // Stepper: - [input] +
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            val v = pageInput.toIntOrNull() ?: currentPage
                            if (v > 1) pageInput = (v - 1).toString()
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(PdfViewerTheme.hoverLight)
                    ) {
                        Text("-", style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = ink))
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(PdfViewerTheme.hoverLight)
                            .border(1.dp, border, RoundedCornerShape(12.dp))
                            .padding(vertical = 8.dp, horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        BasicTextField(
                            value = pageInput,
                            onValueChange = { input ->
                                if (input.isEmpty() || input.all { it.isDigit() }) {
                                    val num = input.toIntOrNull()
                                    if (num == null || num <= pageCount) {
                                        pageInput = input
                                    }
                                }
                            },
                            singleLine = true,
                            textStyle = TextStyle(
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = ink,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            ),
                            cursorBrush = SolidColor(ink),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    IconButton(
                        onClick = {
                            val v = pageInput.toIntOrNull() ?: currentPage
                            if (v < pageCount) pageInput = (v + 1).toString()
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(PdfViewerTheme.hoverLight)
                    ) {
                        Text("+", style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = ink))
                    }
                }

                // Slider
                val sliderVal = (pageInput.toFloatOrNull() ?: 1f).coerceIn(1f, pageCount.toFloat())
                androidx.compose.material3.Slider(
                    value = sliderVal,
                    onValueChange = { pageInput = it.roundToInt().toString() },
                    valueRange = 1f..pageCount.toFloat(),
                    colors = androidx.compose.material3.SliderDefaults.colors(
                        thumbColor = PdfViewerTheme.activeSurface(isDark),
                        activeTrackColor = PdfViewerTheme.activeSurface(isDark),
                        inactiveTrackColor = border
                    )
                )

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PdfViewerTheme.hoverLight,
                            contentColor = ink
                        ),
                        shape = CircleShape,
                        modifier = Modifier.height(40.dp)
                    ) {
                        Text("Cancel", fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            val p = pageInput.toIntOrNull()
                            if (p != null && p in 1..pageCount) {
                                onJumpToPage(p)
                                onDismiss()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PdfViewerTheme.activeSurface(isDark),
                            contentColor = PdfViewerTheme.activeInk(isDark)
                        ),
                        shape = CircleShape,
                        modifier = Modifier.height(40.dp)
                    ) {
                        Text("Jump", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}


