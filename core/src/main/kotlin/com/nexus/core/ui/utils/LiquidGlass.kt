package com.nexus.core.ui.utils

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import dev.chrisbanes.haze.HazeState

// ─────────────────────────────────────────────────────────────────────────────
// Liquid Glass Configuration & Composition Locals
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Configuration options for the native AGSL Liquid Glass effect.
 */
data class GlassEffectConfig(
    val globalEnabled: Boolean = true,
    val navBarEnabled: Boolean = true,
    val blurRadius: Float = 5f,                    // light blur, lets background detail show through
    val vibrancy: Float = 2f,                      // saturation boost (~2.2x, matches CSS saturate())
    val surfaceOpacity: Float = 0.03f,             // near-fully-transparent white fill
    val surfaceTintColor: Color = Color(0xFFFFFFFF), // force light-glass tint regardless of theme
    val lensHeight: Float = 0.5f,                  // 24dp edge refraction height
    val lensAmount: Float = 0.45f,                 // ~21dp refraction strength
    val chromaticAberration: Boolean = true,         // R/G/B channel split at the rim
    val depthEffect: Boolean = true,
)

/**
 * Standard preset for Reader Home navigation bar liquid glass.
 */
val readerNavGlassConfig = GlassEffectConfig(
    globalEnabled = true,
    navBarEnabled = true,
    blurRadius = 36f,                   // rich, silky frosted blur for true frosted glass
    vibrancy = 1.9f,                    // saturation enhancement (~1.9x)
    surfaceOpacity = 0.14f,             // visible translucent white frost sheen
    surfaceTintColor = Color(0xFFFFFFFF), // light-glass tint
    lensHeight = 0.55f,                 // curved edge refraction height
    lensAmount = 0.50f,                 // refraction strength
    chromaticAberration = true,         // subtle R/G/B channel dispersion at the rim
    depthEffect = true,
)

val LocalGlassEffectConfig = compositionLocalOf { readerNavGlassConfig }
val LocalAppBackdrop = compositionLocalOf<Any?> { null }
val LocalHazeState = compositionLocalOf<dev.chrisbanes.haze.HazeState?> { null }

// ─────────────────────────────────────────────────────────────────────────────
// AGSL RuntimeShader Definition
// ─────────────────────────────────────────────────────────────────────────────

private const val LIQUID_GLASS_AGSL = """
    uniform shader content;
    uniform float2 size;
    uniform float vibrancy;
    uniform float surfaceOpacity;
    uniform float4 surfaceTintColor;
    uniform float lensHeight;
    uniform float lensAmount;
    uniform float chromaticAberration;
    uniform float depthEffect;

    half4 main(float2 fragCoord) {
        float2 uv = fragCoord / size;

        // Chromatic aberration / dispersion at the rim
        float dispersion = chromaticAberration > 0.5 ? (0.0032 * lensAmount * 2.0) : 0.0;
        float r = content.eval(float2((uv.x + dispersion) * size.x, uv.y * size.y)).r;
        float g = content.eval(fragCoord).g;
        float b = content.eval(float2((uv.x - dispersion) * size.x, uv.y * size.y)).b;
        float a = content.eval(fragCoord).a;

        float4 color = float4(r, g, b, a);

        // Vibrancy / saturation boost
        if (vibrancy > 1.0) {
            float luma = dot(color.rgb, float3(0.2126, 0.7152, 0.0722));
            color.rgb = mix(float3(luma), color.rgb, vibrancy);
        }

        // Frosted glass tint & surface opacity blend
        color.rgb = mix(color.rgb, surfaceTintColor.rgb, surfaceOpacity);

        // Specular highlight at top edge (light catching curved glass rim)
        if (lensHeight > 0.0) {
            float highlight = smoothstep(lensHeight, 0.0, uv.y) * 0.28 * lensAmount;
            color.rgb += float3(highlight);
        }

        // Depth shadow at bottom rim
        if (depthEffect > 0.5) {
            float shadow = smoothstep(1.0 - lensHeight, 1.0, uv.y) * 0.08 * lensAmount;
            color.rgb -= float3(shadow);
        }

        color.rgb = clamp(color.rgb, float3(0.0), float3(1.0));
        return half4(color);
    }
"""

/**
 * Applies native AGSL Liquid Glass effect to the composable background.
 * Both the Home/Settings pill and standalone Search circle consume [LocalGlassEffectConfig].
 */
fun Modifier.liquidGlass(config: GlassEffectConfig? = null): Modifier = composed {
    val glassConfig = config ?: LocalGlassEffectConfig.current

    if (!glassConfig.globalEnabled || !glassConfig.navBarEnabled) {
        return@composed this
    }

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        return@composed this  // API < 31: no RenderEffect
    }

    val density = LocalDensity.current.density
    var size by remember { mutableStateOf(IntSize.Zero) }

    val runtimeShader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            RuntimeShader(LIQUID_GLASS_AGSL)
        } else null
    }

    this
        .onSizeChanged { size = it }
        .graphicsLayer {
            if (size == IntSize.Zero) return@graphicsLayer

            val blurPx = (glassConfig.blurRadius * density)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && runtimeShader != null) {
                // API 33+: Blur + AGSL glass shader
                runtimeShader.setFloatUniform(
                    "size",
                    size.width.toFloat(),
                    size.height.toFloat()
                )
                runtimeShader.setFloatUniform("vibrancy", glassConfig.vibrancy)
                runtimeShader.setFloatUniform("surfaceOpacity", glassConfig.surfaceOpacity)
                runtimeShader.setFloatUniform(
                    "surfaceTintColor",
                    glassConfig.surfaceTintColor.red,
                    glassConfig.surfaceTintColor.green,
                    glassConfig.surfaceTintColor.blue,
                    glassConfig.surfaceTintColor.alpha
                )
                runtimeShader.setFloatUniform("lensHeight", glassConfig.lensHeight)
                runtimeShader.setFloatUniform("lensAmount", glassConfig.lensAmount)
                runtimeShader.setFloatUniform("chromaticAberration", if (glassConfig.chromaticAberration) 1f else 0f)
                runtimeShader.setFloatUniform("depthEffect", if (glassConfig.depthEffect) 1f else 0f)

                val shaderEffect = RenderEffect.createRuntimeShaderEffect(
                    runtimeShader, "content"
                )
                
                if (blurPx > 0f) {
                    val blurEffect = RenderEffect.createBlurEffect(
                        blurPx.coerceAtLeast(1f), blurPx.coerceAtLeast(1f), Shader.TileMode.CLAMP
                    )
                    // Chain: element content -> blur -> glass AGSL shader
                    renderEffect = RenderEffect.createChainEffect(shaderEffect, blurEffect)
                        .asComposeRenderEffect()
                } else {
                    renderEffect = shaderEffect.asComposeRenderEffect()
                }
            } else {
                // API 31–32: Blur only
                if (blurPx > 0f) {
                    renderEffect = RenderEffect.createBlurEffect(
                        blurPx.coerceAtLeast(1f), blurPx.coerceAtLeast(1f), Shader.TileMode.CLAMP
                    ).asComposeRenderEffect()
                }
            }
        }
}
