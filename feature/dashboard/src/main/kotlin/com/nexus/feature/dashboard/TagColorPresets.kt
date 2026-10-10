package com.nexus.feature.dashboard

import androidx.compose.ui.graphics.Color

object TagColorPresets {

    data class ColorPreset(
        val hex: String,
        val label: String,
        val color: Color
    )

    val PRESET_COLORS = listOf(
        ColorPreset("#3B82F6", "Blue", Color(0xFF3B82F6)),
        ColorPreset("#EF4444", "Red", Color(0xFFEF4444)),
        ColorPreset("#10B981", "Emerald", Color(0xFF10B981)),
        ColorPreset("#F59E0B", "Amber", Color(0xFFF59E0B)),
        ColorPreset("#8B5CF6", "Violet", Color(0xFF8B5CF6)),
        ColorPreset("#06B6D4", "Cyan", Color(0xFF06B6D4)),
        ColorPreset("#EC4899", "Pink", Color(0xFFEC4899)),
        ColorPreset("#6366F1", "Indigo", Color(0xFF6366F1)),
        ColorPreset("#14B8A6", "Teal", Color(0xFF14B8A6)),
        ColorPreset("#F97316", "Orange", Color(0xFFF97316)),
        ColorPreset("#64748B", "Slate", Color(0xFF64748B))
    )

    val PRESET_EMOJIS = listOf(
        "🏷️", "💼", "📊", "🎓", "🔒", "⚡",
        "📁", "📝", "💡", "📌", "🚀", "💰",
        "🔑", "🌟", "🎯", "🎨", "📚", "🏥",
        "✈️", "🛒", "📂", "📋", "☕", "🔥"
    )

    fun getColorForTag(name: String): Color {
        val index = (name.hashCode() and 0x7FFFFFFF) % PRESET_COLORS.size
        return PRESET_COLORS[index].color
    }

    fun getColorHexForTag(name: String): String {
        val index = (name.hashCode() and 0x7FFFFFFF) % PRESET_COLORS.size
        return PRESET_COLORS[index].hex
    }
}
