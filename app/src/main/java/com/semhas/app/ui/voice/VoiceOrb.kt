package com.semhas.app.ui.voice

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.semhas.app.R
import com.semhas.app.ui.theme.Dimensions
import com.semhas.app.voice.VoiceAssistantState

// Futuristic Orb AI Palette: Cyan + Electric Blue + Violet + Magenta/Pink
private val NeonCyan = Color(0xFF00E5FF)
private val ElectricBlue = Color(0xFF2979FF)
private val NeonViolet = Color(0xFF7C4DFF)
private val NeonMagenta = Color(0xFFFF4081)

/**
 * Futuristic Glass Voice Orb with Siri / Google Home style dynamic expansion & contextual animations.
 *
 * Characteristics:
 * - Transparent glossy glass sphere with internal flowing infinity energy ribbon.
 * - Cyan / electric blue / violet / magenta neon lighting with soft bloom.
 * - Zero permanent outer circular border lines.
 * - Dynamic state transitions:
 *    - WAITING_FOR_WAKE: Compact 160dp, calm subtle breathing + slow gentle oscillation, contextual text: "Ready".
 *    - WAKE_DETECTED: Expands smoothly to 1.28x (~205dp), luminous outward halo wake burst.
 *    - LISTENING: Sound-wave ripple rings + internal energy ribbon continuously rotates + contextual "Listening...".
 *    - PROCESSING: Contracts slightly (1.05x) + internal energy rotates faster + controlled rotating sweep energy ring.
 *    - SPEAKING: Rhythmic vocal breathing pulse + assistant response text.
 *    - OFF: Dimmed static sphere (alpha 0.35), zero ongoing animations to preserve battery.
 */
@Composable
fun VoiceOrb(
    state: VoiceAssistantState,
    isEnabled: Boolean,
    currentTranscript: String? = null,
    spokenResponse: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isOff = !isEnabled || state == VoiceAssistantState.OFF
    val isWakeDetected = state == VoiceAssistantState.WAKE_DETECTED
    val isListening = state == VoiceAssistantState.LISTENING
    val isProcessing = state == VoiceAssistantState.PROCESSING
    val isSpeaking = state == VoiceAssistantState.SPEAKING
    val isIdle = state == VoiceAssistantState.WAITING_FOR_WAKE || state == VoiceAssistantState.COMPLETED

    // Infinite transition for smooth GPU-accelerated animations (active only when assistant is ON)
    val infiniteTransition = rememberInfiniteTransition(label = "orbAnimations")

    // 1. Subtle idle calm bloom breathing
    val idleBloomAlpha by if (!isOff && isIdle) {
        infiniteTransition.animateFloat(
            initialValue = 0.12f,
            targetValue = 0.28f,
            animationSpec = infiniteRepeatable(
                animation = tween(2800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "idleBloomAlpha"
        )
    } else {
        remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    }

    // 2. Subtle idle breathing scale
    val idleBreathingScale by if (!isOff && isIdle) {
        infiniteTransition.animateFloat(
            initialValue = 0.985f,
            targetValue = 1.015f,
            animationSpec = infiniteRepeatable(
                animation = tween(2800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "idleBreathingScale"
        )
    } else {
        remember { androidx.compose.runtime.mutableFloatStateOf(1.0f) }
    }

    // 3. Internal energy ribbon rotation / oscillation
    val idleOscillation by if (!isOff && isIdle) {
        infiniteTransition.animateFloat(
            initialValue = -3.5f,
            targetValue = 3.5f,
            animationSpec = infiniteRepeatable(
                animation = tween(3800, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "idleOscillation"
        )
    } else {
        remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    }

    val activeFlowRotation by if (!isOff && (isListening || isSpeaking)) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(6000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "activeFlowRotation"
        )
    } else {
        remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    }

    val processingEnergyRotation by if (isProcessing) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "processingEnergyRotation"
        )
    } else {
        remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    }

    // 4. Outward sound-wave ripple rings for LISTENING
    val ring1Progress by if (isListening) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1400, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "ring1Progress"
        )
    } else {
        remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    }

    val ring2Progress by if (isListening) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1400, delayMillis = 700, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "ring2Progress"
        )
    } else {
        remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    }

    // 5. Rotating energy sweep ring for PROCESSING
    val processingSweepRotation by if (isProcessing) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(1400, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "processingSweepRotation"
        )
    } else {
        remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    }

    // 6. Vocal rhythm pulse for SPEAKING
    val vocalPulseScale by if (isSpeaking) {
        infiniteTransition.animateFloat(
            initialValue = 1.06f,
            targetValue = 1.18f,
            animationSpec = infiniteRepeatable(
                animation = tween(750, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "vocalPulseScale"
        )
    } else {
        remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
    }

    // 7. Wake burst halo expansion for WAKE_DETECTED
    val wakeBurstProgress by animateFloatAsState(
        targetValue = if (isWakeDetected) 1f else 0f,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "wakeBurstProgress"
    )

    // Dynamic scale transition across assistant states (Siri/Google Home style)
    val targetScale = when {
        isOff -> 0.90f
        isWakeDetected -> 1.28f // Expands smoothly to 1.25x–1.30x
        isListening -> 1.18f   // Prominently expanded
        isProcessing -> 1.05f  // Contracts slightly
        isSpeaking -> vocalPulseScale // Pulses with voice rhythm
        else -> idleBreathingScale // Compact idle baseline with subtle breathing
    }

    val orbScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "orbScale"
    )

    // Determine current internal ribbon rotation
    val energyRotationZ = when {
        isOff -> 0f
        isProcessing -> processingEnergyRotation
        isListening || isSpeaking -> activeFlowRotation
        isWakeDetected -> 25f * wakeBurstProgress
        else -> idleOscillation
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Hero Orb Container with dynamic canvas bloom & soundwave rings
        Box(
            modifier = Modifier.size(240.dp),
            contentAlignment = Alignment.Center
        ) {
            // Background Canvas: Bloom, wake halo, sound-wave rings, processing arc
            Canvas(modifier = Modifier.size(240.dp)) {
                val centerOffset = Offset(size.width / 2f, size.height / 2f)
                val baseRadius = 72.dp.toPx()

                when {
                    isOff -> {
                        // Static dormant state: zero outer border drawn, no battery drain
                    }

                    isWakeDetected -> {
                        // Outward expanding luminous halo burst
                        val burstRadius = baseRadius + (wakeBurstProgress * 36.dp.toPx())
                        val burstAlpha = (1f - (wakeBurstProgress * 0.3f)) * 0.85f

                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    NeonCyan.copy(alpha = burstAlpha),
                                    NeonMagenta.copy(alpha = burstAlpha * 0.6f),
                                    NeonViolet.copy(alpha = burstAlpha * 0.3f),
                                    Color.Transparent
                                ),
                                center = centerOffset,
                                radius = burstRadius
                            ),
                            radius = burstRadius,
                            center = centerOffset
                        )

                        // Outward shockwave ring
                        drawCircle(
                            color = NeonCyan.copy(alpha = (1f - wakeBurstProgress) * 0.9f),
                            radius = burstRadius,
                            center = centerOffset,
                            style = Stroke(width = 2.5.dp.toPx())
                        )
                    }

                    isListening -> {
                        // Expanding concentric sound-wave ripple rings
                        val r1 = baseRadius + (ring1Progress * 38.dp.toPx())
                        val a1 = (1f - ring1Progress) * 0.70f
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(NeonCyan.copy(alpha = a1), NeonMagenta.copy(alpha = a1 * 0.4f)),
                                center = centerOffset,
                                radius = r1
                            ),
                            radius = r1,
                            center = centerOffset,
                            style = Stroke(width = 2.dp.toPx())
                        )

                        val r2 = baseRadius + (ring2Progress * 38.dp.toPx())
                        val a2 = (1f - ring2Progress) * 0.70f
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(NeonViolet.copy(alpha = a2), NeonCyan.copy(alpha = a2 * 0.4f)),
                                center = centerOffset,
                                radius = r2
                            ),
                            radius = r2,
                            center = centerOffset,
                            style = Stroke(width = 1.5.dp.toPx())
                        )
                    }

                    isProcessing -> {
                        // Controlled rotating energy sweep ring
                        val arcRadius = baseRadius + 10.dp.toPx()
                        val arcSize = Size(arcRadius * 2, arcRadius * 2)
                        val arcTopLeft = Offset(centerOffset.x - arcRadius, centerOffset.y - arcRadius)

                        drawArc(
                            brush = Brush.sweepGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    NeonCyan.copy(alpha = 0.25f),
                                    ElectricBlue,
                                    NeonMagenta,
                                    NeonCyan
                                ),
                                center = centerOffset
                            ),
                            startAngle = processingSweepRotation,
                            sweepAngle = 280f,
                            useCenter = false,
                            topLeft = arcTopLeft,
                            size = arcSize,
                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }

                    isSpeaking -> {
                        // Vocal rhythm glow expansion
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    NeonMagenta.copy(alpha = 0.45f),
                                    NeonCyan.copy(alpha = 0.28f),
                                    NeonViolet.copy(alpha = 0.15f),
                                    Color.Transparent
                                ),
                                center = centerOffset,
                                radius = baseRadius + 30.dp.toPx()
                            ),
                            radius = baseRadius + 30.dp.toPx(),
                            center = centerOffset
                        )
                    }

                    isIdle -> {
                        // Slow, subtle breathing ambient glow
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    NeonCyan.copy(alpha = idleBloomAlpha),
                                    NeonViolet.copy(alpha = idleBloomAlpha * 0.5f),
                                    Color.Transparent
                                ),
                                center = centerOffset,
                                radius = baseRadius + 22.dp.toPx()
                            ),
                            radius = baseRadius + 22.dp.toPx(),
                            center = centerOffset
                        )
                    }
                }
            }

            // Central Glass Spherical Orb from supplied asset (no static circular border)
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .scale(orbScale)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick
                    ),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.futuristic_voice_orb),
                    contentDescription = "Futuristic Voice Assistant Orb",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(160.dp)
                        .clip(CircleShape)
                        .graphicsLayer {
                            rotationZ = energyRotationZ
                        }
                        .alpha(if (isOff) 0.35f else 1.0f)
                )
            }
        }

        // Contextual Text Behavior: Clean when idle, conversational when interacting
        val contextualMainText = when {
            isOff -> "Voice Assistant is off"
            state == VoiceAssistantState.PERMISSION_REQUIRED -> "Permission Required"
            isWakeDetected -> "Listening..."
            isListening -> "Listening..."
            isProcessing -> "Understanding..."
            isSpeaking -> spokenResponse ?: "Speaking..."
            else -> "Ready"
        }

        val contextualSubText = when {
            isOff -> "Turn on Voice Assistant below"
            state == VoiceAssistantState.PERMISSION_REQUIRED -> "Microphone access needed"
            isWakeDetected -> "Speak your command"
            isListening -> if (!currentTranscript.isNullOrBlank()) "\"$currentTranscript\"" else "Speak your command"
            isProcessing -> "Understanding your command"
            isSpeaking -> null
            else -> null // IDLE: Minimal clean text
        }

        AnimatedVisibility(
            visible = contextualMainText != null,
            enter = fadeIn(tween(250)),
            exit = fadeOut(tween(200))
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = Dimensions.spaceSmall)
            ) {
                contextualMainText?.let { text ->
                    Text(
                        text = text,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.3.sp
                        ),
                        color = if (isOff) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = Dimensions.spaceMedium)
                    )
                }

                contextualSubText?.let { sub ->
                    Spacer(modifier = Modifier.height(Dimensions.spaceExtraSmall))
                    Text(
                        text = sub,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Real-time transcript feedback badge while speech is being captured
        if (isListening && !currentTranscript.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(Dimensions.spaceSmall))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(Dimensions.radiusMedium))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(Dimensions.radiusMedium))
                    .padding(horizontal = Dimensions.spaceMedium, vertical = Dimensions.spaceExtraSmall)
            ) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(Dimensions.spaceSmall))
                Text(
                    text = "\"$currentTranscript\"",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
