package com.nexus.feature.reader.office.word.lokit

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.libreoffice.kit.LibreOfficeKit
import org.libreoffice.kit.Office
import java.io.File
import java.util.concurrent.Executors

/**
 * Thread-safe singleton managing the LibreOfficeKit (LOK) runtime lifecycle.
 *
 * Constraints & Guarantees:
 * 1. Process-lifetime singleton: LOKit native initialization can only happen once per process.
 * 2. Strict thread serialization: LOKit is NOT thread-safe. All calls to the native LOK runtime
 *    must execute on [lokitDispatcher] (a dedicated single-threaded executor).
 * 3. Proper sandboxing: Profile/UserInstallation directory is placed in isolated app private cache.
 */
object LOKitRuntimeManager {

    private const val TAG = "LOKitRuntimeManager"
    private const val RUNTIME_VERSION = "nexus_lok_v1"

    // Dedicated single-threaded executor: serializes all LOKit calls to avoid SIGSEGV
    val lokitDispatcher: CoroutineDispatcher = Executors.newSingleThreadExecutor { r ->
        Thread(r, "LOKit-Worker-Thread").apply {
            priority = Thread.NORM_PRIORITY
            isDaemon = true
        }
    }.asCoroutineDispatcher()

    private val mutex = Mutex()

    @Volatile
    private var isNativeInitialized = false

    @Volatile
    private var officeInstance: Office? = null

    /**
     * Prepares configuration files (`sofficerc`) and directories required by LibreOffice core.
     */
    private fun setupRuntimeDirectories(context: Context): Boolean {
        return try {
            val dataDir = context.applicationInfo.dataDir
            val cacheDir = context.cacheDir.absolutePath

            val programDir = File(dataDir, "program").apply { mkdirs() }
            val userDir = File(cacheDir, "lo_user").apply { mkdirs() }

            val sofficercFile = File(programDir, "sofficerc")
            sofficercFile.writeText(
                """
                [Bootstrap]
                Logo=1
                NativeProgress=1
                URE_BOOTSTRAP=file:///assets/program/fundamentalrc
                UserInstallation=file://$cacheDir/lo_user
                """.trimIndent() + "\n"
            )

            val versionMarker = File(dataDir, ".lokit_version")
            versionMarker.writeText(RUNTIME_VERSION)

            Log.i(TAG, "LibreOffice runtime directories initialized. UserInstallation=$userDir")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set up LibreOffice runtime directories", e)
            false
        }
    }

    /**
     * Initializes LibreOfficeKit and returns the active [Office] instance.
     * Safe to call multiple times; performs initialization exactly once.
     */
    suspend fun getOffice(context: Context): Office? = withContext(lokitDispatcher) {
        mutex.withLock {
            officeInstance?.let { return@withLock it }

            try {
                if (!isNativeInitialized) {
                    if (!setupRuntimeDirectories(context)) {
                        Log.e(TAG, "Runtime directory setup failed, aborting LOKit init")
                        return@withLock null
                    }

                    LibreOfficeKit.putenv("SAL_LOG=+WARN+INFO")
                    LibreOfficeKit.putenv("SAL_LOK_OPTIONS=compact_fonts")
                    LibreOfficeKit.init(context)
                    isNativeInitialized = true
                    Log.i(TAG, "LibreOfficeKit native library bootstrap completed successfully")
                }

                val handle = LibreOfficeKit.getLibreOfficeKitHandle()
                if (handle == null) {
                    Log.e(TAG, "LibreOfficeKit.getLibreOfficeKitHandle() returned null")
                    return@withLock null
                }

                val office = Office(handle)
                officeInstance = office
                Log.i(TAG, "Office instance created and bound to native handle")
                office
            } catch (t: Throwable) {
                Log.e(TAG, "Fatal error initializing LibreOfficeKit", t)
                null
            }
        }
    }

    /**
     * Closes the Office instance and resets the handle.
     */
    suspend fun shutdown() = withContext(lokitDispatcher) {
        mutex.withLock {
            try {
                officeInstance?.destroy()
                officeInstance = null
                Log.i(TAG, "Office instance destroyed")
            } catch (t: Throwable) {
                Log.e(TAG, "Error destroying Office instance", t)
            }
        }
    }
}
