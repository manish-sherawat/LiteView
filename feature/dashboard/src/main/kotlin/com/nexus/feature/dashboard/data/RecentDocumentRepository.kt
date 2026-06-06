package com.nexus.feature.dashboard.data

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

// ─── Recent Document Repository ───────────────────────────────────────────────
// Single source of truth for recent file history.
// The ViewModel talks to the repository; the repository talks to Room.

@Singleton
class RecentDocumentRepository @Inject constructor(
    private val dao: RecentDocumentDao
) {
    /** Reactive stream of all recent documents, newest first. */
    fun observeRecentDocuments(): Flow<List<RecentDocument>> = dao.observeAll()

    /** Record (or refresh) a document that was just opened. */
    suspend fun recordOpen(doc: RecentDocument) = dao.upsert(doc)

    /** Remove one item from the recents list. */
    suspend fun removeDocument(uri: String) = dao.deleteByUri(uri)

    /** Clear the entire history. */
    suspend fun clearAll() = dao.deleteAll()
}
