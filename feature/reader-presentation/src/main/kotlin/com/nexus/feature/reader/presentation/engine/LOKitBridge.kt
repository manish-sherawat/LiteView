package com.nexus.feature.reader.presentation.engine

import android.graphics.Bitmap
import android.util.Log

/**
 * Low-level JNI bridge communicating with the native LibreOfficeKit C API.
 * Uses dynamic symbol binding to safely initialize if binaries are loaded.
 */
object LOKitBridge {

    private const val TAG = "LOKitBridge"

    private var isNativeLibLoaded = false

    init {
        try {
            System.loadLibrary("nexus_presentation_engine")
            isNativeLibLoaded = true
            Log.i(TAG, "nexus_presentation_engine library loaded successfully")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to load nexus_presentation_engine: ${t.message}")
            isNativeLibLoaded = false
        }
    }

    fun isNativeLoaded(): Boolean = isNativeLibLoaded

    external fun nativeIsEngineAvailable(): Boolean

    external fun nativeInit(
        installPath: String,
        userProfileUrl: String
    ): Long

    external fun nativeDocumentLoad(
        lokHandle: Long,
        docPath: String,
        options: String?
    ): Long

    external fun nativeGetParts(docHandle: Long): Int

    external fun nativeGetPartName(docHandle: Long, partIndex: Int): String?

    external fun nativeGetDocumentSize(docHandle: Long): LongArray?

    external fun nativeRenderSlideToBitmap(
        docHandle: Long,
        partIndex: Int,
        bitmap: Bitmap,
        width: Int,
        height: Int
    ): Boolean

    external fun nativeCloseDocument(docHandle: Long)

    external fun nativeDestroy(lokHandle: Long)
}
