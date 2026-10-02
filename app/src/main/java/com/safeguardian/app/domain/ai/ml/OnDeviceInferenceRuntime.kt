package com.safeguardian.app.domain.ai.ml

import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Result returned from on-device model inference.
 */
data class InferenceResult(
    val outputScores: FloatArray,
    val predictedClassIndex: Int,
    val confidence: Float,
    val inferenceLatencyMs: Long,
    val isFallback: Boolean = false,
    val errorMessage: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is InferenceResult) return false
        if (!outputScores.contentEquals(other.outputScores)) return false
        if (predictedClassIndex != other.predictedClassIndex) return false
        if (confidence != other.confidence) return false
        if (inferenceLatencyMs != other.inferenceLatencyMs) return false
        if (isFallback != other.isFallback) return false
        if (errorMessage != other.errorMessage) return false
        return true
    }

    override fun hashCode(): Int {
        var result = outputScores.contentHashCode()
        result = 31 * result + predictedClassIndex
        result = 31 * result + confidence.hashCode()
        result = 31 * result + inferenceLatencyMs.hashCode()
        result = 31 * result + isFallback.hashCode()
        result = 31 * result + (errorMessage?.hashCode() ?: 0)
        return result
    }
}

/**
 * Clean runtime interface for on-device inference execution.
 *
 * Responsibilities:
 * - Lazy model loading and interpreter lifecycle
 * - Tensor shape & buffer validation
 * - Execution on CPU / hardware delegate
 * - Latency tracking
 * - Safe resource cleanup
 * - Zero network requirement
 */
interface OnDeviceInferenceRuntime : AutoCloseable {
    val modelMetadata: ModelMetadata
    val isLoaded: Boolean

    fun load(): Boolean
    fun runInference(inputBuffer: ByteBuffer, expectedOutputSize: Int): InferenceResult
    fun runInferenceFloats(inputFloats: FloatArray, expectedOutputSize: Int): InferenceResult
    fun unload()
}

/**
 * Universal on-device inference runtime with genuine TFLite interpreter execution.
 *
 * Loads INT8 quantized TFLite flatbuffers directly from assets, configures
 * on-device CPU execution (with multi-threading and XNNPACK), and executes genuine
 * inference without any network communication or synthetic latency.
 */
class UniversalInferenceRuntime(
    override val modelMetadata: ModelMetadata,
    private val artifactProvider: ModelArtifactProvider
) : OnDeviceInferenceRuntime {

    private var loaded = false
    private var interpreter: Interpreter? = null
    private var modelByteBuffer: ByteBuffer? = null

    override val isLoaded: Boolean
        get() = loaded && interpreter != null

    @Synchronized
    override fun load(): Boolean {
        if (loaded && interpreter != null) return true
        val availability = artifactProvider.getModelAvailability(modelMetadata.fileName)
        if (availability != ModelAvailability.AVAILABLE) {
            loaded = false
            return false
        }

        return try {
            val stream = artifactProvider.openModelInputStream(modelMetadata.fileName) ?: return false
            val bytes = stream.use { it.readBytes() }

            // Phase 10: Cryptographic model integrity verification
            val integrityCheck = ModelIntegrityVerifier.verifyChecksum(modelMetadata.fileName, bytes)
            if (integrityCheck is ModelIntegrityResult.Corrupted) {
                loaded = false
                interpreter = null
                return false
            }

            val directBuffer = ByteBuffer.allocateDirect(bytes.size).apply {
                order(ByteOrder.nativeOrder())
                put(bytes)
                rewind()
            }
            val options = Interpreter.Options().apply {
                setNumThreads(2)
                setUseXNNPACK(true)
            }
            val interp = Interpreter(directBuffer, options)

            // Phase 10: Structural tensor shape verification
            val actualInShape = interp.getInputTensor(0).shape()
            val actualOutShape = interp.getOutputTensor(0).shape()
            val dimCheck = ModelIntegrityVerifier.verifyTensorDimensions(
                modelMetadata.fileName,
                actualInShape,
                actualOutShape
            )
            if (dimCheck is ModelIntegrityResult.DimensionMismatch) {
                interp.close()
                loaded = false
                interpreter = null
                return false
            }

            interpreter = interp
            modelByteBuffer = directBuffer
            loaded = true
            true
        } catch (e: Exception) {
            loaded = false
            interpreter = null
            false
        }
    }

    @Synchronized
    override fun runInference(inputBuffer: ByteBuffer, expectedOutputSize: Int): InferenceResult {
        val startTime = System.nanoTime()

        // Validate buffer size against expected input shape
        val expectedInputElements = modelMetadata.inputDimensions.fold(1) { acc, dim -> acc * dim }
        val actualBytes = inputBuffer.remaining()
        val expectedBytesFloat = expectedInputElements * 4

        // If buffer size does not match expected dimensions
        if (actualBytes != expectedBytesFloat && actualBytes != expectedInputElements) {
            val latency = (System.nanoTime() - startTime) / 1_000_000
            return InferenceResult(
                outputScores = FloatArray(expectedOutputSize),
                predictedClassIndex = -1,
                confidence = 0f,
                inferenceLatencyMs = latency,
                isFallback = true,
                errorMessage = "Invalid input buffer size: $actualBytes bytes. Expected: $expectedBytesFloat bytes."
            )
        }

        if (!load() || interpreter == null) {
            val latency = (System.nanoTime() - startTime) / 1_000_000
            return InferenceResult(
                outputScores = FloatArray(expectedOutputSize),
                predictedClassIndex = 0,
                confidence = 0f,
                inferenceLatencyMs = latency,
                isFallback = true,
                errorMessage = "Model not loaded"
            )
        }

        val interp = interpreter!!
        return try {
            val outputTensor = interp.getOutputTensor(0)
            val outputBytes = ByteArray(expectedOutputSize)
            val outputMap = HashMap<Int, Any>()
            outputMap[0] = Array(1) { outputBytes }

            inputBuffer.rewind()
            interp.runForMultipleInputsOutputs(arrayOf(inputBuffer), outputMap)

            val elapsedNanos = System.nanoTime() - startTime
            val latencyMs = (elapsedNanos / 1_000_000).coerceAtLeast(1L)

            val outputQuant = outputTensor.quantizationParams()
            val scale = if (outputQuant.scale > 0f) outputQuant.scale else (1.0f / 256.0f)
            val zeroPoint = outputQuant.zeroPoint

            val scores = FloatArray(expectedOutputSize)
            var maxIdx = 0
            var maxScore = -Float.MAX_VALUE

            for (i in 0 until expectedOutputSize) {
                val uint8Val = outputBytes[i].toInt() and 0xFF
                val score = (uint8Val - zeroPoint) * scale
                scores[i] = score.coerceIn(0f, 1f)
                if (scores[i] > maxScore) {
                    maxScore = scores[i]
                    maxIdx = i
                }
            }

            val sum = scores.sum()
            if (sum > 0f) {
                for (i in 0 until expectedOutputSize) {
                    scores[i] /= sum
                }
                maxScore = scores[maxIdx]
            }

            InferenceResult(
                outputScores = scores,
                predictedClassIndex = maxIdx,
                confidence = maxScore,
                inferenceLatencyMs = latencyMs,
                isFallback = false
            )
        } catch (e: Exception) {
            val latency = (System.nanoTime() - startTime) / 1_000_000
            InferenceResult(
                outputScores = FloatArray(expectedOutputSize) { if (it == 0) 1.0f else 0.0f },
                predictedClassIndex = 0,
                confidence = 1.0f,
                inferenceLatencyMs = latency.coerceAtLeast(1L),
                isFallback = true,
                errorMessage = e.message
            )
        }
    }

    @Synchronized
    override fun runInferenceFloats(inputFloats: FloatArray, expectedOutputSize: Int): InferenceResult {
        val startTime = System.nanoTime()
        val expectedInputElements = modelMetadata.inputDimensions.fold(1) { acc, dim -> acc * dim }

        if (inputFloats.size != expectedInputElements) {
            val latency = (System.nanoTime() - startTime) / 1_000_000
            return InferenceResult(
                outputScores = FloatArray(expectedOutputSize),
                predictedClassIndex = -1,
                confidence = 0f,
                inferenceLatencyMs = latency,
                isFallback = true,
                errorMessage = "Tensor dimension mismatch: got ${inputFloats.size}, expected $expectedInputElements."
            )
        }

        if (!load() || interpreter == null) {
            val latency = (System.nanoTime() - startTime) / 1_000_000
            return InferenceResult(
                outputScores = FloatArray(expectedOutputSize),
                predictedClassIndex = 0,
                confidence = 0f,
                inferenceLatencyMs = latency,
                isFallback = true,
                errorMessage = "Model not loaded"
            )
        }

        val interp = interpreter!!
        return try {
            val inputTensor = interp.getInputTensor(0)
            val outputTensor = interp.getOutputTensor(0)

            val inputQuant = inputTensor.quantizationParams()
            val inScale = if (inputQuant.scale > 0f) inputQuant.scale else (1.0f / 255.0f)
            val inZeroPoint = inputQuant.zeroPoint

            val inputBuffer = ByteBuffer.allocateDirect(inputFloats.size).apply {
                order(ByteOrder.nativeOrder())
                for (f in inputFloats) {
                    val q = ((f / inScale) + inZeroPoint).toInt().coerceIn(0, 255).toByte()
                    put(q)
                }
                rewind()
            }

            val outputBytes = ByteArray(expectedOutputSize)
            val outputMap = HashMap<Int, Any>()
            outputMap[0] = Array(1) { outputBytes }

            interp.runForMultipleInputsOutputs(arrayOf(inputBuffer), outputMap)

            val elapsedNanos = System.nanoTime() - startTime
            val latencyMs = (elapsedNanos / 1_000_000).coerceAtLeast(1L)

            val outputQuant = outputTensor.quantizationParams()
            val outScale = if (outputQuant.scale > 0f) outputQuant.scale else (1.0f / 256.0f)
            val outZeroPoint = outputQuant.zeroPoint

            val scores = FloatArray(expectedOutputSize)
            var maxIdx = 0
            var maxScore = -Float.MAX_VALUE

            for (i in 0 until expectedOutputSize) {
                val uint8Val = outputBytes[i].toInt() and 0xFF
                val score = (uint8Val - outZeroPoint) * outScale
                scores[i] = score.coerceIn(0f, 1f)
                if (scores[i] > maxScore) {
                    maxScore = scores[i]
                    maxIdx = i
                }
            }

            val sum = scores.sum()
            if (sum > 0f) {
                for (i in 0 until expectedOutputSize) {
                    scores[i] /= sum
                }
                maxScore = scores[maxIdx]
            }

            InferenceResult(
                outputScores = scores,
                predictedClassIndex = maxIdx,
                confidence = maxScore,
                inferenceLatencyMs = latencyMs,
                isFallback = false
            )
        } catch (e: Exception) {
            val latency = (System.nanoTime() - startTime) / 1_000_000
            InferenceResult(
                outputScores = FloatArray(expectedOutputSize) { if (it == 0) 1.0f else 0.0f },
                predictedClassIndex = 0,
                confidence = 1.0f,
                inferenceLatencyMs = latency.coerceAtLeast(1L),
                isFallback = true,
                errorMessage = e.message
            )
        }
    }

    @Synchronized
    override fun unload() {
        interpreter?.close()
        interpreter = null
        modelByteBuffer = null
        loaded = false
    }

    override fun close() {
        unload()
    }
}

