package com.nexus.core.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.nexus.core.R

/**
 * Enum defining the empty state types for different sections across the app.
 */
enum class NexusEmptyStateType(
    @DrawableRes val resId: Int
) {
    FAVORITES(R.drawable.img_empty_fav),
    RECENT(R.drawable.img_empty_recent),
    STORAGE(R.drawable.img_empty_storage),
    UNSUPPORTED(R.drawable.img_empty_unsupported),
    NOT_FOUND(R.drawable.img_empty_not_found),
    EMPTY(R.drawable.no_documents);

    @get:DrawableRes
    val staticResId: Int get() = resId
}

/**
 * Renders an empty state illustration.
 */
@Composable
fun NexusEmptyStateImage(
    type: NexusEmptyStateType,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Fit,
    alignment: Alignment = Alignment.Center
) {
    Image(
        painter = painterResource(id = type.resId),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        alignment = alignment
    )
}

