package com.nexus.feature.dashboard.data

import androidx.room.Entity
import androidx.room.PrimaryKey

// ─── Recent Document Entity ───────────────────────────────────────────────────
// Stored in Room every time a user opens a document.
// `uri` is the unique key — re-opening the same file updates lastOpenedAt.

@Entity(tableName = "recent_documents")
data class RecentDocument(
    @PrimaryKey
    val uri: String,
    val fileName: String,
    val mimeType: String?,
    val fileSizeBytes: Long,
    val lastOpenedAt: Long = System.currentTimeMillis(),
    val documentType: String,   // Mirrors DocumentType.name
    val lastScrollIndex: Int = 0,
    val lastScrollOffset: Int = 0
)
