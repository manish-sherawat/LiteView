package com.nexus.feature.dashboard

import androidx.compose.ui.graphics.Color

/**
 * UI representation of a custom tag, encapsulating its name, styling, emoji, and document count.
 */
data class TagUiModel(
    val name: String,
    val colorHex: String = "#3B82F6",
    val emoji: String? = null,
    val count: Int = 0
) {
    val color: Color
        get() = try {
            Color(android.graphics.Color.parseColor(colorHex))
        } catch (e: Exception) {
            Color(0xFF3B82F6)
        }

    val displayName: String
        get() = if (!emoji.isNullOrBlank()) "$emoji $name" else name

    val chipLabel: String
        get() = if (!emoji.isNullOrBlank()) "$emoji #$name" else "#$name"
}
