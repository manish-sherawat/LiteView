package com.nexus.feature.dashboard

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import com.nexus.core.ui.components.NexusTopBar
import com.nexus.core.updater.UpdateState
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
    onChangelogVisibilityChanged: (Boolean) -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState        by viewModel.uiState.collectAsStateWithLifecycle()
    val updateState    by viewModel.updateState.collectAsStateWithLifecycle()
    val changelogState by viewModel.changelogState.collectAsStateWithLifecycle()
    val context        = LocalContext.current

    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showChangelogDialog  by remember { mutableStateOf(false) }
    var showThemeDialog      by remember { mutableStateOf(false) }

    LaunchedEffect(showChangelogDialog) {
        onChangelogVisibilityChanged(showChangelogDialog)
    }

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
                        .padding(12.dp)
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // ── Appearance ───────────────────────────────────────────────────
            item {
                SettingsSectionGroup(
                    label    = "Appearance",
                    modifier = Modifier.fadeSlideIn(delay = 60, offsetY = 16.dp)
                ) {
                    val themeText = when (uiState.themeMode) {
                        ThemeMode.LIGHT  -> "Light"
                        ThemeMode.DARK   -> "Dark"
                        ThemeMode.AMOLED -> "Pitch Black (AMOLED)"
                        ThemeMode.SEPIA  -> "Sepia"
                        ThemeMode.FOREST -> "Forest"
                        ThemeMode.SUNSET -> "Sunset"
                        ThemeMode.SYSTEM -> "System Default"
                    }
                    SettingsNavRow(
                        iconRes  = R.drawable.ic_theme,
                        iconTint = NexusTheme.colors.primary,
                        title    = "Theme",
                        value    = themeText,
                        onClick  = { showThemeDialog = true }
                    )
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
                        iconTint        = Color(0xFF10B981),
                        title           = "Remember Position",
                        subtitle        = "Resume where you left off",
                        checked         = uiState.rememberReadingPosition,
                        onCheckedChange = { viewModel.setRememberReadingPosition(it) }
                    )
                    SettingsDivider()
                    SettingsSwitchRow(
                        iconRes         = R.drawable.ic_screen_awake,
                        iconTint        = Color(0xFFF59E0B),
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
                        iconTint        = Color(0xFF6366F1),
                        title           = "Haptic Feedback",
                        subtitle        = "Vibrate on button taps and interactions",
                        checked         = uiState.hapticFeedbackEnabled,
                        onCheckedChange = { viewModel.setHapticFeedbackEnabled(it) }
                    )
                    SettingsDivider()
                    SettingsSwitchRow(
                        iconRes         = R.drawable.ic_view_grid,
                        iconTint        = Color(0xFF0EA5E9),
                        title           = "Default to Grid View",
                        subtitle        = "Show documents in a grid layout by default",
                        checked         = uiState.defaultIsGridView,
                        onCheckedChange = { viewModel.setDefaultIsGridView(it) }
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

            // ── About ────────────────────────────────────────────────────────
            item {
                SettingsSectionGroup(
                    label    = "About",
                    modifier = Modifier.fadeSlideIn(delay = 300, offsetY = 16.dp)
                ) {
                    SettingsInfoRow(
                        iconRes  = R.drawable.ic_info,
                        iconTint = Color(0xFF64748B),
                        title    = "App Version",
                        value    = uiState.appVersion
                    )
                    SettingsDivider()
                    SettingsNavRow(
                        iconRes  = R.drawable.ic_changelog,
                        iconTint = Color(0xFFF43F5E),
                        title    = "Changelog",
                        value    = "What's new",
                        onClick  = {
                            viewModel.fetchChangelog()
                            showChangelogDialog = true
                        }
                    )
                    SettingsDivider()
                    SettingsNavRow(
                        iconRes  = R.drawable.ic_update_progress,
                        iconTint = Color(0xFF0284C7),
                        title    = "Check for Updates",
                        value    = when (updateState) {
                            is UpdateState.Checking    -> "Checking…"
                            is UpdateState.Downloading -> "Downloading…"
                            is UpdateState.Available   -> "Update available"
                            is UpdateState.UpToDate    -> "Up to date"
                            is UpdateState.Error       -> "Update failed"
                            else                       -> "Tap to check"
                        },
                        onClick = { viewModel.checkForUpdates() }
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

    androidx.compose.animation.AnimatedVisibility(
        visible = showChangelogDialog,
        enter   = androidx.compose.animation.slideInVertically(initialOffsetY = { it }) + androidx.compose.animation.fadeIn(),
        exit    = androidx.compose.animation.slideOutVertically(targetOffsetY = { it }) + androidx.compose.animation.fadeOut()
    ) {
        ChangelogFullScreen(
            state     = changelogState,
            onDismiss = { showChangelogDialog = false },
            onRetry   = { viewModel.fetchChangelog() }
        )
    }

    var showUpdateDialog by remember(updateState, uiState.isManualUpdateCheck) {
        mutableStateOf(
            updateState is UpdateState.Available ||
            (uiState.isManualUpdateCheck && (updateState is UpdateState.UpToDate || updateState is UpdateState.Error))
        )
    }

    if (showUpdateDialog) {
        UpdateDialog(
            updateState = updateState,
            onDismiss   = { showUpdateDialog = false; viewModel.resetUpdateState() },
            onDownload  = { url, version -> showUpdateDialog = false; viewModel.downloadAndInstallUpdate(url, version) },
            onRetry     = { showUpdateDialog = false; viewModel.checkForUpdates() }
        )
    }

    if (showThemeDialog) {
        ThemeSelectionDialog(
            currentTheme    = uiState.themeMode,
            onThemeSelected = { viewModel.setThemeMode(it); showThemeDialog = false },
            onDismiss       = { showThemeDialog = false }
        )
    }
}

// ─── Section group ────────────────────────────────────────────────────────────

@Composable
private fun SettingsSectionGroup(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier) {
        // Section label
        NexusText(
            text     = label,
            style    = NexusTheme.typography.label.copy(
                fontSize      = 11.sp,
                fontWeight    = FontWeight.SemiBold,
                letterSpacing = 0.6.sp
            ),
            color    = NexusTheme.colors.textSecondary,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )
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
    iconTint: Color = NexusTheme.colors.primary
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .springBounceClick(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
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
    iconTint:        Color = NexusTheme.colors.primary
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .springBounceClick { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
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
        NexusSwitch(checked = checked, onCheckedChange = null)
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
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
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
    iconTint:  Color = if (isGranted) Color(0xFF14B8A6) else NexusTheme.colors.error
) {
    val statusColor = if (isGranted) NexusTheme.colors.success else NexusTheme.colors.error

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.springBounceClick(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
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
                .clip(RoundedCornerShape(50))
                .background(statusColor.copy(alpha = 0.10f))
                .padding(horizontal = 10.dp, vertical = 4.dp),
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
    iconTint: Color = NexusTheme.colors.primary
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
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
            .padding(start = 68.dp)
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
        Triple(ThemeMode.SYSTEM, "System Default",        R.drawable.ic_theme_system),
        Triple(ThemeMode.LIGHT,  "Light",                 R.drawable.ic_theme_light),
        Triple(ThemeMode.DARK,   "Dark",                  R.drawable.ic_theme_dark),
        Triple(ThemeMode.AMOLED, "Pitch Black (AMOLED)",  R.drawable.ic_theme_dark),
        Triple(ThemeMode.SEPIA,  "Sepia",                 R.drawable.ic_theme_sepia),
        Triple(ThemeMode.FOREST, "Forest",                R.drawable.ic_theme_forest),
        Triple(ThemeMode.SUNSET, "Sunset",                R.drawable.ic_theme_sunset)
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
                .padding(bottom = 32.dp)
                .padding(horizontal = 16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp, start = 4.dp, end = 4.dp)
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
                    ThemeMode.LIGHT  -> Color(0xFF2563EB)
                    ThemeMode.DARK   -> Color(0xFF60A5FA)
                    ThemeMode.AMOLED -> Color(0xFF38BDF8)
                    ThemeMode.SEPIA  -> Color(0xFFD97706)
                    ThemeMode.FOREST -> Color(0xFF10B981)
                    ThemeMode.SUNSET -> Color(0xFFF43F5E)
                    ThemeMode.SYSTEM -> NexusTheme.colors.primary
                }

                val swatchColor = when (mode) {
                    ThemeMode.LIGHT  -> Color(0xFFF8F9FA)
                    ThemeMode.DARK   -> Color(0xFF1E1E1E)
                    ThemeMode.AMOLED -> Color.Black
                    ThemeMode.SEPIA  -> Color(0xFFF4ECD8)
                    ThemeMode.FOREST -> Color(0xFF1F2E20)
                    ThemeMode.SUNSET -> Color(0xFF2E1C2B)
                    ThemeMode.SYSTEM -> NexusTheme.colors.surfaceVariant
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
                        .padding(vertical = 4.dp)
                        .clip(NexusTheme.shapes.medium)
                        .background(if (isSelected) accentColor.copy(alpha = 0.08f) else NexusTheme.colors.surface)
                        .border(
                            width = if (isSelected) 1.5.dp else 0.6.dp,
                            color = if (isSelected) accentColor else NexusTheme.colors.divider.copy(alpha = 0.4f),
                            shape = NexusTheme.shapes.medium
                        )
                        .springBounceClick { onThemeSelected(mode) }
                        .padding(vertical = 12.dp, horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(swatchColor)
                            .border(1.dp, NexusTheme.colors.divider.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
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

// ─── Changelog full-screen ────────────────────────────────────────────────────

@Composable
private fun ChangelogFullScreen(
    state:     ChangelogState,
    onDismiss: () -> Unit,
    onRetry:   () -> Unit
) {
    BackHandler { onDismiss() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NexusTheme.colors.background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = null,
                onClick           = {}
            )
    ) {
        NexusTopBar(
            title          = "Changelog",
            navigationIcon = {
                Image(
                    painter            = painterResource(id = R.drawable.ic_back),
                    contentDescription = "Back",
                    modifier           = Modifier
                        .clip(CircleShape)
                        .springBounceClick { onDismiss() }
                        .padding(16.dp)
                        .size(24.dp),
                    colorFilter = ColorFilter.tint(NexusTheme.colors.textPrimary)
                )
            }
        )

        Box(
            modifier         = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = state,
                transitionSpec = {
                    androidx.compose.animation.fadeIn(animationSpec = tween(300)) togetherWith
                    androidx.compose.animation.fadeOut(animationSpec = tween(300))
                },
                label = "ChangelogState"
            ) { targetState ->
                when (targetState) {
                    is ChangelogState.Idle, is ChangelogState.Loading -> {
                        NexusText("Loading…", color = NexusTheme.colors.primary, style = NexusTheme.typography.body)
                    }
                    is ChangelogState.Error -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            NexusText(
                                text      = targetState.message,
                                color     = NexusTheme.colors.error,
                                style     = NexusTheme.typography.body,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            NexusButton(text = "Retry", onClick = onRetry)
                        }
                    }
                    is ChangelogState.Success -> {
                        androidx.compose.foundation.lazy.LazyColumn(
                            modifier       = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp)
                        ) {
                            items(targetState.releases.size) { index ->
                                TimelineItem(
                                    release = targetState.releases[index],
                                    isLast  = index == targetState.releases.size - 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Timeline item ────────────────────────────────────────────────────────────

@Composable
private fun TimelineItem(
    release: com.nexus.core.updater.GithubRelease,
    isLast:  Boolean
) {
    val pulseTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue  = 0.3f,
        targetValue   = 0.8f,
        animationSpec = infiniteRepeatable(
            animation  = tween(1000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier            = Modifier.width(24.dp).fillMaxHeight()
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier         = Modifier.padding(top = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(NexusTheme.colors.primary.copy(alpha = pulseAlpha))
                )
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(NexusTheme.colors.primary)
                )
            }
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .padding(top = 4.dp, bottom = 4.dp)
                        .width(2.dp)
                        .weight(1f)
                        .background(NexusTheme.colors.primary.copy(alpha = 0.3f))
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                NexusText(
                    text  = release.version,
                    style = NexusTheme.typography.title,
                    color = NexusTheme.colors.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(NexusTheme.shapes.small)
                        .background(NexusTheme.colors.primary.copy(alpha = 0.10f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    NexusText(
                        text  = release.date,
                        style = NexusTheme.typography.caption,
                        color = NexusTheme.colors.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(NexusTheme.shapes.medium)
                    .background(NexusTheme.colors.primary.copy(alpha = 0.05f))
                    .padding(12.dp)
            ) {
                release.body.split('\n').forEach { line ->
                    val trimmed = line.trim()
                    when {
                        trimmed.isEmpty() -> Spacer(modifier = Modifier.height(8.dp))
                        trimmed.startsWith("* ") || trimmed.startsWith("- ") -> {
                            val content = trimmed.removePrefix("* ").removePrefix("- ").trim()
                            Row(modifier = Modifier.padding(bottom = 6.dp)) {
                                NexusText("•", style = NexusTheme.typography.body, color = NexusTheme.colors.primary, modifier = Modifier.padding(end = 8.dp))
                                NexusText(text = content, style = NexusTheme.typography.body, color = NexusTheme.colors.textSecondary)
                            }
                        }
                        trimmed.startsWith("### ") -> NexusText(trimmed.removePrefix("### "), style = NexusTheme.typography.title,   color = NexusTheme.colors.textPrimary, modifier = Modifier.padding(vertical = 4.dp))
                        trimmed.startsWith("## ")  -> NexusText(trimmed.removePrefix("## "),  style = NexusTheme.typography.h2,      color = NexusTheme.colors.textPrimary, modifier = Modifier.padding(vertical = 4.dp))
                        trimmed.startsWith("# ")   -> NexusText(trimmed.removePrefix("# "),   style = NexusTheme.typography.h1,      color = NexusTheme.colors.textPrimary, modifier = Modifier.padding(vertical = 4.dp))
                        else -> NexusText(text = trimmed, style = NexusTheme.typography.body, color = NexusTheme.colors.textSecondary, modifier = Modifier.padding(bottom = 4.dp))
                    }
                }
            }
        }
    }
}

// ─── Update dialog ────────────────────────────────────────────────────────────

@Composable
internal fun UpdateDialog(
    updateState: UpdateState,
    onDismiss:   () -> Unit,
    onDownload:  (String, String) -> Unit,
    onRetry:     () -> Unit
) {
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }
    val handleDismiss = { isVisible = false }

    Dialog(
        onDismissRequest = handleDismiss,
        properties       = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = if (isVisible) 0.5f else 0f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication        = null,
                    onClick           = handleDismiss
                ),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.animation.AnimatedVisibility(
                visible  = isVisible,
                enter    = androidx.compose.animation.slideInVertically(initialOffsetY = { it }, animationSpec = tween(300)) + androidx.compose.animation.fadeIn(),
                exit     = androidx.compose.animation.slideOutVertically(targetOffsetY = { it }, animationSpec = tween(300)) + androidx.compose.animation.fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                NexusSurface(
                    shape     = RoundedCornerShape(0.dp),
                    elevation = 0.dp,
                    color     = NexusTheme.colors.surface,
                    modifier  = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication        = null,
                            onClick           = {}
                        )
                ) {
                    Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
                        Box(
                            modifier         = Modifier.fillMaxWidth().padding(top = 12.dp, end = 12.dp),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            NexusIconButton(
                                onClick        = handleDismiss,
                                elevation      = 0.dp,
                                containerColor = Color.Transparent
                            ) {
                                Image(
                                    painter            = painterResource(id = R.drawable.ic_close),
                                    contentDescription = "Close",
                                    colorFilter        = ColorFilter.tint(NexusTheme.colors.textPrimary),
                                    modifier           = Modifier.size(24.dp)
                                )
                            }
                        }

                        Column(
                            modifier            = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            val iconRes  = when (updateState) { is UpdateState.Available -> R.drawable.ic_update_progress; is UpdateState.UpToDate -> R.drawable.ic_check; is UpdateState.Error -> R.drawable.ic_info; else -> R.drawable.ic_update_progress }
                            val iconTint = when (updateState) { is UpdateState.Available -> NexusTheme.colors.primary; is UpdateState.UpToDate -> NexusTheme.colors.success; is UpdateState.Error -> NexusTheme.colors.error; else -> NexusTheme.colors.primary }

                            Box(
                                modifier = Modifier.size(64.dp).clip(CircleShape).background(iconTint.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(painter = painterResource(id = iconRes), contentDescription = null, modifier = Modifier.size(32.dp), colorFilter = ColorFilter.tint(iconTint))
                            }
                            Spacer(modifier = Modifier.height(16.dp))

                            when (val state = updateState) {
                                is UpdateState.Available -> {
                                    NexusText("Update Available", style = NexusTheme.typography.h2)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    NexusText("Version ${state.version} is now available.", style = NexusTheme.typography.body, color = NexusTheme.colors.textSecondary, textAlign = TextAlign.Center)
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Box(modifier = Modifier.fillMaxWidth().background(NexusTheme.colors.surfaceVariant, NexusTheme.shapes.medium).padding(16.dp)) {
                                        NexusText(state.releaseNotes, style = NexusTheme.typography.caption)
                                    }
                                }
                                is UpdateState.UpToDate -> {
                                    NexusText("Up to Date", style = NexusTheme.typography.h2)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    NexusText("You are using the latest version of LiteView.", style = NexusTheme.typography.body, color = NexusTheme.colors.textSecondary, textAlign = TextAlign.Center)
                                }
                                is UpdateState.Error -> {
                                    NexusText("Update Error", style = NexusTheme.typography.h2)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    NexusText(state.message, style = NexusTheme.typography.body, color = NexusTheme.colors.textSecondary, textAlign = TextAlign.Center)
                                }
                                else -> {}
                            }
                        }

                        Box(modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 64.dp)) {
                            when (val state = updateState) {
                                is UpdateState.Available -> Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    NexusButton(text = "Later",   isOutlined = true, onClick = handleDismiss,                                      modifier = Modifier.weight(1f))
                                    NexusButton(text = "Install", onClick = { onDownload(state.downloadUrl, state.version) }, modifier = Modifier.weight(1f))
                                }
                                is UpdateState.UpToDate -> NexusButton(text = "Awesome", onClick = handleDismiss, modifier = Modifier.fillMaxWidth())
                                is UpdateState.Error    -> Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    NexusButton(text = "Cancel", isOutlined = true, onClick = handleDismiss, modifier = Modifier.weight(1f))
                                    NexusButton(text = "Retry",  onClick = onRetry,        modifier = Modifier.weight(1f))
                                }
                                else -> {}
                            }
                        }
                    }
                }
            }

            LaunchedEffect(isVisible) {
                if (!isVisible) {
                    kotlinx.coroutines.delay(300)
                    onDismiss()
                }
            }
        }
    }
}
