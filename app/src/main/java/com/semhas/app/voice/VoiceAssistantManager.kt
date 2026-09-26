package com.semhas.app.voice

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.core.content.ContextCompat
import com.semhas.app.data.repository.SemhasRepository
import com.semhas.app.service.VoiceAssistantService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID

/**
 * Voice Assistant Manager coordinating:
 * 1. Background Wake-Word Detection ("Hey Jarvis" via OpenWakeWord)
 * 2. Temporary One-Shot Command Recognition (Android SpeechRecognizer)
 * 3. Natural Language Command Parsing (VoiceCommandParser)
 * 4. Command Execution (SemhasRepository)
 * 5. Text-To-Speech Audio Feedback
 * 6. Automatic loop back to Wake-Word listening mode
 *
 * Strict single-microphone ownership: SpeechRecognizer and OpenWakeWord never run simultaneously.
 */
class VoiceAssistantManager(
    private val context: Context,
    private val repository: SemhasRepository
) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "SEMHAS_VOICE"
        const val WAKE_THRESHOLD = 0.35f
        const val WAKE_DEBOUNCE_MS = 2000L
        private const val COMMAND_TIMEOUT_MS = 7000L
        // 100ms audio handoff window: enables AudioRecord to cleanly release without clipping speech
        const val HANDOFF_DELAY_MS = 100L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow(VoiceAssistantState.OFF)
    val state: StateFlow<VoiceAssistantState> = _state.asStateFlow()

    private val _isEnabled = MutableStateFlow(false)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val _lastRecognizedCommand = MutableStateFlow<String?>(null)
    val lastRecognizedCommand: StateFlow<String?> = _lastRecognizedCommand.asStateFlow()

    private val _lastExecutedAction = MutableStateFlow<String?>(null)
    val lastExecutedAction: StateFlow<String?> = _lastExecutedAction.asStateFlow()

    private val _lastSpokenResponse = MutableStateFlow<String?>(null)
    val lastSpokenResponse: StateFlow<String?> = _lastSpokenResponse.asStateFlow()

    private val _currentTranscript = MutableStateFlow<String?>(null)
    val currentTranscript: StateFlow<String?> = _currentTranscript.asStateFlow()

    private val _navigationTarget = MutableStateFlow<String?>(null)
    val navigationTarget: StateFlow<String?> = _navigationTarget.asStateFlow()

    fun clearNavigationTarget() {
        _navigationTarget.value = null
    }

    var wakeThreshold: Float = WAKE_THRESHOLD

    // OpenWakeWord detector instance
    private val openWakeWordManager = OpenWakeWordManager(context) { score ->
        mainHandler.post {
            handleWakeWordDetected(score)
        }
    }

    // SpeechRecognizer state for temporary one-shot commands
    private var speechRecognizer: SpeechRecognizer? = null
    @Volatile private var isListeningSessionActive = false
    private var commandTimeoutJob: Job? = null
    private var voiceCommandStartTimeMs: Long = 0L

    // Text-To-Speech Engine
    private var textToSpeech: TextToSpeech? = null
    @Volatile private var isTtsReady = false
    @Volatile private var isSpeaking = false
    private var activeTtsCallback: (() -> Unit)? = null

    init {
        initializeTextToSpeech()
        checkPermissionAndInitialize()
    }

    private fun initializeTextToSpeech() {
        mainHandler.post {
            try {
                textToSpeech = TextToSpeech(context.applicationContext, this)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to instantiate TextToSpeech: ${e.message}")
                isTtsReady = false
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val tts = textToSpeech ?: return
            val langResult = tts.setLanguage(Locale.US)
            if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                try {
                    tts.language = Locale.getDefault()
                } catch (_: Exception) {}
            }
            tts.setSpeechRate(1.0f)
            tts.setPitch(1.0f)

            // Configure audio attributes for clear and loud assistant vocal response
            try {
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                tts.setAudioAttributes(audioAttributes)
            } catch (e: Exception) {
                Log.w(TAG, "AudioAttributes configuration notice: ${e.message}")
            }

            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    isSpeaking = true
                    Log.i(TAG, "TTS_STARTED: speaking utteranceId=$utteranceId")
                }

                override fun onDone(utteranceId: String?) {
                    Log.i(TAG, "TTS_COMPLETED: utteranceId=$utteranceId finished")
                    mainHandler.post {
                        isSpeaking = false
                        val cb = activeTtsCallback
                        activeTtsCallback = null
                        cb?.invoke()
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    Log.w(TAG, "TTS_COMPLETED: utteranceId=$utteranceId ended with error")
                    mainHandler.post {
                        isSpeaking = false
                        val cb = activeTtsCallback
                        activeTtsCallback = null
                        cb?.invoke()
                    }
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    Log.w(TAG, "TTS_COMPLETED: utteranceId=$utteranceId ended with errorCode=$errorCode")
                    mainHandler.post {
                        isSpeaking = false
                        val cb = activeTtsCallback
                        activeTtsCallback = null
                        cb?.invoke()
                    }
                }
            })

            isTtsReady = true
            Log.i(TAG, "TextToSpeech initialized successfully")
        } else {
            isTtsReady = false
            Log.w(TAG, "TextToSpeech initialization failed with status $status")
        }
    }

    /**
     * Speaks response through speaker and triggers callback when finished.
     */
    fun speak(text: String, onDone: (() -> Unit)? = null) {
        scope.launch {
            var waitedMs = 0
            while (!isTtsReady && waitedMs < 2000) {
                delay(100L)
                waitedMs += 100
            }

            if (!isTtsReady || textToSpeech == null) {
                onDone?.invoke()
                return@launch
            }

            withContext(Dispatchers.Main) {
                try {
                    isSpeaking = true
                    _state.value = VoiceAssistantState.SPEAKING
                    activeTtsCallback = onDone

                    val utteranceId = UUID.randomUUID().toString()
                    val params = Bundle().apply {
                        putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
                        putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
                        putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
                    }

                    // Ensure minimum audible speech volume if device media stream is too low/muted
                    try {
                        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                        if (audioManager != null) {
                            val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                            if (maxVol > 0 && (currentVol.toFloat() / maxVol) < 0.50f) {
                                val targetVol = (maxVol * 0.70f).toInt().coerceIn(1, maxVol)
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
                                Log.i(TAG, "Adjusted low media stream volume for audible TTS: $currentVol -> $targetVol")
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Audio volume check notice: ${e.message}")
                    }

                    Log.i(TAG, "TTS_STARTED: '$text'")
                    val result = textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
                    if (result != TextToSpeech.SUCCESS) {
                        isSpeaking = false
                        activeTtsCallback = null
                        onDone?.invoke()
                    } else {
                        // Safety timeout: 8s fallback if TTS onDone is not fired by OEM engine
                        scope.launch {
                            delay(8000L)
                            if (isSpeaking) {
                                isSpeaking = false
                                val cb = activeTtsCallback
                                activeTtsCallback = null
                                cb?.invoke()
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "TextToSpeech speak error: ${e.message}")
                    isSpeaking = false
                    activeTtsCallback = null
                    onDone?.invoke()
                }
            }
        }
    }

    fun checkPermissionAndInitialize() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission && _isEnabled.value) {
            _state.value = VoiceAssistantState.PERMISSION_REQUIRED
            openWakeWordManager.stopDetection()
        } else if (_isEnabled.value && _state.value == VoiceAssistantState.PERMISSION_REQUIRED) {
            _state.value = VoiceAssistantState.WAITING_FOR_WAKE
            destroySpeechRecognizerSync()
            openWakeWordManager.startDetection(threshold = wakeThreshold, debounceMs = WAKE_DEBOUNCE_MS)
        }
    }

    fun updatePermissionStatus(granted: Boolean) {
        if (granted) {
            if (_state.value == VoiceAssistantState.PERMISSION_REQUIRED) {
                _state.value = if (_isEnabled.value) VoiceAssistantState.WAITING_FOR_WAKE else VoiceAssistantState.OFF
                if (_isEnabled.value) {
                    destroySpeechRecognizerSync()
                    openWakeWordManager.startDetection(threshold = wakeThreshold, debounceMs = WAKE_DEBOUNCE_MS)
                }
            }
        } else {
            _state.value = VoiceAssistantState.PERMISSION_REQUIRED
            openWakeWordManager.stopDetection()
        }
    }

    /**
     * User primary toggle to turn Voice Assistant ON or OFF.
     */
    fun toggleAssistant(enable: Boolean) {
        if (_isEnabled.value == enable) return
        _isEnabled.value = enable

        if (enable) {
            Log.i(TAG, "VOICE_TOGGLE_ON: Voice Assistant enabled by user")

            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasPermission) {
                Log.w(TAG, "RECORD_AUDIO permission missing; prompting user")
                _state.value = VoiceAssistantState.PERMISSION_REQUIRED
                return
            }

            _state.value = VoiceAssistantState.WAITING_FOR_WAKE
            destroySpeechRecognizerSync()

            // Start foreground service for background mic retention
            try {
                val serviceIntent = Intent(context, VoiceAssistantService::class.java).apply {
                    action = VoiceAssistantService.ACTION_START
                }
                ContextCompat.startForegroundService(context, serviceIntent)
            } catch (e: Exception) {
                Log.w(TAG, "Foreground service start failed: ${e.message}")
            }

            // Start openWakeWord background detector
            openWakeWordManager.startDetection(threshold = wakeThreshold, debounceMs = WAKE_DEBOUNCE_MS)
        } else {
            Log.i(TAG, "VOICE_TOGGLE_OFF: Voice Assistant disabled by user")

            commandTimeoutJob?.cancel()
            destroySpeechRecognizerSync()
            openWakeWordManager.release()

            try {
                textToSpeech?.stop()
            } catch (_: Exception) {}

            _state.value = VoiceAssistantState.OFF
            _currentTranscript.value = null

            // Stop foreground service
            try {
                val serviceIntent = Intent(context, VoiceAssistantService::class.java).apply {
                    action = VoiceAssistantService.ACTION_STOP
                }
                context.stopService(serviceIntent)
            } catch (_: Exception) {}
        }
    }

    /**
     * STAGE 1: WAKE DETECTED
     * Triggered by OpenWakeWord callback when score >= threshold.
     */
    private fun handleWakeWordDetected(score: Float) {
        if (!_isEnabled.value) return
        if (_state.value == VoiceAssistantState.LISTENING ||
            _state.value == VoiceAssistantState.PROCESSING ||
            _state.value == VoiceAssistantState.SPEAKING) {
            return
        }

        Log.i(TAG, "[SEMHAS][VOICE] Wake confidence: ${String.format(Locale.US, "%.4f", score)}")
        Log.i(TAG, "[SEMHAS][VOICE] Wake word detected: Hey Jarvis")
        Log.i(TAG, "VOICE_WAKE_DETECTED: 'Hey Jarvis' recognized with score=${String.format(Locale.US, "%.4f", score)}")

        _lastRecognizedCommand.value = "Hey Jarvis"
        _currentTranscript.value = "Hey Jarvis"
        _state.value = VoiceAssistantState.WAKE_DETECTED

        // Step 1: Temporarily stop wake-word detector to release the microphone hardware
        openWakeWordManager.stopDetection()
        Log.i(TAG, "MIC_HANDOFF: AudioRecord stopped. Waiting ${HANDOFF_DELAY_MS}ms before starting SpeechRecognizer.")

        // Step 2: Transition to LISTENING and launch Android SpeechRecognizer for ONE command only
        scope.launch {
            delay(HANDOFF_DELAY_MS) // Snappy 100ms handoff window
            if (!_isEnabled.value) return@launch

            _state.value = VoiceAssistantState.LISTENING
            _currentTranscript.value = null
            startOneShotCommandListening()
        }
    }

    /**
     * STAGE 2: START ANDROID SPEECHRECOGNIZER FOR ONE COMMAND ONLY
     */
    private fun startOneShotCommandListening() {
        mainHandler.post {
            try {
                if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                    Log.w(TAG, "Speech recognition is not available on this device")
                    finishCommandAndReturnToWaitingForWake()
                    return@post
                }

                destroySpeechRecognizerSync()

                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(createOneShotRecognitionListener())
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                    putExtra("android.speech.extra.PREFER_OFFLINE", true)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1500L)
                }

                voiceCommandStartTimeMs = System.currentTimeMillis()
                Log.i(TAG, "[SEMHAS][VOICE] Starting command listening")
                Log.i(TAG, "VOICE_COMMAND_START")
                speechRecognizer?.startListening(intent)
                isListeningSessionActive = true
                Log.i(TAG, "[SEMHAS][VOICE] Recognition started")
                Log.i(TAG, "COMMAND_LISTENING_STARTED: SpeechRecognizer active and listening for command")

                // Timeout safety: 7 seconds if user does not speak after waking
                commandTimeoutJob?.cancel()
                commandTimeoutJob = scope.launch {
                    delay(COMMAND_TIMEOUT_MS)
                    if (_state.value == VoiceAssistantState.LISTENING) {
                        Log.i(TAG, "Command listening timed out (silence). Returning cleanly to wake mode.")
                        destroySpeechRecognizerSync()
                        finishCommandAndReturnToWaitingForWake()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting SpeechRecognizer: ${e.message}", e)
                destroySpeechRecognizerSync()
                finishCommandAndReturnToWaitingForWake()
            }
        }
    }

    private fun destroySpeechRecognizerSync() {
        try {
            isListeningSessionActive = false
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (_: Exception) {}
    }

    private fun getSpeechErrorString(errorCode: Int): String {
        return when (errorCode) {
            SpeechRecognizer.ERROR_AUDIO -> "ERROR_AUDIO (Audio recording error)"
            SpeechRecognizer.ERROR_CLIENT -> "ERROR_CLIENT (Client side error)"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "ERROR_INSUFFICIENT_PERMISSIONS"
            SpeechRecognizer.ERROR_NETWORK -> "ERROR_NETWORK"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "ERROR_NETWORK_TIMEOUT"
            SpeechRecognizer.ERROR_NO_MATCH -> "ERROR_NO_MATCH (No speech match)"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "ERROR_RECOGNIZER_BUSY"
            SpeechRecognizer.ERROR_SERVER -> "ERROR_SERVER"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "ERROR_SPEECH_TIMEOUT (No speech heard)"
            else -> "UNKNOWN_ERROR ($errorCode)"
        }
    }

    private fun createOneShotRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _state.value = VoiceAssistantState.LISTENING
            }

            override fun onBeginningOfSpeech() {
                commandTimeoutJob?.cancel()
            }

            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                commandTimeoutJob?.cancel()
                isListeningSessionActive = false
            }

            override fun onError(error: Int) {
                commandTimeoutJob?.cancel()
                isListeningSessionActive = false
                destroySpeechRecognizerSync()

                Log.w(TAG, "COMMAND_RECOGNITION_ERROR: code=$error (${getSpeechErrorString(error)})")
                // Return cleanly to wake mode on recognition error or silence without crashing
                finishCommandAndReturnToWaitingForWake()
            }

            override fun onResults(results: Bundle?) {
                commandTimeoutJob?.cancel()
                isListeningSessionActive = false
                destroySpeechRecognizerSync()

                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val transcript = matches?.firstOrNull()?.trim() ?: ""

                if (transcript.isNotBlank()) {
                    val elapsedMs = System.currentTimeMillis() - voiceCommandStartTimeMs
                    Log.i(TAG, "VOICE_FINAL_TRANSCRIPT transcript=\"$transcript\" elapsed_ms=$elapsedMs")
                    Log.i(TAG, "COMMAND_FINAL_RESULT: '$transcript'")
                    Log.i(TAG, "[SEMHAS][VOICE] Recognition result: $transcript")
                    handleOneShotCommand(transcript)
                } else {
                    finishCommandAndReturnToWaitingForWake()
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                commandTimeoutJob?.cancel()
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val partial = matches?.firstOrNull()?.trim() ?: return
                _currentTranscript.value = partial
                Log.d(TAG, "COMMAND_PARTIAL_RESULT: '$partial'")
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    /**
     * STAGE 3: STOP SPEECHRECOGNIZER, PARSE COMMAND, AND EXECUTE
     */
    private fun handleOneShotCommand(transcript: String) {
        if (!_isEnabled.value) return

        Log.i(TAG, "[SEMHAS][VOICE] Command received: $transcript")
        _currentTranscript.value = transcript
        _lastRecognizedCommand.value = "\"$transcript\""
        _state.value = VoiceAssistantState.PROCESSING

        val parsed = VoiceCommandParser.parse(
            transcript = transcript,
            channels = repository.channels.value,
            requireWakePhrase = false // Wake phrase was already verified by OpenWakeWord
        )

        if (parsed is ParsedVoiceResult.Unsupported) {
            Log.w(TAG, "COMMAND_PARSER_NO_MATCH: reason='${parsed.reason}', transcript='$transcript'")
        } else {
            Log.i(TAG, "COMMAND_PARSER_MATCH: intent=${parsed::class.simpleName}, transcript='$transcript'")
        }

        scope.launch {
            executeParsedCommand(parsed, transcript)
        }
    }

    /**
     * STAGE 4: EXECUTE VIA REPOSITORY & GENERATE TTS RESPONSE
     */
    private suspend fun executeParsedCommand(command: ParsedVoiceResult, rawTranscript: String) {
        delay(120L) // Brief UI processing tick

        when (command) {
            // 1. ALL DEVICES ON / OFF
            is ParsedVoiceResult.AllDevices -> {
                val targetState = command.targetState
                val stateSpoken = if (targetState) "on" else "off"
                val stateLabel = if (targetState) "ON" else "OFF"
                val spoken = "All devices are now $stateSpoken."
                val action = "All devices turned $stateLabel"

                try {
                    Log.i(TAG, "REPOSITORY_ACTION_STARTED: $action")
                    repository.setAllChannels(targetState)
                    val dbElapsedMs = System.currentTimeMillis() - voiceCommandStartTimeMs
                    Log.i(TAG, "VOICE_SUPABASE_WRITE_COMPLETE channel=ALL elapsed_ms=$dbElapsedMs")
                    Log.i(TAG, "VOICE_HARDWARE_COMMAND_RECEIVED channel=ALL elapsed_ms=$dbElapsedMs")
                    Log.i(TAG, "REPOSITORY_ACTION_SUCCESS: $action")

                    _lastExecutedAction.value = action
                    _lastSpokenResponse.value = spoken
                    _state.value = VoiceAssistantState.SPEAKING

                    speak(spoken) {
                        finishCommandAndReturnToWaitingForWake()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "REPOSITORY_ACTION_FAILED: $action, error=${e.message}", e)
                    val failSpoken = "I couldn't turn $stateSpoken all devices."
                    _lastExecutedAction.value = "Failed: $action"
                    _lastSpokenResponse.value = failSpoken
                    _state.value = VoiceAssistantState.UNSUPPORTED_COMMAND
                    speak(failSpoken) {
                        finishCommandAndReturnToWaitingForWake()
                    }
                }
            }

            // 2. SINGLE APPLIANCE ON / OFF
            is ParsedVoiceResult.SingleDevice -> {
                val stateLabel = if (command.targetState) "ON" else "OFF"
                val stateSpoken = if (command.targetState) "on" else "off"
                val applianceSpoken = VoiceCommandParser.formatApplianceNameForSpeech(command.channelName)
                val spoken = "Your $applianceSpoken is now $stateSpoken."
                val action = "${command.channelName} turned $stateLabel"

                try {
                    Log.i(TAG, "REPOSITORY_ACTION_STARTED: $action (channelId=${command.channelId})")
                    repository.toggleChannel(command.channelId, command.targetState, "VOICE")
                    val dbElapsedMs = System.currentTimeMillis() - voiceCommandStartTimeMs
                    Log.i(TAG, "VOICE_SUPABASE_WRITE_COMPLETE channel=${command.channelId} elapsed_ms=$dbElapsedMs")
                    Log.i(TAG, "VOICE_HARDWARE_COMMAND_RECEIVED channel=${command.channelId} elapsed_ms=$dbElapsedMs")
                    Log.i(TAG, "REPOSITORY_ACTION_SUCCESS: $action")

                    _lastExecutedAction.value = action
                    _lastSpokenResponse.value = spoken
                    _state.value = VoiceAssistantState.SPEAKING

                    speak(spoken) {
                        finishCommandAndReturnToWaitingForWake()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "REPOSITORY_ACTION_FAILED: $action, error=${e.message}", e)
                    val failSpoken = "I couldn't turn $stateSpoken your $applianceSpoken."
                    _lastExecutedAction.value = "Failed: $action"
                    _lastSpokenResponse.value = failSpoken
                    _state.value = VoiceAssistantState.UNSUPPORTED_COMMAND
                    speak(failSpoken) {
                        finishCommandAndReturnToWaitingForWake()
                    }
                }
            }

            // 3. AMBIGUOUS APPLIANCE CLARIFICATION
            is ParsedVoiceResult.AmbiguousAppliance -> {
                val spoken = VoiceCommandParser.AMBIGUOUS_RESPONSE
                _lastExecutedAction.value = "Ambiguous appliance query (${command.candidateNames.joinToString()})"
                _lastSpokenResponse.value = spoken
                _state.value = VoiceAssistantState.UNSUPPORTED_COMMAND

                speak(spoken) {
                    finishCommandAndReturnToWaitingForWake()
                }
            }

            // 4. CURRENT POWER USAGE QUERY ("What is the current power?")
            is ParsedVoiceResult.QueryCurrentPower -> {
                val totalPower = repository.channels.value.filter { it.relayState }.sumOf { it.power }
                val powerWatts = Math.round(totalPower)
                val spoken = "Your current power usage is $powerWatts watts."

                _lastExecutedAction.value = "Queried current power ($powerWatts W)"
                _lastSpokenResponse.value = spoken
                _state.value = VoiceAssistantState.SPEAKING

                speak(spoken) {
                    finishCommandAndReturnToWaitingForWake()
                }
            }

            // 5. SYSTEM VOLTAGE QUERY ("What is the current voltage?")
            is ParsedVoiceResult.QueryVoltage -> {
                val activeVoltages = repository.channels.value.filter { it.voltage > 0 }.map { it.voltage }
                val avgVoltage = if (activeVoltages.isNotEmpty()) activeVoltages.average() else 0.0
                val voltRounded = Math.round(avgVoltage)
                val spoken = if (voltRounded > 0) "The current voltage is $voltRounded volts." else "The voltage information is not currently available."

                _lastExecutedAction.value = "Queried system voltage ($voltRounded V)"
                _lastSpokenResponse.value = spoken
                _state.value = VoiceAssistantState.SPEAKING

                speak(spoken) {
                    finishCommandAndReturnToWaitingForWake()
                }
            }

            // 6. TOTAL CURRENT / AMPERAGE QUERY ("What is the current?", "How much current am I using?")
            is ParsedVoiceResult.QueryCurrent -> {
                val totalCurrent = repository.channels.value.filter { it.relayState }.sumOf { it.current }
                val formatted = String.format(Locale.US, "%.2f", totalCurrent)
                val spoken = "The total current draw is $formatted amperes."

                _lastExecutedAction.value = "Queried total current ($formatted A)"
                _lastSpokenResponse.value = spoken
                _state.value = VoiceAssistantState.SPEAKING

                speak(spoken) {
                    finishCommandAndReturnToWaitingForWake()
                }
            }

            // 7. PER-APPLIANCE TELEMETRY (Power, Voltage, Current, Runtime)
            is ParsedVoiceResult.QueryApplianceTelemetry -> {
                val channel = repository.channels.value.find { it.channelId == command.channelId }
                val name = VoiceCommandParser.formatApplianceNameForSpeech(command.channelName)

                val spoken = when (command.metric) {
                    TelemetryMetric.POWER -> {
                        val watts = Math.round(channel?.power ?: 0.0)
                        "Your $name is using $watts watts."
                    }
                    TelemetryMetric.VOLTAGE -> {
                        val volts = Math.round(channel?.voltage ?: 0.0)
                        "The voltage on your $name is $volts volts."
                    }
                    TelemetryMetric.CURRENT -> {
                        val amps = String.format(Locale.US, "%.2f", channel?.current ?: 0.0)
                        "Your $name is drawing $amps amps."
                    }
                    TelemetryMetric.RUNTIME -> {
                        val secs = channel?.runtime ?: 0L
                        val mins = (secs / 60) % 60
                        val hours = secs / 3600
                        if (hours > 0) "Your $name has been running for $hours hours and $mins minutes."
                        else "Your $name has been running for $mins minutes."
                    }
                }

                _lastExecutedAction.value = "Queried ${command.channelName} ${command.metric.name.lowercase()}"
                _lastSpokenResponse.value = spoken
                _state.value = VoiceAssistantState.SPEAKING

                speak(spoken) {
                    finishCommandAndReturnToWaitingForWake()
                }
            }

            // 8. PER-APPLIANCE ENERGY QUERY ("How much energy did the fan use?")
            is ParsedVoiceResult.QueryApplianceEnergy -> {
                val channel = repository.channels.value.find { it.channelId == command.channelId }
                val name = VoiceCommandParser.formatApplianceNameForSpeech(command.channelName)
                val energy = String.format(Locale.US, "%.2f", channel?.energy ?: 0.0)
                val spoken = "Your $name has used $energy kilowatt hours."

                _lastExecutedAction.value = "Queried ${command.channelName} energy ($energy kWh)"
                _lastSpokenResponse.value = spoken
                _state.value = VoiceAssistantState.SPEAKING

                speak(spoken) {
                    finishCommandAndReturnToWaitingForWake()
                }
            }

            // 9. HIGHEST POWER CONSUMER QUERY ("What is using the most power?")
            is ParsedVoiceResult.QueryHighestPowerDevice -> {
                val channels = repository.channels.value
                val topActive = channels.filter { it.relayState }.maxByOrNull { it.power }
                val topOverall = channels.maxByOrNull { it.power }
                val topChannel = if (topActive != null && topActive.power > 0) topActive else topOverall

                val spoken = if (topChannel != null && topChannel.power > 0) {
                    val appliance = VoiceCommandParser.formatApplianceNameForSpeech(topChannel.applianceName)
                    val watts = Math.round(topChannel.power)
                    "Your $appliance is using the most power at $watts watts."
                } else {
                    "No appliances are currently consuming significant power."
                }

                _lastExecutedAction.value = "Top consumer query: ${topChannel?.applianceName ?: "None"}"
                _lastSpokenResponse.value = spoken
                _state.value = VoiceAssistantState.SPEAKING

                speak(spoken) {
                    finishCommandAndReturnToWaitingForWake()
                }
            }

            // 10. CURRENT / MONTHLY BILL QUERY ("What is my current bill?")
            is ParsedVoiceResult.QueryMonthlyCost -> {
                val cost = repository.billing.value.estimatedCost
                val costRupees = Math.round(cost)
                val spoken = "Your estimated bill is $costRupees rupees."

                _lastExecutedAction.value = "Queried current bill (₹$costRupees)"
                _lastSpokenResponse.value = spoken
                _state.value = VoiceAssistantState.SPEAKING

                speak(spoken) {
                    finishCommandAndReturnToWaitingForWake()
                }
            }

            // 11. ELECTRICITY RATE QUERY ("What is the current electricity rate?")
            is ParsedVoiceResult.QueryElectricityRate -> {
                val rate = repository.billing.value.ratePerKwh
                val spoken = "The current electricity rate is $rate rupees per unit."

                _lastExecutedAction.value = "Queried electricity rate (₹$rate/kWh)"
                _lastSpokenResponse.value = spoken
                _state.value = VoiceAssistantState.SPEAKING

                speak(spoken) {
                    finishCommandAndReturnToWaitingForWake()
                }
            }

            // 12. PER-APPLIANCE COST QUERY ("How much did the fan cost?")
            is ParsedVoiceResult.QueryApplianceCost -> {
                val channel = repository.channels.value.find { it.channelId == command.channelId }
                val name = VoiceCommandParser.formatApplianceNameForSpeech(command.channelName)
                val cost = Math.round(channel?.estimatedCost ?: 0.0)
                val spoken = "The estimated cost for your $name is $cost rupees."

                _lastExecutedAction.value = "Queried ${command.channelName} cost (₹$cost)"
                _lastSpokenResponse.value = spoken
                _state.value = VoiceAssistantState.SPEAKING

                speak(spoken) {
                    finishCommandAndReturnToWaitingForWake()
                }
            }

            // 13. APPLIANCE COST BREAKDOWN QUERY ("How much did each appliance cost?")
            is ParsedVoiceResult.QueryApplianceCostBreakdown -> {
                val channels = repository.channels.value
                val topItems = channels.sortedByDescending { it.estimatedCost }.take(3)
                val breakdown = topItems.joinToString(", ") { "${VoiceCommandParser.formatApplianceNameForSpeech(it.applianceName)} is ${Math.round(it.estimatedCost)} rupees" }
                val spoken = if (breakdown.isNotBlank()) "Top costs: $breakdown." else "Cost breakdown is not currently available."

                _lastExecutedAction.value = "Queried appliance cost breakdown"
                _lastSpokenResponse.value = spoken
                _state.value = VoiceAssistantState.SPEAKING

                speak(spoken) {
                    finishCommandAndReturnToWaitingForWake()
                }
            }

            // 14. TODAY'S COST QUERY
            is ParsedVoiceResult.QueryTodayCost -> {
                val cost = repository.energyUsage.value.estimatedCost
                val costRupees = Math.round(cost)
                val spoken = "Today's estimated cost is $costRupees rupees."

                _lastExecutedAction.value = "Queried today's cost (₹$costRupees)"
                _lastSpokenResponse.value = spoken
                _state.value = VoiceAssistantState.SPEAKING

                speak(spoken) {
                    finishCommandAndReturnToWaitingForWake()
                }
            }

            // 15. TODAY'S ENERGY QUERY
            is ParsedVoiceResult.QueryTodayEnergy -> {
                val energyKwh = repository.energyUsage.value.totalEnergy
                val formatted = String.format(Locale.US, "%.1f", energyKwh)
                val spoken = "Today's energy usage is $formatted kilowatt hours."

                _lastExecutedAction.value = "Queried today's energy ($formatted kWh)"
                _lastSpokenResponse.value = spoken
                _state.value = VoiceAssistantState.SPEAKING

                speak(spoken) {
                    finishCommandAndReturnToWaitingForWake()
                }
            }

            // 16. DEVICE STATE QUERY
            is ParsedVoiceResult.QueryDeviceState -> {
                val channel = repository.channels.value.find { it.channelId == command.channelId }
                val isChannelOn = channel?.relayState ?: false
                val applianceSpoken = VoiceCommandParser.formatApplianceNameForSpeech(command.channelName)
                val stateSpoken = if (isChannelOn) "on" else "off"
                val spoken = "Your $applianceSpoken is currently $stateSpoken."

                _lastExecutedAction.value = "Queried ${command.channelName} state ($stateSpoken)"
                _lastSpokenResponse.value = spoken
                _state.value = VoiceAssistantState.SPEAKING

                speak(spoken) {
                    finishCommandAndReturnToWaitingForWake()
                }
            }

            // 17. SYSTEM HEALTH QUERY
            is ParsedVoiceResult.QuerySystemHealth -> {
                val isHealthy = repository.device.value.isActive && repository.channels.value.all { it.isHealthy }
                val spoken = if (isHealthy) "The system is healthy and connected to the cloud." else "The system is currently reporting an alert or is offline."

                _lastExecutedAction.value = "Queried system health"
                _lastSpokenResponse.value = spoken
                _state.value = VoiceAssistantState.SPEAKING

                speak(spoken) {
                    finishCommandAndReturnToWaitingForWake()
                }
            }

            // 18. IN-APP NAVIGATION
            is ParsedVoiceResult.NavigateTo -> {
                _navigationTarget.value = command.route
                val spoken = "Opening ${command.destinationName}."

                _lastExecutedAction.value = "Navigating to ${command.destinationName}"
                _lastSpokenResponse.value = spoken
                _state.value = VoiceAssistantState.SPEAKING

                speak(spoken) {
                    finishCommandAndReturnToWaitingForWake()
                }
            }

            // 19. HELP QUERY
            is ParsedVoiceResult.QueryHelp -> {
                val spoken = "You can ask me to turn appliances on or off, check your power usage, voltage, today's energy, estimated bill, system health, or navigate between screens."

                _lastExecutedAction.value = "Queried voice help"
                _lastSpokenResponse.value = spoken
                _state.value = VoiceAssistantState.SPEAKING

                speak(spoken) {
                    finishCommandAndReturnToWaitingForWake()
                }
            }

            // 20. UNSUPPORTED / OUT-OF-SCOPE COMMAND
            is ParsedVoiceResult.Unsupported -> {
                _lastExecutedAction.value = command.reason
                _lastSpokenResponse.value = command.spokenResponse
                _state.value = VoiceAssistantState.UNSUPPORTED_COMMAND

                speak(command.spokenResponse) {
                    finishCommandAndReturnToWaitingForWake()
                }
            }

            // WAKE WORD ONLY / IGNORED
            is ParsedVoiceResult.WakeWordOnly,
            is ParsedVoiceResult.Ignored -> {
                finishCommandAndReturnToWaitingForWake()
            }
        }
    }

    /**
     * STAGE 5: RETURN TO WAKE-WORD LISTENING MODE
     */
    private fun finishCommandAndReturnToWaitingForWake() {
        mainHandler.post {
            destroySpeechRecognizerSync()

            if (_isEnabled.value) {
                _state.value = VoiceAssistantState.WAITING_FOR_WAKE
                _currentTranscript.value = null

                Log.i(TAG, "[SEMHAS][VOICE] Returning to wake detection")
                scope.launch {
                    delay(300L) // Ensure TTS audio has ended before waking microphone
                    if (_isEnabled.value && _state.value == VoiceAssistantState.WAITING_FOR_WAKE) {
                        Log.i(TAG, "[SEMHAS][VOICE] Recognition restarted")
                        openWakeWordManager.startDetection(threshold = wakeThreshold, debounceMs = WAKE_DEBOUNCE_MS)
                    }
                }
            } else {
                _state.value = VoiceAssistantState.OFF
            }
        }
    }

    /**
     * Manual Push-to-Talk Mic button on VoiceScreen.
     */
    fun onMicButtonClicked() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            _state.value = VoiceAssistantState.PERMISSION_REQUIRED
            return
        }

        if (isListeningSessionActive) {
            // Cancel current listening session
            commandTimeoutJob?.cancel()
            destroySpeechRecognizerSync()
            finishCommandAndReturnToWaitingForWake()
        } else {
            // Pause wake detector if active and launch one-shot command listening directly
            openWakeWordManager.stopDetection()
            _state.value = VoiceAssistantState.LISTENING
            _currentTranscript.value = null
            startOneShotCommandListening()
        }
    }

    fun isRunning(): Boolean = _isEnabled.value
}
