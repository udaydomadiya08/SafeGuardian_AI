package com.safeguardian.app.domain.ai.ml

import android.graphics.Bitmap
import com.safeguardian.app.domain.model.MotionFeatures

/**
 * Result of a model inference benchmark.
 */
data class ModelBenchmarkResult(
    val modelName: String,
    val targetArchitecture: String,
    val isRealML: Boolean,
    val iterations: Int,
    val meanLatencyMs: Float,
    val medianLatencyMs: Float,
    val p95LatencyMs: Float,
    val minLatencyMs: Long,
    val maxLatencyMs: Long,
    val inputDimensions: String,
    val estimatedMemoryKb: Long
)

/**
 * Deterministic benchmarking framework for on-device inference.
 *
 * Runs at least 100 inference iterations using synthetic deterministic test inputs,
 * ignores warm-up iterations, and computes statistical latency distribution (mean, median, P95, min, max).
 */
class InferenceBenchmarkRunner(
    private val motionModel: MotionMLModel,
    private val audioModel: AudioMLModel,
    private val visionModel: VisionMLModel
) {

    /**
     * Benchmarks the motion model with deterministic synthetic features.
     */
    fun benchmarkMotion(iterations: Int = 100, warmUp: Int = 10): ModelBenchmarkResult {
        val testFeature = MotionFeatures(
            peakAccelMagnitude = 28f,
            accelDeltaMagnitude = 85f,
            meanAccelMagnitude = 11f,
            accelVariance = 12f,
            peakGyroMagnitude = 6f
        )

        // Warm-up runs
        repeat(warmUp) {
            motionModel.predict(testFeature)
        }

        val latencies = LongArray(iterations)
        for (i in 0 until iterations) {
            val start = System.nanoTime()
            motionModel.predict(testFeature)
            val elapsedNanos = System.nanoTime() - start
            latencies[i] = (elapsedNanos / 1_000_000).coerceAtLeast(1L)
        }

        latencies.sort()
        val mean = latencies.average().toFloat()
        val median = latencies[iterations / 2].toFloat()
        val p95 = latencies[(iterations * 0.95).toInt()].toFloat()
        val min = latencies.first()
        val max = latencies.last()

        return ModelBenchmarkResult(
            modelName = motionModel.modelName,
            targetArchitecture = "1D Temporal CNN (INT8) / Temporal MLP",
            isRealML = motionModel.isModelAvailable,
            iterations = iterations,
            meanLatencyMs = mean,
            medianLatencyMs = median,
            p95LatencyMs = p95,
            minLatencyMs = min,
            maxLatencyMs = max,
            inputDimensions = "[1, 16] features",
            estimatedMemoryKb = 64L
        )
    }

    /**
     * Benchmarks the audio model with a synthetic 1-second 16kHz audio buffer.
     */
    fun benchmarkAudio(iterations: Int = 100, warmUp: Int = 10): ModelBenchmarkResult {
        val testBuffer = ShortArray(16000) { (it % 1000).toShort() }

        repeat(warmUp) {
            audioModel.predict(testBuffer, 16000)
        }

        val latencies = LongArray(iterations)
        for (i in 0 until iterations) {
            val start = System.nanoTime()
            audioModel.predict(testBuffer, 16000)
            val elapsedNanos = System.nanoTime() - start
            latencies[i] = (elapsedNanos / 1_000_000).coerceAtLeast(1L)
        }

        latencies.sort()
        val mean = latencies.average().toFloat()
        val median = latencies[iterations / 2].toFloat()
        val p95 = latencies[(iterations * 0.95).toInt()].toFloat()
        val min = latencies.first()
        val max = latencies.last()

        return ModelBenchmarkResult(
            modelName = audioModel.modelName,
            targetArchitecture = "Depthwise Separable 2D CNN (INT8)",
            isRealML = audioModel.isModelAvailable,
            iterations = iterations,
            meanLatencyMs = mean,
            medianLatencyMs = median,
            p95LatencyMs = p95,
            minLatencyMs = min,
            maxLatencyMs = max,
            inputDimensions = "[1, 16, 16] log-mel tensor",
            estimatedMemoryKb = 180L
        )
    }

    /**
     * Benchmarks the vision model with a synthetic 224x224 bitmap.
     */
    fun benchmarkVision(iterations: Int = 50, warmUp: Int = 5): ModelBenchmarkResult {
        val testBitmap = Bitmap.createBitmap(224, 224, Bitmap.Config.ARGB_8888)

        repeat(warmUp) {
            visionModel.predict(testBitmap)
        }

        val latencies = LongArray(iterations)
        for (i in 0 until iterations) {
            val start = System.nanoTime()
            visionModel.predict(testBitmap)
            val elapsedNanos = System.nanoTime() - start
            latencies[i] = (elapsedNanos / 1_000_000).coerceAtLeast(1L)
        }

        latencies.sort()
        val mean = latencies.average().toFloat()
        val median = latencies[iterations / 2].toFloat()
        val p95 = latencies[(iterations * 0.95).toInt()].toFloat()
        val min = latencies.first()
        val max = latencies.last()

        return ModelBenchmarkResult(
            modelName = visionModel.modelName,
            targetArchitecture = "MobileNetV3-Small (INT8)",
            isRealML = visionModel.isModelAvailable,
            iterations = iterations,
            meanLatencyMs = mean,
            medianLatencyMs = median,
            p95LatencyMs = p95,
            minLatencyMs = min,
            maxLatencyMs = max,
            inputDimensions = "[1, 224, 224, 3] RGB",
            estimatedMemoryKb = 850L
        )
    }
}
