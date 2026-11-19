package com.nexus.feature.dashboard.tools.xlsx_to_pdf.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.TableChart
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
import com.nexus.feature.dashboard.tools.xlsx_to_pdf.XlsxStep

@Composable
fun XlsxStepIndicator(
    currentStep: XlsxStep,
    modifier: Modifier = Modifier
) {
    val steps = listOf(XlsxStep.UPLOAD, XlsxStep.SELECT, XlsxStep.CONVERT)

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
                    imageVector = Icons.Rounded.TableChart,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            NexusText(
                text = "Excel to PDF",
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
            append("Turn your spreadsheet into a PDF")
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

        Spacer(modifier = Modifier.height(10.dp))

        // ── Subtitle ─────────────────────────────────────────────────────────
        NexusText(
            text = "Upload an Excel or CSV file, choose the sheets and page layout, then download a clean PDF. Everything runs on your device.",
            style = NexusTheme.typography.body.copy(
                fontSize = 14.5.sp,
                lineHeight = 21.sp,
                letterSpacing = (-0.2).sp
            ),
            color = NexusTheme.colors.textSecondary
        )

        Spacer(modifier = Modifier.height(24.dp))

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
                    label = "xlsxStepBg"
                )

                val circleBorder by animateColorAsState(
                    targetValue = when {
                        isCurrent -> NexusTheme.colors.textPrimary
                        isCompleted -> NexusTheme.colors.textPrimary
                        else -> NexusTheme.colors.divider.copy(alpha = 0.6f)
                    },
                    animationSpec = tween(220),
                    label = "xlsxStepBorder"
                )

                val contentColor by animateColorAsState(
                    targetValue = when {
                        isCurrent -> NexusTheme.colors.surface
                        isCompleted -> NexusTheme.colors.textPrimary
                        else -> NexusTheme.colors.textSecondary.copy(alpha = 0.7f)
                    },
                    animationSpec = tween(220),
                    label = "xlsxStepContentColor"
                )

                val labelColor by animateColorAsState(
                    targetValue = if (isCurrent || isCompleted) NexusTheme.colors.textPrimary else NexusTheme.colors.textSecondary.copy(alpha = 0.65f),
                    animationSpec = tween(220),
                    label = "xlsxStepLabelColor"
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
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
                                tint = contentColor,
                                modifier = Modifier.size(13.dp)
                            )
                        } else {
                            NexusText(
                                text = "${step.stepNumber}",
                                style = NexusTheme.typography.caption.copy(
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = contentColor
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    NexusText(
                        text = step.label,
                        style = NexusTheme.typography.caption.copy(
                            fontSize = 13.sp,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold
                        ),
                        color = labelColor
                    )
                }

                if (index < steps.size - 1) {
                    val barColor by animateColorAsState(
                        targetValue = if (currentStep.stepNumber > index + 1) NexusTheme.colors.textPrimary else NexusTheme.colors.divider.copy(alpha = 0.5f),
                        animationSpec = tween(220),
                        label = "xlsxStepBarColor"
                    )

                    Spacer(modifier = Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .width(24.dp)
                            .height(1.5.dp)
                            .background(barColor)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                }
            }
        }
    }
}

