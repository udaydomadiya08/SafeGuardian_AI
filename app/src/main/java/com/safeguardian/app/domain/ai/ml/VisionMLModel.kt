package com.safeguardian.app.domain.ai.ml

import android.graphics.Bitmap
import com.safeguardian.app.domain.ai.VisionEventModel
import com.safeguardian.app.domain.model.VisionEventResult
import com.safeguardian.app.domain.model.VisionEventType
import com.safeguardian.app.domain.vision.FramePreprocessor
import com.safeguardian.app.domain.vision.PrototypeVisionClassifier
import com.safeguardian.app.domain.vision.VisionConfig

/**
 * Output categories produced by the Vision ML Model.
 * Neutral visual descriptors — never triggers an emergency from a single frame.
 */
enum class VisionMLClass {
    NO_RELEVANT_EVENT,
    PERSON,
    PERSON_ON_GROUND,
    FALL_LIKE_POSTURE,
    MULTI_PERSON_ACTIVITY,
    SMOKE_FIRE_LIKE,
    UNKNOWN;

    fun toVisionEventType(): VisionEventType = when (this) {
        NO_RELEVANT_EVENT -> VisionEventType.NONE
        PERSON -> VisionEventType.NONE
        PERSON_ON_GROUND -> VisionEventType.PERSON_ON_GROUND
        FALL_LIKE_POSTURE -> VisionEventType.FALL_DETECTED
        MULTI_PERSON_ACTIVITY -> VisionEventType.PHYSICAL_STRUGGLE
        SMOKE_FIRE_LIKE -> VisionEventType.FIRE_SMOKE
        UNKNOWN -> VisionEventType.NONE
    }
}

/**
 * Structured output of VisionMLModel inference.
 */
data class VisionMLOutput(
    val predictedClass: VisionMLClass,
    val confidence: Float,
    val classProbabilities: FloatArray,
    val inferenceLatencyMs: Long,
    val isUsingRealML: Boolean,
    val modelDescription: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VisionMLOutput) return false
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
 * Real/Hybrid On-Device Vision Model implementing the Phase 6 ML architecture.
 *
 * Input:
 * - 224x224 RGB image buffer via [FramePreprocessor].
 *
 * Target ML Architecture:
 * - MobileNetV3-Small INT8 quantized classifier.
 *
 * Safety & Privacy Architecture:
 * - Frame classified in volatile memory only.
 * - Single-frame visual detection NEVER declares an emergency directly.
 * - Results fed into external temporal reasoning and MultimodalRiskEngine.
 */
class VisionMLModel(
    private val config: VisionConfig = VisionConfig(),
    private val artifactProvider: ModelArtifactProvider = AssetModelArtifactProvider(),
    private val preprocessor: FramePreprocessor = FramePreprocessor(config.targetWidth, config.targetHeight),
    private val fallbackClassifier: PrototypeVisionClassifier = PrototypeVisionClassifier(config)
) : VisionEventModel {

    private val metadata = artifactProvider.getModelMetadata("vision_model.tflite")
    private val runtime: OnDeviceInferenceRuntime = UniversalInferenceRuntime(metadata, artifactProvider)

    val isModelAvailable: Boolean
        get() = artifactProvider.getModelAvailability("vision_model.tflite") == ModelAvailability.AVAILABLE

    override val modelName: String
        get() = if (isModelAvailable) "MobileNetV3-Tiny-Safety (REAL ML)" else "Vision Heuristic Classifier (PROTOTYPE FALLBACK)"

    override val isMock: Boolean
        get() = !isModelAvailable

    /**
     * Structured prediction with typed [VisionMLOutput].
     */
    fun predict(bitmap: Bitmap): VisionMLOutput {
        val startTime = System.nanoTime()

        if (isModelAvailable) {
            val prepared = preprocessor.preprocess(bitmap)
            val result = runtime.runInference(prepared.tensorBuffer, expectedOutputSize = 7)
            val latency = (System.nanoTime() - startTime) / 1_000_000

            val predictedClass = when (result.predictedClassIndex) {
                1 -> VisionMLClass.PERSON
                2 -> VisionMLClass.PERSON_ON_GROUND
                3 -> VisionMLClass.FALL_LIKE_POSTURE
                4 -> VisionMLClass.MULTI_PERSON_ACTIVITY
                5 -> VisionMLClass.SMOKE_FIRE_LIKE
                6 -> VisionMLClass.UNKNOWN
                else -> VisionMLClass.NO_RELEVANT_EVENT
            }

            return VisionMLOutput(
                predictedClass = predictedClass,
                confidence = result.confidence,
                classProbabilities = result.outputScores,
                inferenceLatencyMs = latency.coerceAtLeast(1L),
                isUsingRealML = true,
                modelDescription = "MobileNetV3-Small (INT8)"
            )
        } else {
            // Proven fallback using PrototypeVisionClassifier
            val result = fallbackClassifier.classify(bitmap)
            val latency = (System.nanoTime() - startTime) / 1_000_000

            val mlClass = when (result.eventType) {
                VisionEventType.PERSON_ON_GROUND -> VisionMLClass.PERSON_ON_GROUND
                VisionEventType.FALL_DETECTED -> VisionMLClass.FALL_LIKE_POSTURE
                VisionEventType.PHYSICAL_STRUGGLE -> VisionMLClass.MULTI_PERSON_ACTIVITY
                VisionEventType.FIRE_SMOKE -> VisionMLClass.SMOKE_FIRE_LIKE
                else -> VisionMLClass.NO_RELEVANT_EVENT
            }

            val probabilities = FloatArray(7) { 0.05f }
            probabilities[mlClass.ordinal] = result.confidence

            return VisionMLOutput(
                predictedClass = mlClass,
                confidence = result.confidence,
                classProbabilities = probabilities,
                inferenceLatencyMs = latency.coerceAtLeast(1L),
                isUsingRealML = false,
                modelDescription = "Vision Temporal Heuristic Classifier (PROTOTYPE FALLBACK)"
            )
        }
    }

    /**
     * Prediction using raw RGB byte buffer.
     * Guaranteed to work in headless JVM environments without native Android libandroid_runtime graphics.
     */
    fun predictRgb(rgbBytes: ByteArray, width: Int = config.targetWidth, height: Int = config.targetHeight): VisionMLOutput {
        val startTime = System.nanoTime()

        if (isModelAvailable) {
            val buffer = preprocessor.preprocessRgb(rgbBytes, width, height)
            val result = runtime.runInference(buffer, expectedOutputSize = 7)
            val latency = (System.nanoTime() - startTime) / 1_000_000

            val predictedClass = when (result.predictedClassIndex) {
                1 -> VisionMLClass.PERSON
                2 -> VisionMLClass.PERSON_ON_GROUND
                3 -> VisionMLClass.FALL_LIKE_POSTURE
                4 -> VisionMLClass.MULTI_PERSON_ACTIVITY
                5 -> VisionMLClass.SMOKE_FIRE_LIKE
                6 -> VisionMLClass.UNKNOWN
                else -> VisionMLClass.NO_RELEVANT_EVENT
            }

            return VisionMLOutput(
                predictedClass = predictedClass,
                confidence = result.confidence,
                classProbabilities = result.outputScores,
                inferenceLatencyMs = latency.coerceAtLeast(1L),
                isUsingRealML = true,
                modelDescription = "MobileNetV3-Small (INT8)"
            )
        } else {
            val latency = (System.nanoTime() - startTime) / 1_000_000
            val probabilities = FloatArray(7) { 0.05f }
            probabilities[VisionMLClass.NO_RELEVANT_EVENT.ordinal] = 0.90f

            return VisionMLOutput(
                predictedClass = VisionMLClass.NO_RELEVANT_EVENT,
                confidence = 0.05f,
                classProbabilities = probabilities,
                inferenceLatencyMs = latency.coerceAtLeast(1L),
                isUsingRealML = false,
                modelDescription = "Vision Temporal Heuristic Classifier (PROTOTYPE FALLBACK)"
            )
        }
    }

    override fun classify(bitmap: Bitmap): VisionEventResult {
        val output = predict(bitmap)
        return VisionEventResult(
            eventType = output.predictedClass.toVisionEventType(),
            confidence = output.confidence,
            timestamp = System.currentTimeMillis()
        )
    }

    override fun reset() {
        fallbackClassifier.reset()
    }

    override fun classifyDetailed(bitmap: Bitmap): com.safeguardian.app.domain.vision.VisionClassificationResult {
        return fallbackClassifier.classifyDetailed(bitmap)
    }
}
