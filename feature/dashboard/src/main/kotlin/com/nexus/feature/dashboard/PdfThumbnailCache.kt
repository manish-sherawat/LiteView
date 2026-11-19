package com.nexus.feature.dashboard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.LruCache
import androidx.compose.ui.graphics.Color
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

    private val colorCache = object : LruCache<String, Color>(40) {
        override fun sizeOf(key: String, value: Color): Int = 1
    }

    private var isRegistered = false

    fun getCachedDominantColor(uriString: String): Color? = colorCache.get(uriString)

    private fun extractDominantColor(bitmap: Bitmap): Color? {
        var bestColor: Color? = null
        var maxSaturation = 0.22f
        val hsv = FloatArray(3)
        val stepX = (bitmap.width / 12).coerceAtLeast(1)
        val stepY = (bitmap.height / 12).coerceAtLeast(1)
        val sampleHeight = (bitmap.height * 0.75f).toInt()
        for (y in 0 until sampleHeight step stepY) {
            for (x in 0 until bitmap.width step stepX) {
                val pixel = bitmap.getPixel(x, y)
                android.graphics.Color.colorToHSV(pixel, hsv)
                val saturation = hsv[1]
                val value = hsv[2]
                if (saturation > maxSaturation && value in 0.25f..0.92f) {
                    maxSaturation = saturation
                    bestColor = Color(pixel)
                }
            }
        }
        return bestColor
    }

    suspend fun getThumbnail(context: Context, uriString: String): ImageBitmap? {
        if (!isRegistered) {
            isRegistered = true
            context.applicationContext.registerComponentCallbacks(object : android.content.ComponentCallbacks2 {
                override fun onTrimMemory(level: Int) {
                    if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
                        memoryCache.evictAll()
                        colorCache.evictAll()
                    }
                }
                override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {}
                override fun onLowMemory() {
                    memoryCache.evictAll()
                    colorCache.evictAll()
                }
            })
        }

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

                        val dominantColor = extractDominantColor(bitmap)
                        if (dominantColor != null) {
                            colorCache.put(uriString, dominantColor)
                        }

                        val imageBitmap = bitmap.asImageBitmap()
                        memoryCache.put(uriString, imageBitmap)
                        return@withPermit imageBitmap
                    }
                    renderer.close()
                    fd.close()
                    null
                } catch (e: Exception) {
                    android.util.Log.e("PdfThumbnailCache", "Failed to generate thumbnail for $uriString", e)
                    null
                }
            }
        }
    }

    suspend fun getThumbnailAndColor(context: Context, uriString: String): Pair<ImageBitmap?, Color?> {
        val bitmap = getThumbnail(context, uriString)
        val color = colorCache.get(uriString)
        return Pair(bitmap, color)
    }
}
