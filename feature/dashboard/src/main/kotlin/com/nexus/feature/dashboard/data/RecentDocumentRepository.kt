package com.nexus.feature.dashboard.data

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

// ─── Recent Document Repository ───────────────────────────────────────────────
// Single source of truth for recent file history and tag definitions.

@Singleton
class RecentDocumentRepository @Inject constructor(
    private val dao: RecentDocumentDao,
    private val tagDao: DocumentTagDao,
    private val tagDefDao: TagDefinitionDao
) {
    /** Reactive stream of all recent documents, newest first. */
    fun observeRecentDocuments(): Flow<List<RecentDocument>> = dao.observeAll()

    /** Record (or refresh) a document that was just opened. */
    suspend fun recordOpen(doc: RecentDocument) = dao.upsert(doc)

    /** Remove one item from the recents list. */
    suspend fun removeDocument(uri: String) = dao.deleteByUri(uri)

    /** Clear the entire history. */
    suspend fun clearAll() = dao.deleteAll()

    // ── Document Tags ──────────────────────────────────────────────────────────

    /** Reactive stream of all document tag assignments. */
    fun observeAllTags(): Flow<List<DocumentTag>> = tagDao.observeAllTags()

    /** Reactive stream of tags for a single document. */
    fun observeTagsForDocument(uri: String): Flow<List<DocumentTag>> = tagDao.getTagsForDocument(uri)

    /** Add a tag to a document. */
    suspend fun addTag(uri: String, tag: String) {
        val trimmed = tag.trim().removePrefix("#").trim()
        if (trimmed.isNotEmpty()) {
            tagDao.insertTag(DocumentTag(documentUri = uri, tag = trimmed))
        }
    }

    /** Remove a tag from a document. */
    suspend fun removeTag(uri: String, tag: String) {
        val trimmed = tag.trim().removePrefix("#").trim()
        tagDao.deleteTag(uri, trimmed)
    }

    /** Set or replace all tags for a document. */
    suspend fun setDocumentTags(uri: String, tags: List<String>) {
        tagDao.deleteAllTagsForDocument(uri)
        val validTags = tags.map { it.trim().removePrefix("#").trim() }.filter { it.isNotEmpty() }.distinct()
        if (validTags.isNotEmpty()) {
            tagDao.insertTags(validTags.map { DocumentTag(documentUri = uri, tag = it) })
        }
    }

    // ── Tag Definitions (Styling, Color, Emoji, Global Rename/Delete) ─────────

    /** Reactive stream of all tag definitions. */
    fun observeAllTagDefinitions(): Flow<List<TagDefinition>> = tagDefDao.observeAllTagDefinitions()

    /** Retrieve single tag definition. */
    suspend fun getTagDefinition(name: String): TagDefinition? = tagDefDao.getTagDefinition(name.trim().removePrefix("#").trim())

    /** Save or update tag definition. */
    suspend fun upsertTagDefinition(name: String, colorHex: String, emoji: String?) {
        val cleanName = name.trim().removePrefix("#").trim()
        if (cleanName.isNotEmpty()) {
            tagDefDao.insertOrReplaceTagDefinition(
                TagDefinition(
                    name = cleanName,
                    colorHex = colorHex,
                    emoji = emoji
                )
            )
        }
    }

    /** Atomically rename a tag and update its styling across all documents. */
    suspend fun renameTagGlobally(oldName: String, newName: String, colorHex: String, emoji: String?) {
        tagDefDao.renameTagGlobally(oldName, newName, colorHex, emoji)
    }

    /** Atomically delete a tag definition and its assignment from all documents. */
    suspend fun deleteTagGlobally(name: String) {
        val cleanName = name.trim().removePrefix("#").trim()
        tagDefDao.deleteTagGlobally(cleanName)
    }
}
