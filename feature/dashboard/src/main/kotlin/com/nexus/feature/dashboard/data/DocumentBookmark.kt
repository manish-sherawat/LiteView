package com.nexus.feature.dashboard.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "document_bookmarks")
data class DocumentBookmark(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val documentUri: String,
    val pageIndex: Int,
    val label: String,
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
