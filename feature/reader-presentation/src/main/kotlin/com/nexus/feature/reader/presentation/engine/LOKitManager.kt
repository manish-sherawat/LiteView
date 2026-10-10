package com.nexus.feature.reader.presentation.engine

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LOKitManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val tag = "LOKitManager"
    private var lokHandle: Long = 0
    private var isInitialized = false

    /**
     * Initializes the LibreOffice runtime.
     * Ensures any necessary bootstrap files (program, services.rdb) are unpacked.
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        if (isInitialized && lokHandle != 0L) return@withContext true
        if (!LOKitBridge.isNativeLoaded()) {
            Log.w(tag, "Native library nexus_presentation_engine is not loaded")
            return@withContext false
        }

        try {
            val installDir = File(context.filesDir, "libreoffice")
            if (!installDir.exists()) {
                installDir.mkdirs()
            }

            // Copy assets if packaged in apk
            unpackAssetsIfNeeded("libreoffice", installDir)

            val userProfileDir = File(context.cacheDir, "lo_profile")
            if (!userProfileDir.exists()) {
                userProfileDir.mkdirs()
            }

            val userProfileUrl = "file://${userProfileDir.absolutePath}"
            val handle = LOKitBridge.nativeInit(installDir.absolutePath, userProfileUrl)

            if (handle != 0L) {
                lokHandle = handle
                isInitialized = true
                Log.i(tag, "LibreOfficeKit initialized successfully: handle=$handle")
                true
            } else {
                Log.w(tag, "nativeInit returned 0 (LibreOffice core binaries may not be present in jniLibs)")
                false
            }
        } catch (e: Exception) {
            Log.e(tag, "Error during LOKit initialization", e)
            false
        }
    }

    suspend fun openDocument(file: File): PresentationDocument? = withContext(Dispatchers.IO) {
        if (!initialize()) {
            Log.w(tag, "Cannot open document: LibreOfficeKit is not initialized")
            return@withContext null
        }

        try {
            val docHandle = LOKitBridge.nativeDocumentLoad(lokHandle, file.absolutePath, null)
            if (docHandle == 0L) {
                Log.e(tag, "nativeDocumentLoad failed for ${file.absolutePath}")
                return@withContext null
            }

            val parts = LOKitBridge.nativeGetParts(docHandle)
            val docSize = LOKitBridge.nativeGetDocumentSize(docHandle)
            val widthTwips = docSize?.getOrNull(0) ?: 28800L
            val heightTwips = docSize?.getOrNull(1) ?: 16200L

            val slideList = (0 until parts).map { index ->
                val name = LOKitBridge.nativeGetPartName(docHandle, index) ?: "Slide ${index + 1}"
                PresentationSlideInfo(
                    index = index,
                    name = name,
                    widthTwips = widthTwips,
                    heightTwips = heightTwips
                )
            }

            object : PresentationDocument {
                override val slideCount: Int = parts
                override val slides: List<PresentationSlideInfo> = slideList

                override fun renderSlide(slideIndex: Int, width: Int, height: Int): Bitmap? {
                    if (slideIndex < 0 || slideIndex >= slideCount) return null
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val success = LOKitBridge.nativeRenderSlideToBitmap(
                        docHandle,
                        slideIndex,
                        bitmap,
                        width,
                        height
                    )
                    return if (success) bitmap else {
                        bitmap.recycle()
                        null
                    }
                }

                override fun close() {
                    LOKitBridge.nativeCloseDocument(docHandle)
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Exception opening document ${file.absolutePath}", e)
            null
        }
    }

    private fun unpackAssetsIfNeeded(assetPath: String, targetDir: File) {
        try {
            val assetList = context.assets.list(assetPath) ?: return
            for (asset in assetList) {
                val subAssetPath = "$assetPath/$asset"
                val subFiles = context.assets.list(subAssetPath)
                val targetFile = File(targetDir, asset)
                if (!subFiles.isNullOrEmpty()) {
                    targetFile.mkdirs()
                    unpackAssetsIfNeeded(subAssetPath, targetFile)
                } else if (!targetFile.exists()) {
                    context.assets.open(subAssetPath).use { input ->
                        FileOutputStream(targetFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        } catch (_: Exception) {}
    }

    fun shutdown() {
        if (lokHandle != 0L) {
            LOKitBridge.nativeDestroy(lokHandle)
            lokHandle = 0L
            isInitialized = false
        }
    }
}
