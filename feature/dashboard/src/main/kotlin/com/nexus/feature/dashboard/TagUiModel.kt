package com.nexus.feature.dashboard

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * UI representation of a custom tag, encapsulating its name, styling, emoji, and document count.
 */
@Immutable
data class TagUiModel(
    val name: String,
    val colorHex: String = "#3B82F6",
    val emoji: String? = null,
    val count: Int = 0
) {
    val color: Color
        get() = try {
            if (colorHex.isNotBlank() && colorHex != "#3B82F6") {
                Color(android.graphics.Color.parseColor(colorHex))
            } else {
                deterministicTagColor(name)
            }
        } catch (e: Exception) {
            deterministicTagColor(name)
        }

    val displayName: String
        get() = if (!emoji.isNullOrBlank()) "$emoji $name" else name

    val chipLabel: String
        get() = if (!emoji.isNullOrBlank()) "$emoji #$name" else "#$name"
}

/**
 * Deterministically assigns a modern, scannable palette color based on tag name hash.
 */
fun deterministicTagColor(name: String): Color {
    val tagPalettes = listOf(
        Color(0xFF3B82F6), // Ocean Blue
        Color(0xFF10B981), // Emerald
        Color(0xFFF59E0B), // Warm Amber
        Color(0xFF8B5CF6), // Royal Violet
        Color(0xFFEC4899), // Rose Pink
        Color(0xFF06B6D4), // Cyan
        Color(0xFFF97316), // Sunset Orange
        Color(0xFF6366F1), // Indigo
        Color(0xFF14B8A6), // Deep Teal
        Color(0xFFE11D48)  // Crimson
    )
    val rawHash = name.hashCode().toLong() and 0x7FFFFFFFL
    val index = (rawHash % tagPalettes.size).toInt()
    return tagPalettes[index]
}
