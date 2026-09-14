package com.mindecho.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Tactile Voice HUD Colors
private val WaveformActiveColor = Color(0xFF64B5F6) // Electric Cyan/Blue
private val WaveformPeakColor = Color(0xFF81C784)   // Subdued Green
private val WaveformIdleColor = Color(0xFF37474F)   // Dark Slate

/**
 * Custom Canvas audio visualizer rendering 7 rounded vertical waveform bars.
 * Driven in real-time by rmsdB from SpeechRecognizer for 120Hz LTPO display smoothness.
 */
@Composable
fun WaveformCanvas(
    rmsdB: Float,
    isListening: Boolean,
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .height(56.dp),
    barCount: Int = 7,
    barWidth: Dp = 6.dp,
    barSpacing: Dp = 10.dp
) {
    // Normalize rmsdB (-2dB to ~10dB) into 0.0f..1.0f amplitude
    val normalizedAmp = if (isListening) {
        ((rmsdB + 2.0f) / 12.0f).coerceIn(0.1f, 1.0f)
    } else {
        0.05f
    }

    val animatedAmp by animateFloatAsState(
        targetValue = normalizedAmp,
        animationSpec = tween(durationMillis = 80, easing = FastOutSlowInEasing),
        label = "waveform_amplitude"
    )

    // Dynamic multipliers across the 7 bars for natural organic wave motion
    val heightMultipliers = listOf(0.35f, 0.65f, 0.95f, 1.0f, 0.85f, 0.55f, 0.30f)

    Canvas(modifier = modifier) {
        val widthPx = size.width
        val heightPx = size.height
        val barWidthPx = barWidth.toPx()
        val barSpacingPx = barSpacing.toPx()

        val totalWaveformWidth = (barCount * barWidthPx) + ((barCount - 1) * barSpacingPx)
        val startX = (widthPx - totalWaveformWidth) / 2f
        val centerY = heightPx / 2f

        for (i in 0 until barCount) {
            val multiplier = heightMultipliers.getOrElse(i) { 0.5f }
            val minHeight = 6.dp.toPx()
            val maxHeight = heightPx * 0.9f

            val dynamicHeight = if (isListening) {
                (minHeight + (maxHeight - minHeight) * animatedAmp * multiplier).coerceIn(minHeight, maxHeight)
            } else {
                minHeight
            }

            val barX = startX + (i * (barWidthPx + barSpacingPx))
            val barY = centerY - (dynamicHeight / 2f)

            val barColor = when {
                !isListening -> WaveformIdleColor
                animatedAmp > 0.6f -> WaveformPeakColor
                else -> WaveformActiveColor
            }

            drawRoundRect(
                color = barColor,
                topLeft = Offset(barX, barY),
                size = Size(barWidthPx, dynamicHeight),
                cornerRadius = CornerRadius(barWidthPx / 2f, barWidthPx / 2f)
            )
        }
    }
}
