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
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.nexus.core.updater.AppUpdater
import com.nexus.core.updater.UpdateState
// ─── Settings UI State ────────────────────────────────────────────────────────

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val defaultFontSize: Float = 14f,
    val appVersion: String = "1.0.0",
    val cacheSizeText: String = "0 B",
    val cacheClearSuccess: Boolean? = null,   // null = idle, true/false = result
    val keepScreenAwake: Boolean = false,
    val rememberReadingPosition: Boolean = true,
    val startupToPicker: Boolean = false,
    val hapticFeedbackEnabled: Boolean = true
)

// ─── Settings ViewModel ───────────────────────────────────────────────────────

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefsRepository: UserPreferencesRepository,
    private val recentDocumentDao: com.nexus.feature.dashboard.data.RecentDocumentDao,
    private val appUpdater: AppUpdater,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _cacheClearResult = MutableStateFlow<Boolean?>(null)
    private val _cacheSizeText = MutableStateFlow("Calculating...")

    init {
        updateCacheSize()
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        prefsRepository.themeMode,
        prefsRepository.defaultFontSize,
        prefsRepository.keepScreenAwake,
        prefsRepository.rememberReadingPosition,
        prefsRepository.startupToPicker,
        prefsRepository.hapticFeedbackEnabled,
        _cacheSizeText,
        _cacheClearResult
    ) { args: Array<Any?> ->
        SettingsUiState(
            themeMode = args[0] as ThemeMode,
            defaultFontSize = args[1] as Float,
            keepScreenAwake = args[2] as Boolean,
            rememberReadingPosition = args[3] as Boolean,
            startupToPicker = args[4] as Boolean,
            hapticFeedbackEnabled = args[5] as Boolean,
            appVersion = getAppVersion(),
            cacheSizeText = args[6] as String,
            cacheClearSuccess = args[7] as Boolean?
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
    fun setStartupToPicker(value: Boolean) = viewModelScope.launch { prefsRepository.setStartupToPicker(value) }
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
                // Optional: Show success
            } catch (e: Exception) {
                e.printStackTrace()
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
                // Clear app cache directory contents safely
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
                }
                _cacheClearResult.value = true
                updateCacheSize()
            } catch (_: Exception) {
                _cacheClearResult.value = false
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

    fun checkForUpdates() {
        viewModelScope.launch {
            appUpdater.checkForUpdates(getAppVersion())
        }
    }

    fun downloadAndInstallUpdate(url: String, version: String) {
        appUpdater.downloadAndInstallUpdate(url, version)
    }

    fun resetUpdateState() {
        appUpdater.resetState()
    }
}
