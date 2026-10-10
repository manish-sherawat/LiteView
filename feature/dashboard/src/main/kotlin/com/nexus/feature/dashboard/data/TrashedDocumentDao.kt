package com.nexus.feature.dashboard.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

// ─── Trashed Document DAO ─────────────────────────────────────────────────────

@Dao
interface TrashedDocumentDao {

    /** Observe all trashed files ordered by deletion time, newest first. */
    @Query("SELECT * FROM trashed_documents ORDER BY trashedAt DESC")
    fun observeAllTrashed(): Flow<List<TrashedDocument>>

    /** Observe the total count of trashed documents. */
    @Query("SELECT COUNT(*) FROM trashed_documents")
    fun observeTrashedCount(): Flow<Int>

    /** Observe the total file size in bytes of all trashed documents. */
    @Query("SELECT SUM(fileSizeBytes) FROM trashed_documents")
    fun observeTotalTrashedSize(): Flow<Long?>

    /** Synchronous snapshot of all trashed documents. */
    @Query("SELECT * FROM trashed_documents ORDER BY trashedAt DESC")
    suspend fun getAllTrashed(): List<TrashedDocument>

    /** Look up a single trashed document by ID. */
    @Query("SELECT * FROM trashed_documents WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): TrashedDocument?

    /** Look up a trashed document by its original URI. */
    @Query("SELECT * FROM trashed_documents WHERE originalUri = :originalUri LIMIT 1")
    suspend fun getByOriginalUri(originalUri: String): TrashedDocument?

    /** Insert or replace a single trashed document record. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrashed(doc: TrashedDocument)

    /** Insert or replace multiple trashed document records. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllTrashed(docs: List<TrashedDocument>)

    /** Delete a specific trashed document. */
    @Delete
    suspend fun deleteTrashed(doc: TrashedDocument)

    /** Delete by ID. */
    @Query("DELETE FROM trashed_documents WHERE id = :id")
    suspend fun deleteById(id: String)

    /** Delete by original URI. */
    @Query("DELETE FROM trashed_documents WHERE originalUri = :originalUri")
    suspend fun deleteByOriginalUri(originalUri: String)

    /** Delete all trashed document records. */
    @Query("DELETE FROM trashed_documents")
    suspend fun deleteAllTrashed()

    /** Find all trashed documents older than cutoff timestamp. */
    @Query("SELECT * FROM trashed_documents WHERE trashedAt < :cutoffTimestamp")
    suspend fun getExpired(cutoffTimestamp: Long): List<TrashedDocument>

    /** Delete all expired trashed records older than cutoff timestamp. */
    @Query("DELETE FROM trashed_documents WHERE trashedAt < :cutoffTimestamp")
    suspend fun deleteExpired(cutoffTimestamp: Long): Int
}
