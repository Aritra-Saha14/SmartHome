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
        const val DEFAULT_THRESHOLD = 0.50f
        const val DEFAULT_DEBOUNCE_MS = 2000L
    }

    private var detector: OpenWakeWord? = null
    @Volatile private var isListening = false
    @Volatile private var lastObservedScore: Float? = null

    /**
     * Starts listening for "Hey Jarvis" wake word.
     * Verifies RECORD_AUDIO permission prior to initialization.
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

            val newDetector = OpenWakeWord.Builder(context)
                .setModel(OpenWakeWord.BuiltInModel.HEY_JARVIS)
                .setThreshold(threshold)
                .setDebounceMs(debounceMs)
                .build()

            newDetector.setOnScoreListener { score, _, _, _ ->
                lastObservedScore = score
            }

            detector = newDetector

            newDetector.start { score ->
                lastObservedScore = score
                Log.i(TAG, "WAKE_WORD_DETECTED: 'Hey Jarvis' recognized with score=${String.format(Locale.US, "%.4f", score)}")
                onWakeWordDetected(score)
            }

            isListening = true
            Log.i(TAG, "OpenWakeWord detector started and actively listening for 'Hey Jarvis'")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start OpenWakeWord detector: ${e.message}", e)
            stopDetection()
        }
    }

    /**
     * Stops detection and releases AudioRecord and ONNX inference session resources.
     */
    @Synchronized
    fun stopDetection() {
        if (!isListening && detector == null) return

        try {
            detector?.stop()
            detector?.release()
            Log.i(TAG, "OpenWakeWord detector stopped successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping OpenWakeWord detector: ${e.message}", e)
        } finally {
            detector = null
            isListening = false
        }
    }

    fun isRunning(): Boolean = isListening

    fun getLatestObservedScore(): Float? = lastObservedScore
}
