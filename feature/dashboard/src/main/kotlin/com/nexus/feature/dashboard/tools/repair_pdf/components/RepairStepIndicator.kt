package com.nexus.feature.dashboard.tools.repair_pdf.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
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
import com.nexus.feature.dashboard.tools.repair_pdf.RepairStep

/**
 * 3-step progress indicator and header for Repair PDF matching the reference design.
 */
@Composable
fun RepairStepIndicator(
    currentStep: RepairStep,
    modifier: Modifier = Modifier
) {
    val steps = listOf(RepairStep.UPLOAD, RepairStep.REPAIR, RepairStep.EXPORT)

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        // ── Brand Pill Bar ───────────────────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(Color(0xFF0A0A0A)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Build,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(19.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            NexusText(
                text = "Repair PDF",
                style = NexusTheme.typography.title.copy(
                    fontSize = 16.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp
                ),
                color = NexusTheme.colors.textPrimary
            )
        }

        // ── Main Header Title ────────────────────────────────────────────────
        val titleText = buildAnnotatedString {
            append("Bring a broken PDF back to life")
            withStyle(SpanStyle(color = Color(0xFFA3A3A3))) {
                append(".")
            }
        }

        NexusText(
            text = titleText.text,
            style = NexusTheme.typography.titleLarge.copy(
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-1.2).sp,
                lineHeight = 34.sp
            ),
            color = NexusTheme.colors.textPrimary
        )

        Spacer(modifier = Modifier.height(8.dp))

        NexusText(
            text = "Recover pages from a corrupted or damaged PDF and save a clean, working copy. Everything runs on your device.",
            style = NexusTheme.typography.body.copy(
                fontSize = 14.sp,
                color = Color(0xFF707070),
                lineHeight = 20.sp
            )
        )

        Spacer(modifier = Modifier.height(24.dp))

        // ── Steps Indicator ──────────────────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            steps.forEachIndexed { index, step ->
                val isActive = step == currentStep
                val isDone = step.stepNumber < currentStep.stepNumber

                val circleBg by animateColorAsState(
                    targetValue = when {
                        isActive -> Color(0xFF0A0A0A)
                        isDone -> Color.Transparent
                        else -> Color.Transparent
                    },
                    animationSpec = tween(300),
                    label = "circleBg"
                )

                val circleBorder by animateColorAsState(
                    targetValue = when {
                        isActive -> Color(0xFF0A0A0A)
                        isDone -> Color(0xFF0A0A0A)
                        else -> Color(0xFFCFCFCF)
                    },
                    animationSpec = tween(300),
                    label = "circleBorder"
                )

                val contentColor by animateColorAsState(
                    targetValue = when {
                        isActive -> Color.White
                        isDone -> Color(0xFF0A0A0A)
                        else -> Color(0xFFA3A3A3)
                    },
                    animationSpec = tween(300),
                    label = "contentColor"
                )

                val labelColor by animateColorAsState(
                    targetValue = when {
                        isActive || isDone -> NexusTheme.colors.textPrimary
                        else -> Color(0xFFA3A3A3)
                    },
                    animationSpec = tween(300),
                    label = "labelColor"
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(circleBg)
                            .border(1.5.dp, circleBorder, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isDone) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = contentColor,
                                modifier = Modifier.size(14.dp)
                            )
                        } else {
                            NexusText(
                                text = step.stepNumber.toString(),
                                style = NexusTheme.typography.caption.copy(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = contentColor
                            )
                        }
                    }

                    NexusText(
                        text = step.label,
                        style = NexusTheme.typography.bodySmall.copy(
                            fontSize = 13.sp,
                            fontWeight = if (isActive || isDone) FontWeight.SemiBold else FontWeight.Medium
                        ),
                        color = labelColor
                    )
                }

                if (index < steps.size - 1) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .width(22.dp)
                            .height(1.5.dp)
                            .background(
                                if (isDone) Color(0xFF0A0A0A) else Color(0xFFCFCFCF)
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
            }
        }
    }
}

