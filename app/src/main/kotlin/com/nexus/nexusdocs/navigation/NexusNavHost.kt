@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
package com.nexus.nexusdocs.navigation

// ─── Android/AndroidX Imports ────────────────────────────────────────
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable

// ─── Compose Animation Imports ──────────────────────────────────────
import androidx.compose.animation.*
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween

// ─── Nexus Core Imports ─────────────────────────────────────────────
import com.nexus.core.navigation.*
import com.nexus.core.ui.animations.EmphasizedAccelerateEasing
import com.nexus.core.ui.animations.EmphasizedDecelerateEasing

// ─── Feature Imports ───────────────────────────────────────────────
import com.nexus.feature.dashboard.DashboardScreen
import com.nexus.feature.dashboard.DashboardViewModel
import com.nexus.feature.dashboard.SettingsScreen
import com.nexus.feature.reader.office.OfficeReaderScreen
import com.nexus.feature.reader.pdf.PdfReaderScreen
import com.nexus.feature.reader.text.TextReaderScreen
import com.nexus.feature.scanner.ui.ScannerScreen
import com.nexus.nexusdocs.ui.splash.NexusSplashScreen
import com.nexus.nexusdocs.ui.UnsupportedFileScreen
import com.nexus.nexusdocs.ui.welcome.WelcomeScreen
import com.nexus.nexusdocs.ui.welcome.PermissionScreen

// ─── Utilities ─────────────────────────────────────────────────────
import android.util.Log
import androidx.hilt.navigation.compose.hiltViewModel
import java.net.URLDecoder

// ════════════════════════════════════════════════════════════════════
// SECTION 1: CONSTANTS
// ════════════════════════════════════════════════════════════════════

object NavConstants {
    // ─── Animation Durations (milliseconds) ─────────────────────────
    const val DURATION_SPLASH_ENTER = 400
    const val DURATION_SPLASH_EXIT = 300
    
    const val DURATION_DASHBOARD_ENTER = 400
    const val DURATION_DASHBOARD_EXIT = 300
    const val DURATION_DASHBOARD_BACK = 300
    
    const val DURATION_SETTINGS_ENTER = 350
    const val DURATION_SETTINGS_EXIT = 250
    const val DURATION_SETTINGS_BACK = 300
    
    const val DURATION_SCANNER_ENTER = 400
    const val DURATION_SCANNER_EXIT = 300
    const val DURATION_SCANNER_BACK = 300
    
    const val DURATION_READER_ENTER = 400
    const val DURATION_READER_EXIT = 350
    const val DURATION_READER_BACK = 350
    
    const val DURATION_UNSUPPORTED_ENTER = 400
    const val DURATION_UNSUPPORTED_EXIT = 300
    const val DURATION_UNSUPPORTED_BACK = 350
    
    // ─── Default Values ─────────────────────────────────────────────
    const val DEFAULT_FILE_NAME = "Document"
    const val DEFAULT_DOC_TYPE = "DOCX"
    
    // ─── Offset Values (dp) ─────────────────────────────────────────
    const val OFFSET_SLIDE_HORIZONTAL_READER = 500
    const val OFFSET_SLIDE_VERTICAL_SPLASH = 100
    const val OFFSET_SLIDE_SETTINGS = 300
    const val OFFSET_SLIDE_DASHBOARD = 200
    
    // ─── Scale Values ───────────────────────────────────────────────
    const val SCALE_SCANNER_ENTER = 0.8f
    const val SCALE_SCANNER_EXIT = 0.8f
    const val SCALE_SPLASH_EXIT = 0.95f
    const val SCALE_NORMAL = 1.0f
}

// ════════════════════════════════════════════════════════════════════
// SECTION 2: ANIMATION SPECIFICATIONS
// ════════════════════════════════════════════════════════════════════

sealed class NavTransitionSpec(
    val enterDuration: Int,
    val exitDuration: Int,
    val enterEasing: Easing,
    val exitEasing: Easing
) {
    /** Splash screen: app launch ceremony animation */
    object SplashScreen : NavTransitionSpec(
        enterDuration = NavConstants.DURATION_SPLASH_ENTER,
        exitDuration = NavConstants.DURATION_SPLASH_EXIT,
        enterEasing = EmphasizedDecelerateEasing,
        exitEasing = EmphasizedAccelerateEasing
    )
    
    /** Dashboard: hub screen, welcoming entrance */
    object DashboardScreen : NavTransitionSpec(
        enterDuration = NavConstants.DURATION_DASHBOARD_ENTER,
        exitDuration = NavConstants.DURATION_DASHBOARD_EXIT,
        enterEasing = EmphasizedDecelerateEasing,
        exitEasing = EmphasizedAccelerateEasing
    )
    
    /** Settings: secondary screen, horizontal slide */
    object SettingsScreen : NavTransitionSpec(
        enterDuration = NavConstants.DURATION_SETTINGS_ENTER,
        exitDuration = NavConstants.DURATION_SETTINGS_EXIT,
        enterEasing = EmphasizedDecelerateEasing,
        exitEasing = EmphasizedAccelerateEasing
    )
    
    /** Scanner: action screen, pop animation */
    object ScannerScreen : NavTransitionSpec(
        enterDuration = NavConstants.DURATION_SCANNER_ENTER,
        exitDuration = NavConstants.DURATION_SCANNER_EXIT,
        enterEasing = EmphasizedDecelerateEasing,
        exitEasing = EmphasizedAccelerateEasing
    )
    
    /** Reader screens: content viewers, horizontal slide */
    object ReaderScreen : NavTransitionSpec(
        enterDuration = NavConstants.DURATION_READER_ENTER,
        exitDuration = NavConstants.DURATION_READER_EXIT,
        enterEasing = EmphasizedDecelerateEasing,
        exitEasing = EmphasizedAccelerateEasing
    )
    
    /** Unsupported file: error state, shake animation */
    object UnsupportedFileScreen : NavTransitionSpec(
        enterDuration = NavConstants.DURATION_UNSUPPORTED_ENTER,
        exitDuration = NavConstants.DURATION_UNSUPPORTED_EXIT,
        enterEasing = EmphasizedDecelerateEasing,
        exitEasing = EmphasizedAccelerateEasing
    )
}

// ════════════════════════════════════════════════════════════════════
// SECTION 3: ANIMATION BUILDERS
// ════════════════════════════════════════════════════════════════════

// ─── SPLASH SCREEN ANIMATIONS ────────────────────────────────────────
fun splashEnterTransition(): EnterTransition = fadeIn(
    animationSpec = tween(NavConstants.DURATION_SPLASH_ENTER, easing = EmphasizedDecelerateEasing)
) + slideInVertically(
    initialOffsetY = { 100 },
    animationSpec = tween(NavConstants.DURATION_SPLASH_ENTER, easing = EmphasizedDecelerateEasing)
) + scaleIn(
    initialScale = 0.95f,
    animationSpec = tween(NavConstants.DURATION_SPLASH_ENTER, easing = EmphasizedDecelerateEasing)
)

fun splashExitTransition(): ExitTransition = fadeOut(
    animationSpec = tween(NavConstants.DURATION_SPLASH_EXIT, easing = EmphasizedAccelerateEasing)
) + slideOutVertically(
    targetOffsetY = { -100 },
    animationSpec = tween(NavConstants.DURATION_SPLASH_EXIT, easing = EmphasizedAccelerateEasing)
) + scaleOut(
    targetScale = 0.95f,
    animationSpec = tween(NavConstants.DURATION_SPLASH_EXIT, easing = EmphasizedAccelerateEasing)
)

// ─── DASHBOARD SCREEN ANIMATIONS ─────────────────────────────────────
fun dashboardEnterTransition(): EnterTransition = fadeIn(
    animationSpec = tween(NavConstants.DURATION_DASHBOARD_ENTER, easing = EmphasizedDecelerateEasing)
) + slideInVertically(
    initialOffsetY = { 100 },
    animationSpec = tween(NavConstants.DURATION_DASHBOARD_ENTER, easing = EmphasizedDecelerateEasing)
)

fun dashboardExitTransition(): ExitTransition = fadeOut(
    animationSpec = tween(NavConstants.DURATION_DASHBOARD_EXIT, easing = EmphasizedAccelerateEasing)
) + slideOutHorizontally(
    targetOffsetX = { 200 },
    animationSpec = tween(NavConstants.DURATION_DASHBOARD_EXIT, easing = EmphasizedAccelerateEasing)
)

fun dashboardBackEnterTransition(): EnterTransition = fadeIn(
    animationSpec = tween(NavConstants.DURATION_DASHBOARD_BACK, easing = EmphasizedDecelerateEasing)
) + slideInHorizontally(
    initialOffsetX = { -200 },
    animationSpec = tween(NavConstants.DURATION_DASHBOARD_BACK, easing = EmphasizedDecelerateEasing)
)

fun dashboardBackExitTransition(): ExitTransition = fadeOut(
    animationSpec = tween(NavConstants.DURATION_DASHBOARD_BACK, easing = EmphasizedAccelerateEasing)
) + slideOutHorizontally(
    targetOffsetX = { 200 },
    animationSpec = tween(NavConstants.DURATION_DASHBOARD_BACK, easing = EmphasizedAccelerateEasing)
)

// ─── SETTINGS SCREEN ANIMATIONS ──────────────────────────────────────
fun settingsEnterTransition(): EnterTransition = fadeIn(
    animationSpec = tween(NavConstants.DURATION_SETTINGS_ENTER, easing = EmphasizedDecelerateEasing)
) + slideInHorizontally(
    initialOffsetX = { 300 },
    animationSpec = tween(NavConstants.DURATION_SETTINGS_ENTER, easing = EmphasizedDecelerateEasing)
)

fun settingsExitTransition(): ExitTransition = fadeOut(
    animationSpec = tween(NavConstants.DURATION_SETTINGS_EXIT, easing = EmphasizedAccelerateEasing)
) + slideOutHorizontally(
    targetOffsetX = { 300 },
    animationSpec = tween(NavConstants.DURATION_SETTINGS_EXIT, easing = EmphasizedAccelerateEasing)
)

fun settingsBackEnterTransition(): EnterTransition = fadeIn(
    animationSpec = tween(NavConstants.DURATION_SETTINGS_BACK, easing = EmphasizedDecelerateEasing)
) + slideInHorizontally(
    initialOffsetX = { -300 },
    animationSpec = tween(NavConstants.DURATION_SETTINGS_BACK, easing = EmphasizedDecelerateEasing)
)

fun settingsBackExitTransition(): ExitTransition = fadeOut(
    animationSpec = tween(NavConstants.DURATION_SETTINGS_EXIT, easing = EmphasizedAccelerateEasing)
) + slideOutHorizontally(
    targetOffsetX = { 300 },
    animationSpec = tween(NavConstants.DURATION_SETTINGS_EXIT, easing = EmphasizedAccelerateEasing)
)

// ─── SCANNER SCREEN ANIMATIONS ───────────────────────────────────────
fun scannerEnterTransition(): EnterTransition = fadeIn(
    animationSpec = tween(NavConstants.DURATION_SCANNER_ENTER, easing = EmphasizedDecelerateEasing)
) + scaleIn(
    initialScale = 0.8f,
    animationSpec = tween(NavConstants.DURATION_SCANNER_ENTER, easing = EmphasizedDecelerateEasing)
)

fun scannerExitTransition(): ExitTransition = fadeOut(
    animationSpec = tween(NavConstants.DURATION_SCANNER_EXIT, easing = EmphasizedAccelerateEasing)
) + scaleOut(
    targetScale = 0.8f,
    animationSpec = tween(NavConstants.DURATION_SCANNER_EXIT, easing = EmphasizedAccelerateEasing)
)

fun scannerBackEnterTransition(): EnterTransition = fadeIn(
    animationSpec = tween(NavConstants.DURATION_SCANNER_BACK, easing = EmphasizedDecelerateEasing)
) + scaleIn(
    initialScale = 0.95f,
    animationSpec = tween(NavConstants.DURATION_SCANNER_BACK, easing = EmphasizedDecelerateEasing)
)

fun scannerBackExitTransition(): ExitTransition = fadeOut(
    animationSpec = tween(NavConstants.DURATION_SCANNER_EXIT, easing = EmphasizedAccelerateEasing)
) + scaleOut(
    targetScale = 0.8f,
    animationSpec = tween(NavConstants.DURATION_SCANNER_EXIT, easing = EmphasizedAccelerateEasing)
)

// ─── READER SCREEN ANIMATIONS ────────────────────────────────────────
fun readerEnterTransition(): EnterTransition = fadeIn(
    animationSpec = tween(NavConstants.DURATION_READER_ENTER, easing = EmphasizedDecelerateEasing)
) + slideInHorizontally(
    initialOffsetX = { 500 },
    animationSpec = tween(NavConstants.DURATION_READER_ENTER, easing = EmphasizedDecelerateEasing)
)

fun readerExitTransition(): ExitTransition = fadeOut(
    animationSpec = tween(NavConstants.DURATION_READER_EXIT, easing = EmphasizedAccelerateEasing)
) + slideOutHorizontally(
    targetOffsetX = { 500 },
    animationSpec = tween(NavConstants.DURATION_READER_EXIT, easing = EmphasizedAccelerateEasing)
)

fun readerBackEnterTransition(): EnterTransition = fadeIn(
    animationSpec = tween(NavConstants.DURATION_READER_BACK, easing = EmphasizedDecelerateEasing)
) + slideInHorizontally(
    initialOffsetX = { -500 },
    animationSpec = tween(NavConstants.DURATION_READER_BACK, easing = EmphasizedDecelerateEasing)
)

fun readerBackExitTransition(): ExitTransition = fadeOut(
    animationSpec = tween(NavConstants.DURATION_READER_EXIT, easing = EmphasizedAccelerateEasing)
) + slideOutHorizontally(
    targetOffsetX = { 500 },
    animationSpec = tween(NavConstants.DURATION_READER_EXIT, easing = EmphasizedAccelerateEasing)
)

// ─── UNSUPPORTED FILE SCREEN ANIMATIONS ──────────────────────────────
fun unsupportedEnterTransition(): EnterTransition = fadeIn(
    animationSpec = tween(NavConstants.DURATION_UNSUPPORTED_ENTER, easing = EmphasizedDecelerateEasing)
) + slideInVertically(
    initialOffsetY = { -50 },
    animationSpec = tween(200, easing = EmphasizedAccelerateEasing)
)

fun unsupportedExitTransition(): ExitTransition = fadeOut(
    animationSpec = tween(NavConstants.DURATION_UNSUPPORTED_EXIT, easing = EmphasizedAccelerateEasing)
) + slideOutVertically(
    targetOffsetY = { 100 },
    animationSpec = tween(NavConstants.DURATION_UNSUPPORTED_EXIT, easing = EmphasizedAccelerateEasing)
)

fun unsupportedBackEnterTransition(): EnterTransition = fadeIn(
    animationSpec = tween(NavConstants.DURATION_UNSUPPORTED_BACK, easing = EmphasizedDecelerateEasing)
) + slideInVertically(
    initialOffsetY = { 100 },
    animationSpec = tween(NavConstants.DURATION_UNSUPPORTED_BACK, easing = EmphasizedDecelerateEasing)
)

fun unsupportedBackExitTransition(): ExitTransition = fadeOut(
    animationSpec = tween(NavConstants.DURATION_UNSUPPORTED_EXIT, easing = EmphasizedAccelerateEasing)
) + slideOutVertically(
    targetOffsetY = { 100 },
    animationSpec = tween(NavConstants.DURATION_UNSUPPORTED_EXIT, easing = EmphasizedAccelerateEasing)
)

// ════════════════════════════════════════════════════════════════════
// SECTION 4: ARGUMENT EXTENSION HELPERS
// ════════════════════════════════════════════════════════════════════

object NavArgumentExtensions {
    fun NavBackStackEntry.getStringArgument(
        key: String,
        default: String = ""
    ): String = arguments?.getString(key)?.takeIf { it.isNotBlank() } ?: default
    
    fun NavBackStackEntry.getDecodedUri(key: String): String {
        val encoded = arguments?.getString(key) ?: return ""
        return try {
            URLDecoder.decode(encoded, "UTF-8")
        } catch (e: Exception) {
            Log.e("NavArguments", "Failed to decode URI: ${e.message}")
            ""
        }
    }
    
    fun NavBackStackEntry.validateDocumentType(
        key: String,
        validTypes: List<String> = listOf("DOCX", "XLSX", "PPTX", "PDF", "TXT")
    ): String {
        val type = arguments?.getString(key) ?: return NavConstants.DEFAULT_DOC_TYPE
        return if (type in validTypes) type else NavConstants.DEFAULT_DOC_TYPE
    }
    
    fun NavBackStackEntry.getFileNameArgument(
        key: String,
        fallback: String = NavConstants.DEFAULT_FILE_NAME
    ): String {
        return try {
            val encoded = arguments?.getString(key) ?: return fallback
            URLDecoder.decode(encoded, "UTF-8")
        } catch (e: Exception) {
            Log.w("NavArguments", "Failed to decode file name, using fallback")
            fallback
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// SECTION 5: COMPOSABLE WRAPPERS & HELPERS
// ════════════════════════════════════════════════════════════════════

/**
 * Wraps screen content with proper AnimatedVisibilityScope.
 * Eliminates repetitive CompositionLocalProvider boilerplate.
 */
@Composable
private inline fun AnimatedVisibilityScope.AnimatedScreen(
    crossinline content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalAnimatedVisibilityScope provides this@AnimatedScreen
    ) {
        content()
    }
}

@Composable
private fun AnimatedVisibilityScope.SplashRoute(navController: NavHostController) {
    AnimatedScreen {
        NexusSplashScreen(
            onSplashComplete = {
                navController.navigate(NexusRoute.Dashboard.route) {
                    popUpTo(NexusRoute.Splash.route) { inclusive = true }
                }
            }
        )
    }
}

@Composable
private fun AnimatedVisibilityScope.WelcomeRoute(navController: NavHostController) {
    AnimatedScreen {
        WelcomeScreen(
            onFinish = {
                navController.navigate(NexusRoute.Permission.route) {
                    popUpTo(NexusRoute.Welcome.route) { inclusive = true }
                }
            }
        )
    }
}

@Composable
private fun AnimatedVisibilityScope.PermissionRoute(navController: NavHostController, onFirstLaunchComplete: () -> Unit) {
    AnimatedScreen {
        PermissionScreen(
            onPermissionHandled = {
                onFirstLaunchComplete()
                navController.navigate(NexusRoute.Splash.route) {
                    popUpTo(NexusRoute.Permission.route) { inclusive = true }
                }
            }
        )
    }
}

private tailrec fun android.content.Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun AnimatedVisibilityScope.DashboardRoute(router: DocumentReaderRouter) {
    AnimatedScreen {
        val activity = LocalContext.current.findActivity()
        if (activity == null) {
            Log.e("NexusNavHost", "Activity not available for Dashboard")
        } else {
            val viewModel: DashboardViewModel = hiltViewModel(activity)
            DashboardScreen(router = router, viewModel = viewModel)
        }
    }
}

@Composable
private fun AnimatedVisibilityScope.SettingsRoute(
    router: DocumentReaderRouter,
    onChangelogVisibilityChanged: (Boolean) -> Unit = {}
) {
    AnimatedScreen {
        SettingsScreen(
            onBack = { router.navigateBack() },
            onChangelogVisibilityChanged = onChangelogVisibilityChanged
        )
    }
}

@Composable
private fun AnimatedVisibilityScope.ScannerRoute(router: DocumentReaderRouter) {
    AnimatedScreen {
        ScannerScreen(onBack = { router.navigateBack() })
    }
}

@Composable
private fun AnimatedVisibilityScope.PdfReaderRoute(
    backStackEntry: NavBackStackEntry,
    router: DocumentReaderRouter
) {
    AnimatedScreen {
        val encodedUri = with(NavArgumentExtensions) { backStackEntry.getDecodedUri(NexusRoute.PdfReader.ARG_URI) }
        val fileName = with(NavArgumentExtensions) { backStackEntry.getFileNameArgument(NexusRoute.PdfReader.ARG_FILE_NAME) }
        
        if (encodedUri.isBlank()) {
            Log.w("PdfReader", "No URI provided, showing unsupported screen")
            UnsupportedFileScreen(fileName = fileName, onBack = { router.navigateBack() })
        } else {
            PdfReaderScreen(
                encodedUri = encodedUri,
                fileName = fileName,
                onBack = { router.navigateBack() }
            )
        }
    }
}

@Composable
private fun AnimatedVisibilityScope.OfficeReaderRoute(
    backStackEntry: NavBackStackEntry,
    router: DocumentReaderRouter
) {
    AnimatedScreen {
        val encodedUri = with(NavArgumentExtensions) { backStackEntry.getDecodedUri(NexusRoute.OfficeReader.ARG_URI) }
        val fileName = with(NavArgumentExtensions) { backStackEntry.getFileNameArgument(NexusRoute.OfficeReader.ARG_FILE_NAME) }
        val docType = with(NavArgumentExtensions) { backStackEntry.validateDocumentType(NexusRoute.OfficeReader.ARG_DOC_TYPE) }
        
        if (encodedUri.isBlank()) {
            Log.w("OfficeReader", "No URI provided, showing unsupported screen")
            UnsupportedFileScreen(fileName = fileName, onBack = { router.navigateBack() })
        } else {
            OfficeReaderScreen(
                encodedUri = encodedUri,
                fileName = fileName,
                docType = docType,
                onBack = { router.navigateBack() }
            )
        }
    }
}

@Composable
private fun AnimatedVisibilityScope.TextReaderRoute(
    backStackEntry: NavBackStackEntry,
    router: DocumentReaderRouter
) {
    AnimatedScreen {
        val encodedUri = with(NavArgumentExtensions) { backStackEntry.getDecodedUri(NexusRoute.TextReader.ARG_URI) }
        val fileName = with(NavArgumentExtensions) { backStackEntry.getFileNameArgument(NexusRoute.TextReader.ARG_FILE_NAME) }
        
        if (encodedUri.isBlank()) {
            Log.w("TextReader", "No URI provided, showing unsupported screen")
            UnsupportedFileScreen(fileName = fileName, onBack = { router.navigateBack() })
        } else {
            TextReaderScreen(
                encodedUri = encodedUri,
                fileName = fileName,
                onBack = { router.navigateBack() }
            )
        }
    }
}

@Composable
private fun AnimatedVisibilityScope.UnsupportedFileRoute(
    backStackEntry: NavBackStackEntry,
    router: DocumentReaderRouter
) {
    AnimatedScreen {
        val fileName = with(NavArgumentExtensions) { backStackEntry.getFileNameArgument("fileName", NavConstants.DEFAULT_FILE_NAME) }
        UnsupportedFileScreen(fileName = fileName, onBack = { router.navigateBack() })
    }
}

// ════════════════════════════════════════════════════════════════════
// SECTION 6: MAIN NAVIGATION HOST
// ════════════════════════════════════════════════════════════════════

/**
 * Central navigation host wiring all application destinations.
 * 
 * Provides coordinated screen transitions with Material Design 3 motion guidelines:
 * - Entrance transitions: Smooth, welcoming animations (EmphasizedDecelerate easing)
 * - Exit transitions: Quick, responsive animations (EmphasizedAccelerate easing)
 * - Back navigation: Faster, predictable reverse animations
 * 
 * Each route has unique animation profile reflecting its purpose:
 * - Dashboard: Hub screen with upward slide (welcoming)
 * - Settings/Scanner: Secondary screens with horizontal slide (distinct)
 * - Readers: Content screens with side slide (purposeful)
 * - Unsupported: Error state with shake (warning)
 * 
 * @param navController Controls navigation between routes
 * @param router Handles document-specific navigation logic
 * @param modifier Compose modifier for layout customization
 * 
 * @see NavTransitionSpec for animation configuration
 * @see NavArgumentExtensions for safe argument retrieval
 */
@Composable
fun NexusNavHost(
    navController: NavHostController,
    router: DocumentReaderRouter,
    startDestination: String,
    onFirstLaunchComplete: () -> Unit = {},
    onChangelogVisibilityChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    SharedTransitionLayout {
        CompositionLocalProvider(
            LocalSharedTransitionScope provides this
        ) {
            NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = modifier
            ) {
                // ─── Welcome Screen ──────────────────────────────────────
                composable(
                    route = NexusRoute.Welcome.route,
                    enterTransition = { splashEnterTransition() },
                    exitTransition = { splashExitTransition() }
                ) {
                    WelcomeRoute(navController)
                }

                // ─── Permission Screen ───────────────────────────────────
                composable(
                    route = NexusRoute.Permission.route,
                    enterTransition = { splashEnterTransition() },
                    exitTransition = { splashExitTransition() }
                ) {
                    PermissionRoute(navController, onFirstLaunchComplete)
                }

                // ─── Splash Screen ───────────────────────────────────────
                composable(
                    route = NexusRoute.Splash.route,
                    enterTransition = { splashEnterTransition() },
                    exitTransition = { splashExitTransition() }
                ) {
                    SplashRoute(navController)
                }

                // ─── Dashboard Screen ────────────────────────────────────
                composable(
                    route = NexusRoute.Dashboard.route,
                    enterTransition = { dashboardEnterTransition() },
                    exitTransition = { dashboardExitTransition() },
                    popEnterTransition = { dashboardBackEnterTransition() },
                    popExitTransition = { dashboardBackExitTransition() }
                ) {
                    DashboardRoute(router)
                }

                // ─── Settings Screen ─────────────────────────────────────
                composable(
                    route = NexusRoute.Settings.route,
                    enterTransition = { settingsEnterTransition() },
                    exitTransition = { settingsExitTransition() },
                    popEnterTransition = { settingsBackEnterTransition() },
                    popExitTransition = { settingsBackExitTransition() }
                ) {
                    SettingsRoute(router, onChangelogVisibilityChanged)
                }

                // ─── Scanner Screen ──────────────────────────────────────
                composable(
                    route = NexusRoute.Scanner.route,
                    enterTransition = { scannerEnterTransition() },
                    exitTransition = { scannerExitTransition() },
                    popEnterTransition = { scannerBackEnterTransition() },
                    popExitTransition = { scannerBackExitTransition() }
                ) {
                    ScannerRoute(router)
                }

                // ─── PDF Reader Screen ────────────────────────────────────
                composable(
                    route = NexusRoute.PdfReader.ROUTE,
                    enterTransition = { readerEnterTransition() },
                    exitTransition = { readerExitTransition() },
                    popEnterTransition = { readerBackEnterTransition() },
                    popExitTransition = { readerBackExitTransition() }
                ) { backStackEntry ->
                    PdfReaderRoute(backStackEntry, router)
                }

                // ─── Office Reader Screen ─────────────────────────────────
                composable(
                    route = NexusRoute.OfficeReader.ROUTE,
                    enterTransition = { readerEnterTransition() },
                    exitTransition = { readerExitTransition() },
                    popEnterTransition = { readerBackEnterTransition() },
                    popExitTransition = { readerBackExitTransition() }
                ) { backStackEntry ->
                    OfficeReaderRoute(backStackEntry, router)
                }

                // ─── Text Reader Screen ───────────────────────────────────
                composable(
                    route = NexusRoute.TextReader.ROUTE,
                    enterTransition = { readerEnterTransition() },
                    exitTransition = { readerExitTransition() },
                    popEnterTransition = { readerBackEnterTransition() },
                    popExitTransition = { readerBackExitTransition() }
                ) { backStackEntry ->
                    TextReaderRoute(backStackEntry, router)
                }

                // ─── Unsupported File Screen ──────────────────────────────
                composable(
                    route = NexusRoute.Unsupported.ROUTE,
                    enterTransition = { unsupportedEnterTransition() },
                    exitTransition = { unsupportedExitTransition() },
                    popEnterTransition = { unsupportedBackEnterTransition() },
                    popExitTransition = { unsupportedBackExitTransition() }
                ) { backStackEntry ->
                    UnsupportedFileRoute(backStackEntry, router)
                }
            }
        }
    }
}
