package com.nexus.feature.dashboard

import androidx.compose.runtime.Immutable
import com.nexus.feature.dashboard.data.RecentDocument

@Immutable
data class RecentDocumentUiModel(
    val doc: RecentDocument,
    val isAccessible: Boolean = true,
    val tags: List<TagUiModel> = emptyList()
)
