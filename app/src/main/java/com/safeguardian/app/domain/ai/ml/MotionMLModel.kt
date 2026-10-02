package com.safeguardian.app.domain.ai.ml

import com.safeguardian.app.domain.ai.MotionModel
import com.safeguardian.app.domain.model.MotionAnomalyResult
import com.safeguardian.app.domain.model.MotionAnomalyType
import com.safeguardian.app.domain.model.MotionFeatures
import com.safeguardian.app.domain.motion.MotionAnomalyDetector

/**
 * Output categories produced by the Motion ML Model.
 */
enum class MotionMLClass {
    NORMAL,
    FALL,
    IMPACT,
    UNUSUAL_MOTION;

    fun toMotionAnomalyType(): MotionAnomalyType = when (this) {
        NORMAL -> MotionAnomalyType.NONE
        FALL -> MotionAnomalyType.FALL_LIKE_MOTION
        IMPACT -> MotionAnomalyType.SUDDEN_IMPACT
        UNUSUAL_MOTION -> MotionAnomalyType.UNUSUAL_ACCELERATION
    }
}

/**
 * Structured output of MotionMLModel inference.
 */
data class MotionMLOutput(
    val predictedClass: MotionMLClass,
    val confidence: Float,
    val classProbabilities: FloatArray,
    val inferenceLatencyMs: Long,
    val isUsingRealML: Boolean,
    val modelDescription: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MotionMLOutput) return false
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
 * Real/Hybrid On-Device Motion Model implementing the Phase 6 ML architecture.
 *
 * Input:
 * - 16-element feature vector extracted from the temporal window of accelerometer & gyroscope data.
 *
 * Target ML Architecture:
 * - 1D Temporal CNN / Quantized INT8 MLP
 *
 * Fallback Policy:
 * - If genuine trained weights are available in `motion_model.tflite`, executes on-device TFLite inference.
 * - If genuine trained weights are not available (`MODEL_NOT_AVAILABLE`), transparently delegates
 *   to the tested mathematical/heuristic [MotionAnomalyDetector] while clearly labeling the result.
 * - Never fabricates fake model weights.
 */
class MotionMLModel(
    private val artifactProvider: ModelArtifactProvider = AssetModelArtifactProvider(),
    private val fallbackDetector: MotionAnomalyDetector = MotionAnomalyDetector()
) : MotionModel {

    private val metadata = artifactProvider.getModelMetadata("motion_model.tflite")
    private val runtime: OnDeviceInferenceRuntime = UniversalInferenceRuntime(metadata, artifactProvider)

    val isModelAvailable: Boolean
        get() = artifactProvider.getModelAvailability("motion_model.tflite") == ModelAvailability.AVAILABLE

    override val modelName: String
        get() = if (isModelAvailable) "Tiny Motion 1D CNN (REAL ML)" else "Motion Heuristic (PROTOTYPE FALLBACK)"

    override val isMock: Boolean
        get() = !isModelAvailable

    /**
     * Converts raw MotionFeatures into a normalized 16-element float vector for ML input tensor:
     * [0] peakAccelMagnitude
     * [1] meanAccelMagnitude
     * [2] accelVariance
     * [3] accelDeltaMagnitude (jerk)
     * [4] signalEnergy
     * [5] suddenImpulse
     * [6] motionIntensity
     * [7] currentAccelMagnitude
     * [8] peakGyroMagnitude
     * [9] meanGyroMagnitude
     * [10] gyroVariance
     * [11] rotationalChange
     * [12] rotationalEnergy
     * [13] gravityNormalizedAccel
     * [14] orientationChange
     * [15] immobilityDuration (normalized to seconds)
     */
    fun extractTensorFeatures(rawFeatures: MotionFeatures): FloatArray {
        val f = com.safeguardian.app.domain.motion.SensorDataSanitizer.sanitizeFeatures(rawFeatures)
        return floatArrayOf(
            (f.peakAccelMagnitude / 50.0f).coerceIn(0f, 1f),
            (f.meanAccelMagnitude / 30.0f).coerceIn(0f, 1f),
            (f.accelVariance / 50.0f).coerceIn(0f, 1f),
            (f.accelDeltaMagnitude / 100.0f).coerceIn(0f, 1f),
            (f.signalEnergy / 500.0f).coerceIn(0f, 1f),
            (f.suddenImpulse / 50.0f).coerceIn(0f, 1f),
            f.motionIntensity.coerceIn(0f, 1f),
            (f.currentAccelMagnitude / 30.0f).coerceIn(0f, 1f),
            (f.peakGyroMagnitude / 20.0f).coerceIn(0f, 1f),
            (f.meanGyroMagnitude / 15.0f).coerceIn(0f, 1f),
            (f.gyroVariance / 20.0f).coerceIn(0f, 1f),
            (f.rotationalChange / 25.0f).coerceIn(0f, 1f),
            (f.rotationalEnergy / 100.0f).coerceIn(0f, 1f),
            (f.gravityNormalizedAccel / 20.0f).coerceIn(0f, 1f),
            (f.orientationChange / 10.0f).coerceIn(0f, 1f),
            (f.immobilityDuration / 10000.0f).coerceIn(0f, 1f)
        )
    }

    /**
     * Structured prediction with typed [MotionMLOutput].
     */
    fun predict(features: MotionFeatures): MotionMLOutput {
        val startTime = System.nanoTime()

        if (isModelAvailable) {
            val inputTensor = extractTensorFeatures(features)
            val result = runtime.runInferenceFloats(inputTensor, expectedOutputSize = 4)
            val latency = (System.nanoTime() - startTime) / 1_000_000

            val predictedClass = when (result.predictedClassIndex) {
                1 -> MotionMLClass.FALL
                2 -> MotionMLClass.IMPACT
                3 -> MotionMLClass.UNUSUAL_MOTION
                else -> MotionMLClass.NORMAL
            }

            return MotionMLOutput(
                predictedClass = predictedClass,
                confidence = result.confidence,
                classProbabilities = result.outputScores,
                inferenceLatencyMs = latency.coerceAtLeast(1L),
                isUsingRealML = true,
                modelDescription = "Quantized 1D CNN INT8"
            )
        } else {
            // High-precision heuristic fallback
            val output = fallbackDetector.analyze(features)
            val latency = (System.nanoTime() - startTime) / 1_000_000

            val mlClass = when (output.result.anomalyType) {
                MotionAnomalyType.FALL_LIKE_MOTION -> MotionMLClass.FALL
                MotionAnomalyType.SUDDEN_IMPACT -> MotionMLClass.IMPACT
                MotionAnomalyType.UNUSUAL_ACCELERATION,
                MotionAnomalyType.UNUSUAL_ROTATION -> MotionMLClass.UNUSUAL_MOTION
                else -> MotionMLClass.NORMAL
            }

            val probabilities = FloatArray(4) { 0.05f }
            val classIdx = mlClass.ordinal
            probabilities[classIdx] = output.result.anomalyScore

            return MotionMLOutput(
                predictedClass = mlClass,
                confidence = output.motionConfidence,
                classProbabilities = probabilities,
                inferenceLatencyMs = latency.coerceAtLeast(1L),
                isUsingRealML = false,
                modelDescription = "Heuristic Temporal Window Detector (PROTOTYPE FALLBACK)"
            )
        }
    }

    /**
     * Preserves compatibility with existing [MotionModel] interface for MultimodalRiskEngine.
     */
    override fun analyze(features: MotionFeatures): MotionAnomalyResult {
        val mlOutput = predict(features)
        val isAnomaly = mlOutput.predictedClass != MotionMLClass.NORMAL

        return MotionAnomalyResult(
            isAnomaly = isAnomaly,
            anomalyScore = if (isAnomaly) mlOutput.confidence else 0.05f,
            anomalyType = mlOutput.predictedClass.toMotionAnomalyType(),
            confidence = mlOutput.confidence,
            features = features
        )
    }
}
