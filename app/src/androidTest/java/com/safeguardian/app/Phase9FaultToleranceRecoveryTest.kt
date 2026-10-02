package com.safeguardian.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.safeguardian.app.data.location.EmergencyLocationManager
import com.safeguardian.app.domain.ai.ml.*
import com.safeguardian.app.domain.audio.AudioConfig
import com.safeguardian.app.domain.audio.AudioFeatureExtractor
import com.safeguardian.app.domain.model.*
import com.safeguardian.app.domain.response.*
import com.safeguardian.app.domain.vision.VisionConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Phase9FaultToleranceRecoveryTest {

    private lateinit var app: SafeGuardianApp

    @Before
    fun setup() {
        app = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testAudioFailureFailsSafely() {
        // An empty or corrupted audio buffer must NOT be classified as distress/emergency
        val emptyBuffer = ShortArray(0)
        val out = app.audioMLModel.predict(emptyBuffer, 16000)

        assertEquals("Empty audio buffer must default to NORMAL", AudioMLClass.NORMAL, out.predictedClass)
        assertTrue("Confidence must be minimal", out.confidence <= 0.10f)

        // Evaluate risk with audio failure
        val eval = app.riskEngine.evaluate(
            motionResult = null,
            audioResult = null,
            visionResult = null,
            temporalPersistence = 0f,
            corroborationCount = 0
        )

        assertEquals("Audio failure must remain NORMAL", RiskLevel.NORMAL, eval.riskLevel)
        assertEquals(SafetyState.NORMAL, eval.recommendedState)
    }

    @Test
    fun testSensorUnavailableFailsSafely() = runBlocking {
        // Zero or static sensor readings must not trigger anomaly
        val staticFeatures = MotionFeatures(
            peakAccelMagnitude = 0f,
            accelDeltaMagnitude = 0f,
            meanAccelMagnitude = 0f,
            accelVariance = 0f,
            peakGyroMagnitude = 0f
        )

        val mlOut = app.motionMLModel.predict(staticFeatures)
        app.stateEngine.processMotionData(staticFeatures)

        assertNotEquals(SafetyState.ACTIVE_EMERGENCY, app.stateEngine.safetyState.value)
        assertNotEquals(SafetyState.HIGH_CONFIDENCE_EMERGENCY, app.stateEngine.safetyState.value)
    }

    @Test
    fun testLocationUnavailableFailsSafely() {
        // When location is unavailable (e.g., GPS off), dispatch must generate safe fallback text
        val event = EmergencyEvent(
            id = "loc-fail-test",
            eventType = "Fall Detected",
            confidence = 0.90f,
            sensorEvidence = SensorEvidence(eventChainDescription = "Test event"),
            state = SafetyState.ACTIVE_EMERGENCY,
            isSimulated = true
        )

        val contacts = listOf(EmergencyContact("1", "Alice", "+15551234567", "Sister", true))
        val records = app.emergencyDispatcher.dispatch(event, null, contacts)

        assertEquals(1, records.size)
        assertTrue(records[0].messageText.contains("Location: Unavailable"))
        assertTrue(records[0].messageText.contains("Device GPS acquiring"))
    }

    @Test
    fun testModelLoadFallbackOnCorruptArtifact() {
        // Non-existent or corrupt model artifact
        val corruptProvider = object : ModelArtifactProvider {
            override fun getModelAvailability(fileName: String) = ModelAvailability.MODEL_NOT_AVAILABLE
            override fun getModelMetadata(fileName: String) = ModelMetadata(
                modelId = "corrupt",
                modelName = "Corrupt Model",
                fileName = "corrupt.tflite",
                targetArchitecture = "None",
                inputDimensions = listOf(1, 16),
                outputDimensions = listOf(1, 4),
                availability = ModelAvailability.MODEL_NOT_AVAILABLE
            )
            override fun openModelInputStream(fileName: String) = null
        }

        val runtime = UniversalInferenceRuntime(corruptProvider.getModelMetadata("corrupt.tflite"), corruptProvider)
        val loaded = runtime.load()
        assertFalse("Corrupt model must not report loaded", loaded)

        val inputFloats = FloatArray(16) { 0.5f }
        val result = runtime.runInferenceFloats(inputFloats, 4)

        assertTrue("Inference must be marked fallback", result.isFallback)
        assertEquals("Fallback must default to class 0 (NORMAL)", 0, result.predictedClassIndex)
        runtime.unload()
    }

    @Test
    fun testAudioAndVisionTimeoutsDoNotEscalate() = runBlocking {
        // When investigation triggers but no subsequent anomaly occurs, it must de-escalate cleanly
        app.stateEngine.simulateMotionAnomaly(
            MotionAnomalyResult(
                isAnomaly = true,
                anomalyScore = 0.60f,
                anomalyType = MotionAnomalyType.UNUSUAL_ACCELERATION,
                confidence = 0.60f,
                features = MotionFeatures(peakAccelMagnitude = 18f)
            )
        )

        // Cancel / timeout de-escalation
        app.stateEngine.cancelInvestigation()
        assertEquals("Timeout/cancellation must return safetyState to NORMAL", SafetyState.NORMAL, app.stateEngine.safetyState.value)
        assertEquals("Pipeline must return to IDLE", PipelineStage.IDLE, app.stateEngine.pipelineStage.value)
    }

    @Test
    fun testMultipleEmergencyResolutionIdempotency() = runBlocking {
        // Calling resolve emergency repeatedly must not throw or leave corrupt state
        app.stateEngine.resolveEmergency()
        app.stateEngine.resolveEmergency()
        app.stateEngine.cancelInvestigation()

        assertEquals(SafetyState.NORMAL, app.stateEngine.safetyState.value)
        assertEquals(PipelineStage.IDLE, app.stateEngine.pipelineStage.value)
    }
}
