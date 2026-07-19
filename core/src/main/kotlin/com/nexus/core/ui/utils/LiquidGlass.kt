package com.nexus.core.ui.utils

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize

// ─────────────────────────────────────────────────────────────────────────────
// Liquid Glass — native AGSL RuntimeShader implementation
//
// Inspired by the FletchMcKee/liquid library's aesthetics.
// Implemented directly via Android's RuntimeShader + RenderEffect APIs so no
// external dependency (and no Kotlin version conflict) is needed.
//
// API tiers:
//   API 33+ (Android 13) → BlurEffect + AGSL shader → full liquid glass effect
//   API 31-32 (Android 12) → BlurEffect only → frosted blur (no chromatic aberration)
//   API < 31               → no-op (caller uses glassBackground() tint as base)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * AGSL (Android Graphics Shading Language) code for the Liquid Glass effect.
 *
 * What it does:
 *  - Chromatic aberration: splits R/G/B channels by a small UV offset, creating
 *    the colour-fringing you see through real glass or water.
 *  - Cool glass tint: mixes output toward an ice-blue/frosted-white.
 *  - Specular highlight: smooth gradient at the top edge simulates ambient light
 *    catching the curved rim of the glass pill.
 *  - Depth shadow: subtle darkening at the bottom adds perceived thickness.
 */
private const val LIQUID_GLASS_AGSL = """
    uniform shader content;
    uniform float2 size;

    half4 main(float2 fragCoord) {
        float2 uv = fragCoord / size;

        // Chromatic aberration — red channel shifts right, blue shifts left
        float dispersion = 0.0035;
        half r = content.eval(float2((uv.x + dispersion) * size.x, uv.y * size.y)).r;
        half g = content.eval(fragCoord).g;
        half b = content.eval(float2((uv.x - dispersion) * size.x, uv.y * size.y)).b;

        half4 color = half4(r, g, b, 1.0);

        // Frosted glass tint: cool ice-blue / white blend
        color.rgb = mix(color.rgb, half3(0.82, 0.90, 1.0), 0.10);

        // Specular highlight at top edge (like light hitting curved glass rim)
        float highlight = smoothstep(0.45, 0.0, uv.y) * 0.22;
        color.rgb += highlight;

        // Depth shadow at bottom
        float shadow = smoothstep(0.55, 1.0, uv.y) * 0.06;
        color.rgb -= shadow;

        color.rgb = clamp(color.rgb, half3(0.0), half3(1.0));
        return color;
    }
"""

/**
 * Applies a Liquid Glass visual effect to any composable.
 *
 * On **API 33+**: chains a [RenderEffect.createBlurEffect] (24px) with an AGSL
 * [RuntimeShader] that adds chromatic aberration, specular highlights, and a
 * cool glass tint — producing a true liquid-glass aesthetic.
 *
 * On **API 31–32**: applies blur only (no AGSL support).
 *
 * On **API < 31**: returns the [Modifier] unchanged; the caller should provide
 * a fallback via [glassBackground].
 *
 * Usage in navbar:
 * ```kotlin
 * Box(
 *     modifier = Modifier
 *         .clip(pillShape)
 *         .glassBackground(...)   // base tint visible on all APIs
 *         .liquidGlass()          // shader overlaid on API 33+
 * )
 * ```
 */
fun Modifier.liquidGlass(): Modifier = composed {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        return@composed this  // API < 31: no RenderEffect
    }

    var size by remember { mutableStateOf(IntSize.Zero) }

    this
        .onSizeChanged { size = it }
        .graphicsLayer {
            if (size == IntSize.Zero) return@graphicsLayer

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // API 33+: Blur + AGSL glass shader
                val runtimeShader = RuntimeShader(LIQUID_GLASS_AGSL)
                runtimeShader.setFloatUniform(
                    "size",
                    size.width.toFloat(),
                    size.height.toFloat()
                )
                val blurEffect = RenderEffect.createBlurEffect(
                    22f, 22f, Shader.TileMode.CLAMP
                )
                val shaderEffect = RenderEffect.createRuntimeShaderEffect(
                    runtimeShader, "content"
                )
                // Chain: element content → blur → glass AGSL shader
                renderEffect = RenderEffect.createChainEffect(shaderEffect, blurEffect)
                    .asComposeRenderEffect()
            } else {
                // API 31–32: Blur only
                renderEffect = RenderEffect.createBlurEffect(
                    20f, 20f, Shader.TileMode.CLAMP
                ).asComposeRenderEffect()
            }
        }
}
