package com.nexus.feature.dashboard.tools.image_to_pdf.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusText
import com.nexus.feature.dashboard.tools.image_to_pdf.ImageToPdfStep

@Composable
fun ImageToPdfStepIndicator(
    currentStep: ImageToPdfStep,
    modifier: Modifier = Modifier
) {
    val steps = listOf(ImageToPdfStep.UPLOAD, ImageToPdfStep.ARRANGE, ImageToPdfStep.SAVE)

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        steps.forEachIndexed { index, step ->
            val isCurrent = step == currentStep
            val isCompleted = step.stepNumber < currentStep.stepNumber

            val circleBg by animateColorAsState(
                targetValue = when {
                    isCurrent -> NexusTheme.colors.textPrimary
                    isCompleted -> NexusTheme.colors.surfaceVariant
                    else -> Color.Transparent
                },
                animationSpec = tween(220),
                label = "stepBg"
            )

            val circleBorder by animateColorAsState(
                targetValue = when {
                    isCurrent -> NexusTheme.colors.textPrimary
                    isCompleted -> NexusTheme.colors.textPrimary
                    else -> NexusTheme.colors.divider.copy(alpha = 0.5f)
                },
                animationSpec = tween(220),
                label = "stepBorder"
            )

            val circleContentColor by animateColorAsState(
                targetValue = when {
                    isCurrent -> NexusTheme.colors.background
                    isCompleted -> NexusTheme.colors.textPrimary
                    else -> NexusTheme.colors.textSecondary
                },
                animationSpec = tween(220),
                label = "stepContent"
            )

            val labelColor by animateColorAsState(
                targetValue = if (isCurrent || isCompleted) NexusTheme.colors.textPrimary else NexusTheme.colors.textSecondary,
                animationSpec = tween(220),
                label = "stepLabel"
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Circle Badge
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(circleBg)
                        .border(1.5.dp, circleBorder, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (isCompleted) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = circleContentColor
                        )
                    } else {
                        NexusText(
                            text = "${step.stepNumber}",
                            style = NexusTheme.typography.caption.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = circleContentColor
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Step Label
                NexusText(
                    text = step.label,
                    style = NexusTheme.typography.caption.copy(
                        fontSize = 12.5.sp,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = labelColor
                )
            }

            // Connecting bar between steps
            if (index < steps.size - 1) {
                val lineBg by animateColorAsState(
                    targetValue = if (currentStep.stepNumber > step.stepNumber) {
                        NexusTheme.colors.textPrimary.copy(alpha = 0.8f)
                    } else {
                        NexusTheme.colors.divider.copy(alpha = 0.45f)
                    },
                    animationSpec = tween(220),
                    label = "stepLine"
                )

                Box(
                    modifier = Modifier
                        .padding(horizontal = 10.dp)
                        .width(28.dp)
                        .height(1.5.dp)
                        .background(lineBg, shape = CircleShape)
                )
            }
        }
    }
}
