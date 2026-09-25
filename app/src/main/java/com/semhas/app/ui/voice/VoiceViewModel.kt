package com.semhas.app.ui.voice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.semhas.app.data.repository.SemhasRepository
import com.semhas.app.voice.VoiceAssistantManager
import com.semhas.app.voice.VoiceAssistantState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class VoiceUiState(
    val isEnabled: Boolean = false,
    val serviceState: VoiceAssistantState = VoiceAssistantState.OFF,
    val lastRecognizedCommand: String? = null,
    val lastExecutedAction: String? = null,
    val lastSpokenResponse: String? = null,
    val currentTranscript: String? = null,
    val supportedCommands: List<String> = listOf(
        "Turn on bedroom fan",
        "Turn off bedroom fan",
        "Turn everything off",
        "Turn everything on",
        "What is the current power?",
        "What is the voltage?",
        "What is the current?",
        "How much energy did I use today?",
        "What is my current bill?",
        "Which appliance uses the most power?",
        "Is the system healthy?",
        "Open Billing",
        "Open Monitor",
        "What can you do?"
    )
)

class VoiceViewModel(
    val repository: SemhasRepository,
    val voiceAssistantManager: VoiceAssistantManager
) : ViewModel() {

    @Suppress("UNCHECKED_CAST")
    val uiState: StateFlow<VoiceUiState> = combine(
        voiceAssistantManager.isEnabled,
        voiceAssistantManager.state,
        voiceAssistantManager.lastRecognizedCommand,
        voiceAssistantManager.lastExecutedAction,
        voiceAssistantManager.lastSpokenResponse,
        voiceAssistantManager.currentTranscript
    ) { args: Array<Any?> ->
        VoiceUiState(
            isEnabled = args[0] as? Boolean ?: false,
            serviceState = args[1] as? VoiceAssistantState ?: VoiceAssistantState.OFF,
            lastRecognizedCommand = args[2] as? String,
            lastExecutedAction = args[3] as? String,
            lastSpokenResponse = args[4] as? String,
            currentTranscript = args[5] as? String
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = VoiceUiState()
    )

    val navigationTarget: StateFlow<String?> = voiceAssistantManager.navigationTarget

    fun clearNavigationTarget() {
        voiceAssistantManager.clearNavigationTarget()
    }

    fun toggleAssistant(enable: Boolean) {
        voiceAssistantManager.toggleAssistant(enable)
    }

    fun onMicButtonClicked() {
        voiceAssistantManager.onMicButtonClicked()
    }

    fun updatePermissionStatus(granted: Boolean) {
        voiceAssistantManager.updatePermissionStatus(granted)
    }

    fun checkPermissionAndInitialize() {
        voiceAssistantManager.checkPermissionAndInitialize()
    }
}
