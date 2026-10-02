package com.safeguardian.app.domain.ai.ml

import com.safeguardian.app.domain.ai.AudioEventModel
import com.safeguardian.app.domain.audio.AudioConfig
import com.safeguardian.app.domain.audio.AudioEvent
import com.safeguardian.app.domain.audio.AudioFeatureExtractor
import com.safeguardian.app.domain.audio.AudioFeatures
import com.safeguardian.app.domain.audio.PrototypeAudioClassifier
import com.safeguardian.app.domain.model.AudioEventResult
import com.safeguardian.app.domain.model.AudioEventType

/**
 * Output categories produced by the Audio ML Model.
 * Neutral acoustic descriptors — does NOT claim definitive violence or forensic detection.
 */
enum class AudioMLClass {
    NORMAL,
    SHOUT,
    DISTRESS_LIKE,
    IMPACT_SOUND,
    GLASS_BREAK_LIKE,
    ALARM_LIKE,
    UNKNOWN;

    fun toAudioEventType(): AudioEventType = when (this) {
        NORMAL -> AudioEventType.NONE
        SHOUT -> AudioEventType.LOUD_ABNORMAL
        DISTRESS_LIKE -> AudioEventType.SCREAM_DISTRESS
        IMPACT_SOUND -> AudioEventType.CRASH_IMPACT
        GLASS_BREAK_LIKE -> AudioEventType.GLASS_BREAKING
        ALARM_LIKE -> AudioEventType.POTENTIALLY_DANGEROUS
        UNKNOWN -> AudioEventType.UNKNOWN_ANOMALY
    }
}

/**
 * Structured output of AudioMLModel inference.
 */
data class AudioMLOutput(
    val predictedClass: AudioMLClass,
    val confidence: Float,
    val classProbabilities: FloatArray,
    val inferenceLatencyMs: Long,
    val isUsingRealML: Boolean,
    val modelDescription: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AudioMLOutput) return false
        if (predictedClass != other.predictedClass) return false
        if (confidence != other.confidence) return false
        if (!classProbabilities.contentEquals(other.classProbabilities)) return false
        if (inferenceLatencyMs != other.inferenceLatencyMs) return false
        if (isUsingRealML != other.isUsingRealML) return false
        return true
    }

    override fun hashCode(): Int {
        var result = predictedClass.hashCode()
        result = 31 * result + confidence.hashCode()
        result = 31 * result + classProbabilities.contentHashCode()
        result = 31 * result + inferenceLatencyMs.hashCode()
        result = 31 * result + isUsingRealML.hashCode()
        return result
    }
}

/**
 * Real/Hybrid On-Device Audio Model implementing the Phase 6 ML architecture.
 *
 * Input:
 * - Compact 16-band log-mel energy tensor extracted by [AudioFeatureExtractor].
 *
 * Target ML Architecture:
 * - Depthwise separable 2D CNN / CRNN operating on compact log-mel features.
 *
 * Privacy Guarantees:
 * - Audio buffer remains in RAM only; discarded immediately after inference.
 * - Zero network transmission.
 * - Zero disk persistence.
 */
class AudioMLModel(
    private val config: AudioConfig = AudioConfig(),
    private val artifactProvider: ModelArtifactProvider = AssetModelArtifactProvider(),
    private val featureExtractor: AudioFeatureExtractor = AudioFeatureExtractor(
        fftSize = config.frameSizeSamples,
        sampleRateHz = config.sampleRateHz,
        numMelBands = config.melBands
    ),
    private val fallbackClassifier: PrototypeAudioClassifier = PrototypeAudioClassifier(config)
) : AudioEventModel {

    private val metadata = artifactProvider.getModelMetadata("audio_model.tflite")
    private val runtime: OnDeviceInferenceRuntime = UniversalInferenceRuntime(metadata, artifactProvider)

    val isModelAvailable: Boolean
        get() = artifactProvider.getModelAvailability("audio_model.tflite") == ModelAvailability.AVAILABLE

    override val modelName: String
        get() = if (isModelAvailable) "Tiny Log-Mel Audio CRNN (REAL ML)" else "Audio Filterbank Heuristic (PROTOTYPE FALLBACK)"

    override val isMock: Boolean
        get() = !isModelAvailable

    /**
     * Extracts a compact 16x16 float tensor (or 256 floats) representing log-mel energies over time.
     */
    fun extractMelTensor(features: AudioFeatures): FloatArray {
        // Mel energies length = 16. Pad or replicate across time window to match expected input dimension [1, 16, 16] = 256 elements
        val tensor = FloatArray(256)
        for (i in 0 until 16) {
            val melVal = if (i < features.melEnergies.size) features.melEnergies[i] else 0f
            for (t in 0 until 16) {
                tensor[t * 16 + i] = melVal
            }
        }
        return tensor
    }

    /**
     * Structured prediction with typed [AudioMLOutput].
     */
    fun predict(audioBuffer: ShortArray, sampleRate: Int): AudioMLOutput {
        val startTime = System.nanoTime()

        if (audioBuffer.isEmpty()) {
            return AudioMLOutput(
                predictedClass = AudioMLClass.NORMAL,
                confidence = 0.05f,
                classProbabilities = FloatArray(7) { if (it == 0) 0.95f else 0.01f },
                inferenceLatencyMs = 0L,
                isUsingRealML = isModelAvailable,
                modelDescription = modelName
            )
        }

        if (isModelAvailable) {
            val features = featureExtractor.extract(audioBuffer, audioBuffer.size)
            val melTensor = extractMelTensor(features)
            val result = runtime.runInferenceFloats(melTensor, expectedOutputSize = 7)
            val latency = (System.nanoTime() - startTime) / 1_000_000

            val predictedClass = when (result.predictedClassIndex) {
                1 -> AudioMLClass.SHOUT
                2 -> AudioMLClass.DISTRESS_LIKE
                3 -> AudioMLClass.IMPACT_SOUND
                4 -> AudioMLClass.GLASS_BREAK_LIKE
                5 -> AudioMLClass.ALARM_LIKE
                6 -> AudioMLClass.UNKNOWN
                else -> AudioMLClass.NORMAL
            }

            return AudioMLOutput(
                predictedClass = predictedClass,
                confidence = result.confidence,
                classProbabilities = result.outputScores,
                inferenceLatencyMs = latency.coerceAtLeast(1L),
                isUsingRealML = true,
                modelDescription = "Separable 2D CNN (INT8)"
            )
        } else {
            // Proven fallback using PrototypeAudioClassifier
            val result = fallbackClassifier.classify(audioBuffer, sampleRate)
            val latency = (System.nanoTime() - startTime) / 1_000_000

            val mlClass = when (result.eventType) {
                AudioEventType.SCREAM_DISTRESS -> AudioMLClass.DISTRESS_LIKE
                AudioEventType.CRASH_IMPACT -> AudioMLClass.IMPACT_SOUND
                AudioEventType.GLASS_BREAKING -> AudioMLClass.GLASS_BREAK_LIKE
                AudioEventType.LOUD_ABNORMAL -> AudioMLClass.SHOUT
                AudioEventType.POTENTIALLY_DANGEROUS -> AudioMLClass.ALARM_LIKE
                AudioEventType.UNKNOWN_ANOMALY -> AudioMLClass.UNKNOWN
                else -> AudioMLClass.NORMAL
            }

            val probabilities = FloatArray(7) { 0.05f }
            probabilities[mlClass.ordinal] = result.confidence

            return AudioMLOutput(
                predictedClass = mlClass,
                confidence = result.confidence,
                classProbabilities = probabilities,
                inferenceLatencyMs = latency.coerceAtLeast(1L),
                isUsingRealML = false,
                modelDescription = "Acoustic Feature Extractor + Filterbank (PROTOTYPE FALLBACK)"
            )
        }
    }

    override fun classify(audioBuffer: ShortArray, sampleRate: Int): AudioEventResult {
        val output = predict(audioBuffer, sampleRate)
        return AudioEventResult(
            eventType = output.predictedClass.toAudioEventType(),
            confidence = output.confidence,
            timestamp = System.currentTimeMillis(),
            durationMs = (audioBuffer.size.toLong() * 1000) / sampleRate
        )
    }

    override fun classifyDetailed(audioBuffer: ShortArray, sampleRate: Int): AudioEvent {
        return fallbackClassifier.classifyDetailed(audioBuffer, sampleRate)
    }
}
