package com.safeguardian.app.domain.ai.mock

import com.safeguardian.app.domain.model.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class MockMotionModelTest {

    private lateinit var model: MockMotionModel

    @Before
    fun setup() {
        model = MockMotionModel()
    }

    @Test
    fun `model is labeled as mock`() {
        assertTrue(model.isMock)
        assertTrue(model.modelName.contains("Mock"))
    }

    @Test
    fun `normal walking does not trigger anomaly`() {
        val features = MotionFeatures(
            meanAccelMagnitude = 9.8f,
            peakAccelMagnitude = 12f,
            accelVariance = 2f,
            meanGyroMagnitude = 0.5f,
            peakGyroMagnitude = 1.5f,
            gyroVariance = 0.3f
        )

        val result = model.analyze(features)
        assertFalse(result.isAnomaly)
        assertEquals(MotionAnomalyType.NONE, result.anomalyType)
    }

    @Test
    fun `high impact triggers sudden impact anomaly`() {
        val features = MotionFeatures(
            peakAccelMagnitude = 40f,
            accelVariance = 100f,
            meanAccelMagnitude = 20f
        )

        val result = model.analyze(features)
        assertTrue(result.isAnomaly)
        assertEquals(MotionAnomalyType.SUDDEN_IMPACT, result.anomalyType)
        assertTrue(result.anomalyScore > 0.8f)
    }

    @Test
    fun `high gyro triggers rotation anomaly`() {
        val features = MotionFeatures(
            peakGyroMagnitude = 12f,
            gyroVariance = 10f,
            peakAccelMagnitude = 5f,
            meanAccelMagnitude = 9.8f
        )

        val result = model.analyze(features)
        assertTrue(result.isAnomaly)
        assertEquals(MotionAnomalyType.UNUSUAL_ROTATION, result.anomalyType)
    }

    @Test
    fun `prolonged immobility is detected`() {
        val features = MotionFeatures(
            meanAccelMagnitude = 9.8f,
            peakAccelMagnitude = 9.9f,
            accelVariance = 0.1f,
            immobilityDuration = 25_000L
        )

        val result = model.analyze(features)
        assertTrue(result.isAnomaly)
        assertEquals(MotionAnomalyType.PROLONGED_IMMOBILITY, result.anomalyType)
    }

    @Test
    fun `mock model confidence is reduced`() {
        val features = MotionFeatures(peakAccelMagnitude = 40f, accelVariance = 100f)
        val result = model.analyze(features)
        // Mock models should have reduced confidence (× 0.7)
        assertTrue(result.confidence <= 0.7f)
    }
}

class MockFusionEngineTest {

    private lateinit var engine: MockFusionEngine

    @Before
    fun setup() {
        engine = MockFusionEngine()
    }

    @Test
    fun `no events produces low risk`() {
        val result = engine.evaluate(null, null, null, 0f, SafetyState.NORMAL)
        assertTrue(result.overallRiskScore < 0.3f)
        assertEquals(SafetyState.NORMAL, result.recommendedState)
    }

    @Test
    fun `single motion anomaly alone is not emergency`() {
        val motionResult = MotionAnomalyResult(
            isAnomaly = true,
            anomalyScore = 0.8f,
            anomalyType = MotionAnomalyType.SUDDEN_IMPACT,
            confidence = 0.7f,
            features = MotionFeatures()
        )

        val result = engine.evaluate(motionResult, null, null, 0f, SafetyState.NORMAL)
        assertNotEquals(SafetyState.HIGH_CONFIDENCE_EMERGENCY, result.recommendedState)
    }

    @Test
    fun `multiple high-confidence sensors increase risk`() {
        val motionResult = MotionAnomalyResult(
            isAnomaly = true,
            anomalyScore = 0.9f,
            anomalyType = MotionAnomalyType.FALL_LIKE_MOTION,
            confidence = 0.85f,
            features = MotionFeatures()
        )
        val audioResult = AudioEventResult(
            eventType = AudioEventType.SCREAM_DISTRESS,
            confidence = 0.8f
        )
        val visionResult = VisionEventResult(
            eventType = VisionEventType.PERSON_ON_GROUND,
            confidence = 0.75f
        )

        val result = engine.evaluate(
            motionResult, audioResult, visionResult, 0.7f, SafetyState.SUSPICIOUS
        )

        assertTrue("Risk should be high with all sensors", result.overallRiskScore > 0.5f)
    }

    @Test
    fun `evidence summary includes all detected events`() {
        val motionResult = MotionAnomalyResult(
            isAnomaly = true,
            anomalyScore = 0.8f,
            anomalyType = MotionAnomalyType.SUDDEN_IMPACT,
            confidence = 0.7f,
            features = MotionFeatures()
        )
        val audioResult = AudioEventResult(
            eventType = AudioEventType.CRASH_IMPACT,
            confidence = 0.75f
        )

        val result = engine.evaluate(motionResult, audioResult, null, 0f, SafetyState.INVESTIGATING)

        assertTrue(result.evidenceSummary.contains("Motion"))
        assertTrue(result.evidenceSummary.contains("Audio"))
    }
}
