package com.nexus.feature.dashboard.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

// ─── Document Tag DAO ─────────────────────────────────────────────────────────

@Dao
interface DocumentTagDao {

    /** Observe all tags for a specific document URI. */
    @Query("SELECT * FROM document_tags WHERE documentUri = :uri ORDER BY tag ASC")
    fun getTagsForDocument(uri: String): Flow<List<DocumentTag>>

    /** Observe all document tags in the database. */
    @Query("SELECT * FROM document_tags ORDER BY tag ASC")
    fun observeAllTags(): Flow<List<DocumentTag>>

    /** Observe distinct tag names across all documents. */
    @Query("SELECT DISTINCT tag FROM document_tags ORDER BY tag ASC")
    fun observeDistinctTags(): Flow<List<String>>

    /** Insert or replace a single document tag. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTag(documentTag: DocumentTag)

    /** Insert or replace a list of document tags. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTags(tags: List<DocumentTag>)

    /** Delete a single tag from a document. */
    @Query("DELETE FROM document_tags WHERE documentUri = :uri AND tag = :tag")
    suspend fun deleteTag(uri: String, tag: String)

    /** Remove all tags associated with a specific document URI. */
    @Query("DELETE FROM document_tags WHERE documentUri = :uri")
    suspend fun deleteAllTagsForDocument(uri: String)

    /** Delete a tag entirely across all documents. */
    @Query("DELETE FROM document_tags WHERE tag = :tag")
    suspend fun deleteTagGlobally(tag: String)

    /** Atomically replace all tags for a document. */
    @androidx.room.Transaction
    suspend fun replaceTags(uri: String, tags: List<DocumentTag>) {
        deleteAllTagsForDocument(uri)
        insertTags(tags)
    }
}
