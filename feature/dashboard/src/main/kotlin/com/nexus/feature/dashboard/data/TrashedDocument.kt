package com.nexus.feature.dashboard.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// ─── Trashed Document Entity ──────────────────────────────────────────────────
// Stored in Room when a document is moved to Recycle Bin (soft-deleted).
// Stores original metadata and optional quarantine path inside context.filesDir/.trash/

@Entity(
    tableName = "trashed_documents",
    indices = [
        Index(value = ["trashedAt"]),
        Index(value = ["originalUri"])
    ]
)
data class TrashedDocument(
    @PrimaryKey
    val id: String,
    val originalUri: String,
    val originalPath: String,
    val fileName: String,
    val fileSizeBytes: Long,
    val mimeType: String?,
    val documentType: String,
    val trashedAt: Long = System.currentTimeMillis(),
    val quarantinePath: String? = null,
    val attachedTagsJson: String? = null,
    val isQuarantined: Boolean = false,
    val lastScrollIndex: Int = 0,
    val lastScrollOffset: Int = 0
)
