package com.nexus.feature.dashboard

import com.nexus.feature.dashboard.data.TrashedDocument
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

// ─── Trashed Document UI Model ────────────────────────────────────────────────
// Visual presentation model for an item in the Recycle Bin.

data class TrashedDocumentUiModel(
    val doc: TrashedDocument,
    val formattedSize: String,
    val formattedTrashedDate: String,
    val daysRemaining: Int,
    val retentionLabel: String,
    val isExpiringSoon: Boolean,
    val tags: List<String>
) {
    companion object {
        private val dateFormat = SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault())

        fun fromEntity(doc: TrashedDocument, retentionDays: Int): TrashedDocumentUiModel {
            val formattedSize = formatFileSize(doc.fileSizeBytes)
            val formattedDate = dateFormat.format(Date(doc.trashedAt))

            val (daysRemaining, retentionLabel, isExpiringSoon) = if (retentionDays <= 0) {
                Triple(-1, "Kept indefinitely", false)
            } else {
                val elapsedMillis = System.currentTimeMillis() - doc.trashedAt
                val retentionMillis = TimeUnit.DAYS.toMillis(retentionDays.toLong())
                val remainingMillis = (retentionMillis - elapsedMillis).coerceAtLeast(0L)
                val remainingDays = TimeUnit.MILLISECONDS.toDays(remainingMillis).toInt()

                val label = when {
                    remainingDays > 1 -> "$remainingDays days left"
                    remainingDays == 1 -> "1 day left"
                    else -> "Expires today"
                }
                Triple(remainingDays, label, remainingDays <= 3)
            }

            val tags = if (!doc.attachedTagsJson.isNullOrEmpty()) {
                try {
                    val jsonArray = JSONArray(doc.attachedTagsJson)
                    (0 until jsonArray.length()).map { jsonArray.getString(it) }
                } catch (_: Exception) {
                    emptyList()
                }
            } else emptyList()

            return TrashedDocumentUiModel(
                doc = doc,
                formattedSize = formattedSize,
                formattedTrashedDate = formattedDate,
                daysRemaining = daysRemaining,
                retentionLabel = retentionLabel,
                isExpiringSoon = isExpiringSoon,
                tags = tags
            )
        }
    }
}
