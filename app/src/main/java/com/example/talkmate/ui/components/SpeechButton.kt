package com.example.talkmate.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlin.math.sin

@Composable
fun SpeechButton(
    isListening: Boolean,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }

    // Pulsing animation for listening state
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isListening) 1.15f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    // Wave animation for listening state
    val waveProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (isListening) 1f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_progress"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(120.dp)
        ) {
            // Animated background waves
            if (isListening) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .scale(pulseScale)
                ) {
                    drawSoundWaves(waveProgress)
                }
            }

            // Main button
            FloatingActionButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (isListening) {
                        onStopListening()
                    } else {
                        onStartListening()
                    }
                },
                modifier = Modifier
                    .size(80.dp)
                    .scale(pulseScale),
                containerColor = if (isListening)
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.primary,
                contentColor = if (isListening)
                    MaterialTheme.colorScheme.onError
                else
                    MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(
                    imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = if (isListening) "Stop listening" else "Start listening",
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Status text
        Text(
            text = if (isListening) "Listening..." else "Tap to speak",
            style = MaterialTheme.typography.bodyMedium,
            color = if (isListening)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun DrawScope.drawSoundWaves(progress: Float) {
    val centerX = size.width / 2
    val centerY = size.height / 2
    val maxRadius = size.minDimension / 2

    // Draw multiple concentric waves
    for (i in 0..2) {
        val waveProgress = (progress + i * 0.3f) % 1f
        val radius = maxRadius * waveProgress
        val alpha = (1f - waveProgress) * 0.3f

        if (alpha > 0f) {
            drawCircle(
                color = Color.Blue.copy(alpha = alpha),
                radius = radius,
                center = Offset(centerX, centerY),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
            )
        }
    }

    // Draw sound wave lines
    val waveHeight = 20f
    val waveCount = 5

    for (i in 0 until waveCount) {
        val x = centerX + (i - waveCount / 2) * 15f
        val wavePhase = progress * 2 * Math.PI + i * 0.5
        val amplitude = sin(wavePhase).toFloat() * waveHeight * (1f - kotlin.math.abs(i - waveCount / 2f) / (waveCount / 2f))

        drawLine(
            color = Color.Blue.copy(alpha = 0.6f),
            start = Offset(x, centerY - amplitude),
            end = Offset(x, centerY + amplitude),
            strokeWidth = 3.dp.toPx()
        )
    }
}