package com.safeguardian.app

import android.content.Context
import android.graphics.Bitmap
import android.os.Debug
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.safeguardian.app.domain.ai.ml.*
import com.safeguardian.app.domain.model.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

private const val TAG = "Phase9Benchmark"

data class BenchmarkStats(
    val modelName: String,
    val isRealML: Boolean,
    val initTimeMs: Long,
    val memoryDeltaKb: Long,
    val coldInferenceMs: Double,
    val iterations: Int,
    val minMs: Double,
    val maxMs: Double,
    val meanMs: Double,
    val medianMs: Double,
    val p95Ms: Double,
    val p99Ms: Double
) {
    override fun toString(): String {
        return """
            [$modelName]
              Real ML: $isRealML
              Init Time: ${initTimeMs}ms
              Memory Delta: ${memoryDeltaKb}KB
              Cold Inference: ${"%.3f".format(coldInferenceMs)}ms
              Iterations: $iterations
              Mean: ${"%.3f".format(meanMs)}ms
              Median: ${"%.3f".format(medianMs)}ms
              P95: ${"%.3f".format(p95Ms)}ms
              P99: ${"%.3f".format(p99Ms)}ms
              Min: ${"%.3f".format(minMs)}ms
              Max: ${"%.3f".format(maxMs)}ms
        """.trimIndent()
    }
}

@RunWith(AndroidJUnit4::class)
class Phase9DeviceBenchmarkTest {

    private lateinit var context: Context
    private lateinit var artifactProvider: AssetModelArtifactProvider

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        artifactProvider = AssetModelArtifactProvider(context)
    }

    private fun getMemoryUsedKb(): Long {
        Runtime.getRuntime().gc()
        return (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024
    }

    @Test
    fun testMotionModelLoadingAndBenchmark() {
        Log.i(TAG, "=== STARTING MOTION MODEL BENCHMARK ===")
        val memBefore = getMemoryUsedKb()
        val initStart = System.nanoTime()

        val motionModel = MotionMLModel(artifactProvider)
        // Force model load
        val meta = artifactProvider.getModelMetadata("motion_model.tflite")
        val runtime = UniversalInferenceRuntime(meta, artifactProvider)
        val loaded = runtime.load()
        val initTimeMs = (System.nanoTime() - initStart) / 1_000_000
        val memDelta = (getMemoryUsedKb() - memBefore).coerceAtLeast(0L)

        assertTrue("Motion model must be available in assets", motionModel.isModelAvailable)
        assertTrue("Motion model runtime must load successfully", loaded)

        val testFeature = MotionFeatures(
            peakAccelMagnitude = 28f,
            accelDeltaMagnitude = 85f,
            meanAccelMagnitude = 11f,
            accelVariance = 12f,
            peakGyroMagnitude = 6f
        )

        // Cold inference
        val coldStart = System.nanoTime()
        val coldOut = motionModel.predict(testFeature)
        val coldMs = (System.nanoTime() - coldStart) / 1_000_000.0
        assertTrue("Cold output must use real ML", coldOut.isUsingRealML)

        // 100 warm iterations
        val iterations = 100
        val times = DoubleArray(iterations)
        for (i in 0 until iterations) {
            val start = System.nanoTime()
            val out = motionModel.predict(testFeature)
            val elapsed = (System.nanoTime() - start) / 1_000_000.0
            times[i] = elapsed
            assertTrue(out.isUsingRealML)
        }

        times.sort()
        val stats = BenchmarkStats(
            modelName = "Motion Model (1D CNN INT8)",
            isRealML = coldOut.isUsingRealML,
            initTimeMs = initTimeMs,
            memoryDeltaKb = memDelta,
            coldInferenceMs = coldMs,
            iterations = iterations,
            minMs = times.first(),
            maxMs = times.last(),
            meanMs = times.average(),
            medianMs = times[iterations / 2],
            p95Ms = times[(iterations * 0.95).toInt()],
            p99Ms = times[(iterations * 0.99).toInt()]
        )

        Log.i(TAG, stats.toString())
        System.out.println("PHASE9_BENCHMARK_RESULT: MOTION\n$stats")

        runtime.unload()
    }

    @Test
    fun testAudioModelLoadingAndBenchmark() {
        Log.i(TAG, "=== STARTING AUDIO MODEL BENCHMARK ===")
        val memBefore = getMemoryUsedKb()
        val initStart = System.nanoTime()

        val audioModel = AudioMLModel(artifactProvider = artifactProvider)
        val meta = artifactProvider.getModelMetadata("audio_model.tflite")
        val runtime = UniversalInferenceRuntime(meta, artifactProvider)
        val loaded = runtime.load()
        val initTimeMs = (System.nanoTime() - initStart) / 1_000_000
        val memDelta = (getMemoryUsedKb() - memBefore).coerceAtLeast(0L)

        assertTrue("Audio model must be available in assets", audioModel.isModelAvailable)
        assertTrue("Audio model runtime must load successfully", loaded)

        val testBuffer = ShortArray(16000) { (it % 1000).toShort() }

        // Cold inference
        val coldStart = System.nanoTime()
        val coldOut = audioModel.predict(testBuffer, 16000)
        val coldMs = (System.nanoTime() - coldStart) / 1_000_000.0
        assertTrue("Cold output must use real ML", coldOut.isUsingRealML)

        // 100 warm iterations
        val iterations = 100
        val times = DoubleArray(iterations)
        for (i in 0 until iterations) {
            val start = System.nanoTime()
            val out = audioModel.predict(testBuffer, 16000)
            val elapsed = (System.nanoTime() - start) / 1_000_000.0
            times[i] = elapsed
            assertTrue(out.isUsingRealML)
        }

        times.sort()
        val stats = BenchmarkStats(
            modelName = "Audio Model (Separable 2D CNN INT8)",
            isRealML = coldOut.isUsingRealML,
            initTimeMs = initTimeMs,
            memoryDeltaKb = memDelta,
            coldInferenceMs = coldMs,
            iterations = iterations,
            minMs = times.first(),
            maxMs = times.last(),
            meanMs = times.average(),
            medianMs = times[iterations / 2],
            p95Ms = times[(iterations * 0.95).toInt()],
            p99Ms = times[(iterations * 0.99).toInt()]
        )

        Log.i(TAG, stats.toString())
        System.out.println("PHASE9_BENCHMARK_RESULT: AUDIO\n$stats")

        runtime.unload()
    }

    @Test
    fun testVisionModelLoadingAndBenchmark() {
        Log.i(TAG, "=== STARTING VISION MODEL BENCHMARK ===")
        val memBefore = getMemoryUsedKb()
        val initStart = System.nanoTime()

        val visionModel = VisionMLModel(artifactProvider = artifactProvider)
        val meta = artifactProvider.getModelMetadata("vision_model.tflite")
        val runtime = UniversalInferenceRuntime(meta, artifactProvider)
        val loaded = runtime.load()
        val initTimeMs = (System.nanoTime() - initStart) / 1_000_000
        val memDelta = (getMemoryUsedKb() - memBefore).coerceAtLeast(0L)

        assertTrue("Vision model must be available in assets", visionModel.isModelAvailable)
        assertTrue("Vision model runtime must load successfully", loaded)

        val testBitmap = Bitmap.createBitmap(224, 224, Bitmap.Config.ARGB_8888)

        // Cold inference
        val coldStart = System.nanoTime()
        val coldOut = visionModel.predict(testBitmap)
        val coldMs = (System.nanoTime() - coldStart) / 1_000_000.0
        assertTrue("Cold output must use real ML", coldOut.isUsingRealML)

        // 100 warm iterations
        val iterations = 100
        val times = DoubleArray(iterations)
        for (i in 0 until iterations) {
            val start = System.nanoTime()
            val out = visionModel.predict(testBitmap)
            val elapsed = (System.nanoTime() - start) / 1_000_000.0
            times[i] = elapsed
            assertTrue(out.isUsingRealML)
        }

        times.sort()
        val stats = BenchmarkStats(
            modelName = "Vision Model (MobileNetV3-Small INT8)",
            isRealML = coldOut.isUsingRealML,
            initTimeMs = initTimeMs,
            memoryDeltaKb = memDelta,
            coldInferenceMs = coldMs,
            iterations = iterations,
            minMs = times.first(),
            maxMs = times.last(),
            meanMs = times.average(),
            medianMs = times[iterations / 2],
            p95Ms = times[(iterations * 0.95).toInt()],
            p99Ms = times[(iterations * 0.99).toInt()]
        )

        Log.i(TAG, stats.toString())
        System.out.println("PHASE9_BENCHMARK_RESULT: VISION\n$stats")

        runtime.unload()
    }
}
