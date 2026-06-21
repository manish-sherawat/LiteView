package com.nexus.nexusdocs.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import com.nexus.core.navigation.DocumentReaderRouter
import com.nexus.core.navigation.NexusRoute
import com.nexus.core.ui.components.NexusTopBar
import com.nexus.feature.dashboard.DashboardScreen
import com.nexus.feature.dashboard.SettingsScreen
import com.nexus.feature.reader.office.OfficeReaderScreen
import com.nexus.feature.reader.pdf.PdfReaderScreen
import com.nexus.feature.reader.text.TextReaderScreen
import com.nexus.nexusdocs.ui.splash.NexusSplashScreen
import com.nexus.nexusdocs.ui.UnsupportedFileScreen

import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.runtime.CompositionLocalProvider
import com.nexus.core.navigation.LocalSharedTransitionScope
import com.nexus.core.navigation.LocalAnimatedVisibilityScope

import com.nexus.core.ui.animations.DurationMedium2
import com.nexus.core.ui.animations.DurationMedium3
import com.nexus.core.ui.animations.DurationShort4
import com.nexus.core.ui.animations.EmphasizedDecelerateEasing
import com.nexus.core.ui.animations.EmphasizedAccelerateEasing

// ─── Navigation Host ──────────────────────────────────────────────────────────
// Central NavHost wiring all destinations together.
// The :app module is the only place that imports feature modules —
// all other modules only depend on :core.

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun NexusNavHost(
    navController: NavHostController,
    router: DocumentReaderRouter,
    modifier: Modifier = Modifier
) {
    SharedTransitionLayout {
        CompositionLocalProvider(
            LocalSharedTransitionScope provides this
        ) {
            NavHost(
                navController = navController,
                startDestination = NexusRoute.Splash.route,
                modifier = modifier,
                // ── Forward push: new screen slides in from right ──────────────────
                // M3 Emphasized Decelerate: fast travel → gentle landing
                enterTransition = {
                    slideInHorizontally(
                        initialOffsetX = { fullWidth -> (fullWidth * 0.15f).toInt() },
                        animationSpec  = tween(DurationMedium3, easing = EmphasizedDecelerateEasing)
                    ) + fadeIn(
                        animationSpec = tween(DurationMedium2, easing = EmphasizedDecelerateEasing)
                    )
                },
                // ── Forward push: old screen slides out to left ────────────────────
                // M3 Emphasized Accelerate: gentle start → fast exit
                exitTransition = {
                    slideOutHorizontally(
                        targetOffsetX  = { fullWidth -> -(fullWidth * 0.15f).toInt() },
                        animationSpec  = tween(DurationMedium2, easing = EmphasizedAccelerateEasing)
                    ) + fadeOut(
                        animationSpec = tween(DurationShort4, easing = EmphasizedAccelerateEasing)
                    )
                },
                // ── Back pop: previous screen slides back in from left ─────────────
                popEnterTransition = {
                    slideInHorizontally(
                        initialOffsetX = { fullWidth -> -(fullWidth * 0.15f).toInt() },
                        animationSpec  = tween(DurationMedium3, easing = EmphasizedDecelerateEasing)
                    ) + fadeIn(
                        animationSpec = tween(DurationMedium2, easing = EmphasizedDecelerateEasing)
                    )
                },
                // ── Back pop: current screen slides out to right ───────────────────
                popExitTransition = {
                    slideOutHorizontally(
                        targetOffsetX  = { fullWidth -> (fullWidth * 0.15f).toInt() },
                        animationSpec  = tween(DurationMedium2, easing = EmphasizedAccelerateEasing)
                    ) + fadeOut(
                        animationSpec = tween(DurationShort4, easing = EmphasizedAccelerateEasing)
                    )
                }
            ) {
                // ── Splash ────────────────────────────────────────────────────────────
                composable(route = NexusRoute.Splash.route) {
                    CompositionLocalProvider(LocalAnimatedVisibilityScope provides this@composable) {
                        NexusSplashScreen(
                            onSplashComplete = {
                                navController.navigate(NexusRoute.Dashboard.route) {
                                    popUpTo(NexusRoute.Splash.route) { inclusive = true }
                                }
                            }
                        )
                    }
                }

                // ── Dashboard ─────────────────────────────────────────────────────────
                composable(route = NexusRoute.Dashboard.route) {
                    CompositionLocalProvider(LocalAnimatedVisibilityScope provides this@composable) {
                        DashboardScreen(router = router)
                    }
                }

                // ── Settings ──────────────────────────────────────────────────────────
                composable(route = NexusRoute.Settings.route) {
                    CompositionLocalProvider(LocalAnimatedVisibilityScope provides this@composable) {
                        SettingsScreen(onBack = { router.navigateBack() })
                    }
                }

                // ── Scanner ───────────────────────────────────────────────────────────
                composable(route = NexusRoute.Scanner.route) {
                    CompositionLocalProvider(LocalAnimatedVisibilityScope provides this@composable) {
                        com.nexus.feature.scanner.ui.ScannerScreen(onBack = { router.navigateBack() })
                    }
                }

                // ── PDF Reader ────────────────────────────────────────────────────────
                composable(route = NexusRoute.PdfReader.ROUTE) { backStackEntry ->
                    val encodedUri = backStackEntry.arguments?.getString(NexusRoute.PdfReader.ARG_URI) ?: ""
                    val fileName = backStackEntry.arguments?.getString(NexusRoute.PdfReader.ARG_FILE_NAME) ?: "Document"
                    CompositionLocalProvider(LocalAnimatedVisibilityScope provides this@composable) {
                        PdfReaderScreen(
                            encodedUri = encodedUri,
                            fileName = fileName,
                            onBack = { router.navigateBack() }
                        )
                    }
                }

                // ── Office Reader (DOCX / XLSX) ──────────────────────────────────────
                composable(route = NexusRoute.OfficeReader.ROUTE) { backStackEntry ->
                    val encodedUri = backStackEntry.arguments?.getString(NexusRoute.OfficeReader.ARG_URI) ?: ""
                    val fileName = backStackEntry.arguments?.getString(NexusRoute.OfficeReader.ARG_FILE_NAME) ?: "Document"
                    val docType = backStackEntry.arguments?.getString(NexusRoute.OfficeReader.ARG_DOC_TYPE) ?: "DOCX"
                    CompositionLocalProvider(LocalAnimatedVisibilityScope provides this@composable) {
                        OfficeReaderScreen(
                            encodedUri = encodedUri,
                            fileName = fileName,
                            docType = docType,
                            onBack = { router.navigateBack() }
                        )
                    }
                }

                // ── Text Reader ───────────────────────────────────────────────────────
                composable(route = NexusRoute.TextReader.ROUTE) { backStackEntry ->
                    val encodedUri = backStackEntry.arguments?.getString(NexusRoute.TextReader.ARG_URI) ?: ""
                    val fileName = backStackEntry.arguments?.getString(NexusRoute.TextReader.ARG_FILE_NAME) ?: "Document.txt"
                    CompositionLocalProvider(LocalAnimatedVisibilityScope provides this@composable) {
                        TextReaderScreen(
                            encodedUri = encodedUri,
                            fileName = fileName,
                            onBack = { router.navigateBack() }
                        )
                    }
                }

                // ── Unsupported File ──────────────────────────────────────────────────
                composable(route = NexusRoute.Unsupported.ROUTE) { backStackEntry ->
                    val fileName = backStackEntry.arguments?.getString("fileName")
                        ?.let { java.net.URLDecoder.decode(java.net.URLDecoder.decode(it, "UTF-8"), "UTF-8") } ?: "Unknown file"
                    CompositionLocalProvider(LocalAnimatedVisibilityScope provides this@composable) {
                        UnsupportedFileScreen(fileName = fileName, onBack = { router.navigateBack() })
                    }
                }
            }
        }
    }
}

