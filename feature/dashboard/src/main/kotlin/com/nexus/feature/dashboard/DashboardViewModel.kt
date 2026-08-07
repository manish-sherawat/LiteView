package com.nexus.feature.dashboard

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexus.core.navigation.DocumentType
import com.nexus.feature.dashboard.data.RecentDocument
import com.nexus.feature.dashboard.data.RecentDocumentRepository
import com.nexus.core.preferences.UserPreferencesRepository
import com.nexus.core.preferences.HomeStyle
import com.nexus.core.updater.AppUpdater
import com.nexus.core.updater.UpdateState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─── Sort Options ─────────────────────────────────────────────────────────────
enum class SortOrder { BY_DATE, BY_NAME, BY_TYPE, BY_SIZE }

// ─── Dashboard Tab ───────────────────────────────────────────────────────────
enum class DashboardTab { ALL, RECENT, STARRED }

// ─── Dashboard UI State ───────────────────────────────────────────────────────
data class DashboardUiState(
    val documents: List<RecentDocumentUiModel> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val searchQuery: String = "",
    val sortOrder: SortOrder = SortOrder.BY_DATE,
    val isGridView: Boolean = false,
    val selectedTab: DashboardTab = DashboardTab.ALL,
    val starredUris: Set<String> = emptySet(),
    val sortAscending: Boolean = false,
    val permissionRationaleShown: Boolean = false,
    val permissionBannerDismissed: Boolean = false,
    val selectedUris: Set<String> = emptySet(),
    val isSelectionMode: Boolean = false
)

// ─── Dashboard ViewModel ──────────────────────────────────────────────────────
// Owns the recent documents list, search/sort state, and file-open logic.

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: RecentDocumentRepository,
    private val prefsRepository: UserPreferencesRepository,
    private val appUpdater: AppUpdater,
    @ApplicationContext private val context: Context
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
    private val _starredUris: StateFlow<Set<String>> = prefsRepository.starredUris
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptySet()
        )
    private val _sortAscending = MutableStateFlow(false)

    val updateState: StateFlow<UpdateState> = appUpdater.updateState
    
    private val _uiEvents = kotlinx.coroutines.flow.MutableSharedFlow<String>()
    val uiEvents = _uiEvents.asSharedFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<DashboardUiState> = combine(
        combine(
            repository.observeRecentDocuments(),
            _scannedDocuments,
            _inaccessibleUris,
            ::Triple
        ),
        combine(
            _isLoading,
            _searchQuery,
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
            _isRefreshing,
            ::Triple
        )
    ) { (docs, scanned, inaccessible): Triple<List<RecentDocument>, List<RecentDocument>, Set<String>>,
        (loading, query, sort): Triple<Boolean, String, SortOrder>,
        (grid, tab, starred): Triple<Boolean, DashboardTab, Set<String>>,
        (selectionMode, selected, bannerDismissed): Triple<Boolean, Set<String>, Boolean>,
        (rationale, ascending, refreshing): Triple<Boolean?, Boolean, Boolean> ->

        val dedupBase = docs + scanned
        val baseDocs = when (tab) {
            DashboardTab.RECENT -> docs.distinctBy { it.uri.ifEmpty { "${it.fileName}_${it.fileSizeBytes}" } }
            DashboardTab.STARRED -> dedupBase.distinctBy { it.uri.ifEmpty { "${it.fileName}_${it.fileSizeBytes}" } }.filter { starred.contains(it.uri) }
            DashboardTab.ALL -> dedupBase.distinctBy { it.uri.ifEmpty { "${it.fileName}_${it.fileSizeBytes}" } }
        }

        val mapped = baseDocs
            .filter { it.fileName.contains(query, ignoreCase = true) }
            .map { doc ->
                RecentDocumentUiModel(
                    doc = doc,
                    isAccessible = !inaccessible.contains(doc.uri)
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
        DashboardUiState(
            documents = mapped,
            isLoading = loading,
            isRefreshing = refreshing,
            searchQuery = query,
            sortOrder = sort,
            isGridView = grid,
            selectedTab = tab,
            starredUris = starred,
            sortAscending = ascending,
            permissionRationaleShown = rationale ?: false,
            permissionBannerDismissed = bannerDismissed,
            selectedUris = selected,
            isSelectionMode = selectionMode
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
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
                    updateAccessibility(docs, _scannedDocuments.value)
                }
            }

            // Starred URIs are now managed via DataStore (UserPreferencesRepository)
            if (hasStoragePermission(context)) {
                scanStorage()
            }

            launch {
                try {
                    val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                    val versionName = packageInfo.versionName ?: "1.0.0"
                    appUpdater.checkForUpdates(versionName)
                } catch (e: Exception) {
                    e.printStackTrace()
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

                // Rescan external storage
                val uri = android.provider.MediaStore.Files.getContentUri("external")
                val projection = arrayOf(
                    android.provider.MediaStore.Files.FileColumns.DATA,
                    android.provider.MediaStore.Files.FileColumns.DISPLAY_NAME,
                    android.provider.MediaStore.Files.FileColumns.SIZE,
                    android.provider.MediaStore.Files.FileColumns.DATE_MODIFIED,
                    android.provider.MediaStore.Files.FileColumns.MIME_TYPE
                )

                val selection = (
                    android.provider.MediaStore.Files.FileColumns.DATA + " LIKE '%.pdf' OR " +
                    android.provider.MediaStore.Files.FileColumns.DATA + " LIKE '%.docx' OR " +
                    android.provider.MediaStore.Files.FileColumns.DATA + " LIKE '%.doc' OR " +
                    android.provider.MediaStore.Files.FileColumns.DATA + " LIKE '%.xlsx' OR " +
                    android.provider.MediaStore.Files.FileColumns.DATA + " LIKE '%.xls' OR " +
                    android.provider.MediaStore.Files.FileColumns.DATA + " LIKE '%.txt'"
                )

                val cursor = context.contentResolver.query(
                    uri,
                    projection,
                    selection,
                    null,
                    "${android.provider.MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
                )

                val scanned = mutableListOf<RecentDocument>()
                cursor?.use { c ->
                    val dataIdx = c.getColumnIndex(android.provider.MediaStore.Files.FileColumns.DATA)
                    val nameIdx = c.getColumnIndex(android.provider.MediaStore.Files.FileColumns.DISPLAY_NAME)
                    val sizeIdx = c.getColumnIndex(android.provider.MediaStore.Files.FileColumns.SIZE)
                    val dateIdx = c.getColumnIndex(android.provider.MediaStore.Files.FileColumns.DATE_MODIFIED)
                    val mimeIdx = c.getColumnIndex(android.provider.MediaStore.Files.FileColumns.MIME_TYPE)

                    while (c.moveToNext()) {
                        val path = if (dataIdx >= 0) c.getString(dataIdx) else null
                        if (path.isNullOrEmpty()) continue
                        val file = java.io.File(path)
                        if (!file.exists() || !file.canRead()) continue

                        val name = if (nameIdx >= 0) c.getString(nameIdx) ?: file.name else file.name
                        val size = if (sizeIdx >= 0) c.getLong(sizeIdx) else 0L
                        val dateModified = if (dateIdx >= 0) c.getLong(dateIdx) * 1000L else 0L
                        val mimeType = if (mimeIdx >= 0) c.getString(mimeIdx) else null

                        val ext = file.extension.lowercase()
                        val docType = when (ext) {
                            "pdf" -> DocumentType.PDF
                            "docx", "doc" -> DocumentType.DOCX
                            "xlsx", "xls" -> DocumentType.XLSX
                            "txt" -> DocumentType.TXT
                            else -> DocumentType.UNKNOWN
                        }
                        if (docType == DocumentType.UNKNOWN) continue

                        val resolvedMimeType = mimeType ?: when (ext) {
                            "pdf" -> "application/pdf"
                            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                            "doc" -> "application/msword"
                            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                            "xls" -> "application/vnd.ms-excel"
                            "txt" -> "text/plain"
                            else -> "*/*"
                        }

                        scanned.add(
                            RecentDocument(
                                uri = Uri.fromFile(file).toString(),
                                fileName = name,
                                mimeType = resolvedMimeType,
                                fileSizeBytes = if (size > 0L) size else file.length(),
                                lastOpenedAt = if (dateModified > 0L) dateModified else file.lastModified(),
                                documentType = docType.name
                            )
                        )
                    }
                }
                val finalScanned = scanned.toList()
                _scannedDocuments.value = finalScanned
                updateAccessibility(repository.observeRecentDocuments().first(), finalScanned)
            } catch (e: Exception) {
                e.printStackTrace()
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

    /** Called when the user picks or opens a document URI from SAF or intent. */
    fun onDocumentOpened(uri: Uri, mimeType: String?) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val finalUri = if (uri.scheme == "content") {
                com.nexus.core.utils.DocumentCache.cacheUri(context, uri)
            } else {
                uri
            }
            val meta = resolveFileMeta(finalUri, mimeType)
            repository.recordOpen(meta)
        }
    }

    var lastDeletedDocument: RecentDocument? = null
        private set

    private val processingUris = mutableSetOf<String>()

    /** Remove one document from the recent list and delete physically. */
    fun removeDocument(uri: String) {
        if (processingUris.contains(uri)) return
        processingUris.add(uri)
        viewModelScope.launch {
            try {
                val docToRemove = repository.observeRecentDocuments().first().find { it.uri == uri }
                    ?: _scannedDocuments.value.find { it.uri == uri }
                if (docToRemove != null) {
                    lastDeletedDocument = docToRemove
                }
                deletePhysicalFile(uri)
                repository.removeDocument(uri)
                _scannedDocuments.value = _scannedDocuments.value.filter { it.uri != uri }
                val updatedSelection = _selectedUris.value - uri
                _selectedUris.value = updatedSelection
                if (updatedSelection.isEmpty()) _isSelectionMode.value = false
                _uiEvents.emit("File deleted successfully")
            } finally {
                processingUris.remove(uri)
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
            _uiEvents.emit("File restored")
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

    // ─── Multi-Select ──────────────────────────────────────────────────────────

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
            val count = selected.size
            selected.forEach { uri ->
                deletePhysicalFile(uri)
                repository.removeDocument(uri)
            }
            _scannedDocuments.value = _scannedDocuments.value.filter { !selected.contains(it.uri) }
            clearSelection()
            _uiEvents.emit("$count file${if (count > 1) "s" else ""} deleted successfully")
        }
    }

    private fun deletePhysicalFile(uriStr: String) {
        try {
            val uri = Uri.parse(uriStr)
            if (uri.scheme == "file") {
                val file = java.io.File(uri.path ?: "")
                if (file.exists()) {
                    file.delete()
                }
            } else if (uri.scheme == "content") {
                context.contentResolver.delete(uri, null, null)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ─── File Operations ───────────────────────────────────────────────────────

    fun shareDocument(uriStr: String) {
        viewModelScope.launch {
            try {
                val rawUri = Uri.parse(uriStr)
                val shareUri = if (rawUri.scheme == "file") {
                    val file = java.io.File(rawUri.path ?: "")
                    androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                } else {
                    rawUri
                }
                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "*/*"
                    putExtra(android.content.Intent.EXTRA_STREAM, shareUri)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = android.content.Intent.createChooser(intent, "Share File").apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
            } catch (e: Exception) {
                _uiEvents.emit("Failed to share document")
            }
        }
    }

    /** Share multiple selected documents in a single chooser sheet. */
    fun shareSelectedDocuments(uris: Set<String>) {
        viewModelScope.launch {
            try {
                val shareUris = ArrayList<Uri>()
                for (uriStr in uris) {
                    val rawUri = Uri.parse(uriStr)
                    val shareUri = if (rawUri.scheme == "file") {
                        val file = java.io.File(rawUri.path ?: "")
                        androidx.core.content.FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file
                        )
                    } else {
                        rawUri
                    }
                    shareUris.add(shareUri)
                }
                if (shareUris.isEmpty()) return@launch

                val intent = if (shareUris.size == 1) {
                    // Single file — use ACTION_SEND for better app compatibility
                    android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "*/*"
                        putExtra(android.content.Intent.EXTRA_STREAM, shareUris[0])
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                } else {
                    // Multiple files — use ACTION_SEND_MULTIPLE
                    android.content.Intent(android.content.Intent.ACTION_SEND_MULTIPLE).apply {
                        type = "*/*"
                        putParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM, shareUris)
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                }
                val chooser = android.content.Intent.createChooser(
                    intent,
                    if (shareUris.size == 1) "Share File" else "Share ${shareUris.size} Files"
                ).apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
            } catch (e: Exception) {
                _uiEvents.emit("Failed to share documents")
            }
        }
    }

    fun renameDocument(uriStr: String, newName: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val uri = Uri.parse(uriStr)
                var success = false
                var newUriStr = uriStr
                
                if (uri.scheme == "file") {
                    val file = java.io.File(uri.path ?: "")
                    if (file.exists()) {
                        val newFile = java.io.File(file.parentFile, newName)
                        success = file.renameTo(newFile)
                        if (success) {
                            newUriStr = Uri.fromFile(newFile).toString()
                        }
                    }
                } else if (uri.scheme == "content") {
                    try {
                        val newUri = android.provider.DocumentsContract.renameDocument(context.contentResolver, uri, newName)
                        if (newUri != null) {
                            success = true
                            newUriStr = newUri.toString()
                        }
                    } catch (e: Exception) {}
                }
                
                if (success) {
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
                    _uiEvents.emit("Rename failed")
                }
            } catch (e: Exception) {
                _uiEvents.emit("Rename failed: ${e.localizedMessage}")
            }
        }
    }

    // ─── Permission Banner ───────────────────────────────────────────────────────

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
        viewModelScope.launch { repository.clearAll() }
    }

    // ─── Updater ─────────────────────────────────────────────────────────────

    fun downloadUpdate(url: String, version: String) {
        appUpdater.downloadAndInstallUpdate(url, version)
    }

    fun dismissUpdate() {
        appUpdater.resetState()
    }
    
    fun resetUpdateState() {
        appUpdater.resetState()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

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

    private suspend fun isUriAccessible(uriStr: String): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val uri = Uri.parse(uriStr)
            if (uri.scheme == "content") {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false
            } else {
                java.io.File(uri.path ?: "").exists()
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun updateAccessibility(docs: List<RecentDocument>, scanned: List<RecentDocument>) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val allDocs = docs + scanned
            val invalid = allDocs.filter { !isUriAccessible(it.uri) }.map { it.uri }.toSet()
            _inaccessibleUris.value = invalid
        }
    }
}
