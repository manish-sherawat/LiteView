package com.nexus.feature.reader.presentation.model

import com.nexus.feature.reader.presentation.engine.PresentationSlideInfo

sealed interface PresentationUiState {
    data object Loading : PresentationUiState

    data class Success(
        val fileName: String,
        val slideCount: Int,
        val currentSlideIndex: Int = 0,
        val slides: List<PresentationSlideInfo>,
        val isFullscreen: Boolean = false,
        val showThumbnails: Boolean = false,
        val isEngineReady: Boolean = true
    ) : PresentationUiState

    data class EngineNotReady(
        val fileName: String,
        val message: String
    ) : PresentationUiState

    data class Error(
        val message: String
    ) : PresentationUiState
}
