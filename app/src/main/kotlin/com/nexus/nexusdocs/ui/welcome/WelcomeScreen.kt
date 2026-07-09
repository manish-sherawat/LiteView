package com.nexus.nexusdocs.ui.welcome

import android.util.Log
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.nexusdocs.R
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

object WelcomeAnimationSpecs {
    private val expoEasing = CubicBezierEasing(0.87f, 0f, 0.13f, 1f)
    private val expoOutEasing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

    val pageTransitionSpec = tween<Float>(
        durationMillis = 600,
        easing = expoEasing
    )
    val buttonPressSpec = tween<Float>(
        durationMillis = 200,
        easing = expoOutEasing
    )
    val fadeInSpec = tween<Float>(
        durationMillis = 400,
        easing = expoOutEasing
    )
    val dotTransitionSpec = tween<Float>(
        durationMillis = 400,
        easing = expoEasing
    )
    val screenEntrySpec = tween<Float>(
        durationMillis = 600,
        easing = expoOutEasing
    )
    const val staggerDelay = 150L
    const val fadeInDuration = 400
    const val pageTransitionDuration = 600
}

@Composable
fun WelcomeButton(
    text: String,
    contentDescriptionText: String,
    isPrimary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = WelcomeAnimationSpecs.buttonPressSpec,
        label = "button_scale_$text"
    )

    Button(
        onClick = onClick,
        modifier = modifier
            .scale(scale)
            .semantics { contentDescription = contentDescriptionText }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    isPressed = true
                    waitForUpOrCancellation()
                    isPressed = false
                }
            },
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isPrimary) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surface,
            contentColor = if (isPrimary) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface
        ),
        shape = RoundedCornerShape(percent = 50)
    ) {
        Text(text = text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun PageImage(
    imageRes: Int,
    contentDesc: String,
    pageOffset: Float
) {
    val absOffset = pageOffset.absoluteValue

    Image(
        painter = painterResource(id = imageRes),
        contentDescription = null,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                val fraction = absOffset.coerceIn(0f, 1f)
                scaleX = 1f - (fraction * 0.2f)
                scaleY = 1f - (fraction * 0.2f)
                alpha = 1f - (fraction * 0.7f)
                rotationZ = pageOffset * 5f
                translationX = pageOffset * size.width * 0.3f 
            }
            .semantics { contentDescription = contentDesc },
        contentScale = ContentScale.FillBounds
    )
}

@Composable
fun WelcomeScreen(
    onFinish: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { 2 })
    val coroutineScope = rememberCoroutineScope()

    var isScreenVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isScreenVisible = true
    }

    val screenAlpha by animateFloatAsState(
        targetValue = if (isScreenVisible) 1f else 0.8f,
        animationSpec = WelcomeAnimationSpecs.screenEntrySpec,
        label = "screen_alpha"
    )

    val screenScale by animateFloatAsState(
        targetValue = if (isScreenVisible) 1f else 0.95f,
        animationSpec = WelcomeAnimationSpecs.screenEntrySpec,
        label = "screen_scale"
    )
    
    val buttonAlpha by animateFloatAsState(
        targetValue = if (isScreenVisible) 1f else 0f,
        animationSpec = tween(
            durationMillis = WelcomeAnimationSpecs.fadeInDuration,
            delayMillis = WelcomeAnimationSpecs.staggerDelay.toInt()
        ),
        label = "bottom_button_alpha"
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
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
            
            PageImage(
                imageRes = if (page == 0) R.drawable.wlc_screen_1 else R.drawable.wlc_screen_2,
                contentDesc = "Welcome page ${page + 1}",
                pageOffset = pageOffset
            )
        }

        // Top-right Skip Button
        if (pagerState.currentPage == 0) {
            TextButton(
                onClick = onFinish,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 16.dp, end = 16.dp)
                    .statusBarsPadding()
                    .alpha(buttonAlpha)
            ) {
                Text(
                    text = "Skip",
                    color = Color.Black,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Bottom Next / Get Started Button
        WelcomeButton(
            text = if (pagerState.currentPage == 0) "Next" else "Get Started",
            contentDescriptionText = if (pagerState.currentPage == 0) "Next page" else "Get Started with Nexus",
            isPrimary = true,
            onClick = {
                if (pagerState.currentPage == 0) {
                    coroutineScope.launch {
                        try {
                            pagerState.animateScrollToPage(
                                page = 1,
                                animationSpec = WelcomeAnimationSpecs.pageTransitionSpec
                            )
                        } catch (e: Exception) {
                            Log.e("WelcomeScreen", "Navigation failed", e)
                            onFinish()
                        }
                    }
                } else {
                    onFinish()
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(24.dp)
                .navigationBarsPadding()
                .height(56.dp)
                .alpha(buttonAlpha)
        )
    }
}
