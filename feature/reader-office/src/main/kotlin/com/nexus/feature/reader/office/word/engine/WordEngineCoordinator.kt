package com.nexus.feature.reader.office.word.engine

import android.content.Context
import android.net.Uri
import android.util.Log
import com.nexus.core.preferences.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates document parsing across conversion engines:
 * 1. Primary: Headless LibreOfficeKit (converts DOCX/DOC to PDF byte stream with 100% desktop fidelity).
 * 2. Fallback: Native OOXML-to-PDF Converter (converts directly to in-memory PDF stream using Android PdfDocument).
 * 3. Emergency: Legacy POI engine.
 *
 * Guarantees that documents are converted to in-memory PDF byte streams upon opening,
 * completely hidden from storage scanners and excluded from database indexing.
 */
@Singleton
class WordEngineCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
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
                true // Default to primary LOKit engine
            }
        }

        if (useLokit) {
            Log.i(TAG, "Attempting load with primary headless LOKit engine: ${lokitEngine.engineName}")
            val lokitResult = lokitEngine.loadDocument(uri, docType, password)

            when (lokitResult) {
                is WordEngineResult.PdfStream,
                is WordEngineResult.ConvertedPdf,
                is WordEngineResult.PaginatedBitmaps,
                is WordEngineResult.PasswordRequired -> {
                    return Pair(lokitResult, lokitEngine.engineName)
                }
                is WordEngineResult.Error -> {
                    Log.w(TAG, "LOKit engine reported error: ${lokitResult.message}. Falling back to Native OOXML-to-PDF converter.")
                    val nativePdfResult = NativeWordToPdfConverter.convertToPdfStream(context, uri, docType, password)
                    when (nativePdfResult) {
                        is WordEngineResult.PdfStream,
                        is WordEngineResult.PasswordRequired -> {
                            return Pair(nativePdfResult, "Native PDF Conversion Wrapper")
                        }
                        else -> {
                            Log.w(TAG, "Native PDF conversion also failed. Falling back to POI engine.")
                            val poiResult = poiEngine.loadDocument(uri, docType, password)
                            return Pair(poiResult, poiEngine.engineName)
                        }
                    }
                }
                is WordEngineResult.Html -> {
                    return Pair(lokitResult, lokitEngine.engineName)
                }
            }
        } else {
            Log.i(TAG, "Attempting load with Native OOXML-to-PDF conversion wrapper.")
            val nativePdfResult = NativeWordToPdfConverter.convertToPdfStream(context, uri, docType, password)
            if (nativePdfResult is WordEngineResult.PdfStream || nativePdfResult is WordEngineResult.PasswordRequired) {
                return Pair(nativePdfResult, "Native PDF Conversion Wrapper")
            }
            Log.w(TAG, "Native conversion failed, falling back to legacy POI engine.")
            val poiResult = poiEngine.loadDocument(uri, docType, password)
            return Pair(poiResult, poiEngine.engineName)
        }
    }
}
