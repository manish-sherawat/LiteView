package com.nexus.feature.dashboard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

object PdfThumbnailCache {
    private val renderSemaphore = Semaphore(1)
    
    // Cache up to 20 thumbnails
    private val memoryCache = object : LruCache<String, ImageBitmap>(20) {
        override fun sizeOf(key: String, value: ImageBitmap): Int {
            return 1
        }
    }

    suspend fun getThumbnail(context: Context, uriString: String): ImageBitmap? {
        val cached = memoryCache.get(uriString)
        if (cached != null) return cached

        return withContext(Dispatchers.IO) {
            renderSemaphore.withPermit {
                try {
                    val uri = Uri.parse(uriString)
                    val fd = context.contentResolver.openFileDescriptor(uri, "r") ?: return@withPermit null
                    val renderer = PdfRenderer(fd)
                    if (renderer.pageCount > 0) {
                        val page = renderer.openPage(0)
                        // Calculate thumbnail size (e.g. max 400px width/height)
                        val scale = Math.min(400f / page.width, 400f / page.height)
                        val width = (page.width * scale).toInt()
                        val height = (page.height * scale).toInt()

                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        // Fill white background before rendering
                        bitmap.eraseColor(android.graphics.Color.WHITE)
                        
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        page.close()
                        renderer.close()
                        fd.close()

                        val imageBitmap = bitmap.asImageBitmap()
                        memoryCache.put(uriString, imageBitmap)
                        return@withPermit imageBitmap
                    }
                    renderer.close()
                    fd.close()
                    null
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }
        }
    }
}
