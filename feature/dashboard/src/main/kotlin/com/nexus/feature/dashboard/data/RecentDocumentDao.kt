package com.nexus.feature.dashboard.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

// ─── Recent Document DAO ──────────────────────────────────────────────────────

@Dao
interface RecentDocumentDao {

    /** Observe all recent files ordered by last opened, newest first. */
    @Query("SELECT * FROM recent_documents ORDER BY lastOpenedAt DESC")
    fun observeAll(): Flow<List<RecentDocument>>

    /**
     * Insert or replace a document.
     * Re-opening the same URI updates the lastOpenedAt timestamp
     * because [OnConflictStrategy.REPLACE] removes the old row first.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(doc: RecentDocument)

    /** Remove a single document from recents. */
    @Query("DELETE FROM recent_documents WHERE uri = :uri")
    suspend fun deleteByUri(uri: String)

    /** Wipe all recent history. */
    @Query("DELETE FROM recent_documents")
    suspend fun deleteAll()

    /** Look up a document by URI (used to check existence before inserting). */
    @Query("SELECT * FROM recent_documents WHERE uri = :uri LIMIT 1")
    suspend fun findByUri(uri: String): RecentDocument?

    /** Update the last scrolled position of a specific document. */
    @Query("UPDATE recent_documents SET lastScrollIndex = :index, lastScrollOffset = :offset WHERE uri = :uri")
    suspend fun updateScrollPosition(uri: String, index: Int, offset: Int)

    /** Wipe all scroll positions across all documents. */
    @Query("UPDATE recent_documents SET lastScrollIndex = 0, lastScrollOffset = 0")
    suspend fun clearAllScrollPositions()
}
