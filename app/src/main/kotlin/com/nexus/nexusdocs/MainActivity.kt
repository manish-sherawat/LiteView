package com.nexus.nexusdocs

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.zIndex
import androidx.compose.ui.Modifier
import com.nexus.core.ui.animations.DurationMedium1
import com.nexus.core.ui.animations.DurationMedium3
import com.nexus.core.ui.animations.DurationShort3
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

import com.nexus.core.ui.components.NexusFloatingBottomNav
import com.nexus.core.ui.components.NexusFloatingBottomNav
import com.nexus.core.ui.components.NexusNavItem
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
                .collectAsState(initial = ThemeMode.SYSTEM)
            
            val homeStyle by preferencesRepository.homeStyle
                .collectAsState(initial = HomeStyle.APPLE_GLASSMORPHIC)

            val hapticFeedbackEnabled by preferencesRepository.hapticFeedbackEnabled
                .collectAsState(initial = true)

            val isDark = when (themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            com.nexus.core.theme.NexusDocsViewerTheme(
                darkTheme = isDark,
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

                // Bottom nav is only shown on the home screen (Dashboard or Settings)
                val showBottomNav = currentRoute == NexusRoute.Dashboard.route || currentRoute == NexusRoute.Settings.route



                val navItems = listOf(
                    NexusNavItem(
                        label = "Home",
                        selectedIconRes = R.drawable.ic_home,
                        unselectedIconRes = R.drawable.ic_home,
                        route = NexusRoute.Dashboard.route
                    ),
                    NexusNavItem(
                        label = "Add",
                        selectedIconRes = R.drawable.ic_scan_fab,
                        unselectedIconRes = R.drawable.ic_scan_fab,
                        route = "action_add_file"
                    ),
                    NexusNavItem(
                        label = "Settings",
                        selectedIconRes = R.drawable.ic_settings,
                        unselectedIconRes = R.drawable.ic_settings,
                        route = NexusRoute.Settings.route
                    )
                )

                // ── Root Box: content fills full size, nav pill is a Z-ordered overlay ──
                // The Scaffold bottomBar approach added innerPadding that pushed content
                // up, causing the surface background to bleed behind the transparent pill.
                // Instead, we use a plain Box where the nav sits at Alignment.BottomCenter
                // with zIndex(1f) so it always renders above the content layer without
                // influencing the layout size of the content beneath it.
                Box(modifier = Modifier.fillMaxSize()) {

                    // ── Layer 0: main content ────────────────────────────────────────────
                    // No bottomBar innerPadding here. Each screen's LazyColumn already
                    // carries its own contentPadding(bottom = 120.dp) to clear the pill.
                    Box(modifier = Modifier.fillMaxSize()) {
                        NexusNavHost(
                            navController = navController,
                            router = router,
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
                                durationMillis = DurationMedium3,
                                easing         = EmphasizedDecelerateEasing
                            )
                        ),
                        exit = androidx.compose.animation.slideOutVertically(
                            targetOffsetY = { it },
                            animationSpec = androidx.compose.animation.core.tween(
                                durationMillis = DurationMedium1,
                                easing         = EmphasizedAccelerateEasing
                            )
                        ) + androidx.compose.animation.fadeOut(
                            animationSpec = androidx.compose.animation.core.tween(
                                durationMillis = DurationShort3,
                                easing         = EmphasizedAccelerateEasing
                            )
                        )
                    ) {
                        NexusFloatingBottomNav(
                            items = navItems,
                            currentRoute = currentRoute,
                            homeStyle = homeStyle,
                            onItemSelected = { item ->
                                if (item.route == "action_add_file") {
                                    filePicker.launch(arrayOf("*/*"))
                                } else {
                                    navController.navigate(item.route) {
                                        popUpTo(NexusRoute.Dashboard.route) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                        )
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
