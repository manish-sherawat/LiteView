package com.nexus.feature.reader.presentation.model

import androidx.compose.ui.geometry.Offset
import com.nexus.feature.reader.presentation.engine.PresentationSlideInfo

data class SlideSearchMatch(
    val slideIndex: Int,
    val matchedText: String,
    val snippet: String
)

enum class InkingTool {
    NONE,
    LASER,
    PEN,
    HIGHLIGHTER,
    ERASER
}

data class InkPoint(
    val x: Float, // Normalized 0.0f..1.0f
    val y: Float
)

data class InkStroke(
    val points: List<InkPoint>,
    val colorArgb: Int,
    val strokeWidth: Float,
    val isHighlighter: Boolean = false
)

sealed interface PresentationUiState {
    data object Loading : PresentationUiState

    data class Success(
        val fileName: String,
        val slideCount: Int,
        val currentSlideIndex: Int = 0,
        val slides: List<PresentationSlideInfo>,
        val isFullscreen: Boolean = false,
        val showThumbnails: Boolean = false,
        val isEngineReady: Boolean = true,
        // Search
        val isSearchActive: Boolean = false,
        val searchQuery: String = "",
        val searchMatches: List<SlideSearchMatch> = emptyList(),
        val currentMatchIndex: Int = 0,
        // Speaker notes
        val showSpeakerNotes: Boolean = false,
        val speakerNotesFontSize: Float = 16f,
        // Lightbox grid sorter
        val isGridSorterVisible: Boolean = false,
        // Inking & laser pointer
        val activeInkingTool: InkingTool = InkingTool.NONE,
        val inkingColorArgb: Int = 0xFFFF3333.toInt(), // Default laser red
        val inkingStrokeWidth: Float = 4f,
        val slideAnnotations: Map<Int, List<InkStroke>> = emptyMap(),
        val laserPosition: Offset? = null,
        // Auto-play slideshow
        val isSlideshowActive: Boolean = false,
        val slideshowIntervalSeconds: Int = 5,
        val isSlideshowLoop: Boolean = true,
        // Landscape orientation lock
        val isLandscapeLocked: Boolean = false
    ) : PresentationUiState

    data class EngineNotReady(
        val fileName: String,
        val message: String
    ) : PresentationUiState

    data class Error(
        val message: String
    ) : PresentationUiState
}
