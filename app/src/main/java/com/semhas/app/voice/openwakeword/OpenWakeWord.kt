package com.semhas.app.voice.openwakeword

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Vendored and optimized openWakeWord engine for SEMHAS.
 *
 * Runs the verified 3-stage ONNX pipeline:
 *  1. Mel-spectrogram: [1, 1760] -> [1, 1, 8, 32] (mel / 10.0 + 2.0)
 *  2. Speech Embedding: [1, 76, 32, 1] -> [1, 1, 1, 96]
 *  3. Wake Word Classifier: [1, 16, 96] -> [1, 1] score
 *
 * Uses Direct ByteBuffers with Native Byte Order for zero-copy ONNX Runtime JNI execution.
 * Full internal observability: AUDIO_STATS, MEL_STATS, EMBED_STATS, WAKE_SCORE.
 */
class OpenWakeWord private constructor(
    private val context: Context,
    private val wakeWordModelSource: ModelSource,
    private val threshold: Float,
    private val debounceMs: Long
) {
    enum class BuiltInModel(internal val assetPath: String, val displayName: String) {
        HEY_JARVIS("openwakeword/hey_jarvis_v0.1.onnx", "Hey Jarvis"),
        ALEXA("openwakeword/alexa_v0.1.onnx", "Alexa"),
        HEY_MYCROFT("openwakeword/hey_mycroft_v0.1.onnx", "Hey Mycroft")
    }

    sealed class ModelSource {
        data class BuiltIn(val model: BuiltInModel) : ModelSource()
        data class Asset(val path: String) : ModelSource()
        data class Raw(val bytes: ByteArray) : ModelSource()
    }

    data class AudioStats(val min: Float, val max: Float, val rms: Float)
    data class MelStats(val min: Float, val max: Float, val mean: Float)
    data class EmbedStats(val min: Float, val max: Float, val mean: Float)

    fun interface OnDetectionListener {
        fun onDetected(score: Float)
    }

    fun interface OnScoreListener {
        fun onScore(score: Float, audioStats: AudioStats, melStats: MelStats, embedStats: EmbedStats)
    }

    class Builder(private val context: Context) {
        private var modelSource: ModelSource = ModelSource.BuiltIn(BuiltInModel.HEY_JARVIS)
        private var threshold = 0.50f
        private var debounceMs = 2000L

        fun setModel(model: BuiltInModel) = apply {
            modelSource = ModelSource.BuiltIn(model)
        }

        fun setModelAsset(assetPath: String) = apply {
            modelSource = ModelSource.Asset(assetPath)
        }

        fun setModelBytes(bytes: ByteArray) = apply {
            modelSource = ModelSource.Raw(bytes)
        }

        fun setThreshold(threshold: Float) = apply {
            this.threshold = threshold.coerceIn(0.01f, 0.99f)
        }

        fun setDebounceMs(ms: Long) = apply {
            this.debounceMs = ms.coerceAtLeast(0)
        }

        fun build(): OpenWakeWord = OpenWakeWord(
            context.applicationContext,
            modelSource,
            threshold,
            debounceMs
        )
    }

    companion object {
        private const val TAG = "OpenWakeWord"
        private const val DIAG_TAG = "SEMHAS_WAKE_PREPROC"
        const val SAMPLE_RATE = 16000
        const val FRAME_SAMPLES = 1280 // 80 ms
        const val MEL_CONTEXT_SAMPLES = 480 // 160 * 3 overlap = 30 ms
        const val TOTAL_MEL_INPUT_SAMPLES = FRAME_SAMPLES + MEL_CONTEXT_SAMPLES // 1760 samples
        const val MEL_BINS = 32
        const val MEL_WINDOW_FRAMES = 76 // 76 mel frames for embedding window
        const val EMBEDDING_DIM = 96
        const val FEATURE_WINDOW = 16 // 16 sequential embeddings for classifier
        const val FEATURE_BUFFER_MAX = 120
        const val MEL_BUFFER_MAX = 970
        const val SKIP_INITIAL_PREDICTIONS = 5
        const val RAW_BUFFER_SECONDS = 10
    }

    // ONNX Runtime State
    private var ortEnv: OrtEnvironment? = null
    private var melSpecSession: OrtSession? = null
    private var embeddingSession: OrtSession? = null
    private var wakeWordSession: OrtSession? = null
    private var wakeWordInputName: String? = null

    // Pre-allocated DIRECT FloatBuffers with NATIVE byte order
    private val melInputDirectBuffer: FloatBuffer = ByteBuffer
        .allocateDirect(TOTAL_MEL_INPUT_SAMPLES * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()

    private val embInputDirectBuffer: FloatBuffer = ByteBuffer
        .allocateDirect(MEL_WINDOW_FRAMES * MEL_BINS * 1 * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()

    private val wwInputDirectBuffer: FloatBuffer = ByteBuffer
        .allocateDirect(FEATURE_WINDOW * EMBEDDING_DIM * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()

    // Audio Capture State
    private var audioRecord: AudioRecord? = null
    private var processingThread: Thread? = null
    @Volatile private var isRunning = false

    // Detection & Score Listeners
    private var detectionListener: OnDetectionListener? = null
    private var scoreListener: OnScoreListener? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private var lastDetectionTime = 0L
    @Volatile var latestScore: Float = 0.0f
        private set
    @Volatile var predictionCount = 0
        private set
    @Volatile var rawTotalWritten = 0L
        private set

    // Internal ring and feature buffers
    private lateinit var rawBuffer: FloatArray
    private var rawWritePos = 0
    private val melBuffer = ArrayList<FloatArray>()
    private val featureBuffer = ArrayList<FloatArray>()

    // Latest statistics for telemetry
    @Volatile var latestAudioStats = AudioStats(0f, 0f, 0f)
        private set
    @Volatile var latestMelStats = MelStats(0f, 0f, 0f)
        private set
    @Volatile var latestEmbedStats = EmbedStats(0f, 0f, 0f)
        private set

    fun setOnScoreListener(listener: OnScoreListener?) {
        this.scoreListener = listener
    }

    /**
     * Start listening on background audio thread.
     */
    fun start(listener: OnDetectionListener) {
        if (isRunning) return
        this.detectionListener = listener
        isRunning = true

        processingThread = Thread({
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO)
            try {
                if (melSpecSession == null) initModels()
                initBuffers()
                if (!initAudioRecord()) {
                    Log.e(TAG, "Failed to initialize AudioRecord – check RECORD_AUDIO permission")
                    return@Thread
                }
                audioLoop()
            } catch (e: Exception) {
                Log.e(TAG, "Fatal error in OpenWakeWord processing thread", e)
            } finally {
                releaseAudioRecord()
            }
        }, "OpenWakeWord")
        processingThread?.start()
    }

    fun stop() {
        isRunning = false
        processingThread?.join(3000)
        processingThread = null
        releaseAudioRecord()
    }

    fun release() {
        stop()
        try {
            wakeWordSession?.close()
            embeddingSession?.close()
            melSpecSession?.close()
            ortEnv?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing ONNX sessions: ${e.message}")
        }
        wakeWordSession = null
        embeddingSession = null
        melSpecSession = null
        ortEnv = null
        detectionListener = null
        scoreListener = null
    }

    private fun initModels() {
        ortEnv = OrtEnvironment.getEnvironment()
        val opts = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(1)
            setInterOpNumThreads(1)
        }

        melSpecSession = ortEnv!!.createSession(
            loadAsset("openwakeword/melspectrogram.onnx"), opts
        )
        embeddingSession = ortEnv!!.createSession(
            loadAsset("openwakeword/embedding_model.onnx"), opts
        )

        val modelBytes = when (val src = wakeWordModelSource) {
            is ModelSource.BuiltIn -> loadAsset(src.model.assetPath)
            is ModelSource.Asset -> loadAsset(src.path)
            is ModelSource.Raw -> src.bytes
        }
        wakeWordSession = ortEnv!!.createSession(modelBytes, opts)
        wakeWordInputName = wakeWordSession!!.inputNames.first()
        Log.i(TAG, "ONNX models initialized successfully. Classifier input: $wakeWordInputName")
    }

    private fun loadAsset(name: String): ByteArray =
        context.assets.open(name).use { it.readBytes() }

    private fun initBuffers() {
        rawBuffer = FloatArray(SAMPLE_RATE * RAW_BUFFER_SECONDS)
        rawWritePos = 0
        rawTotalWritten = 0L

        melBuffer.clear()
        featureBuffer.clear()

        predictionCount = 0
        lastDetectionTime = 0L
        latestScore = 0.0f
    }

    private fun initAudioRecord(): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) return false

        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) return false

        val bufferSize = minBufferSize.coerceAtLeast(FRAME_SAMPLES * 4)

        // Try VOICE_RECOGNITION first, fallback to MIC
        val sources = listOf(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.MIC
        )

        for (source in sources) {
            try {
                val record = AudioRecord(
                    source,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )
                if (record.state == AudioRecord.STATE_INITIALIZED) {
                    audioRecord = record
                    Log.i(TAG, "AudioRecord initialized with audioSource=$source, bufferSize=$bufferSize")
                    return true
                } else {
                    record.release()
                }
            } catch (e: Exception) {
                Log.w(TAG, "AudioRecord init failed for audioSource=$source: ${e.message}")
            }
        }
        return false
    }

    private fun releaseAudioRecord() {
        try { audioRecord?.stop() } catch (_: Exception) {}
        try { audioRecord?.release() } catch (_: Exception) {}
        audioRecord = null
    }

    private fun audioLoop() {
        audioRecord?.startRecording() ?: return
        val frame = ShortArray(FRAME_SAMPLES)

        Log.i(TAG, "Audio loop started. Listening for wake word...")

        var logCounter = 0

        while (isRunning) {
            val read = audioRecord?.read(frame, 0, FRAME_SAMPLES) ?: break
            if (read != FRAME_SAMPLES) continue

            // 1. Raw Audio Scale: Unnormalized int16 magnitude float scale [-32768f, 32767f]
            var minVal = Float.MAX_VALUE
            var maxVal = -Float.MAX_VALUE
            var sumSq = 0.0

            for (sample in frame) {
                val f = sample.toFloat()
                if (f < minVal) minVal = f
                if (f > maxVal) maxVal = f
                sumSq += (f * f)

                rawBuffer[rawWritePos] = f
                rawWritePos = (rawWritePos + 1) % rawBuffer.size
            }
            rawTotalWritten += frame.size

            val rms = sqrt(sumSq / frame.size).toFloat()
            latestAudioStats = AudioStats(minVal, maxVal, rms)

            if (rawTotalWritten < TOTAL_MEL_INPUT_SAMPLES) continue

            // 2. Extract last 1760 samples (1280 current frame + 480 overlap)
            val audioSlice = getLastNSamples(TOTAL_MEL_INPUT_SAMPLES)

            // 3. Compute Mel-spectrogram -> shape [1, 1, 8, 32]
            val newMelFrames = computeMelSpectrogram(audioSlice) ?: continue

            for (melFrame in newMelFrames) melBuffer.add(melFrame)
            while (melBuffer.size > MEL_BUFFER_MAX) melBuffer.removeAt(0)

            // 4. Compute Embedding when at least 76 mel frames accumulated
            if (melBuffer.size >= MEL_WINDOW_FRAMES) {
                val start = melBuffer.size - MEL_WINDOW_FRAMES
                val melWindow = melBuffer.subList(start, melBuffer.size)

                val embedding = computeEmbedding(melWindow) ?: continue
                featureBuffer.add(embedding)
                while (featureBuffer.size > FEATURE_BUFFER_MAX) featureBuffer.removeAt(0)
            }

            // 5. Run Wake Word Classifier when at least 16 feature frames accumulated
            if (featureBuffer.size >= FEATURE_WINDOW) {
                predictionCount++
                if (predictionCount <= SKIP_INITIAL_PREDICTIONS) continue

                val fStart = featureBuffer.size - FEATURE_WINDOW
                val features = featureBuffer.subList(fStart, featureBuffer.size)

                val score = runWakeWordModel(features)
                latestScore = score

                logCounter++
                if (logCounter % 10 == 0 || score >= threshold) { // Log stats throttled ~every 800ms
                    Log.i(DIAG_TAG, "AUDIO_STATS: min=${String.format(Locale.US, "%.1f", latestAudioStats.min)}, max=${String.format(Locale.US, "%.1f", latestAudioStats.max)}, rms=${String.format(Locale.US, "%.1f", latestAudioStats.rms)}")
                    Log.i(DIAG_TAG, "MEL_STATS: min=${String.format(Locale.US, "%.4f", latestMelStats.min)}, max=${String.format(Locale.US, "%.4f", latestMelStats.max)}, mean=${String.format(Locale.US, "%.4f", latestMelStats.mean)}")
                    Log.i(DIAG_TAG, "EMBED_STATS: min=${String.format(Locale.US, "%.4f", latestEmbedStats.min)}, max=${String.format(Locale.US, "%.4f", latestEmbedStats.max)}, mean=${String.format(Locale.US, "%.4f", latestEmbedStats.mean)}")
                    Log.i(DIAG_TAG, "WAKE_SCORE: score=${String.format(Locale.US, "%.4f", score)} | threshold=${String.format(Locale.US, "%.2f", threshold)}")
                }

                scoreListener?.onScore(score, latestAudioStats, latestMelStats, latestEmbedStats)

                if (score >= threshold) {
                    val now = System.currentTimeMillis()
                    if (now - lastDetectionTime > debounceMs) {
                        lastDetectionTime = now
                        Log.i(TAG, "WAKE_WORD_DETECTED: 'Hey Jarvis' recognized with score=$score >= $threshold")
                        val cb = detectionListener
                        mainHandler.post { cb?.onDetected(score) }
                    }
                }
            }
        }
    }

    private fun getLastNSamples(n: Int): FloatArray {
        val result = FloatArray(n)
        val available = minOf(n.toLong(), rawBuffer.size.toLong(), rawTotalWritten).toInt()
        var readPos = (rawWritePos - available + rawBuffer.size) % rawBuffer.size
        for (i in 0 until available) {
            result[n - available + i] = rawBuffer[readPos]
            readPos = (readPos + 1) % rawBuffer.size
        }
        return result
    }

    /**
     * Stage 1: Mel-spectrogram inference.
     * Input: [1, 1760] -> Output: [1, 1, 8, 32]
     * Output transform: mel / 10.0f + 2.0f
     */
    private fun computeMelSpectrogram(audio: FloatArray): List<FloatArray>? {
        val env = ortEnv ?: return null
        val session = melSpecSession ?: return null

        return try {
            melInputDirectBuffer.clear()
            melInputDirectBuffer.put(audio)
            melInputDirectBuffer.flip()

            OnnxTensor.createTensor(
                env, melInputDirectBuffer, longArrayOf(1, audio.size.toLong())
            ).use { input ->
                session.run(mapOf("input" to input)).use { result ->
                    val output = result[0] as OnnxTensor
                    val shape = output.info.shape // [1, 1, 8, 32]
                    val flat = output.floatBuffer
                    val numFrames = shape[2].toInt()
                    val numBins = shape[3].toInt()

                    var melMin = Float.MAX_VALUE
                    var melMax = -Float.MAX_VALUE
                    var melSum = 0.0

                    val frames = ArrayList<FloatArray>(numFrames)
                    for (f in 0 until numFrames) {
                        val melFrame = FloatArray(numBins)
                        for (b in 0 until numBins) {
                            val rawVal = flat.get(f * numBins + b)
                            val transformed = rawVal / 10.0f + 2.0f
                            melFrame[b] = transformed
                            if (transformed < melMin) melMin = transformed
                            if (transformed > melMax) melMax = transformed
                            melSum += transformed
                        }
                        frames.add(melFrame)
                    }

                    val melMean = (melSum / (numFrames * numBins)).toFloat()
                    latestMelStats = MelStats(melMin, melMax, melMean)
                    frames
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Mel spectrogram inference error: ${e.message}", e)
            null
        }
    }

    /**
     * Stage 2: Audio embedding inference.
     * Input: [1, 76, 32, 1] -> Output: [1, 1, 1, 96]
     */
    private fun computeEmbedding(melWindow: List<FloatArray>): FloatArray? {
        val env = ortEnv ?: return null
        val session = embeddingSession ?: return null

        return try {
            embInputDirectBuffer.clear()
            for (i in 0 until MEL_WINDOW_FRAMES) {
                embInputDirectBuffer.put(melWindow[i])
            }
            embInputDirectBuffer.flip()

            OnnxTensor.createTensor(
                env, embInputDirectBuffer,
                longArrayOf(1, MEL_WINDOW_FRAMES.toLong(), MEL_BINS.toLong(), 1)
            ).use { input ->
                session.run(mapOf("input_1" to input)).use { result ->
                    val buf = (result[0] as OnnxTensor).floatBuffer
                    val emb = FloatArray(EMBEDDING_DIM)

                    var eMin = Float.MAX_VALUE
                    var eMax = -Float.MAX_VALUE
                    var eSum = 0.0

                    for (i in 0 until EMBEDDING_DIM) {
                        val v = buf.get(i)
                        emb[i] = v
                        if (v < eMin) eMin = v
                        if (v > eMax) eMax = v
                        eSum += v
                    }

                    val eMean = (eSum / EMBEDDING_DIM).toFloat()
                    latestEmbedStats = EmbedStats(eMin, eMax, eMean)
                    emb
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Embedding model inference error: ${e.message}", e)
            null
        }
    }

    /**
     * Stage 3: Wake word classifier inference.
     * Input: [1, 16, 96] -> Output: [1, 1] float score
     */
    private fun runWakeWordModel(features: List<FloatArray>): Float {
        val env = ortEnv ?: return 0f
        val session = wakeWordSession ?: return 0f
        val inputName = wakeWordInputName ?: return 0f

        return try {
            wwInputDirectBuffer.clear()
            for (i in 0 until FEATURE_WINDOW) {
                wwInputDirectBuffer.put(features[i])
            }
            wwInputDirectBuffer.flip()

            OnnxTensor.createTensor(
                env, wwInputDirectBuffer,
                longArrayOf(1, FEATURE_WINDOW.toLong(), EMBEDDING_DIM.toLong())
            ).use { input ->
                session.run(mapOf(inputName to input)).use { result ->
                    val score = (result[0] as OnnxTensor).floatBuffer.get(0)
                    score
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Wake word classifier inference error: ${e.message}", e)
            0f
        }
    }

    /**
     * Deterministic in-memory control test:
     * Records 5 seconds (80,000 samples) of PCM into memory (no remote upload).
     * Runs the exact ONNX pipeline frame-by-frame and exports a diagnostic test WAV file locally.
     */
    fun runDeterministicControlTest(
        onProgress: (Int, Float) -> Unit = { _, _ -> },
        onComplete: (maxScore: Float, wavPath: String) -> Unit
    ) {
        Thread({
            Log.i(TAG, "Starting deterministic 5-second control test recording...")
            val testBuffer = ShortArray(SAMPLE_RATE * 5) // 5 seconds = 80,000 samples
            val record = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                testBuffer.size * 2
            )

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "Control test failed: AudioRecord not initialized")
                return@Thread
            }

            record.startRecording()
            var offset = 0
            while (offset < testBuffer.size) {
                val read = record.read(testBuffer, offset, minOf(FRAME_SAMPLES, testBuffer.size - offset))
                if (read <= 0) break
                offset += read
            }
            record.stop()
            record.release()

            Log.i(TAG, "Control test recorded $offset samples. Saving test WAV locally to internal storage...")
            val wavFile = File(context.filesDir, "semhas_wake_test.wav")
            savePcmToWav(testBuffer, wavFile, SAMPLE_RATE)
            Log.i(TAG, "Control test WAV saved to ${wavFile.absolutePath} (size: ${wavFile.length()} bytes)")

            // Run ONNX pipeline deterministically on recorded PCM
            if (melSpecSession == null) initModels()
            initBuffers()

            var maxScore = 0.0f
            val chunk = ShortArray(FRAME_SAMPLES)
            val numChunks = testBuffer.size / FRAME_SAMPLES

            for (c in 0 until numChunks) {
                System.arraycopy(testBuffer, c * FRAME_SAMPLES, chunk, 0, FRAME_SAMPLES)

                for (s in chunk) {
                    rawBuffer[rawWritePos] = s.toFloat()
                    rawWritePos = (rawWritePos + 1) % rawBuffer.size
                }
                rawTotalWritten += chunk.size

                if (rawTotalWritten < TOTAL_MEL_INPUT_SAMPLES) continue
                val audioSlice = getLastNSamples(TOTAL_MEL_INPUT_SAMPLES)
                val newMelFrames = computeMelSpectrogram(audioSlice) ?: continue
                for (mf in newMelFrames) melBuffer.add(mf)
                while (melBuffer.size > MEL_BUFFER_MAX) melBuffer.removeAt(0)

                if (melBuffer.size >= MEL_WINDOW_FRAMES) {
                    val start = melBuffer.size - MEL_WINDOW_FRAMES
                    val melWindow = melBuffer.subList(start, melBuffer.size)
                    val embedding = computeEmbedding(melWindow) ?: continue
                    featureBuffer.add(embedding)
                    while (featureBuffer.size > FEATURE_BUFFER_MAX) featureBuffer.removeAt(0)
                }

                if (featureBuffer.size >= FEATURE_WINDOW) {
                    predictionCount++
                    val fStart = featureBuffer.size - FEATURE_WINDOW
                    val features = featureBuffer.subList(fStart, featureBuffer.size)
                    val score = runWakeWordModel(features)
                    if (score > maxScore) maxScore = score
                    onProgress(c, score)
                }
            }

            Log.i(TAG, "Control test completed. Max score: $maxScore. WAV: ${wavFile.absolutePath}")
            mainHandler.post { onComplete(maxScore, wavFile.absolutePath) }
        }, "OWW-ControlTest").start()
    }

    private fun savePcmToWav(pcmData: ShortArray, outputFile: File, sampleRate: Int) {
        val totalAudioLen = pcmData.size * 2L
        val totalDataLen = totalAudioLen + 36
        val channels = 1
        val byteRate = 16 * sampleRate * channels / 8

        FileOutputStream(outputFile).use { out ->
            // RIFF header
            out.write("RIFF".toByteArray())
            out.write(intToByteArray(totalDataLen.toInt()))
            out.write("WAVE".toByteArray())
            out.write("fmt ".toByteArray())
            out.write(intToByteArray(16)) // Subchunk1Size
            out.write(shortToByteArray(1)) // AudioFormat = 1 (PCM)
            out.write(shortToByteArray(channels.toShort()))
            out.write(intToByteArray(sampleRate))
            out.write(intToByteArray(byteRate))
            out.write(shortToByteArray((channels * 2).toShort())) // BlockAlign
            out.write(shortToByteArray(16)) // BitsPerSample
            out.write("data".toByteArray())
            out.write(intToByteArray(totalAudioLen.toInt()))

            val byteBuffer = ByteBuffer.allocate(pcmData.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            for (s in pcmData) byteBuffer.putShort(s)
            out.write(byteBuffer.array())
        }
    }

    private fun intToByteArray(v: Int): ByteArray = byteArrayOf(
        (v and 0xff).toByte(),
        ((v shr 8) and 0xff).toByte(),
        ((v shr 16) and 0xff).toByte(),
        ((v shr 24) and 0xff).toByte()
    )

    private fun shortToByteArray(v: Short): ByteArray = byteArrayOf(
        (v.toInt() and 0xff).toByte(),
        ((v.toInt() shr 8) and 0xff).toByte()
    )
}
