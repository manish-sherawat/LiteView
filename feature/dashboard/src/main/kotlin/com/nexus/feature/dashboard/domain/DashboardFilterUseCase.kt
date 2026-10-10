package com.nexus.feature.dashboard.domain

import android.net.Uri
import com.nexus.feature.dashboard.DashboardTab
import com.nexus.feature.dashboard.DashboardUiState
import com.nexus.core.navigation.DocumentType
import com.nexus.feature.dashboard.DocumentTypeFilter
import com.nexus.feature.dashboard.data.RecentDocument
import com.nexus.feature.dashboard.RecentDocumentUiModel
import com.nexus.feature.dashboard.SortOrder
import com.nexus.feature.dashboard.data.TagDefinition
import com.nexus.feature.dashboard.data.TrashedDocument
import com.nexus.feature.dashboard.TagColorPresets
import com.nexus.feature.dashboard.TagUiModel
import javax.inject.Inject

class DashboardFilterUseCase @Inject constructor() {

    fun filterAndSortDocuments(
        docs: List<RecentDocument>,
        scanned: List<RecentDocument>,
        inaccessible: Set<String>,
        allTags: List<com.nexus.feature.dashboard.data.DocumentTag>,
        allTagDefs: List<TagDefinition>,
        trashedDocs: List<TrashedDocument>,
        loading: Boolean,
        query: String,
        sort: SortOrder,
        grid: Boolean,
        tab: DashboardTab,
        starred: Set<String>,
        selectionMode: Boolean,
        selected: Set<String>,
        bannerDismissed: Boolean,
        rationale: Boolean?,
        ascending: Boolean,
        refreshing: Boolean,
        fileFilter: DocumentTypeFilter,
        activeTag: String?
    ): DashboardUiState {

        val trashedUris = trashedDocs.map { it.originalUri }.toSet()
        val trashedPaths = trashedDocs.mapNotNull { it.originalPath }.toSet()
        val isDocTrashed: (RecentDocument) -> Boolean = { doc ->
            val normalizedUri = doc.uri.removePrefix("file://")
            trashedUris.contains(doc.uri) || trashedPaths.contains(normalizedUri)
        }

        val dedupKey: (RecentDocument) -> String = { doc ->
            val decodedUri = try { java.net.URLDecoder.decode(doc.uri, "UTF-8") } catch (_: Exception) { doc.uri }
            if (decodedUri.startsWith("file://")) {
                decodedUri.removePrefix("file://").lowercase()
            } else if (doc.fileName.isNotEmpty()) {
                "${doc.fileName.lowercase()}_${doc.documentType}"
            } else {
                doc.uri
            }
        }
        
        val dedupBase = (docs + scanned).filter { !isDocTrashed(it) }.distinctBy(dedupKey)
        val baseDocs = when (tab) {
            DashboardTab.RECENT -> docs.filter { !isDocTrashed(it) }.distinctBy(dedupKey)
            DashboardTab.STARRED -> dedupBase.filter { starred.contains(it.uri) }
            DashboardTab.ALL -> dedupBase
        }

        fun isDocMatchingFilter(doc: RecentDocument, filter: DocumentTypeFilter): Boolean {
            val type = runCatching { DocumentType.valueOf(doc.documentType) }.getOrElse {
                DocumentType.fromUri(Uri.parse(doc.uri), doc.mimeType)
            }
            return when (filter) {
                DocumentTypeFilter.ALL -> true
                DocumentTypeFilter.PDF -> type == DocumentType.PDF || doc.fileName.endsWith(".pdf", ignoreCase = true)
                DocumentTypeFilter.DOCX -> type == DocumentType.DOCX || doc.fileName.endsWith(".docx", ignoreCase = true) || doc.fileName.endsWith(".doc", ignoreCase = true)
                DocumentTypeFilter.XLSX -> type == DocumentType.XLSX || doc.fileName.endsWith(".xlsx", ignoreCase = true) || doc.fileName.endsWith(".xls", ignoreCase = true)
                DocumentTypeFilter.TXT -> type == DocumentType.TXT || DocumentType.fromUri(Uri.parse("file:///${doc.fileName}")) == DocumentType.TXT
            }
        }

        val tagDefMap = allTagDefs.associateBy { it.name }
        val uriToTagNamesMap: Map<String, List<String>> = allTags.groupBy({ it.documentUri }, { it.tag })

        val uriToTagUiModelsMap: Map<String, List<TagUiModel>> = uriToTagNamesMap.mapValues { (_, tagNames) ->
            tagNames.map { tagName ->
                val def = tagDefMap[tagName]
                TagUiModel(
                    name = tagName,
                    colorHex = def?.colorHex ?: TagColorPresets.getColorHexForTag(tagName),
                    emoji = def?.emoji
                )
            }
        }

        val allUniqueTagNames: List<String> = (allTags.map { it.tag } + allTagDefs.map { it.name }).distinct().sorted()
        val tagCounts: Map<String, Int> = allUniqueTagNames.associateWith { tagName ->
            baseDocs.count { doc -> (uriToTagNamesMap[doc.uri] ?: emptyList()).contains(tagName) }
        }

        val availableTagUiModels: List<TagUiModel> = allUniqueTagNames.map { tagName ->
            val def = tagDefMap[tagName]
            TagUiModel(
                name = tagName,
                colorHex = def?.colorHex ?: TagColorPresets.getColorHexForTag(tagName),
                emoji = def?.emoji,
                count = tagCounts[tagName] ?: 0
            )
        }

        val counts = mapOf(
            DocumentTypeFilter.ALL to baseDocs.size,
            DocumentTypeFilter.PDF to baseDocs.count { isDocMatchingFilter(it, DocumentTypeFilter.PDF) },
            DocumentTypeFilter.DOCX to baseDocs.count { isDocMatchingFilter(it, DocumentTypeFilter.DOCX) },
            DocumentTypeFilter.XLSX to baseDocs.count { isDocMatchingFilter(it, DocumentTypeFilter.XLSX) },
            DocumentTypeFilter.TXT to baseDocs.count { isDocMatchingFilter(it, DocumentTypeFilter.TXT) }
        )

        val filteredDocs = baseDocs.filter { doc ->
            val matchesType = if (fileFilter == DocumentTypeFilter.ALL) true else isDocMatchingFilter(doc, fileFilter)
            val matchesTag = if (activeTag == null) true else (uriToTagNamesMap[doc.uri] ?: emptyList()).contains(activeTag)
            matchesType && matchesTag
        }

        val mapped = filteredDocs
            .filter { it.fileName.contains(query, ignoreCase = true) }
            .map { doc ->
                RecentDocumentUiModel(
                    doc = doc,
                    isAccessible = !inaccessible.contains(doc.uri),
                    tags = uriToTagUiModelsMap[doc.uri] ?: emptyList()
                )
            }
            .let { list: List<RecentDocumentUiModel> ->
                if (ascending) {
                    when (sort) {
                        SortOrder.BY_DATE -> list.sortedBy { item -> item.doc.lastOpenedAt }
                        SortOrder.BY_NAME -> list.sortedBy { item -> item.doc.fileName.lowercase() }
                        SortOrder.BY_TYPE -> list.sortedBy { item -> item.doc.documentType }
                        SortOrder.BY_SIZE -> list.sortedBy { item -> item.doc.fileSizeBytes }
                    }
                } else {
                    when (sort) {
                        SortOrder.BY_DATE -> list.sortedByDescending { item -> item.doc.lastOpenedAt }
                        SortOrder.BY_NAME -> list.sortedByDescending { item -> item.doc.fileName.lowercase() }
                        SortOrder.BY_TYPE -> list.sortedByDescending { item -> item.doc.documentType }
                        SortOrder.BY_SIZE -> list.sortedByDescending { item -> item.doc.fileSizeBytes }
                    }
                }
            }

        return DashboardUiState(
            documents = mapped,
            isLoading = loading,
            isRefreshing = refreshing,
            searchQuery = query,
            sortOrder = sort,
            isGridView = grid,
            selectedTab = tab,
            selectedFilter = fileFilter,
            filterCounts = counts,
            availableTags = availableTagUiModels,
            selectedTag = activeTag,
            tagCounts = tagCounts,
            starredUris = starred,
            sortAscending = ascending,
            permissionRationaleShown = rationale ?: false,
            permissionBannerDismissed = bannerDismissed,
            selectedUris = selected,
            isSelectionMode = selectionMode,
            pendingDeletion = null // This gets managed elsewhere or preserved if we handled it properly
        )
    }
}
