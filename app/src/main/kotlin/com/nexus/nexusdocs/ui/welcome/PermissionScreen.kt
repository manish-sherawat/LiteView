package com.nexus.nexusdocs.ui.welcome

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nexus.nexusdocs.R

@Composable
fun PermissionScreen(
    onPermissionHandled: () -> Unit
) {
    val context = LocalContext.current
    
    val legacyLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        onPermissionHandled()
    }
    
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        onPermissionHandled()
    }

    var isScreenVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isScreenVisible = true
    }

    val screenAlpha by animateFloatAsState(
        targetValue = if (isScreenVisible) 1f else 0.8f,
        animationSpec = WelcomeAnimationSpecs.screenEntrySpec,
        label = "perm_screen_alpha"
    )

    val screenScale by animateFloatAsState(
        targetValue = if (isScreenVisible) 1f else 0.95f,
        animationSpec = WelcomeAnimationSpecs.screenEntrySpec,
        label = "perm_screen_scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = screenAlpha
                scaleX = screenScale
                scaleY = screenScale
            }
    ) {
        Image(
            painter = painterResource(id = R.drawable.wlc_screen_3),
            contentDescription = "Storage permission screen background",
            modifier = Modifier
                .fillMaxSize()
                .semantics { contentDescription = "Storage permission required screen" },
            contentScale = ContentScale.FillBounds
        )

        // Floating Bottom Buttons
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(24.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            WelcomeButton(
                text = "Grant Permission",
                contentDescriptionText = "Grant storage permission",
                isPrimary = true,
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        try {
                            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                            intent.data = Uri.parse("package:${context.packageName}")
                            launcher.launch(intent)
                        } catch (e: Exception) {
                            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                            launcher.launch(intent)
                        }
                    } else {
                        legacyLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            )

            WelcomeButton(
                text = "Not Now",
                contentDescriptionText = "Skip storage permission for now",
                isPrimary = false,
                onClick = onPermissionHandled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            )
        }
    }
}
