package com.semhas.app.ui.voice

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.semhas.app.ui.components.AppButton
import com.semhas.app.ui.components.AppCard
import com.semhas.app.ui.theme.Dimensions
import com.semhas.app.voice.VoiceAssistantState

/**
 * SEMHAS Voice Assistant Screen.
 *
 * Theme Consistency:
 * - Automatically adapts to Light and Dark SEMHAS themes via MaterialTheme.colorScheme.
 * - Dark Mode: Deep AMOLED Black background, dark elevated cards, SEMHAS green accents.
 * - Light Mode: Clean light background, elevated white cards, light-theme contrast, SEMHAS green accents.
 * - The hero orb retains its multi-color AI palette in both themes.
 *
 * Screen Layout:
 * - Top: Single "Voice Assistant" section header (duplicate "SEMHAS — Voice" removed) + compact live status badge
 * - Center: Futuristic Hero Orb (VoiceOrb)
 * - Below Orb: Latest Interaction Card
 * - Help / Discovery: Compact horizontal chips
 * - Bottom: Compact secondary manual Voice Assistant ON/OFF switch
 */
@Composable
fun VoiceScreen(
    viewModel: VoiceViewModel
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()

    // Permission launcher for RECORD_AUDIO
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.updatePermissionStatus(isGranted)
        if (isGranted) {
            viewModel.toggleAssistant(true)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.checkPermissionAndInitialize()
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = Dimensions.screenHorizontalPadding)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Dimensions.spaceSmall)
        ) {
            Spacer(modifier = Modifier.height(Dimensions.spaceExtraSmall))

            // 1. Top Section Header: "Voice Assistant" with Compact Live Status Badge
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Dimensions.spaceSmall),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Voice Assistant",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.3).sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Hands-free control for SEMHAS",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Compact Live Status Badge (● Ready, ● Listening, etc.)
                VoiceStatusBadge(state = state.serviceState, isEnabled = state.isEnabled)
            }

            // 2. Permission Banner (Shown only when microphone permission is required)
            AnimatedVisibility(
                visible = state.serviceState == VoiceAssistantState.PERMISSION_REQUIRED,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                AppCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
                    borderColor = MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
                    contentPadding = Dimensions.cardCompactPadding
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(Dimensions.spaceSmall))
                            Text(
                                text = "Microphone permission required",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        Spacer(modifier = Modifier.width(Dimensions.spaceSmall))

                        AppButton(
                            text = "Grant",
                            onClick = {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            },
                            isPrimary = true
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(Dimensions.spaceExtraSmall))

            // 3. Center: Futuristic Hero Orb (Glass sphere + neon infinity energy ribbon)
            VoiceOrb(
                state = state.serviceState,
                isEnabled = state.isEnabled,
                currentTranscript = state.currentTranscript,
                spokenResponse = state.lastSpokenResponse ?: state.lastExecutedAction,
                onClick = {
                    val hasPermission = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED

                    if (!hasPermission) {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    } else {
                        viewModel.onMicButtonClicked()
                    }
                }
            )

            Spacer(modifier = Modifier.height(Dimensions.spaceExtraSmall))

            // 4. Latest Interaction Card (Theme-consistent card)
            LatestInteractionCard(
                recognizedCommand = state.lastRecognizedCommand,
                spokenResponse = state.lastSpokenResponse ?: state.lastExecutedAction,
                currentTranscript = state.currentTranscript,
                isListening = state.serviceState == VoiceAssistantState.LISTENING
            )

            // 5. Compact Quick Commands Horizontal Chip Row
            SampleCommandsSection()
        }

        // 6. Bottom: Visually Secondary Manual Voice Assistant ON/OFF Switch
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Dimensions.spaceMedium)
        ) {
            AppCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.surface,
                borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                contentPadding = Dimensions.cardCompactPadding
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    if (state.isEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                    else MaterialTheme.colorScheme.surfaceVariant
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (state.isEnabled) Icons.Default.Mic else Icons.Default.MicOff,
                                contentDescription = null,
                                tint = if (state.isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(Dimensions.spaceMedium))

                        Column {
                            Text(
                                text = "Voice Assistant",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (state.isEnabled) "Listening for wake word" else "Voice assistant is off",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (state.isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Switch(
                        checked = state.isEnabled,
                        onCheckedChange = { requestedEnabled ->
                            if (requestedEnabled) {
                                val hasPermission = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED

                                if (!hasPermission) {
                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                } else {
                                    viewModel.toggleAssistant(true)
                                }
                            } else {
                                viewModel.toggleAssistant(false)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                            uncheckedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )
                }
            }
        }
    }
}

/**
 * Compact SEMHAS Green live status badge chip.
 */
@Composable
private fun VoiceStatusBadge(
    state: VoiceAssistantState,
    isEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val isOff = !isEnabled || state == VoiceAssistantState.OFF

    val (label, dotColor) = when {
        isOff -> Pair("Off", MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        state == VoiceAssistantState.WAITING_FOR_WAKE -> Pair("Ready", MaterialTheme.colorScheme.primary)
        state == VoiceAssistantState.WAKE_DETECTED -> Pair("Listening", MaterialTheme.colorScheme.primary)
        state == VoiceAssistantState.LISTENING -> Pair("Listening", MaterialTheme.colorScheme.primary)
        state == VoiceAssistantState.PROCESSING -> Pair("Processing", MaterialTheme.colorScheme.primary)
        state == VoiceAssistantState.SPEAKING -> Pair("Speaking", MaterialTheme.colorScheme.primary)
        state == VoiceAssistantState.PERMISSION_REQUIRED -> Pair("Off", MaterialTheme.colorScheme.error)
        else -> Pair("Ready", MaterialTheme.colorScheme.primary)
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(Dimensions.radiusSmall))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(Dimensions.radiusSmall))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.2.sp
            ),
            color = if (isOff) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Compact Conversation Card displaying the latest user command and assistant response.
 */
@Composable
private fun LatestInteractionCard(
    recognizedCommand: String?,
    spokenResponse: String?,
    currentTranscript: String?,
    isListening: Boolean,
    modifier: Modifier = Modifier
) {
    AppCard(
        modifier = modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surface,
        borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
        contentPadding = Dimensions.cardCompactPadding
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Dimensions.spaceSmall)
        ) {
            // Header label
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "LATEST INTERACTION",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // User Row (You)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = "You: ",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                val userText = when {
                    !recognizedCommand.isNullOrBlank() -> recognizedCommand
                    isListening && !currentTranscript.isNullOrBlank() -> "\"$currentTranscript...\""
                    else -> "— (Say \"Hey SEM\" to begin)"
                }
                Text(
                    text = userText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (!recognizedCommand.isNullOrBlank() || (!currentTranscript.isNullOrBlank() && isListening)) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    }
                )
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                thickness = 0.8.dp,
                modifier = Modifier.padding(vertical = 2.dp)
            )

            // Assistant Row (SEM)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = "SEM: ",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                val semText = when {
                    !spokenResponse.isNullOrBlank() -> "\"$spokenResponse\""
                    else -> "— (Awaiting command)"
                }
                Text(
                    text = semText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (!spokenResponse.isNullOrBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}

/**
 * Compact quick commands horizontal chip row styled with theme tokens.
 */
@Composable
private fun SampleCommandsSection(
    modifier: Modifier = Modifier
) {
    val quickCommands = listOf(
        "Turn on bedroom fan",
        "Turn off bedroom fan",
        "Turn everything off",
        "Turn everything on",
        "What is the current power?",
        "What is the voltage?",
        "What is my current bill?",
        "Which appliance uses the most power?",
        "Is the system healthy?",
        "Open Billing",
        "Open Monitor"
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimensions.spaceExtraSmall)
    ) {
        Text(
            text = "SAMPLE COMMANDS",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 2.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Dimensions.spaceSmall)
        ) {
            quickCommands.forEach { cmd ->
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(Dimensions.radiusMedium))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(Dimensions.radiusMedium))
                        .padding(horizontal = Dimensions.spaceMedium, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ChatBubbleOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "\"$cmd\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
