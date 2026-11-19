package com.nexus.feature.dashboard.tools.protect_pdf.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lock
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
import com.nexus.feature.dashboard.tools.protect_pdf.ProtectStep

/**
 * 3-step progress indicator and header for Protect PDF.
 */
@Composable
fun ProtectStepIndicator(
    currentStep: ProtectStep,
    modifier: Modifier = Modifier
) {
    val steps = listOf(ProtectStep.UPLOAD, ProtectStep.PROTECT, ProtectStep.EXPORT)

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
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(19.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            NexusText(
                text = "Protect PDF",
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
            append("Lock your PDF with a password")
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
            text = "Add a password so only the people you choose can open your PDF. Everything runs on your device.",
            style = NexusTheme.typography.body.copy(
                fontSize = 14.sp,
                color = Color(0xFF707070),
                lineHeight = 20.sp
            )
        )

        Spacer(modifier = Modifier.height(24.dp))

        // ── 3-Step Progress Indicators ───────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            steps.forEachIndexed { index, step ->
                val isActive = step == currentStep
                val isDone = step.stepNumber < currentStep.stepNumber

                val circleBg by animateColorAsState(
                    targetValue = when {
                        isActive -> Color(0xFF0A0A0A)
                        isDone -> Color.White
                        else -> Color.Transparent
                    },
                    animationSpec = tween(250),
                    label = "circleBg"
                )

                val circleBorder by animateColorAsState(
                    targetValue = when {
                        isActive || isDone -> Color(0xFF0A0A0A)
                        else -> Color(0xFFCFCFCF)
                    },
                    animationSpec = tween(250),
                    label = "circleBorder"
                )

                val circleContentColor by animateColorAsState(
                    targetValue = when {
                        isActive -> Color.White
                        isDone -> Color(0xFF0A0A0A)
                        else -> Color(0xFFA3A3A3)
                    },
                    animationSpec = tween(250),
                    label = "circleContent"
                )

                val labelColor by animateColorAsState(
                    targetValue = when {
                        isActive || isDone -> NexusTheme.colors.textPrimary
                        else -> Color(0xFFA3A3A3)
                    },
                    animationSpec = tween(250),
                    label = "stepLabel"
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically
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
                                tint = circleContentColor,
                                modifier = Modifier.size(13.dp)
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

                    NexusText(
                        text = step.label,
                        style = NexusTheme.typography.caption.copy(
                            fontSize = 13.sp,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = labelColor
                    )
                }

                if (index < steps.size - 1) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .width(26.dp)
                            .height(1.5.dp)
                            .background(Color(0xFFCFCFCF))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
            }
        }
    }
}

