package com.nexus.feature.dashboard

import com.nexus.core.theme.*

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexus.core.R
import com.nexus.core.preferences.ThemeMode
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusSurface
import com.nexus.core.ui.NexusText
import com.nexus.core.ui.animations.fadeSlideIn
import com.nexus.core.ui.animations.springBounceClick
import com.nexus.core.ui.components.NexusButton
import com.nexus.core.ui.components.NexusCard
import com.nexus.core.ui.components.NexusDialog
import com.nexus.core.ui.components.NexusIconButton
import com.nexus.core.ui.components.NexusSwitch
import com.nexus.core.ui.liquidglass.NexusLiquidToggle
import com.nexus.core.ui.liquidglass.NexusLiquidSlider
import kotlin.math.roundToInt
import com.nexus.core.ui.components.NexusTopBar
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.ui.graphics.Brush
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import com.nexus.core.ui.animations.EaseInOutSine

// ─── Settings Screen ──────────────────────────────────────────────────────────

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToRecycleBin: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState        by viewModel.uiState.collectAsStateWithLifecycle()
    val availableTags  by viewModel.availableTags.collectAsStateWithLifecycle()
    val documents      by viewModel.documents.collectAsStateWithLifecycle()
    val context        = LocalContext.current

    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showThemeDialog      by remember { mutableStateOf(false) }
    var showTagManagerScreen by remember { mutableStateOf(false) }
    var showRetentionDialog  by remember { mutableStateOf(false) }
    var isCustomizationExpanded by rememberSaveable { mutableStateOf(false) }


    // ── Storage permission helpers ────────────────────────────────────────────
    val checkStoragePermission = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            Environment.isExternalStorageManager()
        else
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
    }

    var isPermissionGranted by remember { mutableStateOf(checkStoragePermission()) }

    val manageStorageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { isPermissionGranted = checkStoragePermission() }

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> isPermissionGranted = granted }

    val triggerPermissionRequest = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                manageStorageLauncher.launch(
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                        .apply { data = Uri.parse("package:${context.packageName}") }
                )
            } catch (_: Exception) {
                try {
                    manageStorageLauncher.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                } catch (_: Exception) {}
            }
        } else {
            requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isPermissionGranted = checkStoragePermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(uiState.cacheClearMessage) {
        uiState.cacheClearMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.dismissCacheClearResult()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.uiEvents.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    if (showTagManagerScreen) {
        TagManagerScreen(
            tags = availableTags,
            documents = documents,
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
                try {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(Uri.parse(doc.uri), doc.mimeType)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(intent)
                } catch (_: Exception) {}
            }
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NexusTheme.colors.background)
    ) {
        NexusTopBar(
            title = "Settings",
            navigationIcon = {
                Image(
                    painter            = painterResource(id = R.drawable.ic_back),
                    contentDescription = "Back",
                    modifier           = Modifier
                        .clip(CircleShape)
                        .springBounceClick { onBack() }
                        .padding(NexusTheme.spacing.sm)
                        .size(24.dp),
                    colorFilter = ColorFilter.tint(NexusTheme.colors.textPrimary)
                )
            }
        )

        LazyColumn(
            modifier       = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(
                start  = 16.dp,
                end    = 16.dp,
                top    = 16.dp,
                bottom = 120.dp
            ),
            verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.sm)
        ) {

            // ── Appearance ───────────────────────────────────────────────────
            item {
                SettingsSectionGroup(
                    label    = "Appearance",
                    modifier = Modifier.fadeSlideIn(delay = 60, offsetY = 16.dp)
                ) {
                    val themeText = when (uiState.themeMode) {
                        ThemeMode.SYSTEM -> "System Default"
                        ThemeMode.LIGHT  -> "Light"
                        ThemeMode.DARK   -> "Dark"
                        ThemeMode.FOREST -> "Forest"
                        ThemeMode.SUNSET -> "Sunset"
                    }
                    SettingsNavRow(
                        iconRes  = R.drawable.ic_theme,
                        title    = "Theme",
                        value    = themeText,
                        onClick  = { showThemeDialog = true }
                    )
                }
            }

            // ── Navigation Bar Customization ───────────────────────────────────
            item {
                SettingsSectionGroup(
                    label    = "Bottom Tab Bar Customization",
                    modifier = Modifier.fadeSlideIn(delay = 90, offsetY = 16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = NexusTheme.spacing.md, vertical = NexusTheme.spacing.sm)
                            .clip(RoundedCornerShape(50.dp))
                            .background(NexusTheme.colors.surfaceVariant.copy(alpha = 0.5f))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        NavStyleOption(
                            label = "Classic Bar",
                            iconRes = R.drawable.ic_nav_dock,
                            selected = !uiState.useLiquidBottomNav,
                            onClick = { viewModel.setUseLiquidBottomNav(false) },
                            modifier = Modifier.weight(1f)
                        )
                        NavStyleOption(
                            label = "Liquid Glass",
                            iconRes = R.drawable.ic_blur,
                            selected = uiState.useLiquidBottomNav,
                            onClick = {
                                viewModel.setUseLiquidBottomNav(true)
                                isCustomizationExpanded = true
                            },
                            isExpanded = isCustomizationExpanded,
                            onToggleExpand = { isCustomizationExpanded = !isCustomizationExpanded },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    androidx.compose.animation.AnimatedVisibility(
                        visible = uiState.useLiquidBottomNav && isCustomizationExpanded,
                        enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
                        exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
                    ) {
                        Column {
                            SettingsDivider()
                            // 1. Corner Radius
                            SettingsSliderRow(
                                title = "Corner Radius",
                                valueText = "${uiState.bottomNavCornerRadius.roundToInt()} dp",
                                value = uiState.bottomNavCornerRadius,
                                onValueChange = { viewModel.setBottomNavCornerRadius(it) },
                                valueRange = 4f..31f,
                                iconRes = R.drawable.ic_corner_radius
                            )

                            SettingsDivider()
                            // 2. Blur Radius
                            SettingsSliderRow(
                                title = "Blur Radius",
                                valueText = "${uiState.bottomNavBlurRadius.roundToInt()} dp",
                                value = uiState.bottomNavBlurRadius,
                                onValueChange = { viewModel.setBottomNavBlurRadius(it) },
                                valueRange = 0f..35f,
                                iconRes = R.drawable.ic_blur
                            )

                            SettingsDivider()
                            // 3. Refraction Height
                            SettingsSliderRow(
                                title = "Refraction Height",
                                valueText = "${uiState.bottomNavRefractionHeight.roundToInt()} dp",
                                value = uiState.bottomNavRefractionHeight,
                                onValueChange = { viewModel.setBottomNavRefractionHeight(it) },
                                valueRange = 0f..50f,
                                iconRes = R.drawable.ic_refraction_height
                            )

                            SettingsDivider()
                            // 4. Refraction Amount
                            SettingsSliderRow(
                                title = "Refraction Amount",
                                valueText = "${uiState.bottomNavRefractionAmount.roundToInt()} dp",
                                value = uiState.bottomNavRefractionAmount,
                                onValueChange = { viewModel.setBottomNavRefractionAmount(it) },
                                valueRange = 0f..50f,
                                iconRes = R.drawable.ic_refraction_amount
                            )

                            SettingsDivider()
                            // Reset
                            SettingsNavRow(
                                iconRes = R.drawable.ic_rotate,
                                title = "Reset Tab Bar Optics",
                                value = "Default",
                                onClick = { viewModel.resetBottomNavCustomization() }
                            )
                        }
                    }
                }
            }

            // ── Reader ───────────────────────────────────────────────────────
            item {
                SettingsSectionGroup(
                    label    = "Reader",
                    modifier = Modifier.fadeSlideIn(delay = 120, offsetY = 16.dp)
                ) {
                    SettingsSwitchRow(
                        iconRes         = R.drawable.ic_book,
                        title           = "Remember Position",
                        subtitle        = "Resume where you left off",
                        checked         = uiState.rememberReadingPosition,
                        onCheckedChange = { viewModel.setRememberReadingPosition(it) }
                    )
                    SettingsDivider()
                    SettingsSwitchRow(
                        iconRes         = R.drawable.ic_screen_awake,
                        title           = "Keep Screen Awake",
                        subtitle        = "Prevent sleep while reading",
                        checked         = uiState.keepScreenAwake,
                        onCheckedChange = { viewModel.setKeepScreenAwake(it) }
                    )
                }
            }

            // ── General ──────────────────────────────────────────────────────
            item {
                SettingsSectionGroup(
                    label    = "General",
                    modifier = Modifier.fadeSlideIn(delay = 180, offsetY = 16.dp)
                ) {
                    SettingsSwitchRow(
                        iconRes         = R.drawable.ic_haptic,
                        title           = "Haptic Feedback",
                        subtitle        = "Vibrate on button taps and interactions",
                        checked         = uiState.hapticFeedbackEnabled,
                        onCheckedChange = { viewModel.setHapticFeedbackEnabled(it) }
                    )
                    SettingsDivider()
                    SettingsSwitchRow(
                        iconRes         = R.drawable.ic_view_grid,
                        title           = "Default to Grid View",
                        subtitle        = "Show documents in a grid layout by default",
                        checked         = uiState.defaultIsGridView,
                        onCheckedChange = { viewModel.setDefaultIsGridView(it) }
                    )
                    SettingsDivider()
                    SettingsNavRow(
                        iconRes  = R.drawable.ic_tag,
                        title    = "Tag Library",
                        value    = if (availableTags.isEmpty()) "Manage" else "${availableTags.size} tags",
                        onClick  = { showTagManagerScreen = true }
                    )
                    SettingsDivider()
                    SettingsNavRow(
                        iconRes  = R.drawable.ic_delete,
                        title    = "Recycle Bin",
                        value    = uiState.trashedSizeText,
                        onClick  = onNavigateToRecycleBin
                    )
                    SettingsDivider()
                    SettingsNavRow(
                        iconRes  = R.drawable.ic_save,
                        title    = "Trash Retention",
                        value    = if (uiState.trashRetentionDays <= 0) "Never" else "${uiState.trashRetentionDays} days",
                        onClick  = { showRetentionDialog = true }
                    )
                    SettingsDivider()
                    SettingsDestructiveRow(
                        iconRes  = R.drawable.ic_delete,
                        title    = "Clear Cache",
                        subtitle = "Currently ${uiState.cacheSizeText}",
                        onClick  = { showClearCacheDialog = true }
                    )
                }
            }

            // ── Permissions ──────────────────────────────────────────────────
            item {
                SettingsSectionGroup(
                    label    = "Permissions",
                    modifier = Modifier.fadeSlideIn(delay = 240, offsetY = 16.dp)
                ) {
                    SettingsPermissionRow(
                        iconRes   = R.drawable.ic_folder,
                        title     = "Storage Access",
                        isGranted = isPermissionGranted,
                        onClick   = if (isPermissionGranted) null else ({ triggerPermissionRequest() })
                    )
                }
            }

            // ── About & Legal ────────────────────────────────────────────────
            item {
                SettingsSectionGroup(
                    label    = "About & Legal",
                    modifier = Modifier.fadeSlideIn(delay = 300, offsetY = 16.dp)
                ) {
                    SettingsInfoRow(
                        iconRes  = R.drawable.ic_info,
                        title    = "App Version",
                        value    = uiState.appVersion
                    )

                    SettingsNavRow(
                        iconRes  = R.drawable.ic_lock,
                        title    = "Privacy Policy",
                        value    = "100% Offline",
                        onClick  = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://lite-view.vercel.app/privacy.html"))
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                Toast.makeText(context, "Could not open browser", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
        }
    }

    // ── Dialogs ───────────────────────────────────────────────────────────────

    if (showClearCacheDialog) {
        NexusDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = { NexusText("Clear Cache?", style = NexusTheme.typography.h2) },
            text  = { NexusText("This will remove temporary files like document thumbnails and extracted text. Your recent documents history will remain intact.") },
            confirmButton = {
                NexusButton(
                    text           = "Clear Cache",
                    containerColor = NexusTheme.colors.error,
                    contentColor   = NexusTheme.colors.background,
                    onClick        = { viewModel.clearCache(); showClearCacheDialog = false }
                )
            },
            dismissButton = {
                NexusButton(
                    text      = "Cancel",
                    isOutlined = true,
                    onClick    = { showClearCacheDialog = false }
                )
            }
        )
    }


    if (showThemeDialog) {
        ThemeSelectionDialog(
            currentTheme    = uiState.themeMode,
            onThemeSelected = { viewModel.setThemeMode(it); showThemeDialog = false },
            onDismiss       = { showThemeDialog = false }
        )
    }

    if (showRetentionDialog) {
        TrashRetentionDialog(
            currentDays = uiState.trashRetentionDays,
            onDaysSelected = { viewModel.setTrashRetentionDays(it) },
            onDismiss = { showRetentionDialog = false }
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun TrashRetentionDialog(
    currentDays: Int,
    onDaysSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(
        30 to "30 Days (Recommended)",
        15 to "15 Days",
        7 to "7 Days",
        -1 to "Never (Keep indefinitely)"
    )

    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = NexusTheme.colors.surface,
        contentColor = NexusTheme.colors.textPrimary,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = NexusTheme.spacing.xl)
                .padding(horizontal = NexusTheme.spacing.md)
        ) {
            NexusText(
                text = "Trash Retention Period",
                style = NexusTheme.typography.title,
                color = NexusTheme.colors.textPrimary
            )
            NexusText(
                text = "Documents moved to Recycle Bin will be automatically and permanently purged after this duration.",
                style = NexusTheme.typography.caption,
                color = NexusTheme.colors.textSecondary,
                modifier = Modifier.padding(top = NexusTheme.spacing.xxs, bottom = NexusTheme.spacing.md)
            )

            Column(verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs)) {
                options.forEach { (days, label) ->
                    val isSelected = (currentDays == days) || (days == -1 && currentDays <= 0)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(NexusTheme.shapes.medium)
                            .background(
                                if (isSelected) NexusTheme.colors.primary.copy(alpha = 0.12f)
                                else NexusTheme.colors.surfaceVariant.copy(alpha = 0.5f)
                            )
                            .border(
                                width = if (isSelected) 1.2.dp else 0.6.dp,
                                color = if (isSelected) NexusTheme.colors.primary else NexusTheme.colors.divider.copy(alpha = 0.5f),
                                shape = NexusTheme.shapes.medium
                            )
                            .springBounceClick {
                                onDaysSelected(days)
                                onDismiss()
                            }
                            .padding(horizontal = NexusTheme.spacing.md, vertical = NexusTheme.spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        NexusText(
                            text = label,
                            style = NexusTheme.typography.body.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 14.sp
                            ),
                            color = if (isSelected) NexusTheme.colors.primary else NexusTheme.colors.textPrimary
                        )
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(NexusTheme.colors.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_check),
                                    contentDescription = null,
                                    modifier = Modifier.size(13.dp),
                                    colorFilter = ColorFilter.tint(NexusTheme.colors.onPrimary)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Section group ────────────────────────────────────────────────────────────

@Composable
private fun SettingsSectionGroup(
    label: String,
    modifier: Modifier = Modifier,
    badge: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier) {
        // Section label
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = NexusTheme.spacing.xxs, bottom = NexusTheme.spacing.xs)
        ) {
            NexusText(
                text     = label,
                style    = NexusTheme.typography.label.copy(
                    fontSize      = 11.sp,
                    fontWeight    = FontWeight.SemiBold,
                    letterSpacing = 0.6.sp
                ),
                color    = NexusTheme.colors.textSecondary
            )
            if (badge != null) {
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(NexusTheme.shapes.small)
                        .background(NexusTheme.colors.primary.copy(alpha = 0.15f))
                        .padding(horizontal = NexusTheme.spacing.xs, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    NexusText(
                        text = badge.uppercase(),
                        style = NexusTheme.typography.caption.copy(
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp
                        ),
                        color = NexusTheme.colors.primary
                    )
                }
            }
        }
        // Card shell
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation    = 2.dp,
                    shape        = NexusTheme.shapes.large,
                    spotColor    = Color.Black.copy(alpha = 0.08f),
                    ambientColor = Color.Black.copy(alpha = 0.04f)
                )
                .clip(NexusTheme.shapes.large)
                .background(NexusTheme.colors.surface)
                .border(
                    width = 0.6.dp,
                    color = NexusTheme.colors.divider.copy(alpha = 0.7f),
                    shape = NexusTheme.shapes.large
                )
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                content()
            }
        }
    }
}

// ─── Row types ────────────────────────────────────────────────────────────────

@Composable
private fun SettingsNavRow(
    title:    String,
    value:    String,
    onClick:  () -> Unit,
    iconRes:  Int,
    iconTint: Color = NexusTheme.colors.textPrimary
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .springBounceClick(onClick = onClick)
            .padding(horizontal = NexusTheme.spacing.md, vertical = NexusTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(NexusTheme.shapes.medium)
                .background(NexusTheme.colors.surfaceVariant.copy(alpha = 0.7f)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter            = painterResource(id = iconRes),
                contentDescription = title,
                modifier           = Modifier.size(20.dp),
                colorFilter        = ColorFilter.tint(iconTint)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        NexusText(
            text     = title,
            style    = NexusTheme.typography.body,
            modifier = Modifier.weight(1f)
        )
        NexusText(
            text  = value,
            style = NexusTheme.typography.caption,
            color = NexusTheme.colors.textSecondary
        )
        Spacer(modifier = Modifier.width(6.dp))
        Image(
            painter            = painterResource(id = R.drawable.ic_arrow_right),
            contentDescription = null,
            colorFilter        = ColorFilter.tint(NexusTheme.colors.textSecondary.copy(alpha = 0.50f)),
            modifier           = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun SettingsSwitchRow(
    title:           String,
    subtitle:        String,
    checked:         Boolean,
    onCheckedChange: (Boolean) -> Unit,
    iconRes:         Int,
    iconTint:        Color = NexusTheme.colors.textPrimary
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .springBounceClick { onCheckedChange(!checked) }
            .padding(horizontal = NexusTheme.spacing.md, vertical = NexusTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(NexusTheme.shapes.medium)
                .background(NexusTheme.colors.surfaceVariant.copy(alpha = 0.7f)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter            = painterResource(id = iconRes),
                contentDescription = title,
                modifier           = Modifier.size(20.dp),
                colorFilter        = ColorFilter.tint(iconTint)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            NexusText(
                text  = title,
                style = NexusTheme.typography.body
            )
            NexusText(
                text     = subtitle,
                style    = NexusTheme.typography.caption,
                color    = NexusTheme.colors.textSecondary,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        NexusLiquidToggle(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun NavStyleOption(
    label:          String,
    iconRes:        Int,
    selected:       Boolean,
    onClick:        () -> Unit,
    modifier:       Modifier = Modifier,
    isExpanded:     Boolean? = null,
    onToggleExpand: (() -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    val hapticEnabled = LocalHapticFeedbackEnabled.current
    val primaryColor = NexusTheme.colors.primary

    val backgroundColor by animateColorAsState(
        targetValue = if (selected) NexusTheme.colors.surface else Color.Transparent,
        animationSpec = tween(180),
        label = "navOptionBg"
    )

    val contentColor by animateColorAsState(
        targetValue = if (selected) primaryColor else NexusTheme.colors.textSecondary,
        animationSpec = tween(180),
        label = "navOptionContent"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50.dp))
            .then(
                if (selected) {
                    Modifier
                        .shadow(2.dp, RoundedCornerShape(50.dp))
                        .background(backgroundColor, RoundedCornerShape(50.dp))
                        .border(1.dp, primaryColor.copy(alpha = 0.28f), RoundedCornerShape(50.dp))
                } else {
                    Modifier.background(backgroundColor, RoundedCornerShape(50.dp))
                }
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (hapticEnabled) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                if (!selected) {
                    onClick()
                } else if (onToggleExpand != null) {
                    onToggleExpand()
                }
            }
            .padding(vertical = 10.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Image(
                painter            = painterResource(id = iconRes),
                contentDescription = null,
                modifier           = Modifier.size(17.dp),
                colorFilter        = ColorFilter.tint(contentColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
            NexusText(
                text  = label,
                style = NexusTheme.typography.body.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    fontSize   = 13.5.sp
                ),
                color = contentColor
            )

            if (isExpanded != null && selected) {
                Spacer(modifier = Modifier.width(4.dp))
                val arrowRotation by animateFloatAsState(
                    targetValue   = if (isExpanded) -90f else 90f,
                    animationSpec = tween(200),
                    label         = "navOptionChevron"
                )
                Image(
                    painter            = painterResource(id = R.drawable.ic_arrow_right),
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    modifier           = Modifier
                        .size(13.dp)
                        .graphicsLayer { rotationZ = arrowRotation },
                    colorFilter        = ColorFilter.tint(contentColor)
                )
            }
        }
    }
}

@Composable
private fun SettingsSliderRow(
    title:           String,
    valueText:       String,
    value:           Float,
    onValueChange:   (Float) -> Unit,
    valueRange:      ClosedFloatingPointRange<Float>,
    iconRes:         Int,
    iconTint:        Color = NexusTheme.colors.textPrimary
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = NexusTheme.spacing.md, vertical = NexusTheme.spacing.sm)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(NexusTheme.shapes.medium)
                    .background(NexusTheme.colors.surfaceVariant.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter            = painterResource(id = iconRes),
                    contentDescription = title,
                    modifier           = Modifier.size(20.dp),
                    colorFilter        = ColorFilter.tint(iconTint)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            NexusText(
                text     = title,
                style    = NexusTheme.typography.body,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(NexusTheme.colors.primary.copy(alpha = 0.12f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                NexusText(
                    text  = valueText,
                    style = NexusTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = NexusTheme.colors.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        NexusLiquidSlider(
            value         = value,
            onValueChange = onValueChange,
            valueRange    = valueRange,
            accentColor   = NexusTheme.colors.primary,
            modifier      = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
        )
    }
}

@Composable
private fun SettingsDestructiveRow(
    title:    String,
    subtitle: String,
    onClick:  () -> Unit,
    iconRes:  Int,
    iconTint: Color = NexusTheme.colors.error
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .springBounceClick(onClick = onClick)
            .padding(horizontal = NexusTheme.spacing.md, vertical = NexusTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(NexusTheme.shapes.medium)
                .background(iconTint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter            = painterResource(id = iconRes),
                contentDescription = title,
                modifier           = Modifier.size(20.dp),
                colorFilter        = ColorFilter.tint(iconTint)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            NexusText(
                text  = title,
                style = NexusTheme.typography.body,
                color = NexusTheme.colors.error
            )
            NexusText(
                text     = subtitle,
                style    = NexusTheme.typography.caption,
                color    = NexusTheme.colors.error.copy(alpha = 0.60f),
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun SettingsPermissionRow(
    title:     String,
    isGranted: Boolean,
    onClick:   (() -> Unit)?,
    iconRes:   Int,
    iconTint:  Color = NexusTheme.colors.textPrimary
) {
    val statusColor = if (isGranted) NexusTheme.colors.success else NexusTheme.colors.error

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.springBounceClick(onClick = onClick) else Modifier)
            .padding(horizontal = NexusTheme.spacing.md, vertical = NexusTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(NexusTheme.shapes.medium)
                .background(NexusTheme.colors.surfaceVariant.copy(alpha = 0.7f)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter            = painterResource(id = iconRes),
                contentDescription = title,
                modifier           = Modifier.size(20.dp),
                colorFilter        = ColorFilter.tint(iconTint)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            NexusText(
                text  = title,
                style = NexusTheme.typography.body
            )
            if (!isGranted) {
                NexusText(
                    text     = "Required to browse and open documents",
                    style    = NexusTheme.typography.caption,
                    color    = NexusTheme.colors.textSecondary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        // Status indicator pill
        Box(
            modifier = Modifier
                .clip(NexusTheme.shapes.pill)
                .background(statusColor.copy(alpha = 0.10f))
                .padding(horizontal = NexusTheme.spacing.xs, vertical = NexusTheme.spacing.xxs),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Spacer(modifier = Modifier.width(5.dp))
                NexusText(
                    text  = if (isGranted) "Granted" else "Denied",
                    style = NexusTheme.typography.caption.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize   = 11.sp
                    ),
                    color = statusColor
                )
            }
        }
    }
}

@Composable
private fun SettingsInfoRow(
    title:    String,
    value:    String,
    iconRes:  Int,
    iconTint: Color = NexusTheme.colors.textPrimary
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = NexusTheme.spacing.md, vertical = NexusTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(NexusTheme.shapes.medium)
                .background(NexusTheme.colors.surfaceVariant.copy(alpha = 0.7f)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter            = painterResource(id = iconRes),
                contentDescription = title,
                modifier           = Modifier.size(20.dp),
                colorFilter        = ColorFilter.tint(iconTint)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        NexusText(
            text     = title,
            style    = NexusTheme.typography.body,
            modifier = Modifier.weight(1f)
        )
        NexusText(
            text  = value,
            style = NexusTheme.typography.body,
            color = NexusTheme.colors.textSecondary
        )
    }
}

// ─── Divider ──────────────────────────────────────────────────────────────────

@Composable
private fun SettingsDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = NexusTheme.spacing.xxl)
            .height(0.8.dp)
            .background(NexusTheme.colors.divider.copy(alpha = 0.6f))
    )
}

// ─── Theme selection bottom sheet ─────────────────────────────────────────────

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ThemeSelectionDialog(
    currentTheme:    ThemeMode,
    onThemeSelected: (ThemeMode) -> Unit,
    onDismiss:       () -> Unit
) {
    val options = listOf(
        Triple(ThemeMode.SYSTEM, "System Default", R.drawable.ic_theme),
        Triple(ThemeMode.LIGHT,  "Light",          R.drawable.ic_theme_light),
        Triple(ThemeMode.DARK,   "Dark",           R.drawable.ic_theme_dark),
        Triple(ThemeMode.FOREST, "Forest",         R.drawable.ic_theme_forest),
        Triple(ThemeMode.SUNSET, "Sunset",         R.drawable.ic_theme_sunset)
    )

    val sheetState = androidx.compose.material3.rememberModalBottomSheetState()
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        containerColor   = NexusTheme.colors.surface,
        contentColor     = NexusTheme.colors.textPrimary,
        shape            = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = NexusTheme.spacing.xl)
                .padding(horizontal = NexusTheme.spacing.md)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = NexusTheme.spacing.sm, start = NexusTheme.spacing.xxs, end = NexusTheme.spacing.xxs)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(NexusTheme.colors.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_theme),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        colorFilter = ColorFilter.tint(NexusTheme.colors.primary)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    NexusText(
                        text  = "Appearance Theme",
                        style = NexusTheme.typography.title.copy(fontWeight = FontWeight.Bold)
                    )
                    NexusText(
                        text  = "Select your preferred color profile",
                        style = NexusTheme.typography.caption,
                        color = NexusTheme.colors.textSecondary
                    )
                }
            }

            options.forEach { (mode, label, iconRes) ->
                val isSelected  = currentTheme == mode
                val accentColor = when (mode) {
                    ThemeMode.LIGHT  -> lightNexusColors.primary
                    ThemeMode.DARK   -> darkNexusColors.primary
                    ThemeMode.FOREST -> forestNexusColors.primary
                    ThemeMode.SUNSET -> sunsetNexusColors.primary
                    else             -> NexusTheme.colors.primary
                }

                val swatchColor = when (mode) {
                    ThemeMode.LIGHT  -> lightNexusColors.background
                    ThemeMode.DARK   -> darkNexusColors.background
                    ThemeMode.FOREST -> forestNexusColors.background
                    ThemeMode.SUNSET -> sunsetNexusColors.background
                    else             -> NexusTheme.colors.background
                }

                val contentColor by animateColorAsState(
                    targetValue   = if (isSelected) accentColor else NexusTheme.colors.textPrimary,
                    animationSpec = tween(200),
                    label         = "themeColor"
                )

                val checkScale by androidx.compose.animation.core.animateFloatAsState(
                    targetValue   = if (isSelected) 1f else 0f,
                    animationSpec = androidx.compose.animation.core.spring(
                        stiffness    = androidx.compose.animation.core.Spring.StiffnessMedium,
                        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy
                    ),
                    label = "checkScale"
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = NexusTheme.spacing.xxs)
                        .clip(NexusTheme.shapes.medium)
                        .background(if (isSelected) accentColor.copy(alpha = 0.08f) else NexusTheme.colors.surface)
                        .border(
                            width = if (isSelected) 1.5.dp else 0.6.dp,
                            color = if (isSelected) accentColor else NexusTheme.colors.divider.copy(alpha = 0.4f),
                            shape = NexusTheme.shapes.medium
                        )
                        .springBounceClick { onThemeSelected(mode) }
                        .padding(vertical = NexusTheme.spacing.sm, horizontal = NexusTheme.spacing.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(NexusTheme.shapes.medium)
                            .background(swatchColor)
                            .border(1.dp, NexusTheme.colors.divider.copy(alpha = 0.3f), NexusTheme.shapes.medium),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter            = painterResource(id = iconRes),
                            contentDescription = label,
                            modifier           = Modifier.size(20.dp),
                            colorFilter        = ColorFilter.tint(accentColor)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    NexusText(
                        text     = label,
                        style    = NexusTheme.typography.body.copy(
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        ),
                        color    = contentColor,
                        modifier = Modifier.weight(1f)
                    )
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .scale(checkScale)
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(accentColor),
                            contentAlignment = Alignment.Center
                        ) {
                            NexusText(
                                text  = "✓",
                                color = Color.White,
                                style = NexusTheme.typography.caption.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}


