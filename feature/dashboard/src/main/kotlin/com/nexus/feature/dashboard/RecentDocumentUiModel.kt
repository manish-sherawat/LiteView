package com.nexus.feature.dashboard

import com.nexus.feature.dashboard.data.RecentDocument

data class RecentDocumentUiModel(
    val doc: RecentDocument,
    val isAccessible: Boolean = true
)
