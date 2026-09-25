package com.semhas.app.voice

enum class VoiceAssistantState(val displayLabel: String, val detailDescription: String) {
    OFF(
        displayLabel = "Voice Assistant Off",
        detailDescription = "Enable the assistant to start voice monitoring"
    ),
    PERMISSION_REQUIRED(
        displayLabel = "Permission Required",
        detailDescription = "Microphone access is needed for voice control"
    ),
    READY(
        displayLabel = "Ready",
        detailDescription = "Voice service initialized and ready"
    ),
    WAITING_FOR_WAKE(
        displayLabel = "Waiting for wake",
        detailDescription = "Wake detector active • Say 'Hey Jarvis'"
    ),
    WAKE_DETECTED(
        displayLabel = "Wake detected",
        detailDescription = "\"Hey Jarvis\" recognized!"
    ),
    LISTENING(
        displayLabel = "Listening",
        detailDescription = "Listening for your command..."
    ),
    PROCESSING(
        displayLabel = "Processing",
        detailDescription = "Processing command..."
    ),
    SPEAKING(
        displayLabel = "Speaking",
        detailDescription = "Assistant responding..."
    ),
    COMPLETED(
        displayLabel = "Completed",
        detailDescription = "Command executed successfully."
    ),
    UNSUPPORTED_COMMAND(
        displayLabel = "Unsupported command",
        detailDescription = "Sorry, I can only help with SEMHAS appliance control and power information."
    ),
    BACKGROUND_UNAVAILABLE(
        displayLabel = "Background voice unavailable",
        detailDescription = "Android restricted microphone access while backgrounded"
    );

    companion object {
        // Backward-compatible aliases for UI references
        val LISTENING_WAKE_PHRASE get() = WAITING_FOR_WAKE
        val WAKE_PHRASE_DETECTED get() = WAKE_DETECTED
        val LISTENING_COMMAND get() = LISTENING
        val LOCKING_COMMAND get() = PROCESSING
        val EXECUTING get() = PROCESSING
        val COMMAND_RECOGNIZED get() = PROCESSING
    }
}
