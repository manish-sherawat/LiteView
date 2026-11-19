package com.nexus.core.ui.liquidglass.backdrops

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.nexus.core.ui.liquidglass.Backdrop
import com.nexus.core.ui.liquidglass.rememberBackdrop as rememberRootBackdrop

@Composable
fun rememberBackdrop(
    backdrop: Backdrop,
    onDraw: DrawScope.(drawBackdrop: DrawScope.() -> Unit) -> Unit
): Backdrop = rememberRootBackdrop(backdrop, onDraw)
