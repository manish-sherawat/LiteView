package com.nexus.nexusdocs

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import kotlinx.coroutines.launch

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.nexus.core.ui.animations.DurationQuickRelease
import com.nexus.core.ui.animations.DurationScreenEnter
import com.nexus.core.ui.animations.DurationFadeIn
import com.nexus.core.ui.animations.EmphasizedAccelerateEasing
import com.nexus.core.ui.animations.EmphasizedDecelerateEasing
import com.nexus.core.ui.animations.navPillSpring
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.NavHostController
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nexus.core.navigation.DocumentReaderRouter
import com.nexus.core.navigation.NexusRoute
import com.nexus.core.preferences.ThemeMode
import com.nexus.core.preferences.HomeStyle
import com.nexus.core.preferences.UserPreferencesRepository

import android.net.Uri

import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
import com.nexus.core.ui.components.NexusFloatingBottomNav
import com.nexus.core.ui.components.NexusNavItem
import com.nexus.core.ui.components.rememberFloatingTabBarScrollConnection
import com.nexus.core.ui.utils.LocalAppBackdrop
import com.nexus.core.ui.utils.LocalGlassEffectConfig
import com.nexus.core.ui.utils.layerBackdrop
import com.nexus.core.ui.utils.readerNavGlassConfig
import com.nexus.core.ui.utils.rememberLayerBackdrop
import com.nexus.nexusdocs.navigation.DocumentReaderRouterImpl
import com.nexus.nexusdocs.navigation.NexusNavHost
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import com.nexus.core.R

// ─── Main Activity ────────────────────────────────────────────────────────────
// @AndroidEntryPoint enables Hilt injection in this Activity.
// SingleTask launch mode ensures only one instance exists — important for
// handling document opening intents without creating duplicate back stacks.

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var router: DocumentReaderRouter

    @Inject
    lateinit var preferencesRepository: UserPreferencesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        // Edge-to-edge must be called before installSplashScreen()
        enableEdgeToEdge()
        // Install splash screen BEFORE super.onCreate()
        installSplashScreen()
        super.onCreate(savedInstanceState)

        setContent {
            val themeMode by preferencesRepository.themeMode
                .collectAsStateWithLifecycle(initialValue = ThemeMode.LIGHT)
            
            val homeStyle by preferencesRepository.homeStyle
                .collectAsStateWithLifecycle(initialValue = HomeStyle.APPLE_GLASSMORPHIC)

            val hapticFeedbackEnabled by preferencesRepository.hapticFeedbackEnabled
                .collectAsStateWithLifecycle(initialValue = true)

            val isFirstLaunchState = preferencesRepository.isFirstLaunch
                .collectAsStateWithLifecycle(initialValue = null)

            val isFirstLaunch = isFirstLaunchState.value

            if (isFirstLaunch == null) {
                Box(modifier = Modifier.fillMaxSize())
                return@setContent
            }

            val startDestination = if (isFirstLaunch) com.nexus.core.navigation.NexusRoute.Welcome.route else com.nexus.core.navigation.NexusRoute.Splash.route

            com.nexus.core.theme.NexusDocsViewerTheme(
                themeMode = themeMode,
                hapticFeedbackEnabled = hapticFeedbackEnabled
            ) {
                val navController: NavHostController = rememberNavController()

                // Bind the NavController to our router implementation
                LaunchedEffect(navController) {
                    (router as? DocumentReaderRouterImpl)?.bind(navController)
                }

                val dashboardViewModel: com.nexus.feature.dashboard.DashboardViewModel = hiltViewModel()
                val context = LocalContext.current
                val filePicker = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri: Uri? ->
                    if (uri != null) {
                        try {
                            context.contentResolver.takePersistableUriPermission(
                                uri,
                                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                            )
                        } catch (e: Exception) {}
                        dashboardViewModel.onDocumentOpened(uri, mimeType = null)
                        router.openDocument(uri = uri, mimeType = null)
                    }
                }

                // Handle incoming document intent
                LaunchedEffect(intent) {
                    handleIncomingIntent(intent)
                }

                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                val dashboardUiState by dashboardViewModel.uiState.collectAsStateWithLifecycle()
                val isSelectionMode = dashboardUiState.isSelectionMode
                var isChangelogVisible by remember { mutableStateOf(false) }

                val isStorageGranted = remember(currentRoute) { dashboardViewModel.hasStoragePermission(this@MainActivity) }
                val isPermissionScreenShowing = !isStorageGranted && !dashboardUiState.permissionRationaleShown

                // Scroll collapse state
                var isNavCollapsed by remember { mutableStateOf(false) }

                // Reset collapsed state whenever navigating to a different destination
                LaunchedEffect(currentRoute) {
                    isNavCollapsed = false
                }

                val nestedScrollConnection = rememberFloatingTabBarScrollConnection(
                    scrollThreshold = 36.dp,
                    onCollapseChanged = { isNavCollapsed = it }
                )

                // Bottom nav is shown on Dashboard and Settings screens when not in selection mode, changelog, or permission request screen
                val showBottomNav = (currentRoute == NexusRoute.Dashboard.route || currentRoute == NexusRoute.Settings.route)
                        && !isSelectionMode
                        && !isChangelogVisible
                        && !isPermissionScreenShowing

                val navItems = listOf(
                    NexusNavItem(
                        label = "Home",
                        selectedIconRes = R.drawable.ic_home_filled,
                        unselectedIconRes = R.drawable.ic_home_outline,
                        route = NexusRoute.Dashboard.route
                    ),
                    NexusNavItem(
                        label = "Settings",
                        selectedIconRes = R.drawable.ic_settings_filled,
                        unselectedIconRes = R.drawable.ic_settings_outline,
                        route = NexusRoute.Settings.route
                    )
                )

                val backdrop = rememberLayerBackdrop()

                CompositionLocalProvider(
                    LocalGlassEffectConfig provides readerNavGlassConfig,
                    LocalAppBackdrop provides backdrop,
                ) {
                    // ── Root Box: content fills full size, nav pill is a Z-ordered overlay ──
                    // The Scaffold bottomBar approach added innerPadding that pushed content
                    // up, causing the surface background to bleed behind the transparent pill.
                    // Instead, we use a plain Box where the nav sits at Alignment.BottomCenter
                    // with zIndex(1f) so it always renders above the content layer without
                    // influencing the layout size of the content beneath it.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(nestedScrollConnection)
                    ) {

                        // ── Layer 0: main content ────────────────────────────────────────────
                        // No bottomBar innerPadding here. Each screen's LazyColumn already
                        // carries its own contentPadding(bottom = 120.dp) to clear the pill.
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .layerBackdrop(backdrop)
                        ) {
                            NexusNavHost(
                                navController = navController,
                                router = router,
                                startDestination = startDestination,
                                onFirstLaunchComplete = {
                                    kotlinx.coroutines.MainScope().launch {
                                        preferencesRepository.completeFirstLaunch()
                                    }
                                },
                                onChangelogVisibilityChanged = { isChangelogVisible = it },
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // ── Layer 1: floating pill nav — drawn ON TOP via zIndex ─────────────
                        androidx.compose.animation.AnimatedVisibility(
                            visible = showBottomNav,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                            .zIndex(1f),
                        enter = androidx.compose.animation.slideInVertically(
                            initialOffsetY = { it },
                            animationSpec  = navPillSpring()
                        ) + androidx.compose.animation.fadeIn(
                            animationSpec = androidx.compose.animation.core.tween(
                                durationMillis = DurationScreenEnter,
                                easing         = EmphasizedDecelerateEasing
                            )
                        ),
                        exit = androidx.compose.animation.slideOutVertically(
                            targetOffsetY = { it },
                            animationSpec = androidx.compose.animation.core.tween(
                                durationMillis = DurationQuickRelease,
                                easing         = EmphasizedAccelerateEasing
                            )
                        ) + androidx.compose.animation.fadeOut(
                            animationSpec = androidx.compose.animation.core.tween(
                                durationMillis = DurationFadeIn,
                                easing         = EmphasizedAccelerateEasing
                            )
                        )
                    ) {
                        NexusFloatingBottomNav(
                            items = navItems,
                            currentRoute = currentRoute,
                            homeStyle = homeStyle,
                            isCollapsed = isNavCollapsed,
                            onScrollToTop = { dashboardViewModel.scrollToTop() },
                            onItemSelected = { item ->
                                // Same-tab re-selection on Dashboard = scroll to top (#10/#14)
                                if (item.route == currentRoute && item.route == NexusRoute.Dashboard.route) {
                                    dashboardViewModel.scrollToTop()
                                    return@NexusFloatingBottomNav
                                }
                                navController.navigate(item.route) {
                                    popUpTo(NexusRoute.Dashboard.route) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

    // Handle new intents when the app is already running (singleTask)
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            val uri = intent.data ?: return
            val mimeType = intent.type
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                contentResolver.takePersistableUriPermission(uri, takeFlags)
            } catch (_: Exception) {
                // Safe to ignore if the calling app did not grant persistable permission
            }
            router.openDocument(uri = uri, mimeType = mimeType)
        } else if (intent?.action == Intent.ACTION_SEND) {
            @Suppress("DEPRECATION")
            val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) ?: return
            val mimeType = intent.type
            router.openDocument(uri = uri, mimeType = mimeType)
        }
    }
}
