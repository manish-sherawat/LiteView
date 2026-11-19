package com.nexus.core.ui.liquidglass

import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp

@ChecksSdkIntAtLeast(Build.VERSION_CODES.S)
fun isRenderEffectSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@ChecksSdkIntAtLeast(Build.VERSION_CODES.TIRAMISU)
fun isRuntimeShaderSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

fun Capsule(): CornerBasedShape = CircleShape

fun RoundedRectangle(radius: Dp): CornerBasedShape = RoundedCornerShape(radius)
