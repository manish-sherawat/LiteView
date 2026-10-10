package com.nexus.feature.dashboard.data

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import com.nexus.core.preferences.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

// ─── Trash Repository ─────────────────────────────────────────────────────────
// Manages the complete lifecycle of soft-deleted documents: moving to trash,
// physical quarantine, 1-tap restore with tags, permanent deletion, and auto-purge.

@Singleton
class TrashRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val trashedDao: TrashedDocumentDao,
    private val recentDocDao: RecentDocumentDao,
    private val tagDao: DocumentTagDao,
    private val prefsRepository: UserPreferencesRepository
) {

    private val trashDir: File
        get() = File(context.filesDir, ".trash").apply {
            if (!exists()) mkdirs()
        }

    /** Reactive stream of all trashed documents ordered newest to oldest. */
    fun observeAllTrashed(): Flow<List<TrashedDocument>> = trashedDao.observeAllTrashed()

    /** Reactive stream of the count of items in trash. */
    fun observeTrashedCount(): Flow<Int> = trashedDao.observeTrashedCount()

    /** Reactive stream of total size (bytes) occupied by trashed files. */
    fun observeTotalTrashedSize(): Flow<Long> = trashedDao.observeTotalTrashedSize().map { it ?: 0L }

    /** Move a single active document to the Recycle Bin. */
    suspend fun moveToTrash(doc: RecentDocument): TrashedDocument = withContext(Dispatchers.IO) {
        // 1. Capture and preserve attached tags
        val currentTags = tagDao.getTagsForDocument(doc.uri).first()
        val tagsJson = if (currentTags.isNotEmpty()) {
            val jsonArray = JSONArray()
            currentTags.forEach { jsonArray.put(it.tag) }
            jsonArray.toString()
        } else null

        // 2. Quarantine internal / external file
        var quarantinePath: String? = null
        var isQuarantined = false

        val rawUri = Uri.parse(doc.uri)
        if (doc.uri.startsWith("file://") || rawUri.scheme == "file" || doc.uri.startsWith("/")) {
            val filePath = rawUri.path ?: doc.uri.removePrefix("file://")
            val origFile = File(filePath)
            if (origFile.exists()) {
                val sanitizedName = doc.fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                val qFile = File(trashDir, "${UUID.randomUUID()}_$sanitizedName")
                var moved = origFile.renameTo(qFile)
                if (!moved) {
                    // renameTo fails across filesystem partitions, so perform copy + delete
                    try {
                        origFile.copyTo(qFile, overwrite = true)
                        if (origFile.delete()) {
                            moved = true
                        } else {
                            qFile.delete() // Rollback copy if original delete fails
                            android.util.Log.e("TrashRepo", "Failed to delete original file after copy")
                        }
                    } catch (e: Exception) {
                        qFile.delete() // Rollback copy on exception
                        android.util.Log.e("TrashRepo", "Failed to copy to quarantine", e)
                    }
                }
                if (moved && qFile.exists()) {
                    quarantinePath = qFile.absolutePath
                    isQuarantined = true
                    // Notify media scanner so original path is removed from MediaStore query results
                    try {
                        MediaScannerConnection.scanFile(
                            context,
                            arrayOf(origFile.absolutePath),
                            null,
                            null
                        )
                    } catch (_: Exception) {}
                }
            }
        } else if (rawUri.scheme == "content") {
            // For content URIs, try to copy bytes to quarantine
            try {
                val sanitizedName = doc.fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                val qFile = File(trashDir, "${UUID.randomUUID()}_$sanitizedName")
                context.contentResolver.openInputStream(rawUri)?.use { input ->
                    qFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                if (qFile.exists() && qFile.length() > 0) {
                    quarantinePath = qFile.absolutePath
                    isQuarantined = true
                    try {
                        context.contentResolver.delete(rawUri, null, null)
                    } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                android.util.Log.e("TrashRepo", "Failed to quarantine content URI", e)
            }
        }

        val trashed = TrashedDocument(
            id = UUID.randomUUID().toString(),
            originalUri = doc.uri,
            originalPath = doc.uri,
            fileName = doc.fileName,
            fileSizeBytes = if (quarantinePath != null) File(quarantinePath).length() else doc.fileSizeBytes,
            mimeType = doc.mimeType,
            documentType = doc.documentType,
            trashedAt = System.currentTimeMillis(),
            quarantinePath = quarantinePath,
            attachedTagsJson = tagsJson,
            isQuarantined = isQuarantined,
            lastScrollIndex = doc.lastScrollIndex,
            lastScrollOffset = doc.lastScrollOffset
        )

        // 3. Save trashed entity & remove from recents and active tags
        trashedDao.insertTrashed(trashed)
        recentDocDao.deleteByUri(doc.uri)
        tagDao.deleteAllTagsForDocument(doc.uri)

        trashed
    }

    /** Batch move multiple documents to the Recycle Bin. */
    suspend fun moveToTrash(docs: List<RecentDocument>): List<TrashedDocument> = withContext(Dispatchers.IO) {
        docs.map { moveToTrash(it) }
    }

    /** Restore a trashed document back to the dashboard with all metadata and tags. */
    suspend fun restoreDocument(trashed: TrashedDocument): RecentDocument = withContext(Dispatchers.IO) {
        // 1. Move quarantined file back to original location if applicable
        if (trashed.isQuarantined && !trashed.quarantinePath.isNullOrEmpty()) {
            val qFile = File(trashed.quarantinePath)
            if (qFile.exists()) {
                val rawUri = Uri.parse(trashed.originalUri)
                val targetPath = rawUri.path ?: trashed.originalUri.removePrefix("file://")
                val origFile = File(targetPath)
                origFile.parentFile?.let { if (!it.exists()) it.mkdirs() }
                var restoredPhysical = qFile.renameTo(origFile)
                if (!restoredPhysical) {
                    try {
                        qFile.copyTo(origFile, overwrite = true)
                        qFile.delete()
                        restoredPhysical = true
                    } catch (e: Exception) {
                        android.util.Log.e("TrashRepo", "Failed to restore physical file", e)
                    }
                }
                if (restoredPhysical && origFile.exists()) {
                    try {
                        MediaScannerConnection.scanFile(
                            context,
                            arrayOf(origFile.absolutePath),
                            null,
                            null
                        )
                    } catch (_: Exception) {}
                }
            }
        }

        // 2. Re-insert RecentDocument into recents
        val restoredDoc = RecentDocument(
            uri = trashed.originalUri,
            fileName = trashed.fileName,
            mimeType = trashed.mimeType,
            fileSizeBytes = trashed.fileSizeBytes,
            lastOpenedAt = System.currentTimeMillis(),
            documentType = trashed.documentType,
            lastScrollIndex = trashed.lastScrollIndex,
            lastScrollOffset = trashed.lastScrollOffset
        )
        recentDocDao.upsert(restoredDoc)

        // 3. Re-insert preserved tags
        if (!trashed.attachedTagsJson.isNullOrEmpty()) {
            try {
                val jsonArray = JSONArray(trashed.attachedTagsJson)
                val restoredTags = mutableListOf<DocumentTag>()
                for (i in 0 until jsonArray.length()) {
                    val tag = jsonArray.getString(i).trim()
                    if (tag.isNotEmpty()) {
                        restoredTags.add(DocumentTag(documentUri = trashed.originalUri, tag = tag))
                    }
                }
                if (restoredTags.isNotEmpty()) {
                    tagDao.insertTags(restoredTags)
                }
            } catch (e: Exception) {
                android.util.Log.e("TrashRepo", "Failed to restore tags", e)
            }
        }

        // 4. Remove from trash database
        trashedDao.deleteById(trashed.id)

        restoredDoc
    }

    /** Restore multiple trashed documents by ID. */
    suspend fun restoreAll(ids: Set<String>): List<RecentDocument> = withContext(Dispatchers.IO) {
        ids.mapNotNull { id ->
            trashedDao.getById(id)?.let { restoreDocument(it) }
        }
    }

    /** Permanently delete a single trashed document. */
    suspend fun deletePermanently(trashed: TrashedDocument) = withContext(Dispatchers.IO) {
        // 1. Delete physical file from quarantine or storage
        if (!trashed.quarantinePath.isNullOrEmpty()) {
            try {
                val qFile = File(trashed.quarantinePath)
                if (qFile.exists()) qFile.delete()
            } catch (e: Exception) {
                android.util.Log.e("TrashRepo", "Failed to delete physical file from quarantine", e)
            }
        }
        
        val rawUri = Uri.parse(trashed.originalUri)
        if (rawUri.scheme == "file" || trashed.originalUri.startsWith("file://")) {
            try {
                val targetPath = rawUri.path ?: trashed.originalUri.removePrefix("file://")
                val origFile = File(targetPath)
                if (origFile.exists()) {
                    origFile.delete()
                }
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(targetPath),
                    null,
                    null
                )
            } catch (e: Exception) {
                android.util.Log.e("TrashRepo", "Failed to delete original file permanently", e)
            }
        } else if (rawUri.scheme == "content") {
            try {
                context.contentResolver.delete(rawUri, null, null)
            } catch (_: Exception) {}
        }

        // 2. Delete database record
        trashedDao.deleteById(trashed.id)
    }

    /** Permanently delete multiple trashed documents by ID. */
    suspend fun deletePermanently(ids: Set<String>) = withContext(Dispatchers.IO) {
        ids.forEach { id ->
            trashedDao.getById(id)?.let { deletePermanently(it) }
        }
    }

    /** Empty the entire Recycle Bin permanently. */
    suspend fun emptyTrash() = withContext(Dispatchers.IO) {
        val allTrashed = trashedDao.getAllTrashed()
        allTrashed.forEach { doc ->
            deletePermanently(doc)
        }
        try {
            trashDir.deleteRecursively()
            trashDir.mkdirs()
        } catch (_: Exception) {}
        trashedDao.deleteAllTrashed()
    }

    /**
     * Purge all items older than the retention period (e.g. 30 days).
     * If retentionDays <= 0 (e.g., -1 for "Never"), auto-purge is skipped.
     */
    suspend fun purgeExpired(retentionDaysOverride: Int? = null): Int = withContext(Dispatchers.IO) {
        val retentionDays = retentionDaysOverride ?: prefsRepository.trashRetentionDays.first()
        if (retentionDays <= 0) return@withContext 0

        val cutoffTimestamp = System.currentTimeMillis() - (retentionDays.toLong() * 24L * 60L * 60L * 1000L)
        val expiredList = trashedDao.getExpired(cutoffTimestamp)

        expiredList.forEach { doc ->
            deletePermanently(doc)
        }

        trashedDao.deleteExpired(cutoffTimestamp)
    }
}
