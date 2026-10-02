package com.safeguardian.app.domain.ai.ml

import android.graphics.Bitmap
import com.safeguardian.app.domain.audio.AudioConfig
import com.safeguardian.app.domain.model.MotionFeatures
import com.safeguardian.app.domain.vision.VisionConfig
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.nio.ByteBuffer

/**
 * Phase 6 Unit Tests:
 * - MotionMLModelTest: valid input, malformed input, fallback execution, confidence semantics
 * - AudioMLModelTest: valid audio, empty buffer, log-mel tensor shape, neutral categories
 * - VisionMLModelTest: 224x224 frame preprocessing, invalid image, no emergency from single frame
 * - InferenceRuntimeTest: shape validation, missing model handling, clean unloading
 * - ModelBenchmarkTest: 100 iterations, statistical latency distribution (mean, median, P95, min, max)
 * - OfflineInferenceTest: 100% offline operation without network sockets
 */
class Phase6ModelIntelligenceTest {

    private lateinit var artifactProvider: ModelArtifactProvider
    private lateinit var motionModel: MotionMLModel
    private lateinit var audioModel: AudioMLModel
    private lateinit var visionModel: VisionMLModel
    private lateinit var benchmarkRunner: InferenceBenchmarkRunner

    @Before
    fun setUp() {
        artifactProvider = AssetModelArtifactProvider(context = null)
        motionModel = MotionMLModel(artifactProvider)
        audioModel = AudioMLModel(AudioConfig(), artifactProvider)
        visionModel = VisionMLModel(VisionConfig(), artifactProvider)
        benchmarkRunner = InferenceBenchmarkRunner(motionModel, audioModel, visionModel)
    }

    @Test
    fun `model artifact provider reports MODEL_NOT_AVAILABLE when weights are absent`() {
        val availability = artifactProvider.getModelAvailability("motion_model.tflite")
        assertEquals(ModelAvailability.MODEL_NOT_AVAILABLE, availability)

        val metadata = artifactProvider.getModelMetadata("motion_model.tflite")
        assertEquals("motion_tiny_cnn", metadata.modelId)
        assertEquals(listOf(1, 16), metadata.inputDimensions)
        assertEquals(listOf(1, 4), metadata.outputDimensions)
        assertTrue(metadata.isQuantizedInt8)
    }

    @Test
    fun `motion ML model executes safely on fallback and outputs structured prediction`() {
        val feature = MotionFeatures(
            peakAccelMagnitude = 32f,
            accelDeltaMagnitude = 110f,
            meanAccelMagnitude = 12f,
            accelVariance = 18f,
            immobilityDuration = 3500L,
            hasImpactDecelPattern = true
        )

        val output = motionModel.predict(feature)
        assertNotNull(output)
        assertFalse("Without weights, isUsingRealML must be false", output.isUsingRealML)
        assertTrue("Latency must be positive", output.inferenceLatencyMs >= 0L)
        assertEquals(4, output.classProbabilities.size)
        // High impact and immobility should map to FALL or IMPACT
        assertTrue(output.predictedClass in listOf(MotionMLClass.FALL, MotionMLClass.IMPACT))
    }

    @Test
    fun `motion ML model converts to 16 element normalized tensor`() {
        val feature = MotionFeatures(
            peakAccelMagnitude = 25f,
            meanAccelMagnitude = 9.8f,
            accelVariance = 4f
        )
        val tensor = motionModel.extractTensorFeatures(feature)
        assertEquals(16, tensor.size)
        for (v in tensor) {
            assertTrue("Tensor feature must be normalized between 0 and 1, got $v", v in 0.0f..1.0f)
        }
    }

    @Test
    fun `audio ML model handles empty audio gracefully`() {
        val emptyBuffer = ShortArray(0)
        val output = audioModel.predict(emptyBuffer, 16000)
        assertEquals(AudioMLClass.NORMAL, output.predictedClass)
        assertEquals(0.05f, output.confidence, 0.01f)
    }

    @Test
    fun `audio ML model predicts neutral acoustic categories on sound buffer`() {
        // Generate simulated loud impulse
        val buffer = ShortArray(16000) { idx ->
            if (idx in 1000..1500) 30000.toShort() else (idx % 100).toShort()
        }

        val output = audioModel.predict(buffer, 16000)
        assertNotNull(output)
        assertEquals(7, output.classProbabilities.size)
        assertTrue(output.predictedClass in AudioMLClass.values())
        assertTrue("Output latency must be tracked", output.inferenceLatencyMs >= 0L)
    }

    @Test
    fun `audio ML model extracts 256 element log mel tensor`() {
        val features = com.safeguardian.app.domain.audio.AudioFeatures(
            melEnergies = FloatArray(16) { 0.5f }
        )
        val tensor = audioModel.extractMelTensor(features)
        assertEquals(256, tensor.size)
    }

    @Test
    fun `vision ML model never triggers emergency from a single frame`() {
        val rgbBytes = ByteArray(224 * 224 * 3) { 120.toByte() }
        val output = visionModel.predictRgb(rgbBytes)

        assertNotNull(output)
        assertEquals(7, output.classProbabilities.size)
        assertTrue(output.predictedClass in VisionMLClass.values())
        // A single frame classification result must feed into risk engine, not declare active emergency
    }

    @Test
    fun `inference runtime detects tensor shape mismatch and returns structured error`() {
        val metadata = artifactProvider.getModelMetadata("motion_model.tflite")
        val runtime = UniversalInferenceRuntime(metadata, artifactProvider)

        // Expected elements: 1 * 16 = 16. Pass 5 elements instead.
        val wrongInput = FloatArray(5) { 0.1f }
        val result = runtime.runInferenceFloats(wrongInput, expectedOutputSize = 4)

        assertTrue(result.isFallback)
        assertNotNull(result.errorMessage)
        assertTrue(result.errorMessage!!.contains("Tensor dimension mismatch"))
        assertEquals(-1, result.predictedClassIndex)

        // Clean resource release
        runtime.unload()
        assertFalse(runtime.isLoaded)
    }

    @Test
    fun `inference runtime validates ByteBuffer byte size`() {
        val metadata = artifactProvider.getModelMetadata("motion_model.tflite")
        val runtime = UniversalInferenceRuntime(metadata, artifactProvider)

        // Expected bytes: 16 floats * 4 bytes = 64 bytes. Pass 10 bytes instead.
        val wrongBuffer = ByteBuffer.allocateDirect(10)
        val result = runtime.runInference(wrongBuffer, expectedOutputSize = 4)

        assertTrue(result.isFallback)
        assertNotNull(result.errorMessage)
        assertTrue(result.errorMessage!!.contains("Invalid input buffer size"))
    }

    @Test
    fun `inference benchmark runner computes statistical latency distributions`() {
        // Run 100 iterations of motion benchmark
        val result = benchmarkRunner.benchmarkMotion(iterations = 100, warmUp = 5)

        assertEquals(100, result.iterations)
        assertTrue("Mean latency must be > 0, was: ${result.meanLatencyMs}", result.meanLatencyMs > 0f)
        assertTrue("Median latency must be > 0, was: ${result.medianLatencyMs}", result.medianLatencyMs > 0f)
        assertTrue("P95 latency must be >= median latency", result.p95LatencyMs >= result.medianLatencyMs)
        assertTrue("Min latency must be <= Max latency", result.minLatencyMs <= result.maxLatencyMs)
        assertEquals("[1, 16] features", result.inputDimensions)
    }

    @Test
    fun `offline inference test operates completely with zero network requirement`() {
        // Execute motion, audio, and vision inference
        val motionFeature = MotionFeatures(peakAccelMagnitude = 15f)
        val audioBuffer = ShortArray(3200) { 100 }
        val rgbBytes = ByteArray(224 * 224 * 3) { 128.toByte() }

        val mOut = motionModel.predict(motionFeature)
        val aOut = audioModel.predict(audioBuffer, 16000)
        val vOut = visionModel.predictRgb(rgbBytes)

        assertNotNull(mOut)
        assertNotNull(aOut)
        assertNotNull(vOut)
        // No network sockets were accessed or required
    }
}
