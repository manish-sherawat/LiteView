package com.nexus.core.ui.liquidglass.effects

import android.os.Build
import androidx.compose.ui.graphics.RenderEffect
import com.nexus.core.ui.liquidglass.BackdropEffectScope
import com.nexus.core.ui.liquidglass.RuntimeShader
import com.nexus.core.ui.liquidglass.internal.RuntimeShaderEffect
import com.nexus.core.ui.liquidglass.internal.chain
import com.nexus.core.ui.liquidglass.isRenderEffectSupported
import com.nexus.core.ui.liquidglass.isRuntimeShaderSupported
import org.intellij.lang.annotations.Language

fun BackdropEffectScope.effect(effect: RenderEffect) {
    if (!isRenderEffectSupported()) return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        renderEffect = renderEffect.chain(effect)
    }
}

fun BackdropEffectScope.runtimeShaderEffect(
    key: String,
    @Language("AGSL") shaderString: String,
    uniformShaderName: String,
    block: RuntimeShader.() -> Unit
) {
    if (!isRuntimeShaderSupported()) return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val effect =
            RuntimeShaderEffect(
                runtimeShader = obtainRuntimeShader(key, shaderString).apply(block),
                uniformShaderName = uniformShaderName
            )
        renderEffect = renderEffect.chain(effect)
    }
}
