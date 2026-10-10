package com.nexus.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexus.core.preferences.UserPreferencesRepository
import com.nexus.feature.dashboard.data.RecentDocument
import com.nexus.feature.dashboard.data.TrashRepository
import com.nexus.feature.dashboard.data.TrashedDocument
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─── Recycle Bin UI State ─────────────────────────────────────────────────────

data class RecycleBinUiState(
    val items: List<TrashedDocumentUiModel> = emptyList(),
    val totalCount: Int = 0,
    val totalSizeBytes: Long = 0L,
    val formattedTotalSize: String = "0 B",
    val selectedIds: Set<String> = emptySet(),
    val isSelectionMode: Boolean = false,
    val retentionDays: Int = 30,
    val searchQuery: String = "",
    val isLoading: Boolean = true
)

// ─── Recycle Bin ViewModel ───────────────────────────────────────────────────

@HiltViewModel
class RecycleBinViewModel @Inject constructor(
    private val trashRepository: TrashRepository,
    private val prefsRepository: UserPreferencesRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    private val _isSelectionMode = MutableStateFlow(false)

    private val _uiEvents = MutableSharedFlow<String>()
    val uiEvents = _uiEvents.asSharedFlow()

    init {
        // Run auto-purge check on opening recycle bin
        viewModelScope.launch {
            trashRepository.purgeExpired()
        }
    }

    val uiState: StateFlow<RecycleBinUiState> = combine(
        trashRepository.observeAllTrashed(),
        prefsRepository.trashRetentionDays,
        _searchQuery,
        _selectedIds,
        _isSelectionMode
    ) { trashedList, retentionDays, query, selected, selectionMode ->
        val totalSize = trashedList.sumOf { it.fileSizeBytes }
        val filtered = if (query.isBlank()) {
            trashedList
        } else {
            trashedList.filter { it.fileName.contains(query, ignoreCase = true) }
        }

        val models = filtered.map { TrashedDocumentUiModel.fromEntity(it, retentionDays) }

        RecycleBinUiState(
            items = models,
            totalCount = trashedList.size,
            totalSizeBytes = totalSize,
            formattedTotalSize = formatFileSize(totalSize),
            selectedIds = selected,
            isSelectionMode = selectionMode && selected.isNotEmpty(),
            retentionDays = retentionDays,
            searchQuery = query,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RecycleBinUiState()
    )

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleSelection(id: String) {
        val current = _selectedIds.value.toMutableSet()
        if (current.contains(id)) {
            current.remove(id)
            if (current.isEmpty()) {
                _isSelectionMode.value = false
            }
        } else {
            current.add(id)
            _isSelectionMode.value = true
        }
        _selectedIds.value = current
    }

    fun selectAll() {
        val allIds = uiState.value.items.map { it.doc.id }.toSet()
        _selectedIds.value = allIds
        if (allIds.isNotEmpty()) {
            _isSelectionMode.value = true
        }
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
        _isSelectionMode.value = false
    }

    fun toggleSelectionMode() {
        if (_isSelectionMode.value) {
            clearSelection()
        } else {
            _isSelectionMode.value = true
        }
    }

    /** Restore a single trashed document. */
    fun restore(trashed: TrashedDocument) {
        viewModelScope.launch {
            trashRepository.restoreDocument(trashed)
            _selectedIds.value = _selectedIds.value - trashed.id
            _uiEvents.emit("\"${trashed.fileName}\" restored")
        }
    }

    /** Batch restore all currently selected documents. */
    fun restoreSelected() {
        val selected = _selectedIds.value
        if (selected.isEmpty()) return
        viewModelScope.launch {
            val count = selected.size
            trashRepository.restoreAll(selected)
            clearSelection()
            _uiEvents.emit("Restored $count document${if (count > 1) "s" else ""}")
        }
    }

    /** Permanently delete a single trashed document. */
    fun deletePermanently(trashed: TrashedDocument) {
        viewModelScope.launch {
            trashRepository.deletePermanently(trashed)
            _selectedIds.value = _selectedIds.value - trashed.id
            _uiEvents.emit("Permanently deleted \"${trashed.fileName}\"")
        }
    }

    /** Batch permanently delete all currently selected documents. */
    fun deleteSelected() {
        val selected = _selectedIds.value
        if (selected.isEmpty()) return
        viewModelScope.launch {
            val count = selected.size
            trashRepository.deletePermanently(selected)
            clearSelection()
            _uiEvents.emit("Permanently deleted $count document${if (count > 1) "s" else ""}")
        }
    }

    /** Empty the entire Recycle Bin permanently. */
    fun emptyTrash() {
        viewModelScope.launch {
            val count = uiState.value.totalCount
            trashRepository.emptyTrash()
            clearSelection()
            _uiEvents.emit("Recycle Bin emptied ($count item${if (count > 1) "s" else ""} removed)")
        }
    }

    /** Set the auto-purge retention duration (e.g. 30, 15, 7, or -1 for never). */
    fun setRetentionDays(days: Int) {
        viewModelScope.launch {
            prefsRepository.setTrashRetentionDays(days)
            trashRepository.purgeExpired(days)
            val msg = if (days <= 0) "Retention set to Never" else "Auto-purge set to $days days"
            _uiEvents.emit(msg)
        }
    }
}
