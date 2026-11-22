package com.nexus.feature.reader.office.word.engine

import android.net.Uri
import android.util.Log
import com.nexus.core.preferences.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates document parsing across engines (LibreOfficeKit vs Apache POI).
 *
 * Responsibilities:
 * 1. Checks feature flag preference [UserPreferencesRepository.useLibreOfficeEngine].
 * 2. Attempts [LOKitWordEngine] for desktop-grade fidelity.
 * 3. Fallback resilience: If LOKit fails (e.g. init failure, unsupported binary format, native error),
 *    it transparently falls back to [PoiWordEngine] to ensure the user never sees a hard failure.
 */
@Singleton
class WordEngineCoordinator @Inject constructor(
    private val lokitEngine: LOKitWordEngine,
    private val poiEngine: PoiWordEngine,
    private val userPreferencesRepository: UserPreferencesRepository
) {

    companion object {
        private const val TAG = "WordEngineCoordinator"
    }

    suspend fun loadDocument(
        uri: Uri,
        docType: String,
        password: String? = null,
        forceEngine: String? = null
    ): Pair<WordEngineResult, String> {
        val useLokit = if (forceEngine != null) {
            forceEngine.equals("LOKIT", ignoreCase = true)
        } else {
            try {
                userPreferencesRepository.useLibreOfficeEngine.first()
            } catch (_: Exception) {
                false
            }
        }

        if (useLokit) {
            Log.i(TAG, "Attempting load with primary engine: ${lokitEngine.engineName}")
            val lokitResult = lokitEngine.loadDocument(uri, docType, password)

            when (lokitResult) {
                is WordEngineResult.ConvertedPdf,
                is WordEngineResult.PaginatedBitmaps,
                is WordEngineResult.PasswordRequired -> {
                    return Pair(lokitResult, lokitEngine.engineName)
                }
                is WordEngineResult.Error -> {
                    Log.w(TAG, "LOKit engine reported error: ${lokitResult.message}. Falling back to POI legacy engine.")
                    val poiResult = poiEngine.loadDocument(uri, docType, password)
                    return Pair(poiResult, poiEngine.engineName)
                }
                is WordEngineResult.Html -> {
                    return Pair(lokitResult, lokitEngine.engineName)
                }
            }
        } else {
            Log.i(TAG, "Feature flag indicates legacy engine: ${poiEngine.engineName}")
            val poiResult = poiEngine.loadDocument(uri, docType, password)
            return Pair(poiResult, poiEngine.engineName)
        }
    }
}
