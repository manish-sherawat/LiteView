package com.nexus.feature.dashboard

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexus.core.navigation.DocumentType
import com.nexus.feature.dashboard.data.RecentDocument
import com.nexus.feature.dashboard.data.RecentDocumentRepository
import com.nexus.feature.dashboard.data.TrashRepository
import com.nexus.feature.dashboard.data.TrashedDocument
import com.nexus.core.preferences.UserPreferencesRepository
import com.nexus.core.preferences.HomeStyle
import com.nexus.core.util.*
import com.nexus.feature.dashboard.domain.StorageScanUseCase
import com.nexus.feature.dashboard.domain.DocumentAccessibilityChecker
import com.nexus.feature.dashboard.domain.DocumentShareHelper
import com.nexus.feature.dashboard.domain.FileOperationUseCase
import com.nexus.feature.dashboard.domain.DashboardFilterUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import javax.inject.Inject


// ─── Sort Options ─────────────────────────────────────────────────────────────
enum class SortOrder { BY_DATE, BY_NAME, BY_TYPE, BY_SIZE }

// ─── Dashboard Tab ────────────────────────────────────────────────────────────
enum class DashboardTab { ALL, RECENT, STARRED }

// ─── File Type Filter ────────────────────────────────────────────────────────
enum class DocumentTypeFilter(val title: String, val docType: DocumentType?) {
    ALL("All", null),
    PDF("PDF", DocumentType.PDF),
    DOCX("Word", DocumentType.DOCX),
    XLSX("Excel", DocumentType.XLSX),
    TXT("Text", DocumentType.TXT)
}



// ─── Dashboard UI State ───────────────────────────────────────────────────────
data class PendingDeletionState(
    val documents: List<RecentDocument>,
    val remainingSeconds: Int
)

data class DashboardUiState(
    val documents: List<RecentDocumentUiModel> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val searchQuery: String = "",
    val sortOrder: SortOrder = SortOrder.BY_DATE,
    val isGridView: Boolean = false,
    val selectedTab: DashboardTab = DashboardTab.ALL,
    val selectedFilter: DocumentTypeFilter = DocumentTypeFilter.ALL,
    val filterCounts: Map<DocumentTypeFilter, Int> = emptyMap(),
    val availableTags: List<TagUiModel> = emptyList(),
    val selectedTag: String? = null,
    val tagCounts: Map<String, Int> = emptyMap(),
    val starredUris: Set<String> = emptySet(),
    val sortAscending: Boolean = false,
    val permissionRationaleShown: Boolean = false,
    val permissionBannerDismissed: Boolean = false,
    val selectedUris: Set<String> = emptySet(),
    val isSelectionMode: Boolean = false,
    val pendingDeletion: PendingDeletionState? = null
)

// ─── Dashboard ViewModel ──────────────────────────────────────────────────────
// Owns the recent documents list, search/sort state, and file-open logic.

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: RecentDocumentRepository,
    private val trashRepository: TrashRepository,
    private val prefsRepository: UserPreferencesRepository,
    @ApplicationContext private val context: Context,
    private val storageScanUseCase: StorageScanUseCase,
    private val accessibilityChecker: DocumentAccessibilityChecker,
    private val shareHelper: DocumentShareHelper,
    private val fileOpUseCase: FileOperationUseCase,
    private val filterUseCase: DashboardFilterUseCase
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _sortOrder = MutableStateFlow(SortOrder.BY_DATE)
    private val _isGridView = MutableStateFlow(false)
    private val _isLoading = MutableStateFlow(true)
    private val _isRefreshing = MutableStateFlow(false)
    private val _inaccessibleUris = MutableStateFlow<Set<String>>(emptySet())
    private val _scannedDocuments = MutableStateFlow<List<RecentDocument>>(emptyList())
    private val _selectedUris = MutableStateFlow<Set<String>>(emptySet())
    private val _isSelectionMode = MutableStateFlow(false)
    private val _selectedTab = MutableStateFlow(DashboardTab.ALL)
    private val _selectedFilter = MutableStateFlow(DocumentTypeFilter.ALL)
    private val _selectedTag = MutableStateFlow<String?>(null)
    private val _starredUris: StateFlow<Set<String>> = prefsRepository.starredUris
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptySet()
        )
    private val _sortAscending = MutableStateFlow(false)
    private val _pendingDeletion = MutableStateFlow<PendingDeletionState?>(null)

    private val _uiEvents = kotlinx.coroutines.flow.MutableSharedFlow<String>()
    val uiEvents = _uiEvents.asSharedFlow()

    private val _searchFocusEvent = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val searchFocusEvent = _searchFocusEvent.asSharedFlow()

    private val _scrollToTopEvent = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val scrollToTopEvent = _scrollToTopEvent.asSharedFlow()

    fun triggerSearchFocus() {
        _searchFocusEvent.tryEmit(Unit)
    }

    fun scrollToTop() {
        _scrollToTopEvent.tryEmit(Unit)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<DashboardUiState> = combine(
        combine(
            repository.observeRecentDocuments(),
            _scannedDocuments,
            _inaccessibleUris,
            repository.observeAllTags(),
            repository.observeAllTagDefinitions(),
            trashRepository.observeAllTrashed(),
            ::Tuple6
        ),
        combine(
            _isLoading,
            @OptIn(kotlinx.coroutines.FlowPreview::class) _searchQuery.debounce(150),
            _sortOrder,
            ::Triple
        ),
        combine(
            _isGridView,
            _selectedTab,
            _starredUris,
            ::Triple
        ),
        combine(
            _isSelectionMode,
            _selectedUris,
            prefsRepository.permissionBannerDismissed,
            ::Triple
        ),
        combine(
            prefsRepository.permissionRationaleShown,
            _sortAscending,
            combine(_isRefreshing, _selectedFilter, _selectedTag, ::Triple),
            ::Triple
        )
    ) { (docs, scanned, inaccessible, allTags, allTagDefs, trashedDocs): Tuple6<List<RecentDocument>, List<RecentDocument>, Set<String>, List<com.nexus.feature.dashboard.data.DocumentTag>, List<com.nexus.feature.dashboard.data.TagDefinition>, List<TrashedDocument>>,
        (loading, query, sort): Triple<Boolean, String, SortOrder>,
        (grid, tab, starred): Triple<Boolean, DashboardTab, Set<String>>,
        (selectionMode, selected, bannerDismissed): Triple<Boolean, Set<String>, Boolean>,
        (rationale, ascending, refreshingFilterAndTag): Triple<Boolean?, Boolean, Triple<Boolean, DocumentTypeFilter, String?>> ->

        val (refreshing, fileFilter, activeTag) = refreshingFilterAndTag
        val state = filterUseCase.filterAndSortDocuments(
            docs = docs,
            scanned = scanned,
            inaccessible = inaccessible,
            allTags = allTags,
            allTagDefs = allTagDefs,
            trashedDocs = trashedDocs,
            loading = loading,
            query = query,
            sort = sort,
            grid = grid,
            tab = tab,
            starred = starred,
            selectionMode = selectionMode,
            selected = selected,
            bannerDismissed = bannerDismissed,
            rationale = rationale,
            ascending = ascending,
            refreshing = refreshing,
            fileFilter = fileFilter,
            activeTag = activeTag
        )

        // Restore pendingDeletion state
        state.copy(pendingDeletion = _pendingDeletion.value)
    }.flowOn(kotlinx.coroutines.Dispatchers.Default)
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState()
    )


    init {
        viewModelScope.launch {
            // Allow initial UI composition to finish before starting heavy disk/DB flows
            kotlinx.coroutines.yield()

            launch {
                prefsRepository.sortAscending.collect { _sortAscending.value = it }
            }

            launch {
                _isGridView.value = prefsRepository.defaultIsGridView.first()
            }

            launch {
                repository.observeRecentDocuments().collect { docs ->
                    if (!_isRefreshing.value) _isLoading.value = false
                    // updateAccessibility(docs, _scannedDocuments.value) is now handled by a separate debounced flow below
                }
            }

            launch {
                @OptIn(kotlinx.coroutines.FlowPreview::class)
                repository.observeRecentDocuments()
                    .debounce(5000L) // Debounce accessibility checks
                    .collect { docs ->
                        updateAccessibility(docs, _scannedDocuments.value)
                    }
            }

            // Starred URIs are now managed via DataStore (UserPreferencesRepository)
            if (hasStoragePermission(context)) {
                scanStorage()
            }

            launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    trashRepository.purgeExpired()
                } catch (e: Exception) {
                    android.util.Log.e("DashboardVM", "Failed to purge expired trash", e)
                }
            }
        }
    }

    fun hasStoragePermission(context: Context): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            android.os.Environment.isExternalStorageManager()
        } else {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    private var scanJob: kotlinx.coroutines.Job? = null

    fun scanStorage(isBackground: Boolean = false) {
        if (!hasStoragePermission(context)) return
        if (!isBackground && !_isRefreshing.value) {
            _isLoading.value = true
        }
        scanJob?.cancel()
        scanJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val trashedList = try { trashRepository.observeAllTrashed().first() } catch (_: Exception) { emptyList() }
                
                val finalScanned = storageScanUseCase.scanStorage(trashedList)
                _scannedDocuments.value = finalScanned
                updateAccessibility(repository.observeRecentDocuments().first(), finalScanned)
            } catch (e: SecurityException) {
                _uiEvents.emit("Storage permission was revoked — please re-grant it in Settings")
                android.util.Log.e("DashboardVM", "Storage permission revoked", e)
            } catch (e: Exception) {
                android.util.Log.e("DashboardVM", "Scan failed", e)
            } finally {
                _isLoading.value = false
                _isRefreshing.value = false
            }
        }
    }

    fun refreshDocuments() {
        if (!hasStoragePermission(context)) {
            _isRefreshing.value = false
            return
        }
        _isRefreshing.value = true
        scanStorage()
    }

    fun setSearchQuery(query: String) { _searchQuery.value = query }
    fun setSortOrder(sort: SortOrder) { _sortOrder.value = sort }
    fun toggleGridView() { _isGridView.value = !_isGridView.value }
    fun setSelectedTab(tab: DashboardTab) { 
        _selectedTab.value = tab 
        clearSelection()
    }
    fun setSelectedFilter(filter: DocumentTypeFilter) {
        if (_selectedFilter.value == filter) {
            _selectedFilter.value = DocumentTypeFilter.ALL
        } else {
            _selectedFilter.value = filter
        }
        clearSelection()
    }
    fun selectTag(tag: String?) {
        if (_selectedTag.value == tag) {
            _selectedTag.value = null
        } else {
            _selectedTag.value = tag
        }
        clearSelection()
    }
    fun addTagToDocument(uri: String, tag: String) {
        viewModelScope.launch {
            repository.addTag(uri, tag)
        }
    }
    fun removeTagFromDocument(uri: String, tag: String) {
        viewModelScope.launch {
            repository.removeTag(uri, tag)
        }
    }
    fun setDocumentTags(uri: String, tags: List<String>) {
        viewModelScope.launch {
            repository.setDocumentTags(uri, tags)
        }
    }
    fun upsertTagDefinition(name: String, colorHex: String, emoji: String?) {
        viewModelScope.launch {
            repository.upsertTagDefinition(name, colorHex, emoji)
        }
    }
    fun renameTagGlobally(oldName: String, newName: String, colorHex: String, emoji: String?) {
        viewModelScope.launch {
            if (_selectedTag.value == oldName) {
                _selectedTag.value = newName
            }
            repository.renameTagGlobally(oldName, newName, colorHex, emoji)
        }
    }
    fun deleteTagGlobally(name: String) {
        viewModelScope.launch {
            if (_selectedTag.value == name) {
                _selectedTag.value = null
            }
            repository.deleteTagGlobally(name)
        }
    }
    fun toggleStarred(uri: String) {
        viewModelScope.launch {
            val current = _starredUris.value.toMutableSet()
            if (current.contains(uri)) {
                current.remove(uri)
            } else {
                current.add(uri)
            }
            prefsRepository.setStarredUris(current)
        }
    }
    fun setHomeStyle(style: HomeStyle) {
        viewModelScope.launch {
            prefsRepository.setHomeStyle(style)
        }
    }

    var lastDeletedDocument: RecentDocument? = null
        private set

    var lastTrashedDocs: List<TrashedDocument> = emptyList()
        private set

    private val processingUris = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    /** Move one document to the Recycle Bin (soft delete with Undo option). */
    fun removeDocument(uri: String) {
        if (processingUris.contains(uri)) return
        processingUris.add(uri)
        viewModelScope.launch {
            try {
                val docToRemove = repository.observeRecentDocuments().first().find { it.uri == uri }
                    ?: _scannedDocuments.value.find { it.uri == uri }
                if (docToRemove != null) {
                    lastDeletedDocument = docToRemove
                    val trashed = trashRepository.moveToTrash(docToRemove)
                    lastTrashedDocs = listOf(trashed)
                } else {
                    repository.removeDocument(uri)
                }
                _scannedDocuments.value = _scannedDocuments.value.filter { it.uri != uri }
                val updatedSelection = _selectedUris.value - uri
                _selectedUris.value = updatedSelection
                if (updatedSelection.isEmpty()) _isSelectionMode.value = false
                _uiEvents.emit("Moved to Recycle Bin")
            } catch (e: Exception) {
                android.util.Log.e("DashboardVM", "Failed to move to Recycle Bin", e)
                _uiEvents.emit("Failed to move to Recycle Bin")
            } finally {
                processingUris.remove(uri)
            }
        }
    }

    /** Undo the most recent delete operation (single or batch). */
    fun undoLastDelete() {
        viewModelScope.launch {
            try {
                if (lastTrashedDocs.isNotEmpty()) {
                    val count = lastTrashedDocs.size
                    lastTrashedDocs.forEach { trashed ->
                        val restored = trashRepository.restoreDocument(trashed)
                        val currentScanned = _scannedDocuments.value.toMutableList()
                        if (!currentScanned.any { it.uri == restored.uri }) {
                            currentScanned.add(0, restored)
                            _scannedDocuments.value = currentScanned
                        }
                    }
                    lastTrashedDocs = emptyList()
                    lastDeletedDocument = null
                    _uiEvents.emit("Restored $count file${if (count > 1) "s" else ""}")
                } else if (lastDeletedDocument != null) {
                    restoreDocument(lastDeletedDocument!!)
                    lastDeletedDocument = null
                }
            } catch (e: Exception) {
                android.util.Log.e("DashboardVM", "Undo failed", e)
                _uiEvents.emit("Undo failed — file may have been permanently deleted")
            }
        }
    }

    /** Restore a removed document to the recent list. */
    fun restoreDocument(doc: RecentDocument) {
        viewModelScope.launch {
            repository.recordOpen(doc)
            val currentScanned = _scannedDocuments.value.toMutableList()
            if (!currentScanned.any { it.uri == doc.uri }) {
                currentScanned.add(0, doc)
                _scannedDocuments.value = currentScanned
            }
            // Ensure restored file is marked accessible
            _inaccessibleUris.value = _inaccessibleUris.value - doc.uri
            _uiEvents.emit("File restored to Recents")
        }
    }

    /** Toggle the sort direction between ascending and descending. */
    fun toggleSortDirection() {
        val current = _sortAscending.value
        _sortAscending.value = !current
        viewModelScope.launch {
            prefsRepository.setSortAscending(!current)
        }
    }

    // ─── Multi-Select ─────────────────────────────────────────────────────────

    fun toggleSelection(uri: String) {
        val current = _selectedUris.value.toMutableSet()
        if (current.contains(uri)) {
            current.remove(uri)
            if (current.isEmpty()) _isSelectionMode.value = false
        } else {
            current.add(uri)
            _isSelectionMode.value = true
        }
        _selectedUris.value = current
    }

    fun selectAll() {
        val allUris = uiState.value.documents.map { it.doc.uri }.toSet()
        _selectedUris.value = allUris
        if (allUris.isNotEmpty()) _isSelectionMode.value = true
    }

    fun clearSelection() {
        _selectedUris.value = emptySet()
        _isSelectionMode.value = false
    }

    fun deleteSelected() {
        val selected = _selectedUris.value.toList()
        if (selected.isEmpty()) return
        viewModelScope.launch {
            try {
                val allRecentDocs = repository.observeRecentDocuments().first()
                val docsToTrash = selected.mapNotNull { uri ->
                    allRecentDocs.find { it.uri == uri } ?: _scannedDocuments.value.find { it.uri == uri }
                }
                val trashed = trashRepository.moveToTrash(docsToTrash)
                lastTrashedDocs = trashed
                _scannedDocuments.value = _scannedDocuments.value.filter { !selected.contains(it.uri) }
                val count = selected.size
                clearSelection()
                _uiEvents.emit("Moved $count file${if (count > 1) "s" else ""} to Recycle Bin")
            } catch (e: Exception) {
                android.util.Log.e("DashboardVM", "Failed to delete selected files", e)
                _uiEvents.emit("Failed to delete selected files")
            }
        }
    }



    // â”€â”€â”€ File Operations â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    fun shareDocument(uriStr: String) {
        shareHelper.shareDocument(uriStr)
    }

    /** Share multiple selected documents in a single chooser sheet. */
    fun shareSelectedDocuments(uris: Set<String>) {
        shareHelper.shareSelectedDocuments(uris)
    }

    fun renameDocument(uriStr: String, newName: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val result = fileOpUseCase.renameDocument(uriStr, newName)
            if (result.isSuccess) {
                val newUriStr = result.getOrNull()!!
                
                val currentScanned = _scannedDocuments.value.toMutableList()
                val idx = currentScanned.indexOfFirst { it.uri == uriStr }
                if (idx >= 0) {
                    currentScanned[idx] = currentScanned[idx].copy(uri = newUriStr, fileName = newName)
                    _scannedDocuments.value = currentScanned
                }
                
                val existing = uiState.value.documents.find { it.doc.uri == uriStr }?.doc
                if (existing != null) {
                    repository.removeDocument(uriStr)
                    repository.recordOpen(existing.copy(uri = newUriStr, fileName = newName))
                }
                
                val currentStarred = _starredUris.value.toMutableSet()
                if (currentStarred.contains(uriStr)) {
                    currentStarred.remove(uriStr)
                    currentStarred.add(newUriStr)
                    prefsRepository.setStarredUris(currentStarred)
                }
            } else {
                val exception = result.exceptionOrNull()
                if (exception?.message == "Rename failed") {
                    _uiEvents.emit("Rename failed")
                } else {
                    _uiEvents.emit("Rename failed: ${exception?.localizedMessage}")
                }
            }
        }
    }

    // â”€â”€â”€ Permission Banner â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    fun dismissPermissionBanner() {
        viewModelScope.launch {
            prefsRepository.setPermissionBannerDismissed(true)
        }
    }

    /** Record that the permission rationale has been shown to the user. */
    fun setPermissionRationaleShown() {
        viewModelScope.launch {
            prefsRepository.setPermissionRationaleShown(true)
        }
    }

    /** Clear the full history (called from Settings). */
    fun clearHistory() {
        viewModelScope.launch {
            try {
                repository.clearAll()
            } catch (e: Exception) {
                android.util.Log.e("DashboardVM", "Clear history failed", e)
                _uiEvents.emit("Failed to clear history")
            }
        }
    }

    // â”€â”€ Helpers â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private fun resolveFileMeta(uri: Uri, mimeType: String?): RecentDocument {
        var fileName = uri.lastPathSegment ?: "Document"
        var fileSize = 0L
        var resolvedMimeType = mimeType

        if (uri.scheme == "content") {
            try {
                if (resolvedMimeType == null) {
                    resolvedMimeType = context.contentResolver.getType(uri)
                }
            } catch (_: Exception) {}

            try {
                context.contentResolver.query(
                    uri, null, null, null, null
                )?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (cursor.moveToFirst()) {
                        if (nameIndex >= 0) fileName = cursor.getString(nameIndex) ?: fileName
                        if (sizeIndex >= 0) fileSize = cursor.getLong(sizeIndex)
                    }
                }
            } catch (_: Exception) { /* Fallback to defaults */ }
        } else if (uri.scheme == "file") {
            try {
                val file = java.io.File(uri.path ?: "")
                if (file.exists()) {
                    fileSize = file.length()
                }
            } catch (_: Exception) {}
            if (resolvedMimeType == null) {
                val extension = android.webkit.MimeTypeMap.getFileExtensionFromUrl(uri.toString())
                resolvedMimeType = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            }
        }

        val docType = DocumentType.fromUri(Uri.parse("file:///$fileName"), resolvedMimeType)

        return RecentDocument(
            uri = uri.toString(),
            fileName = fileName,
            mimeType = resolvedMimeType,
            fileSizeBytes = fileSize,
            lastOpenedAt = System.currentTimeMillis(),
            documentType = docType.name
        )
    }

    private fun updateAccessibility(docs: List<RecentDocument>, scanned: List<RecentDocument>) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val allDocs = docs + scanned
            val currentlyInaccessible = _inaccessibleUris.value
            
            // Only check documents we don't already know are inaccessible (incremental check).
            // A more comprehensive approach would periodically re-check known-inaccessible ones,
            // but for performance, we minimize disk I/O.
            val toCheck = allDocs.filter { it.uri !in currentlyInaccessible }
            
            if (toCheck.isNotEmpty()) {
                val newInvalid = accessibilityChecker.getInaccessibleUris(toCheck)
                if (newInvalid.isNotEmpty()) {
                    _inaccessibleUris.value = currentlyInaccessible + newInvalid
                }
            }
        }
    }
}

