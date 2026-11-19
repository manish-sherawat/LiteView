package com.nexus.feature.dashboard.tools.merge_pdf.components

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
import com.nexus.feature.dashboard.tools.merge_pdf.MergeStep

@Composable
fun MergeStepIndicator(
    currentStep: MergeStep,
    modifier: Modifier = Modifier
) {
    val steps = listOf(MergeStep.UPLOAD, MergeStep.ORDER, MergeStep.SAVE)

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
                    else -> NexusTheme.colors.divider.copy(alpha = 0.6f)
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

                Spacer(modifier = Modifier.width(8.dp))

                // Step Label
                NexusText(
                    text = step.label,
                    style = NexusTheme.typography.caption.copy(
                        fontSize = 13.sp,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold
                    ),
                    color = labelColor
                )
            }

            // Connecting bar between steps
            if (index < steps.lastIndex) {
                Spacer(modifier = Modifier.width(10.dp))
                val barColor by animateColorAsState(
                    targetValue = if (currentStep.stepNumber > step.stepNumber) {
                        NexusTheme.colors.textPrimary
                    } else {
                        NexusTheme.colors.divider.copy(alpha = 0.5f)
                    },
                    animationSpec = tween(220),
                    label = "barColor"
                )
                Box(
                    modifier = Modifier
                        .width(26.dp)
                        .height(1.5.dp)
                        .clip(CircleShape)
                        .background(barColor)
                )
                Spacer(modifier = Modifier.width(10.dp))
            }
        }
    }
}
