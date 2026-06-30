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
import androidx.compose.ui.text.style.TextAlign
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
import com.nexus.core.ui.animations.springBounceClick
import com.nexus.core.ui.components.NexusCard
import com.nexus.core.ui.components.NexusDialog
import com.nexus.core.ui.components.NexusSlider
import com.nexus.core.ui.components.NexusSwitch
import com.nexus.core.ui.components.NexusTopBar
import com.nexus.core.R
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nexus.core.ui.NexusSurface
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.launch
import androidx.compose.ui.draw.shadow
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val changelogState by viewModel.changelogState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showChangelogDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }

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
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .springBounceClick { onBack() }
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
                        label = "Appearance"
                    ) {
                        val themeText = when (uiState.themeMode) {
                            ThemeMode.LIGHT -> "Light"
                            ThemeMode.DARK -> "Dark"
                            ThemeMode.SEPIA -> "Sepia"
                            ThemeMode.FOREST -> "Forest"
                            ThemeMode.SUNSET -> "Sunset"
                            ThemeMode.SYSTEM -> "System Default"
                        }
                        SettingsNavRow(
                            iconRes = R.drawable.ic_theme,
                            title = "Theme",
                            value = themeText,
                            onClick = { showThemeDialog = true }
                        )
                    }
                }

                item {
                    SettingsSectionGroup(
                        label = "Reader"
                    ) {
                        SettingsSwitchRow(
                            iconRes = R.drawable.ic_book,
                            title = "Remember Position",
                            subtitle = "Resume where you left off",
                            checked = uiState.rememberReadingPosition,
                            onCheckedChange = { viewModel.setRememberReadingPosition(it) }
                        )
                        SettingsDivider()
                        SettingsSwitchRow(
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
                        label = "General"
                    ) {
                        SettingsSwitchRow(
                            iconRes = R.drawable.ic_haptic,
                            title = "Haptic Feedback",
                            subtitle = "Vibrate on button taps and interactions",
                            checked = uiState.hapticFeedbackEnabled,
                            onCheckedChange = { viewModel.setHapticFeedbackEnabled(it) }
                        )
                        SettingsDivider()
                        SettingsSwitchRow(
                            iconRes = R.drawable.ic_startup,
                            title = "Startup to Picker",
                            subtitle = "Bypass the Home screen and open file picker\n(Long-press the app icon to access Settings)",
                            checked = uiState.startupToPicker,
                            onCheckedChange = { viewModel.setStartupToPicker(it) }
                        )
                        SettingsDivider()
                        SettingsSwitchRow(
                            iconRes = R.drawable.ic_view_grid,
                            title = "Default to Grid View",
                            subtitle = "Show documents in a grid layout by default",
                            checked = uiState.defaultIsGridView,
                            onCheckedChange = { viewModel.setDefaultIsGridView(it) }
                        )
                        SettingsDivider()
                        SettingsDestructiveRow(
                            iconRes = R.drawable.ic_delete,
                            title = "Clear Cache",
                            subtitle = "Currently ${uiState.cacheSizeText}",
                            onClick = { showClearCacheDialog = true }
                        )
                    }
                }

                item {
                    SettingsSectionGroup(
                        label = "Permissions"
                    ) {
                        SettingsPermissionRow(
                            iconRes = R.drawable.ic_folder,
                            title = "Storage Access",
                            isGranted = isPermissionGranted,
                            onClick = if (isPermissionGranted) null else ({ triggerPermissionRequest() })
                        )
                    }
                }

                item {
                    SettingsSectionGroup(
                        label = "About"
                    ) {
                        SettingsInfoRow(
                            iconRes = R.drawable.ic_info,
                            title = "App Version",
                            value = uiState.appVersion
                        )
                        SettingsDivider()
                        SettingsNavRow(
                            iconRes = R.drawable.ic_changelog,
                            title = "Changelog",
                            value = "What's new",
                            onClick = {
                                viewModel.fetchChangelog()
                                showChangelogDialog = true
                            }
                        )
                        SettingsDivider()
                        SettingsNavRow(
                            iconRes = R.drawable.ic_update_progress,
                            title = "Check for Updates",
                            value = when (updateState) {
                                is UpdateState.Checking -> "Checking..."
                                is UpdateState.Downloading -> "Downloading..."
                                is UpdateState.Available -> "Update available"
                                is UpdateState.UpToDate -> "Up to date"
                                is UpdateState.Error -> "Update failed"
                                else -> "Tap to check"
                            },
                            onClick = { viewModel.checkForUpdates() }
                        )
                    }
                }
            }
        }
    }

    if (showClearCacheDialog) {
        NexusDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = { NexusText("Clear Cache?", style = NexusTheme.typography.h2) },
            text = { NexusText("This will remove temporary files like document thumbnails and extracted text. Your recent documents history will remain intact.") },
            confirmButton = {
                com.nexus.core.ui.components.NexusButton(
                    text = "Clear Cache",
                    containerColor = NexusTheme.colors.error,
                    contentColor = NexusTheme.colors.background,
                    onClick = {
                        viewModel.clearCache()
                        showClearCacheDialog = false
                    }
                )
            },
            dismissButton = {
                com.nexus.core.ui.components.NexusButton(
                    text = "Cancel",
                    isOutlined = true,
                    onClick = { showClearCacheDialog = false }
                )
            }
        )
    }

    androidx.compose.animation.AnimatedVisibility(
        visible = showChangelogDialog,
        enter = androidx.compose.animation.slideInVertically(initialOffsetY = { it }) + androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { it }) + androidx.compose.animation.fadeOut()
    ) {
        ChangelogFullScreen(
            state = changelogState,
            onDismiss = { showChangelogDialog = false },
            onRetry = { viewModel.fetchChangelog() }
        )
    }

    var showUpdateDialog by remember(updateState) {
        mutableStateOf(updateState is UpdateState.Available)
    }

    if (showUpdateDialog) {
        UpdateDialog(
            updateState = updateState,
            onDismiss = {
                showUpdateDialog = false
                viewModel.resetUpdateState()
            },
            onDownload = { url, version ->
                showUpdateDialog = false
                viewModel.downloadAndInstallUpdate(url, version)
            },
            onRetry = {
                showUpdateDialog = false
                viewModel.checkForUpdates()
            }
        )
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
}

@Composable
private fun SettingsSectionGroup(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier.padding(top = 16.dp)) {
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
    title: String,
    value: String,
    onClick: () -> Unit,
    iconRes: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .springBounceClick(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(id = iconRes),
            contentDescription = title,
            modifier = Modifier.size(24.dp),
            colorFilter = ColorFilter.tint(NexusTheme.colors.textPrimary)
        )
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
        Image(
            painter = painterResource(id = com.nexus.core.R.drawable.ic_arrow_right),
            contentDescription = null,
            colorFilter = ColorFilter.tint(NexusTheme.colors.textSecondary),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    iconRes: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .springBounceClick { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(id = iconRes),
            contentDescription = title,
            modifier = Modifier.size(24.dp),
            colorFilter = ColorFilter.tint(NexusTheme.colors.textPrimary)
        )
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
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    iconRes: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .springBounceClick(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(id = iconRes),
            contentDescription = title,
            modifier = Modifier.size(24.dp),
            colorFilter = ColorFilter.tint(NexusTheme.colors.error)
        )
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
    title: String,
    isGranted: Boolean,
    onClick: (() -> Unit)?,
    iconRes: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.springBounceClick(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(id = iconRes),
            contentDescription = title,
            modifier = Modifier.size(24.dp),
            colorFilter = ColorFilter.tint(NexusTheme.colors.textPrimary)
        )
        Spacer(modifier = Modifier.width(16.dp))
        NexusText(
            text = title,
            style = NexusTheme.typography.body,
            modifier = Modifier.weight(1f)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(if (isGranted) NexusTheme.colors.success else NexusTheme.colors.error)
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
    title: String,
    value: String,
    iconRes: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(id = iconRes),
            contentDescription = title,
            modifier = Modifier.size(24.dp),
            colorFilter = ColorFilter.tint(NexusTheme.colors.textPrimary)
        )
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
            .padding(start = 56.dp)
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
        Triple(ThemeMode.SYSTEM, "System Default", R.drawable.ic_theme_system),
        Triple(ThemeMode.LIGHT, "Light", R.drawable.ic_theme_light),
        Triple(ThemeMode.DARK, "Dark", R.drawable.ic_theme_dark),
        Triple(ThemeMode.SEPIA, "Sepia", R.drawable.ic_theme_sepia),
        Triple(ThemeMode.FOREST, "Forest", R.drawable.ic_theme_forest),
        Triple(ThemeMode.SUNSET, "Sunset", R.drawable.ic_theme_sunset)
    )

    var dragOffset by remember { mutableStateOf(0f) }
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }
    
    val animatedOffset by animateFloatAsState(
        targetValue = if (isVisible) dragOffset else 1000f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
        ),
        finishedListener = { if (!isVisible) onDismiss() }
    )
    
    val handleDismiss = { isVisible = false }

    Dialog(
        onDismissRequest = handleDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = handleDismiss
                ),
            contentAlignment = Alignment.BottomCenter
        ) {
            NexusSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { androidx.compose.ui.unit.IntOffset(0, animatedOffset.toInt()) }
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = { 
                                if (dragOffset > 150f) handleDismiss() else dragOffset = 0f 
                            },
                            onDragCancel = { dragOffset = 0f },
                            onVerticalDrag = { _, dragAmount ->
                                dragOffset = (dragOffset + dragAmount).coerceAtLeast(0f)
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
                                .springBounceClick { onThemeSelected(mode) }
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
                                NexusText("\u2713", color = NexusTheme.colors.primary, style = NexusTheme.typography.title)
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
private fun ChangelogFullScreen(state: ChangelogState, onDismiss: () -> Unit, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NexusTheme.colors.background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {} 
            )
    ) {
        NexusTopBar(
            title = "Changelog",
            navigationIcon = {
                Image(
                    painter = painterResource(id = R.drawable.ic_back),
                    contentDescription = "Back",
                    modifier = Modifier
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .springBounceClick { onDismiss() }
                        .padding(16.dp)
                        .size(24.dp),
                    colorFilter = ColorFilter.tint(NexusTheme.colors.textPrimary)
                )
            }
        )
        
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            when (state) {
                is ChangelogState.Idle, is ChangelogState.Loading -> {
                    NexusText("Loading...", color = NexusTheme.colors.primary, style = NexusTheme.typography.body)
                }
                is ChangelogState.Error -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        NexusText(
                            text = state.message,
                            color = NexusTheme.colors.error,
                            style = NexusTheme.typography.body,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        com.nexus.core.ui.components.NexusButton(
                            text = "Retry",
                            onClick = onRetry
                        )
                    }
                }
                is ChangelogState.Success -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp)
                    ) {
                        items(state.releases.size) { index ->
                            TimelineItem(
                                release = state.releases[index],
                                isLast = index == state.releases.size - 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineItem(
    release: com.nexus.core.updater.GithubRelease,
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
                    text = release.version,
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
                        text = release.date,
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
                // Render full release body directly, parsing basic structure
                val lines = release.body.split('\n')
                
                lines.forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.isEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                    } else if (trimmed.startsWith("* ") || trimmed.startsWith("- ")) {
                        val content = trimmed.removePrefix("* ").removePrefix("- ").trim()
                        Row(modifier = Modifier.padding(bottom = 6.dp)) {
                            NexusText(
                                text = "\u2022",
                                style = NexusTheme.typography.body,
                                color = NexusTheme.colors.primary,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            NexusText(
                                text = content,
                                style = NexusTheme.typography.body,
                                color = NexusTheme.colors.textSecondary
                            )
                        }
                    } else if (trimmed.startsWith("### ")) {
                         NexusText(
                             text = trimmed.removePrefix("### "),
                             style = NexusTheme.typography.title,
                             color = NexusTheme.colors.textPrimary,
                             modifier = Modifier.padding(vertical = 4.dp)
                         )
                    } else if (trimmed.startsWith("## ")) {
                         NexusText(
                             text = trimmed.removePrefix("## "),
                             style = NexusTheme.typography.h2,
                             color = NexusTheme.colors.textPrimary,
                             modifier = Modifier.padding(vertical = 4.dp)
                         )
                    } else if (trimmed.startsWith("# ")) {
                         NexusText(
                             text = trimmed.removePrefix("# "),
                             style = NexusTheme.typography.h1,
                             color = NexusTheme.colors.textPrimary,
                             modifier = Modifier.padding(vertical = 4.dp)
                         )
                    } else {
                        NexusText(
                            text = trimmed,
                            style = NexusTheme.typography.body,
                            color = NexusTheme.colors.textSecondary,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdateDialog(
    updateState: UpdateState,
    onDismiss: () -> Unit,
    onDownload: (String, String) -> Unit,
    onRetry: () -> Unit
) {
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }

    val handleDismiss = {
        isVisible = false
        // Let the animation finish before calling onDismiss
    }

    Dialog(
        onDismissRequest = handleDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = if (isVisible) 0.5f else 0f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = handleDismiss
                ),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.animation.AnimatedVisibility(
                visible = isVisible,
                enter = androidx.compose.animation.scaleIn(
                    animationSpec = androidx.compose.animation.core.spring(
                        dampingRatio = 0.75f,
                        stiffness = 300f
                    )
                ) + androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.scaleOut() + androidx.compose.animation.fadeOut(),
                modifier = Modifier.padding(24.dp)
            ) {
                NexusSurface(
                    shape = NexusTheme.shapes.large,
                    elevation = 24.dp,
                    color = NexusTheme.colors.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {} // Prevent dismiss when clicking inside
                        )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val iconRes = when (updateState) {
                            is UpdateState.Available -> R.drawable.ic_update_progress
                            is UpdateState.UpToDate -> R.drawable.ic_check
                            is UpdateState.Error -> R.drawable.ic_info
                            else -> R.drawable.ic_update_progress
                        }
                        
                        val iconTint = when (updateState) {
                            is UpdateState.Available -> NexusTheme.colors.primary
                            is UpdateState.UpToDate -> NexusTheme.colors.success
                            is UpdateState.Error -> NexusTheme.colors.error
                            else -> NexusTheme.colors.primary
                        }

                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(iconTint.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = iconRes),
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                                colorFilter = ColorFilter.tint(iconTint)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        when (val state = updateState) {
                            is UpdateState.Available -> {
                                NexusText(
                                    text = "Update Available",
                                    style = NexusTheme.typography.h2,
                                    color = NexusTheme.colors.textPrimary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                NexusText(
                                    text = "Version ${state.version} is now available.",
                                    style = NexusTheme.typography.body,
                                    color = NexusTheme.colors.textSecondary,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(NexusTheme.colors.surfaceVariant, NexusTheme.shapes.medium)
                                        .padding(16.dp)
                                ) {
                                    NexusText(
                                        text = state.releaseNotes,
                                        style = NexusTheme.typography.caption,
                                        color = NexusTheme.colors.textPrimary
                                    )
                                }
                                Spacer(modifier = Modifier.height(24.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    com.nexus.core.ui.components.NexusButton(
                                        text = "Later",
                                        isOutlined = true,
                                        onClick = handleDismiss,
                                        modifier = Modifier.weight(1f)
                                    )
                                    com.nexus.core.ui.components.NexusButton(
                                        text = "Install",
                                        onClick = { onDownload(state.downloadUrl, state.version) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            is UpdateState.UpToDate -> {
                                NexusText(
                                    text = "Up to Date",
                                    style = NexusTheme.typography.h2,
                                    color = NexusTheme.colors.textPrimary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                NexusText(
                                    text = "You are using the latest version of LiteView.",
                                    style = NexusTheme.typography.body,
                                    color = NexusTheme.colors.textSecondary,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                                com.nexus.core.ui.components.NexusButton(
                                    text = "Awesome",
                                    onClick = handleDismiss,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            is UpdateState.Error -> {
                                NexusText(
                                    text = "Update Error",
                                    style = NexusTheme.typography.h2,
                                    color = NexusTheme.colors.textPrimary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                NexusText(
                                    text = state.message,
                                    style = NexusTheme.typography.body,
                                    color = NexusTheme.colors.textSecondary,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    com.nexus.core.ui.components.NexusButton(
                                        text = "Cancel",
                                        isOutlined = true,
                                        onClick = handleDismiss,
                                        modifier = Modifier.weight(1f)
                                    )
                                    com.nexus.core.ui.components.NexusButton(
                                        text = "Retry",
                                        onClick = onRetry,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            else -> {}
                        }
                    }
                }
            }
            
            LaunchedEffect(isVisible) {
                if (!isVisible) {
                    kotlinx.coroutines.delay(300) // Wait for exit animation
                    onDismiss()
                }
            }
        }
    }
}
