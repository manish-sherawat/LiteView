package com.nexus.core.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

@Immutable
data class NexusShapes(
    val small: Shape,
    val medium: Shape,
    val large: Shape,
    val pill: Shape,
    val circle: Shape
)

val LocalNexusShapes = staticCompositionLocalOf {
    NexusShapes(
        small = RoundedCornerShape(0.dp),
        medium = RoundedCornerShape(0.dp),
        large = RoundedCornerShape(0.dp),
        pill = RoundedCornerShape(0.dp),
        circle = CircleShape
    )
}

val defaultNexusShapes = NexusShapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    pill = RoundedCornerShape(50),
    circle = CircleShape
)
