package com.nexus.feature.dashboard.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDefinitionDao {

    @Query("SELECT * FROM tag_definitions ORDER BY name ASC")
    fun observeAllTagDefinitions(): Flow<List<TagDefinition>>

    @Query("SELECT * FROM tag_definitions WHERE name = :name LIMIT 1")
    suspend fun getTagDefinition(name: String): TagDefinition?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplaceTagDefinition(tag: TagDefinition)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfNotExist(tag: TagDefinition)

    @Query("DELETE FROM tag_definitions WHERE name = :name")
    suspend fun deleteTagDefinition(name: String)

    @Query("UPDATE document_tags SET tag = :newName WHERE tag = :oldName")
    suspend fun updateTagNameInDocumentTags(oldName: String, newName: String)

    @Query("DELETE FROM document_tags WHERE tag = :name")
    suspend fun deleteTagFromDocumentTags(name: String)

    @Transaction
    suspend fun renameTagGlobally(
        oldName: String,
        newName: String,
        colorHex: String,
        emoji: String?
    ) {
        val cleanNewName = newName.trim().removePrefix("#").trim()
        val cleanOldName = oldName.trim().removePrefix("#").trim()

        if (cleanOldName != cleanNewName) {
            // Delete old definition
            deleteTagDefinition(cleanOldName)
            // Insert updated definition
            insertOrReplaceTagDefinition(
                TagDefinition(
                    name = cleanNewName,
                    colorHex = colorHex,
                    emoji = emoji
                )
            )
            // Update all assigned document tags
            updateTagNameInDocumentTags(cleanOldName, cleanNewName)
        } else {
            // Only styling changed
            insertOrReplaceTagDefinition(
                TagDefinition(
                    name = cleanNewName,
                    colorHex = colorHex,
                    emoji = emoji
                )
            )
        }
    }

    @Transaction
    suspend fun deleteTagGlobally(name: String) {
        val cleanName = name.trim().removePrefix("#").trim()
        deleteTagDefinition(cleanName)
        deleteTagFromDocumentTags(cleanName)
    }
}
