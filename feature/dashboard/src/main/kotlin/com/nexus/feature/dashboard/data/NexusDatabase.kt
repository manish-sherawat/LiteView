package com.nexus.feature.dashboard.data

import androidx.room.Database
import androidx.room.RoomDatabase

// ─── Nexus Room Database ──────────────────────────────────────────────────────
// Single database for the app. Version bump required whenever schema changes.

@Database(
    entities = [
        RecentDocument::class,
        DocumentBookmark::class,
        DocumentTag::class,
        TagDefinition::class
    ],
    version = 6,
    exportSchema = false
)
abstract class NexusDatabase : RoomDatabase() {
    abstract fun recentDocumentDao(): RecentDocumentDao
    abstract fun documentBookmarkDao(): DocumentBookmarkDao
    abstract fun documentTagDao(): DocumentTagDao
    abstract fun tagDefinitionDao(): TagDefinitionDao
}
