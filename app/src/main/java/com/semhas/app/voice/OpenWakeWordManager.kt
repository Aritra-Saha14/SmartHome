package com.semhas.app.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.semhas.app.voice.openwakeword.OpenWakeWord
import java.util.Locale

/**
 * Production wake-word detector manager using the vendored OpenWakeWord engine.
 * Runs on-device detection using ONNX Runtime for "Hey Jarvis".
 * Threshold fixed at 0.50. Lightweight production logs only.
 * Single microphone owner while active.
 */
class OpenWakeWordManager(
    private val context: Context,
    private val onWakeWordDetected: (Float) -> Unit = {}
) {
    companion object {
        const val TAG = "SEMHAS_WAKE"
        const val DEFAULT_THRESHOLD = 0.35f
        const val DEFAULT_DEBOUNCE_MS = 2000L
    }

    private var detector: OpenWakeWord? = null
    @Volatile private var isListening = false
    @Volatile private var lastObservedScore: Float? = null

    /**
     * Starts listening for "Hey Jarvis" wake word.
     * Verifies RECORD_AUDIO permission prior to initialization.
     * Reuses warmed ONNX detector instance for instant response.
     */
    @Synchronized
    fun startDetection(
        threshold: Float = DEFAULT_THRESHOLD,
        debounceMs: Long = DEFAULT_DEBOUNCE_MS
    ) {
        if (isListening) return

        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            Log.w(TAG, "RECORD_AUDIO_PERMISSION_DENIED: Cannot start OpenWakeWord without RECORD_AUDIO.")
            return
        }

        try {
            Log.i(TAG, "OpenWakeWord detector starting: model=HEY_JARVIS, threshold=${String.format(Locale.US, "%.2f", threshold)}")

            if (detector == null) {
                val newDetector = OpenWakeWord.Builder(context)
                    .setModel(OpenWakeWord.BuiltInModel.HEY_JARVIS)
                    .setThreshold(threshold)
                    .setDebounceMs(debounceMs)
                    .build()

                newDetector.setOnScoreListener { score, _, _, _ ->
                    lastObservedScore = score
                }

                detector = newDetector
            }

            detector?.start { score ->
                lastObservedScore = score
                Log.i(TAG, "[SEMHAS][VOICE] Wake confidence: ${String.format(Locale.US, "%.4f", score)}")
                Log.i(TAG, "[SEMHAS][VOICE] Wake word detected: Hey Jarvis")
                onWakeWordDetected(score)
            }

            isListening = true
            Log.i(TAG, "[SEMHAS][VOICE] Listening for Hey Jarvis")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start OpenWakeWord detector: ${e.message}", e)
            stopDetection()
        }
    }

    /**
     * Temporarily pauses wake detection and releases AudioRecord so microphone is available for SpeechRecognizer.
     * Retains initialized ONNX sessions in memory for instant warm resumption.
     */
    @Synchronized
    fun stopDetection() {
        if (!isListening) return

        try {
            detector?.stop()
            Log.i(TAG, "OpenWakeWord audio capture stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping OpenWakeWord detector: ${e.message}", e)
        } finally {
            isListening = false
        }
    }

    /**
     * Fully releases AudioRecord and ONNX inference sessions when Voice Assistant is turned off.
     */
    @Synchronized
    fun release() {
        stopDetection()
        try {
            detector?.release()
            Log.i(TAG, "OpenWakeWord detector and ONNX sessions released")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing OpenWakeWord detector: ${e.message}", e)
        } finally {
            detector = null
        }
    }

    fun isRunning(): Boolean = isListening

    fun getLatestObservedScore(): Float? = lastObservedScore
}
