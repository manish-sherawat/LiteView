package com.nexus.feature.dashboard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexus.core.preferences.ThemeMode
import com.nexus.core.preferences.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.nexus.core.updater.AppUpdater
import com.nexus.core.updater.UpdateState
import com.nexus.feature.dashboard.data.RecentDocumentRepository
import com.nexus.feature.dashboard.data.TagDefinition

// ─── Settings UI State ────────────────────────────────────────────────────────

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.LIGHT,
    val defaultFontSize: Float = 14f,
    val appVersion: String = "1.0.0",
    val cacheSizeText: String = "0 B",
    val cacheClearMessage: String? = null,
    val keepScreenAwake: Boolean = false,
    val rememberReadingPosition: Boolean = true,
    val defaultIsGridView: Boolean = false,
    val hapticFeedbackEnabled: Boolean = true,
    val isManualUpdateCheck: Boolean = false
)

// ─── Settings ViewModel ───────────────────────────────────────────────────────

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefsRepository: UserPreferencesRepository,
    private val recentDocumentDao: com.nexus.feature.dashboard.data.RecentDocumentDao,
    private val repository: RecentDocumentRepository,
    private val appUpdater: AppUpdater,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _cacheClearResult = MutableStateFlow<String?>(null)
    private val _cacheSizeText = MutableStateFlow("Calculating...")
    private val _isManualUpdateCheck = MutableStateFlow(false)
    
    private val _uiEvents = kotlinx.coroutines.flow.MutableSharedFlow<String>()
    val uiEvents = _uiEvents.asSharedFlow()

    init {
        updateCacheSize()
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        combine(
            prefsRepository.themeMode,
            prefsRepository.defaultFontSize,
            prefsRepository.keepScreenAwake,
            ::Triple
        ),
        combine(
            prefsRepository.rememberReadingPosition,
            prefsRepository.defaultIsGridView,
            prefsRepository.hapticFeedbackEnabled,
            ::Triple
        ),
        combine(
            _cacheSizeText,
            _cacheClearResult,
            _isManualUpdateCheck,
            ::Triple
        )
    ) { (themeMode, defaultFontSize, keepScreenAwake),
        (rememberReadingPosition, defaultIsGridView, hapticFeedbackEnabled),
        (cacheSizeText, cacheClearResult, isManualUpdateCheck) ->
        SettingsUiState(
            themeMode = themeMode,
            defaultFontSize = defaultFontSize,
            keepScreenAwake = keepScreenAwake,
            rememberReadingPosition = rememberReadingPosition,
            defaultIsGridView = defaultIsGridView,
            hapticFeedbackEnabled = hapticFeedbackEnabled,
            appVersion = getAppVersion(),
            cacheSizeText = cacheSizeText,
            cacheClearMessage = cacheClearResult,
            isManualUpdateCheck = isManualUpdateCheck
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState()
    )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { prefsRepository.setThemeMode(mode) }
    }

    fun setDefaultFontSize(size: Float) {
        viewModelScope.launch { prefsRepository.setDefaultFontSize(size) }
    }

    fun setKeepScreenAwake(value: Boolean) = viewModelScope.launch { prefsRepository.setKeepScreenAwake(value) }
    fun setRememberReadingPosition(value: Boolean) = viewModelScope.launch { 
        prefsRepository.setRememberReadingPosition(value) 
        if (!value) {
            recentDocumentDao.clearAllScrollPositions()
        }
    }
    fun setDefaultIsGridView(value: Boolean) = viewModelScope.launch { prefsRepository.setDefaultIsGridView(value) }
    fun setHapticFeedbackEnabled(value: Boolean) = viewModelScope.launch { prefsRepository.setHapticFeedbackEnabled(value) }

    fun resetToDefaults() = viewModelScope.launch { prefsRepository.resetToDefaults() }

    fun exportReadingHistory(uri: android.net.Uri) {
        viewModelScope.launch {
            try {
                val docs = recentDocumentDao.observeAll().first()
                // Convert list to simple JSON string manually or using org.json
                val jsonArray = org.json.JSONArray()
                for (doc in docs) {
                    val jsonObj = org.json.JSONObject()
                    jsonObj.put("uri", doc.uri)
                    jsonObj.put("fileName", doc.fileName)
                    jsonObj.put("mimeType", doc.mimeType)
                    jsonObj.put("documentType", doc.documentType)
                    jsonObj.put("fileSizeBytes", doc.fileSizeBytes)
                    jsonObj.put("lastOpenedAt", doc.lastOpenedAt)
                    jsonObj.put("lastScrollIndex", doc.lastScrollIndex)
                    jsonObj.put("lastScrollOffset", doc.lastScrollOffset)
                    jsonArray.put(jsonObj)
                }
                
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(jsonArray.toString(2).toByteArray())
                }
                _uiEvents.emit("Reading history exported successfully")
            } catch (e: Exception) {
                _uiEvents.emit("Export failed: ${e.message ?: "Unknown error"}")
            }
        }
    }

    fun updateCacheSize() {
        viewModelScope.launch {
            val size = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                getFolderSize(context.cacheDir)
            }
            _cacheSizeText.value = formatFileSize(size)
        }
    }

    private fun getFolderSize(file: java.io.File): Long {
        if (!file.exists()) return 0L
        if (file.isFile) return file.length()
        var size = 0L
        val files = file.listFiles()
        if (files != null) {
            for (f in files) {
                size += getFolderSize(f)
            }
        }
        return size
    }

    fun clearCache() {
        viewModelScope.launch {
            try {
                val sizeBefore = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    getFolderSize(context.cacheDir)
                }
                
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
                }
                
                val sizeAfter = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    getFolderSize(context.cacheDir)
                }
                
                val freed = sizeBefore - sizeAfter
                if (freed > 0) {
                    _cacheClearResult.value = "Cleared ${formatFileSize(freed)}"
                } else {
                    _cacheClearResult.value = "No cache to clear"
                }
                updateCacheSize()
            } catch (e: Exception) {
                _cacheClearResult.value = "Failed to clear cache: ${e.message}"
            }
        }
    }

    fun dismissCacheClearResult() {
        _cacheClearResult.value = null
    }

    private fun getAppVersion(): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "Unknown"
        } catch (_: Exception) {
            "Unknown"
        }
    }

    val updateState: StateFlow<UpdateState> = appUpdater.updateState

    fun checkForUpdates(isManual: Boolean = true) {
        viewModelScope.launch {
            _isManualUpdateCheck.value = isManual
            appUpdater.checkForUpdates(getAppVersion(), showNotification = !isManual)
        }
    }

    fun downloadAndInstallUpdate(url: String, version: String) {
        appUpdater.downloadAndInstallUpdate(url, version)
    }

    fun resetUpdateState() {
        _isManualUpdateCheck.value = false
        appUpdater.resetState()
    }

    // ─── Dynamic Changelog ────────────────────────────────────────────────────
    
    private val _changelogState = MutableStateFlow<ChangelogState>(ChangelogState.Idle)
    val changelogState: StateFlow<ChangelogState> = _changelogState.asStateFlow()

    fun fetchChangelog() {
        // If already fetched successfully, don't fetch again
        if (_changelogState.value is ChangelogState.Success) return
        
        _changelogState.value = ChangelogState.Loading
        viewModelScope.launch {
            try {
                val releases = appUpdater.fetchAllReleases()
                if (releases.isNotEmpty()) {
                    _changelogState.value = ChangelogState.Success(releases)
                } else {
                    _changelogState.value = ChangelogState.Error("No releases found.")
                }
            } catch (e: Exception) {
                _changelogState.value = ChangelogState.Error("Failed to load changelog: ${e.message}")
            }
        }
    }

    // ─── Tag Library Management ──────────────────────────────────────────────

    val availableTags: StateFlow<List<TagUiModel>> = combine(
        repository.observeAllTags(),
        repository.observeAllTagDefinitions()
    ) { docTags, defs ->
        val defMap = defs.associateBy { it.name }
        val allNames = (docTags.map { it.tag } + defs.map { it.name }).distinct().sorted()
        allNames.map { name ->
            val def = defMap[name]
            TagUiModel(
                name = name,
                colorHex = def?.colorHex ?: TagColorPresets.getColorHexForTag(name),
                emoji = def?.emoji
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val documents: StateFlow<List<RecentDocumentUiModel>> = combine(
        repository.observeRecentDocuments(),
        repository.observeAllTags(),
        repository.observeAllTagDefinitions()
    ) { docs, allTags, defs ->
        val tagsByDoc = allTags.groupBy({ it.documentUri }, { it.tag })
        val defMap = defs.associateBy { it.name }
        docs.map { doc ->
            val docTags = tagsByDoc[doc.uri] ?: emptyList()
            RecentDocumentUiModel(
                doc = doc,
                isAccessible = true,
                tags = docTags.map { tagName ->
                    val def = defMap[tagName]
                    TagUiModel(
                        name = tagName,
                        colorHex = def?.colorHex ?: TagColorPresets.getColorHexForTag(tagName),
                        emoji = def?.emoji
                    )
                }
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    fun upsertTagDefinition(name: String, colorHex: String, emoji: String?) {
        viewModelScope.launch {
            repository.upsertTagDefinition(name, colorHex, emoji)
        }
    }

    fun renameTagGlobally(oldName: String, newName: String, colorHex: String, emoji: String?) {
        viewModelScope.launch {
            repository.renameTagGlobally(oldName, newName, colorHex, emoji)
        }
    }

    fun deleteTagGlobally(name: String) {
        viewModelScope.launch {
            repository.deleteTagGlobally(name)
        }
    }
}

sealed class ChangelogState {
    object Idle : ChangelogState()
    object Loading : ChangelogState()
    data class Success(val releases: List<com.nexus.core.updater.GithubRelease>) : ChangelogState()
    data class Error(val message: String) : ChangelogState()
}
