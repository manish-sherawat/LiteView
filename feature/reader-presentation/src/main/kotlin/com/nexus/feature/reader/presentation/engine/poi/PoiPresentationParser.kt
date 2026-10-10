package com.nexus.feature.reader.presentation.engine.poi

import android.graphics.BitmapFactory
import android.graphics.RectF
import android.util.Log
import com.nexus.feature.reader.presentation.engine.PresentationDocument
import com.nexus.feature.reader.presentation.engine.PresentationSlideInfo
import com.nexus.feature.reader.presentation.engine.SlideElement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.hslf.usermodel.HSLFPictureShape
import org.apache.poi.hslf.usermodel.HSLFSlideShow
import org.apache.poi.hslf.usermodel.HSLFTable
import org.apache.poi.hslf.usermodel.HSLFTextShape
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xslf.usermodel.XSLFPictureShape
import org.apache.poi.xslf.usermodel.XSLFTable
import org.apache.poi.xslf.usermodel.XSLFTextShape
import java.io.File
import java.io.FileInputStream

object PoiPresentationParser {

    private const val TAG = "PoiPresentationParser"

    suspend fun parse(file: File): PresentationDocument? = withContext(Dispatchers.IO) {
        val lowerName = file.name.lowercase()
        return@withContext try {
            when {
                lowerName.endsWith(".pptx") -> parsePptx(file) ?: parsePpt(file)
                lowerName.endsWith(".ppt") -> parsePpt(file) ?: parsePptx(file)
                else -> parsePptx(file) ?: parsePpt(file)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse presentation ${file.absolutePath}", e)
            null
        }
    }

    private fun parsePptx(file: File): PresentationDocument? {
        return try {
            FileInputStream(file).use { fis ->
                val slideShow = XMLSlideShow(fis)
                val defaultBounds = RectF(0.05f, 0.1f, 0.95f, 0.9f)

                val parsedSlides = slideShow.slides.mapIndexed { index, slide ->
                    val slideTitle = slide.title ?: "Slide ${index + 1}"
                    val elements = mutableListOf<SlideElement>()

                    for (shape in slide.shapes) {
                        try {
                            when (shape) {
                                is XSLFTextShape -> {
                                    val text = shape.text?.trim()
                                    if (!text.isNullOrEmpty()) {
                                        val bullets = shape.textParagraphs
                                            .filter { it.isBullet }
                                            .map { it.text.trim() }
                                            .filter { it.isNotEmpty() }

                                        val firstRun = shape.textParagraphs.firstOrNull()?.textRuns?.firstOrNull()
                                        val fontSize = firstRun?.fontSize?.toFloat() ?: 16f
                                        val isBold = firstRun?.isBold ?: false

                                        elements.add(
                                            SlideElement.Text(
                                                bounds = defaultBounds,
                                                text = text,
                                                isTitle = (text == slide.title || fontSize >= 24f),
                                                bullets = bullets,
                                                fontSize = fontSize,
                                                isBold = isBold
                                            )
                                        )
                                    }
                                }

                                is XSLFPictureShape -> {
                                    val pictureData = shape.pictureData
                                    if (pictureData != null) {
                                        val bytes = pictureData.data
                                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                        if (bitmap != null) {
                                            elements.add(SlideElement.Image(bounds = defaultBounds, bitmap = bitmap))
                                        }
                                    }
                                }

                                is XSLFTable -> {
                                    val rows = mutableListOf<List<String>>()
                                    for (r in 0 until shape.numberOfRows) {
                                        val rowText = (0 until shape.numberOfColumns).map { c ->
                                            shape.getCell(r, c)?.text ?: ""
                                        }
                                        rows.add(rowText)
                                    }
                                    if (rows.isNotEmpty()) {
                                        elements.add(SlideElement.Table(bounds = defaultBounds, rows = rows))
                                    }
                                }
                            }
                        } catch (shapeError: Exception) {
                            Log.w(TAG, "Error parsing shape on slide $index", shapeError)
                        }
                    }

                    PresentationSlideInfo(
                        index = index,
                        name = slideTitle,
                        widthTwips = 28800L,
                        heightTwips = 16200L,
                        title = slideTitle,
                        notes = null,
                        elements = elements
                    )
                }

                object : PresentationDocument {
                    override val slideCount: Int = parsedSlides.size
                    override val slides: List<PresentationSlideInfo> = parsedSlides

                    override fun renderSlide(slideIndex: Int, width: Int, height: Int) = null

                    override fun close() {
                        try {
                            slideShow.close()
                        } catch (_: Exception) {}
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "parsePptx failed", e)
            null
        }
    }

    private fun parsePpt(file: File): PresentationDocument? {
        return try {
            FileInputStream(file).use { fis ->
                val slideShow = HSLFSlideShow(fis)
                val defaultBounds = RectF(0.05f, 0.1f, 0.95f, 0.9f)

                val parsedSlides = slideShow.slides.mapIndexed { index, slide ->
                    val slideTitle = slide.title ?: "Slide ${index + 1}"
                    val elements = mutableListOf<SlideElement>()

                    for (shape in slide.shapes) {
                        try {
                            when (shape) {
                                is HSLFTextShape -> {
                                    val text = shape.text?.trim()
                                    if (!text.isNullOrEmpty()) {
                                        elements.add(
                                            SlideElement.Text(
                                                bounds = defaultBounds,
                                                text = text,
                                                isTitle = (text == slide.title)
                                            )
                                        )
                                    }
                                }

                                is HSLFPictureShape -> {
                                    val pictureData = shape.pictureData
                                    if (pictureData != null) {
                                        val bytes = pictureData.rawData
                                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                        if (bitmap != null) {
                                            elements.add(SlideElement.Image(bounds = defaultBounds, bitmap = bitmap))
                                        }
                                    }
                                }

                                is HSLFTable -> {
                                    val rows = mutableListOf<List<String>>()
                                    for (r in 0 until shape.numberOfRows) {
                                        val rowText = (0 until shape.numberOfColumns).map { c ->
                                            shape.getCell(r, c)?.text ?: ""
                                        }
                                        rows.add(rowText)
                                    }
                                    if (rows.isNotEmpty()) {
                                        elements.add(SlideElement.Table(bounds = defaultBounds, rows = rows))
                                    }
                                }
                            }
                        } catch (shapeError: Exception) {
                            Log.w(TAG, "Error parsing HSLF shape on slide $index", shapeError)
                        }
                    }

                    PresentationSlideInfo(
                        index = index,
                        name = slideTitle,
                        widthTwips = 28800L,
                        heightTwips = 16200L,
                        title = slideTitle,
                        notes = null,
                        elements = elements
                    )
                }

                object : PresentationDocument {
                    override val slideCount: Int = parsedSlides.size
                    override val slides: List<PresentationSlideInfo> = parsedSlides

                    override fun renderSlide(slideIndex: Int, width: Int, height: Int) = null

                    override fun close() {
                        try {
                            slideShow.close()
                        } catch (_: Exception) {}
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "parsePpt failed", e)
            null
        }
    }
}
