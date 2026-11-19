package com.nexus.core.ui.liquidglass.internal

import android.graphics.BlurMaskFilter
import androidx.compose.ui.graphics.AndroidPaint
import androidx.compose.ui.graphics.Paint
import com.nexus.core.ui.liquidglass.RuntimeShader
import com.nexus.core.ui.liquidglass.asAndroidRuntimeShader

fun Paint.asFrameworkPaint(): android.graphics.Paint {
    return (this as AndroidPaint).asFrameworkPaint()
}

internal fun Paint.blur(radius: Float) {
    this.asFrameworkPaint().maskFilter =
        if (radius > 0f) BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL)
        else null
}

internal fun Paint.setRuntimeShader(runtimeShader: RuntimeShader?) {
    try {
        this.asFrameworkPaint().shader = runtimeShader?.asAndroidRuntimeShader()
    } catch (_: Exception) {
    }
}
