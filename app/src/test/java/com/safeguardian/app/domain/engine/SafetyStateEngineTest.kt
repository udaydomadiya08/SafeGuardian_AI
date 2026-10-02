package com.safeguardian.app.domain.engine

import com.safeguardian.app.domain.ai.mock.MockAudioEventModel
import com.safeguardian.app.domain.ai.mock.MockFusionEngine
import com.safeguardian.app.domain.ai.mock.MockMotionModel
import com.safeguardian.app.domain.ai.mock.MockVisionEventModel
import com.safeguardian.app.domain.model.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SafetyStateEngineTest {

    private lateinit var engine: SafetyStateEngine

    @Before
    fun setup() {
        engine = SafetyStateEngine(
            motionModel = MockMotionModel(),
            audioModel = MockAudioEventModel(),
            visionModel = MockVisionEventModel(),
            fusionEngine = MockFusionEngine(),
            config = GuardianConfig()
        )
    }

    @Test
    fun `initial state is NORMAL`() {
        assertEquals(SafetyState.NORMAL, engine.safetyState.value)
    }

    @Test
    fun `initial pipeline stage is IDLE`() {
        assertEquals(PipelineStage.IDLE, engine.pipelineStage.value)
    }

    @Test
    fun `normal motion does not trigger anomaly`() {
        val features = MotionFeatures(
            meanAccelMagnitude = 9.8f,
            peakAccelMagnitude = 11f,
            accelVariance = 1.5f,
            meanGyroMagnitude = 0.3f,
            peakGyroMagnitude = 0.8f,
            gyroVariance = 0.2f
        )

        val result = engine.processMotionData(features)
        assertFalse(result.isAnomaly)
        assertEquals(SafetyState.NORMAL, engine.safetyState.value)
    }

    @Test
    fun `high impact motion triggers anomaly candidate via simulation`() {
        engine.simulateMotionAnomaly(
            MotionAnomalyResult(
                isAnomaly = true,
                anomalyScore = 0.85f,
                anomalyType = MotionAnomalyType.SUDDEN_IMPACT,
                confidence = 0.8f,
                features = MotionFeatures(peakAccelMagnitude = 35f, accelVariance = 80f)
            )
        )

        assertTrue(engine.safetyState.value != SafetyState.NORMAL)
    }

    @Test
    fun `cancel investigation returns to NORMAL`() {
        engine.simulateMotionAnomaly(
            MotionAnomalyResult(
                isAnomaly = true,
                anomalyScore = 0.85f,
                anomalyType = MotionAnomalyType.SUDDEN_IMPACT,
                confidence = 0.8f,
                features = MotionFeatures(peakAccelMagnitude = 35f)
            )
        )

        engine.cancelInvestigation()
        assertEquals(SafetyState.NORMAL, engine.safetyState.value)
        assertEquals(PipelineStage.IDLE, engine.pipelineStage.value)
        assertEquals(0f, engine.riskScore.value)
    }

    @Test
    fun `force state to ACTIVE_EMERGENCY creates emergency event`() {
        engine.forceState(SafetyState.ACTIVE_EMERGENCY)

        assertEquals(SafetyState.ACTIVE_EMERGENCY, engine.safetyState.value)
        assertNotNull(engine.currentEmergency.value)
        assertTrue(engine.currentEmergency.value!!.isSimulated)
    }

    @Test
    fun `resolve emergency returns to NORMAL`() {
        engine.forceState(SafetyState.ACTIVE_EMERGENCY)
        engine.resolveEmergency()

        assertEquals(SafetyState.NORMAL, engine.safetyState.value)
        assertEquals(PipelineStage.IDLE, engine.pipelineStage.value)
    }
}
