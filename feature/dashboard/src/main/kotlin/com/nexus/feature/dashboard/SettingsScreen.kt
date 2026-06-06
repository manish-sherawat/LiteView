package com.nexus.feature.dashboard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexus.core.preferences.ThemeMode
import com.nexus.core.theme.NexusTheme
import com.nexus.core.updater.UpdateState
import com.nexus.core.ui.NexusText
import com.nexus.core.ui.animations.fadeSlideIn
import com.nexus.core.ui.components.NexusCard
import com.nexus.core.ui.components.NexusDialog
import com.nexus.core.ui.components.NexusSlider
import com.nexus.core.ui.components.NexusSwitch
import com.nexus.core.ui.components.NexusTopBar
import com.nexus.core.R
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nexus.core.ui.NexusSurface
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showThemeDialog by remember { mutableStateOf(false) }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showChangelogDialog by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.exportReadingHistory(uri)
            Toast.makeText(context, "History exported", Toast.LENGTH_SHORT).show()
        }
    }

    var isPermissionGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                Environment.isExternalStorageManager()
            else
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val manageStorageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        isPermissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            Environment.isExternalStorageManager()
        else
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
    }

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
                    manageStorageLauncher.launch(
                        Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    )
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
                isPermissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                    Environment.isExternalStorageManager()
                else
                    ContextCompat.checkSelfPermission(
                        context, Manifest.permission.READ_EXTERNAL_STORAGE
                    ) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(uiState.cacheClearSuccess) {
        uiState.cacheClearSuccess?.let { success ->
            val message = if (success) "Cache cleared successfully" else "Failed to clear cache"
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.dismissCacheClearResult()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NexusTheme.colors.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            NexusTopBar(
                title = "Settings",
                navigationIcon = {
                    Image(
                        painter = painterResource(id = R.drawable.ic_back),
                        contentDescription = "Back",
                        modifier = Modifier
                            .clickable { onBack() }
                            .padding(16.dp)
                            .size(24.dp),
                        colorFilter = ColorFilter.tint(NexusTheme.colors.textPrimary)
                    )
                }
            )
            
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                item {
                    SettingsSectionGroup(
                        label = "Appearance",
                        modifier = Modifier.fadeSlideIn(delay = 0)
                    ) {
                        SettingsNavRow(
                            emoji = "🎨",
                            iconRes = R.drawable.ic_theme,
                            title = "Theme",
                            value = when (uiState.themeMode) {
                                ThemeMode.SYSTEM -> "System"
                                ThemeMode.LIGHT -> "Light"
                                ThemeMode.DARK -> "Dark"
                            },
                            onClick = { showThemeDialog = true }
                        )
                    }
                }

                item {
                    SettingsSectionGroup(
                        label = "Reader",
                        modifier = Modifier.fadeSlideIn(delay = 60)
                    ) {
                        SettingsSwitchRow(
                            emoji = "📖",
                            iconRes = R.drawable.ic_book,
                            title = "Remember Position",
                            subtitle = "Resume where you left off",
                            checked = uiState.rememberReadingPosition,
                            onCheckedChange = { viewModel.setRememberReadingPosition(it) }
                        )
                        SettingsDivider()
                        SettingsSwitchRow(
                            emoji = "📱",
                            iconRes = R.drawable.ic_screen_awake,
                            title = "Keep Screen Awake",
                            subtitle = "Prevent sleep while reading",
                            checked = uiState.keepScreenAwake,
                            onCheckedChange = { viewModel.setKeepScreenAwake(it) }
                        )
                    }
                }

                item {
                    SettingsSectionGroup(
                        label = "General",
                        modifier = Modifier.fadeSlideIn(delay = 100)
                    ) {
                        SettingsSwitchRow(
                            emoji = "📳",
                            iconRes = R.drawable.ic_haptic,
                            title = "Haptic Feedback",
                            subtitle = "Vibrate on button taps and interactions",
                            checked = uiState.hapticFeedbackEnabled,
                            onCheckedChange = { viewModel.setHapticFeedbackEnabled(it) }
                        )
                        SettingsDivider()
                        SettingsSwitchRow(
                            emoji = "🚀",
                            iconRes = R.drawable.ic_startup,
                            title = "Startup to Picker",
                            subtitle = "Bypass the Home screen and open file picker",
                            checked = uiState.startupToPicker,
                            onCheckedChange = { viewModel.setStartupToPicker(it) }
                        )
                        SettingsDivider()
                        SettingsNavRow(
                            emoji = "💾",
                            iconRes = R.drawable.ic_save,
                            title = "Export Reading History",
                            value = "Backup as JSON",
                            onClick = { exportLauncher.launch("nexus_history_backup.json") }
                        )
                        SettingsDivider()
                        SettingsDestructiveRow(
                            emoji = "🗑️",
                            iconRes = R.drawable.ic_delete,
                            title = "Clear Cache",
                            subtitle = "Currently ${uiState.cacheSizeText}",
                            onClick = { showClearCacheDialog = true }
                        )
                        SettingsDivider()
                        SettingsDestructiveRow(
                            emoji = "🔄",
                            iconRes = R.drawable.ic_reset,
                            title = "Reset All Settings",
                            subtitle = "Restore default preferences",
                            onClick = { showResetDialog = true }
                        )
                    }
                }

                item {
                    SettingsSectionGroup(
                        label = "Permissions",
                        modifier = Modifier.fadeSlideIn(delay = 140)
                    ) {
                        SettingsPermissionRow(
                            emoji = "📂",
                            iconRes = R.drawable.ic_folder,
                            title = "Storage Access",
                            isGranted = isPermissionGranted,
                            onClick = if (isPermissionGranted) null else ({ triggerPermissionRequest() })
                        )
                    }
                }

                item {
                    SettingsSectionGroup(
                        label = "About",
                        modifier = Modifier.fadeSlideIn(delay = 180)
                    ) {
                        SettingsInfoRow(
                            emoji = "ℹ️",
                            iconRes = R.drawable.ic_info,
                            title = "App Version",
                            value = uiState.appVersion
                        )
                        SettingsDivider()
                        SettingsNavRow(
                            emoji = "",
                            iconRes = R.drawable.ic_changelog,
                            title = "Changelog",
                            value = "What's new",
                            onClick = { showChangelogDialog = true }
                        )
                        SettingsDivider()
                        SettingsNavRow(
                            emoji = "",
                            iconRes = R.drawable.ic_update_progress,
                            title = "Check for Updates",
                            value = when (updateState) {
                                is UpdateState.Checking -> "Checking..."
                                is UpdateState.Downloading -> "Downloading..."
                                else -> "Check GitHub"
                            },
                            onClick = { viewModel.checkForUpdates() }
                        )
                    }
                }
            }
        }
    }

    if (showThemeDialog) {
        ThemeSelectionDialog(
            currentTheme = uiState.themeMode,
            onThemeSelected = {
                viewModel.setThemeMode(it)
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false }
        )
    }

    if (showClearCacheDialog) {
        NexusDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = { NexusText("Clear Cache?", style = NexusTheme.typography.h2) },
            text = { NexusText("This will remove temporary files like document thumbnails and extracted text. Your recent documents history will remain intact.") },
            confirmButton = {
                NexusText(
                    text = "Clear Cache",
                    color = NexusTheme.colors.error,
                    modifier = Modifier
                        .clickable {
                            viewModel.clearCache()
                            showClearCacheDialog = false
                        }
                        .padding(8.dp)
                )
            },
            dismissButton = {
                NexusText(
                    text = "Cancel",
                    modifier = Modifier
                        .clickable { showClearCacheDialog = false }
                        .padding(8.dp)
                )
            }
        )
    }

    if (showResetDialog) {
        NexusDialog(
            onDismissRequest = { showResetDialog = false },
            title = { NexusText("Reset Settings?", style = NexusTheme.typography.h2) },
            text = { NexusText("Are you sure you want to restore all settings to their defaults? Your reading history and starred files will not be affected.") },
            confirmButton = {
                NexusText(
                    text = "Reset",
                    color = NexusTheme.colors.error,
                    modifier = Modifier
                        .clickable {
                            viewModel.resetToDefaults()
                            showResetDialog = false
                        }
                        .padding(8.dp)
                )
            },
            dismissButton = {
                NexusText(
                    text = "Cancel",
                    modifier = Modifier
                        .clickable { showResetDialog = false }
                        .padding(8.dp)
                )
            }
        )
    }

    if (showChangelogDialog) {
        ChangelogDialog(onDismiss = { showChangelogDialog = false })
    }

    when (val state = updateState) {
        is UpdateState.Available -> {
            NexusDialog(
                onDismissRequest = { viewModel.resetUpdateState() },
                title = { NexusText("Update Available (${state.version})", style = NexusTheme.typography.h2) },
                text = { NexusText("Release Notes:\n${state.releaseNotes}") },
                confirmButton = {
                    NexusText(
                        text = "Download & Install",
                        color = NexusTheme.colors.primary,
                        modifier = Modifier
                            .clickable { viewModel.downloadAndInstallUpdate(state.downloadUrl, state.version) }
                            .padding(8.dp)
                    )
                },
                dismissButton = {
                    NexusText(
                        text = "Later",
                        modifier = Modifier
                            .clickable { viewModel.resetUpdateState() }
                            .padding(8.dp)
                    )
                }
            )
        }
        is UpdateState.UpToDate -> {
            NexusDialog(
                onDismissRequest = { viewModel.resetUpdateState() },
                title = { NexusText("Up to Date", style = NexusTheme.typography.h2) },
                text = { NexusText("You are using the latest version of LiteView.") },
                confirmButton = {
                    NexusText(
                        text = "OK",
                        color = NexusTheme.colors.primary,
                        modifier = Modifier
                            .clickable { viewModel.resetUpdateState() }
                            .padding(8.dp)
                    )
                }
            )
        }
        is UpdateState.Error -> {
            NexusDialog(
                onDismissRequest = { viewModel.resetUpdateState() },
                title = { NexusText("Error Checking for Update", style = NexusTheme.typography.h2) },
                text = { NexusText(state.message) },
                confirmButton = {
                    NexusText(
                        text = "OK",
                        color = NexusTheme.colors.primary,
                        modifier = Modifier
                            .clickable { viewModel.resetUpdateState() }
                            .padding(8.dp)
                    )
                }
            )
        }
        else -> {}
    }
}

@Composable
private fun SettingsSectionGroup(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier) {
        NexusText(
            text = label,
            style = NexusTheme.typography.label,
            color = NexusTheme.colors.textSecondary,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
        )
        NexusCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                content()
            }
        }
    }
}

@Composable
private fun SettingsNavRow(
    emoji: String,
    title: String,
    value: String,
    onClick: () -> Unit,
    iconRes: Int? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (iconRes != null) {
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = title,
                modifier = Modifier.size(24.dp),
                colorFilter = ColorFilter.tint(NexusTheme.colors.textPrimary)
            )
        } else {
            NexusText(text = emoji, style = NexusTheme.typography.h2)
        }
        Spacer(modifier = Modifier.width(16.dp))
        NexusText(
            text = title,
            style = NexusTheme.typography.body,
            modifier = Modifier.weight(1f)
        )
        NexusText(
            text = value,
            style = NexusTheme.typography.caption,
            color = NexusTheme.colors.textSecondary
        )
        Spacer(modifier = Modifier.width(8.dp))
        NexusText(">", color = NexusTheme.colors.textSecondary)
    }
}

@Composable
private fun SettingsSwitchRow(
    emoji: String,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    iconRes: Int? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (iconRes != null) {
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = title,
                modifier = Modifier.size(24.dp),
                colorFilter = ColorFilter.tint(NexusTheme.colors.textPrimary)
            )
        } else {
            NexusText(text = emoji, style = NexusTheme.typography.h2)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            NexusText(
                text = title,
                style = NexusTheme.typography.body
            )
            NexusText(
                text = subtitle,
                style = NexusTheme.typography.caption,
                color = NexusTheme.colors.textSecondary,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        NexusSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsDestructiveRow(
    emoji: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    iconRes: Int? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (iconRes != null) {
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = title,
                modifier = Modifier.size(24.dp),
                colorFilter = ColorFilter.tint(NexusTheme.colors.error)
            )
        } else {
            NexusText(text = emoji, style = NexusTheme.typography.h2)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            NexusText(
                text = title,
                style = NexusTheme.typography.body,
                color = NexusTheme.colors.error
            )
            NexusText(
                text = subtitle,
                style = NexusTheme.typography.caption,
                color = NexusTheme.colors.error.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun SettingsPermissionRow(
    emoji: String,
    title: String,
    isGranted: Boolean,
    onClick: (() -> Unit)?,
    iconRes: Int? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (iconRes != null) {
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = title,
                modifier = Modifier.size(24.dp),
                colorFilter = ColorFilter.tint(NexusTheme.colors.textPrimary)
            )
        } else {
            NexusText(text = emoji, style = NexusTheme.typography.h2)
        }
        Spacer(modifier = Modifier.width(16.dp))
        NexusText(
            text = title,
            style = NexusTheme.typography.body,
            modifier = Modifier.weight(1f)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (isGranted) Color.Green else NexusTheme.colors.error)
            )
            Spacer(modifier = Modifier.width(8.dp))
            NexusText(
                text = if (isGranted) "Granted" else "Denied",
                style = NexusTheme.typography.caption,
                color = NexusTheme.colors.textSecondary
            )
        }
    }
}

@Composable
private fun SettingsInfoRow(
    emoji: String,
    title: String,
    value: String,
    iconRes: Int? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (iconRes != null) {
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = title,
                modifier = Modifier.size(24.dp),
                colorFilter = ColorFilter.tint(NexusTheme.colors.textPrimary)
            )
        } else {
            NexusText(text = emoji, style = NexusTheme.typography.h2)
        }
        Spacer(modifier = Modifier.width(16.dp))
        NexusText(
            text = title,
            style = NexusTheme.typography.body,
            modifier = Modifier.weight(1f)
        )
        NexusText(
            text = value,
            style = NexusTheme.typography.body,
            color = NexusTheme.colors.textSecondary
        )
    }
}

@Composable
private fun SettingsDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 68.dp)
            .height(1.dp)
            .background(NexusTheme.colors.divider)
    )
}

@Composable
private fun ThemeSelectionDialog(
    currentTheme: ThemeMode,
    onThemeSelected: (ThemeMode) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(
        Triple(ThemeMode.SYSTEM, "System", R.drawable.ic_theme_system),
        Triple(ThemeMode.LIGHT, "Light", R.drawable.ic_theme_light),
        Triple(ThemeMode.DARK, "Dark", R.drawable.ic_theme_dark)
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.BottomCenter
        ) {
            NexusSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        var accumulatedDrag = 0f
                        detectVerticalDragGestures(
                            onDragEnd = { accumulatedDrag = 0f },
                            onDragCancel = { accumulatedDrag = 0f },
                            onVerticalDrag = { _, dragAmount ->
                                accumulatedDrag += dragAmount
                                if (accumulatedDrag > 100f) {
                                    onDismiss()
                                    accumulatedDrag = 0f
                                }
                            }
                        )
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {} // Prevent clicks on the dialog from dismissing it
                    ),
                shape = NexusTheme.shapes.large,
                elevation = 24.dp,
                color = NexusTheme.colors.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(32.dp)
                            .height(4.dp)
                            .clip(NexusTheme.shapes.pill)
                            .background(NexusTheme.colors.textSecondary.copy(alpha = 0.2f))
                            .align(Alignment.CenterHorizontally)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))

                    NexusText(
                        text = "Theme",
                        style = NexusTheme.typography.title,
                        modifier = Modifier.padding(bottom = 4.dp, start = 8.dp, end = 8.dp)
                    )
                    NexusText(
                        text = "Choose how the app looks",
                        style = NexusTheme.typography.body,
                        color = NexusTheme.colors.textSecondary,
                        modifier = Modifier.padding(bottom = 16.dp, start = 8.dp, end = 8.dp)
                    )

                    options.forEach { (mode, label, iconRes) ->
                        val isSelected = currentTheme == mode
                        val contentColor = if (isSelected) NexusTheme.colors.primary else NexusTheme.colors.textPrimary
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(NexusTheme.shapes.medium)
                                .clickable { onThemeSelected(mode) }
                                .padding(vertical = 12.dp, horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(contentColor.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = iconRes),
                                    contentDescription = label,
                                    modifier = Modifier.size(20.dp),
                                    colorFilter = ColorFilter.tint(contentColor)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            NexusText(
                                text = label,
                                style = NexusTheme.typography.body,
                                color = contentColor,
                                modifier = Modifier.weight(1f)
                            )
                            if (isSelected) {
                                NexusText("✓", color = NexusTheme.colors.primary, style = NexusTheme.typography.title)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

private data class ChangelogEntry(val version: String, val date: String, val notes: List<String>)

@Composable
private fun ChangelogDialog(onDismiss: () -> Unit) {
    val changelog = listOf(
        ChangelogEntry("v1.2.0", "June 2026", listOf("Renamed app to LiteView", "Added smooth spring-bounce UI animations", "Redesigned Settings with Changelog timeline", "Performance optimizations for large PDFs")),
        ChangelogEntry("v1.1.5", "May 2026", listOf("Integrated built-in Document Scanner (CameraX & ML Kit)", "Improved offline document caching", "Added quick actions to Dashboard")),
        ChangelogEntry("v1.1.0", "April 2026", listOf("Added support for Dark Mode", "Implemented 'Remember Reading Position' feature", "Added Haptic Feedback support", "Fixed minor text rendering bugs")),
        ChangelogEntry("v1.0.5", "March 2026", listOf("Added XLSX spreadsheet viewing support", "Improved DOCX parsing speed", "Added full-text search within documents")),
        ChangelogEntry("v1.0.0", "January 2026", listOf("Initial Release of LiteView", "Offline PDF and TXT Viewer", "Recent & Starred Files Dashboard", "Local storage integration"))
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.Center
        ) {
            NexusSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {} 
                    ),
                shape = NexusTheme.shapes.large,
                elevation = 24.dp,
                color = NexusTheme.colors.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
                    NexusText(
                        text = "Changelog",
                        style = NexusTheme.typography.h1,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(changelog.size) { index ->
                            TimelineItem(
                                entry = changelog[index],
                                isLast = index == changelog.size - 1
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        NexusText(
                            text = "Close",
                            color = NexusTheme.colors.primary,
                            style = NexusTheme.typography.label,
                            modifier = Modifier
                                .clip(NexusTheme.shapes.small)
                                .clickable(onClick = onDismiss)
                                .padding(12.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineItem(
    entry: ChangelogEntry,
    isLast: Boolean
) {
    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(24.dp).fillMaxHeight()
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(NexusTheme.colors.primary)
            )
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                NexusText(
                    text = entry.version,
                    style = NexusTheme.typography.title,
                    color = NexusTheme.colors.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(NexusTheme.shapes.small)
                        .background(NexusTheme.colors.primary.copy(alpha = 0.1f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    NexusText(
                        text = entry.date,
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
                entry.notes.forEach { note ->
                    Row(modifier = Modifier.padding(bottom = 6.dp)) {
                        NexusText(
                            text = "•",
                            style = NexusTheme.typography.body,
                            color = NexusTheme.colors.primary,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        NexusText(
                            text = note,
                            style = NexusTheme.typography.body,
                            color = NexusTheme.colors.textSecondary
                        )
                    }
                }
            }
        }
    }
}