package com.nexus.feature.dashboard.data

import androidx.room.Entity
import androidx.room.Index

// ─── Document Tag Entity ───────────────────────────────────────────────────────
// Represents a custom label/tag attached to a document URI.
// Multiple tags can be attached to one URI, and one tag can label multiple documents.

@Entity(
    tableName = "document_tags",
    primaryKeys = ["documentUri", "tag"],
    indices = [
        Index(value = ["tag"]),
        Index(value = ["documentUri"])
    ]
)
data class DocumentTag(
    val documentUri: String,
    val tag: String,
    val createdAt: Long = System.currentTimeMillis()
)
