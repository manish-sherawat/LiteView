package com.nexus.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nexus.core.ui.animations.shimmerEffect
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.components.NexusCard

@Composable
fun FileListItemShimmer(modifier: Modifier = Modifier) {
    val shimmerBg = NexusTheme.colors.surface
    
    NexusCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .shadow(
                elevation = 2.dp, 
                shape = NexusTheme.shapes.large,
                ambientColor = Color.Black.copy(alpha = 0.05f),
                spotColor = Color.Black.copy(alpha = 0.08f)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(NexusTheme.shapes.small)
                    .background(shimmerBg)
                    .shimmerEffect()
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(14.dp)
                        .clip(NexusTheme.shapes.small)
                        .background(shimmerBg)
                        .shimmerEffect()
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.35f)
                        .height(10.dp)
                        .clip(NexusTheme.shapes.small)
                        .background(shimmerBg)
                        .shimmerEffect()
                )
            }
        }
    }
}

@Composable
fun FileGridItemShimmer(modifier: Modifier = Modifier) {
    val shimmerBg = NexusTheme.colors.surface

    NexusCard(
        modifier = modifier
            .aspectRatio(0.7f)
            .padding(4.dp)
            .shadow(
                elevation = 2.dp, 
                shape = NexusTheme.shapes.large,
                ambientColor = Color.Black.copy(alpha = 0.05f),
                spotColor = Color.Black.copy(alpha = 0.08f)
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .clip(NexusTheme.shapes.small)
                    .background(shimmerBg)
                    .shimmerEffect()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(12.dp)
                        .clip(NexusTheme.shapes.small)
                        .background(shimmerBg)
                        .shimmerEffect()
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(12.dp)
                        .clip(NexusTheme.shapes.small)
                        .background(shimmerBg)
                        .shimmerEffect()
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(10.dp)
                    .clip(NexusTheme.shapes.pill)
                    .background(shimmerBg)
                    .shimmerEffect()
            )
        }
    }
}
