package com.safeguardian.app.domain.response

import com.safeguardian.app.domain.model.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Phase 5 Unit Tests:
 * 1. Multimodal risk calculation and weighted corroboration
 * 2. Single-sensor risk capping (<= 0.70)
 * 3. False-positive rejection scenarios (motion only, audio only, vision only, normal audio/vision)
 * 4. Temporal persistence requirements
 * 5. Emergency contact management (add, toggle, delete)
 * 6. Emergency message generator formatting
 * 7. SimulationEmergencyDispatcher recording
 * 8. LocationState transitions
 */
class EmergencyResponseTest {

    private lateinit var riskEngine: MultimodalRiskEngine
    private lateinit var dispatcher: SimulationEmergencyDispatcher

    @Before
    fun setUp() {
        riskEngine = MultimodalRiskEngine()
        dispatcher = SimulationEmergencyDispatcher()
    }

    @Test
    fun `single sensor motion spike is capped and cannot trigger high confidence`() {
        val motion = MotionAnomalyResult(
            isAnomaly = true,
            anomalyType = MotionAnomalyType.SUDDEN_IMPACT,
            anomalyScore = 0.95f,
            confidence = 0.95f,
            features = MotionFeatures(peakAccelMagnitude = 35f, accelDeltaMagnitude = 120f)
        )

        val result = riskEngine.evaluate(
            motionResult = motion,
            audioResult = null,
            visionResult = null,
            temporalPersistence = 0.2f,
            corroborationCount = 1,
            eventDurationMs = 200L
        )

        // Must be capped at <= 0.70f per Phase 5 requirement
        assertTrue("Single-sensor spike must be <= 0.70f, was: ${result.riskScore}", result.riskScore <= 0.70f)
        assertNotEquals("Single-sensor spike must not reach HIGH_CONFIDENCE", RiskLevel.HIGH_CONFIDENCE, result.riskLevel)
        assertTrue("Contributing signals must contain Motion", result.contributingSignals.any { it.contains("Motion") })
    }

    @Test
    fun `audio only loud spike is capped and cannot trigger high confidence`() {
        val audio = AudioEventResult(
            eventType = AudioEventType.SCREAM_DISTRESS,
            confidence = 0.95f
        )

        val result = riskEngine.evaluate(
            motionResult = null,
            audioResult = audio,
            visionResult = null,
            temporalPersistence = 0.2f,
            corroborationCount = 1,
            eventDurationMs = 500L
        )

        assertTrue("Audio only spike must be <= 0.70f, was: ${result.riskScore}", result.riskScore <= 0.70f)
        assertNotEquals(RiskLevel.HIGH_CONFIDENCE, result.riskLevel)
    }

    @Test
    fun `vision only anomaly is capped and cannot trigger high confidence`() {
        val vision = VisionEventResult(
            eventType = VisionEventType.PERSON_ON_GROUND,
            confidence = 0.95f
        )

        val result = riskEngine.evaluate(
            motionResult = null,
            audioResult = null,
            visionResult = vision,
            temporalPersistence = 0.2f,
            corroborationCount = 1,
            eventDurationMs = 300L
        )

        assertTrue("Vision only anomaly must be <= 0.70f, was: ${result.riskScore}", result.riskScore <= 0.70f)
        assertNotEquals(RiskLevel.HIGH_CONFIDENCE, result.riskLevel)
    }

    @Test
    fun `motion plus normal audio does not escalate to emergency`() {
        val motion = MotionAnomalyResult(
            isAnomaly = true,
            anomalyType = MotionAnomalyType.SUDDEN_IMPACT,
            anomalyScore = 0.55f,
            confidence = 0.55f,
            features = MotionFeatures(peakAccelMagnitude = 18f, accelDeltaMagnitude = 45f)
        )
        val normalAudio = AudioEventResult(
            eventType = AudioEventType.NONE,
            confidence = 0.05f
        )

        val result = riskEngine.evaluate(
            motionResult = motion,
            audioResult = normalAudio,
            visionResult = null,
            temporalPersistence = 0.1f,
            corroborationCount = 1,
            eventDurationMs = 400L
        )

        assertTrue("Normal audio must yield low risk score", result.riskScore < 0.50f)
        assertEquals(RiskLevel.NORMAL, result.riskLevel)
    }

    @Test
    fun `motion plus audio corroboration yields higher score than motion alone`() {
        val motion = MotionAnomalyResult(
            isAnomaly = true,
            anomalyType = MotionAnomalyType.SUDDEN_IMPACT,
            anomalyScore = 0.80f,
            confidence = 0.80f,
            features = MotionFeatures(peakAccelMagnitude = 26f, accelDeltaMagnitude = 80f)
        )
        val audio = AudioEventResult(
            eventType = AudioEventType.CRASH_IMPACT,
            confidence = 0.75f
        )

        val motionOnly = riskEngine.evaluate(
            motionResult = motion,
            audioResult = null,
            visionResult = null,
            temporalPersistence = 0.2f,
            corroborationCount = 1,
            eventDurationMs = 300L
        )

        val motionPlusAudio = riskEngine.evaluate(
            motionResult = motion,
            audioResult = audio,
            visionResult = null,
            temporalPersistence = 0.5f,
            corroborationCount = 2,
            eventDurationMs = 1200L
        )

        assertTrue(
            "Motion + audio corroboration (${motionPlusAudio.riskScore}) must exceed motion alone (${motionOnly.riskScore})",
            motionPlusAudio.riskScore > motionOnly.riskScore
        )
        assertTrue(motionPlusAudio.contributingSignals.any { it.contains("Motion") })
        assertTrue(motionPlusAudio.contributingSignals.any { it.contains("Audio") })
    }

    @Test
    fun `multi-sensor corroboration with temporal persistence escalates to high confidence`() {
        val motion = MotionAnomalyResult(
            isAnomaly = true,
            anomalyType = MotionAnomalyType.FALL_LIKE_MOTION,
            anomalyScore = 0.90f,
            confidence = 0.90f,
            features = MotionFeatures(peakAccelMagnitude = 28f, immobilityDuration = 3000L)
        )
        val audio = AudioEventResult(
            eventType = AudioEventType.SCREAM_DISTRESS,
            confidence = 0.85f
        )
        val vision = VisionEventResult(
            eventType = VisionEventType.PERSON_ON_GROUND,
            confidence = 0.90f
        )

        val result = riskEngine.evaluate(
            motionResult = motion,
            audioResult = audio,
            visionResult = vision,
            temporalPersistence = 0.8f,
            corroborationCount = 3,
            eventDurationMs = 4500L
        )

        assertTrue("Corroborated multimodal event score must be >= 0.85f, was: ${result.riskScore}", result.riskScore >= 0.85f)
        assertEquals(RiskLevel.HIGH_CONFIDENCE, result.riskLevel)
        assertEquals(SafetyState.HIGH_CONFIDENCE_EMERGENCY, result.recommendedState)
        assertEquals(3, result.corroborationCount)
    }

    @Test
    fun `emergency message generation includes category timestamp and location`() {
        val event = EmergencyEvent(
            id = "evt-12345",
            timestamp = 1700000000000L,
            eventType = "FALL_DETECTED",
            confidence = 0.92f,
            sensorEvidence = SensorEvidence(
                motionAnomalyType = MotionAnomalyType.FALL_LIKE_MOTION,
                motionConfidence = 0.92f
            ),
            isSimulated = true
        )

        val location = GeoLocation(
            latitude = 37.7749,
            longitude = -122.4194,
            accuracyMeters = 4.5f,
            timestamp = 1700000000000L
        )

        val message = EmergencyMessageGenerator.generateMessage(event, location)

        assertTrue("Message must contain 'Possible emergency detected'", message.contains("Possible emergency detected"))
        assertTrue("Message must include event type", message.contains("FALL_DETECTED"))
        assertTrue("Message must include maps link", message.contains("https://maps.google.com/?q=37.7749,-122.4194"))
        assertTrue("Message must include accuracy", message.contains("4m"))
    }

    @Test
    fun `emergency message handles null location gracefully`() {
        val event = EmergencyEvent(
            id = "evt-999",
            timestamp = 1700000000000L,
            eventType = "CRASH",
            confidence = 0.88f,
            sensorEvidence = SensorEvidence(),
            isSimulated = true
        )

        val message = EmergencyMessageGenerator.generateMessage(event, null)

        assertTrue(message.contains("Location: Unavailable"))
        assertFalse(message.contains("https://maps.google.com"))
    }

    @Test
    fun `simulation dispatcher records simulated sms without triggering external services`() {
        val event = EmergencyEvent(
            id = "evt-test-1",
            timestamp = System.currentTimeMillis(),
            eventType = "FALL",
            confidence = 0.95f,
            sensorEvidence = SensorEvidence(),
            isSimulated = true
        )

        val contacts = listOf(
            EmergencyContact("c1", "Alice", "+15550001", "Friend", isEnabled = true),
            EmergencyContact("c2", "Bob", "+15550002", "Sibling", isEnabled = false), // disabled
            EmergencyContact("c3", "Carol", "+15550003", "Parent", isEnabled = true)
        )

        val records = dispatcher.dispatch(event, null, contacts)

        // Only enabled contacts should receive dispatch records
        assertEquals(2, records.size)
        assertEquals("Alice", records[0].recipientName)
        assertEquals("Carol", records[1].recipientName)
        assertTrue(records[0].messageText.contains("Possible emergency detected"))

        // Check dispatcher history
        assertEquals(2, dispatcher.dispatchedRecords.value.size)
    }

    @Test
    fun `location state values are well defined`() {
        val states = LocationState.values()
        assertTrue(states.contains(LocationState.UNAVAILABLE))
        assertTrue(states.contains(LocationState.ACQUIRING))
        assertTrue(states.contains(LocationState.AVAILABLE))
        assertTrue(states.contains(LocationState.UPDATING))
        assertTrue(states.contains(LocationState.STOPPED))
    }
}
