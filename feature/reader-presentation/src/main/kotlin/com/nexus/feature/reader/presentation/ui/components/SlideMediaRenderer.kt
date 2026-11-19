package com.nexus.feature.reader.presentation.ui.components

import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusSurface
import com.nexus.core.ui.NexusText
import com.nexus.feature.reader.presentation.engine.SlideMediaType
import java.io.File

/**
 * In-place media player overlay for embedded PowerPoint videos and audio clips.
 */
@Composable
fun SlideMediaRenderer(
    mediaPath: String,
    mediaType: SlideMediaType,
    title: String? = null,
    modifier: Modifier = Modifier
) {
    var isPlaying by remember { mutableStateOf(false) }
    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }
    val mediaFile = remember(mediaPath) { File(mediaPath) }

    val cornerShape = RoundedCornerShape(10.dp)

    NexusSurface(
        shape = cornerShape,
        color = Color.Black.copy(alpha = 0.85f),
        borderWidth = 1.dp,
        borderColor = NexusTheme.colors.divider.copy(alpha = 0.5f),
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(cornerShape)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (mediaType == SlideMediaType.VIDEO && mediaFile.exists()) {
                AndroidView(
                    factory = { ctx ->
                        VideoView(ctx).apply {
                            setVideoPath(mediaFile.absolutePath)
                            setOnPreparedListener { mp ->
                                mp.isLooping = true
                            }
                            setOnCompletionListener {
                                isPlaying = false
                            }
                            videoViewRef = this
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Audio badge / placeholder
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (mediaType == SlideMediaType.AUDIO) Icons.Default.Audiotrack else Icons.Default.Videocam,
                        contentDescription = "Media Clip",
                        tint = NexusTheme.colors.primary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    NexusText(
                        text = title ?: if (mediaType == SlideMediaType.AUDIO) "Audio Clip" else "Video Media",
                        style = NexusTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White
                    )
                }
            }

            // Play / Pause overlay button
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .border(1.5.dp, NexusTheme.colors.primary, CircleShape)
                    .clickable {
                        val vv = videoViewRef
                        if (vv != null) {
                            if (vv.isPlaying) {
                                vv.pause()
                                isPlaying = false
                            } else {
                                vv.start()
                                isPlaying = true
                            }
                        } else {
                            isPlaying = !isPlaying
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }

            // Top status pill
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (mediaType == SlideMediaType.AUDIO) Icons.Default.Audiotrack else Icons.Default.Videocam,
                        contentDescription = null,
                        tint = NexusTheme.colors.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    NexusText(
                        text = if (mediaType == SlideMediaType.AUDIO) "AUDIO" else "VIDEO",
                        style = NexusTheme.typography.caption.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color.White
                    )
                }
            }
        }
    }

    DisposableEffect(mediaPath) {
        onDispose {
            videoViewRef?.stopPlayback()
            videoViewRef = null
        }
    }
}
