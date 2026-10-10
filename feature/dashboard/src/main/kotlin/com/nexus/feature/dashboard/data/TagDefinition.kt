package com.nexus.feature.dashboard.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing custom tag definitions with user-selected visual styling (color and emoji).
 */
@Entity(tableName = "tag_definitions")
data class TagDefinition(
    @PrimaryKey
    val name: String,
    val colorHex: String = "#3B82F6",
    val emoji: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
