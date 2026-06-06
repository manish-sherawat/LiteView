package com.nexus.feature.dashboard.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentBookmarkDao {
    @Query("SELECT * FROM document_bookmarks WHERE documentUri = :uri ORDER BY pageIndex ASC")
    fun getBookmarksForDocument(uri: String): Flow<List<DocumentBookmark>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: DocumentBookmark)

    @Delete
    suspend fun deleteBookmark(bookmark: DocumentBookmark)
    
    @Query("DELETE FROM document_bookmarks WHERE documentUri = :uri AND pageIndex = :pageIndex")
    suspend fun deleteBookmarkByPage(uri: String, pageIndex: Int)
}
