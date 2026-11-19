package com.nexus.feature.dashboard.tools.extract_pages.components

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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusText
import com.nexus.feature.dashboard.tools.extract_pages.ExtractStep

@Composable
fun ExtractStepIndicator(
    currentStep: ExtractStep,
    modifier: Modifier = Modifier
) {
    val steps = listOf(ExtractStep.UPLOAD, ExtractStep.SELECT, ExtractStep.EXPORT)

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        // ── Main Header Title ────────────────────────────────────────────────
        val titleText = buildAnnotatedString {
            append("Keep just the pages you need")
            withStyle(SpanStyle(color = Color(0xFFA3A3A3))) {
                append(".")
            }
        }

        NexusText(
            text = titleText.text,
            style = NexusTheme.typography.titleLarge.copy(
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-1.2).sp,
                lineHeight = 36.sp
            ),
            color = NexusTheme.colors.textPrimary
        )

        Spacer(modifier = Modifier.height(10.dp))

        // ── Subtitle ─────────────────────────────────────────────────────────
        NexusText(
            text = "Pick pages by tapping or typing a range, then save them as a new PDF. Everything runs on your device.",
            style = NexusTheme.typography.body.copy(
                fontSize = 15.sp,
                lineHeight = 22.sp,
                letterSpacing = (-0.2).sp
            ),
            color = NexusTheme.colors.textSecondary
        )

        Spacer(modifier = Modifier.height(28.dp))

        // ── Stepper Row ───────────────────────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
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
                                    fontSize = 11.5.sp,
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
                        style = NexusTheme.typography.label.copy(
                            fontSize = 13.sp,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = labelColor
                    )

                    // Connecting Divider Bar
                    if (index < steps.lastIndex) {
                        Spacer(modifier = Modifier.width(10.dp))
                        Box(
                            modifier = Modifier
                                .width(28.dp)
                                .height(1.5.dp)
                                .clip(CircleShape)
                                .background(NexusTheme.colors.divider.copy(alpha = 0.6f))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                    }
                }
            }
        }
    }
}
