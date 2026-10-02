package com.safeguardian.app.domain.ai.ml

import com.safeguardian.app.domain.model.*
import com.safeguardian.app.domain.motion.SensorDataSanitizer
import com.safeguardian.app.domain.response.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class Phase10AdversarialTest {

    private lateinit var riskEngine: MultimodalRiskEngine

    @Before
    fun setup() {
        riskEngine = MultimodalRiskEngine(
            motionWeight = 0.35f,
            audioWeight = 0.30f,
            visionWeight = 0.25f,
            temporalWeight = 0.10f,
            suspiciousThreshold = 0.65f,
            highConfidenceThreshold = 0.85f,
            minPersistenceForEmergency = 0.40f
        )
    }

    // =========================================================================
    // 1. ADVERSARIAL FALSE-POSITIVE TESTING: 21 MOTION SCENARIOS
    // =========================================================================
    @Test
    fun testAdversarialMotion21ScenariosDoNotTriggerEmergency() {
        data class MotionScenario(val name: String, val features: MotionFeatures)

        val scenarios = listOf(
            MotionScenario("walking while carrying phone", MotionFeatures(peakAccelMagnitude = 14f, accelDeltaMagnitude = 12f, meanAccelMagnitude = 10f, accelVariance = 2.5f)),
            MotionScenario("running with phone", MotionFeatures(peakAccelMagnitude = 24f, accelDeltaMagnitude = 45f, meanAccelMagnitude = 12f, accelVariance = 9.0f, peakGyroMagnitude = 4f)),
            MotionScenario("climbing stairs rapidly", MotionFeatures(peakAccelMagnitude = 18f, accelDeltaMagnitude = 28f, meanAccelMagnitude = 11f, accelVariance = 6.0f, peakGyroMagnitude = 2.5f)),
            MotionScenario("descending stairs", MotionFeatures(peakAccelMagnitude = 19f, accelDeltaMagnitude = 32f, meanAccelMagnitude = 11.5f, accelVariance = 6.5f, peakGyroMagnitude = 2.8f)),
            MotionScenario("jumping", MotionFeatures(peakAccelMagnitude = 26f, accelDeltaMagnitude = 50f, meanAccelMagnitude = 13f, accelVariance = 12f, peakGyroMagnitude = 3f)),
            MotionScenario("sitting quickly", MotionFeatures(peakAccelMagnitude = 15f, accelDeltaMagnitude = 20f, meanAccelMagnitude = 9.8f, accelVariance = 3f)),
            MotionScenario("lying down quickly", MotionFeatures(peakAccelMagnitude = 13f, accelDeltaMagnitude = 18f, meanAccelMagnitude = 9.8f, accelVariance = 2.5f)),
            MotionScenario("rolling over", MotionFeatures(peakAccelMagnitude = 11f, accelDeltaMagnitude = 8f, meanAccelMagnitude = 9.8f, accelVariance = 1.5f, peakGyroMagnitude = 3.2f)),
            MotionScenario("phone placed on table", MotionFeatures(peakAccelMagnitude = 16f, accelDeltaMagnitude = 25f, meanAccelMagnitude = 9.8f, accelVariance = 4f)),
            MotionScenario("phone placed on bed", MotionFeatures(peakAccelMagnitude = 12f, accelDeltaMagnitude = 10f, meanAccelMagnitude = 9.8f, accelVariance = 1.8f)),
            MotionScenario("phone dropped onto couch", MotionFeatures(peakAccelMagnitude = 22f, accelDeltaMagnitude = 38f, meanAccelMagnitude = 9.8f, accelVariance = 8.5f)),
            MotionScenario("phone dropped onto mattress", MotionFeatures(peakAccelMagnitude = 20f, accelDeltaMagnitude = 30f, meanAccelMagnitude = 9.8f, accelVariance = 7.0f)),
            MotionScenario("phone sliding across table", MotionFeatures(peakAccelMagnitude = 11f, accelDeltaMagnitude = 6f, meanAccelMagnitude = 9.8f, accelVariance = 0.8f)),
            MotionScenario("phone placed in bag", MotionFeatures(peakAccelMagnitude = 14f, accelDeltaMagnitude = 16f, meanAccelMagnitude = 9.8f, accelVariance = 2.5f)),
            MotionScenario("phone removed from bag", MotionFeatures(peakAccelMagnitude = 15f, accelDeltaMagnitude = 18f, meanAccelMagnitude = 10f, accelVariance = 3.0f)),
            MotionScenario("phone inside pocket", MotionFeatures(peakAccelMagnitude = 13f, accelDeltaMagnitude = 14f, meanAccelMagnitude = 9.8f, accelVariance = 2.0f)),
            MotionScenario("phone rotating in pocket", MotionFeatures(peakAccelMagnitude = 12f, accelDeltaMagnitude = 10f, meanAccelMagnitude = 9.8f, peakGyroMagnitude = 4.5f)),
            MotionScenario("vehicle vibration", MotionFeatures(peakAccelMagnitude = 12.5f, accelDeltaMagnitude = 15f, meanAccelMagnitude = 9.8f, accelVariance = 2.2f)),
            MotionScenario("motorcycle vibration", MotionFeatures(peakAccelMagnitude = 16f, accelDeltaMagnitude = 28f, meanAccelMagnitude = 10.5f, accelVariance = 5.0f, peakGyroMagnitude = 1.8f)),
            MotionScenario("bus vibration", MotionFeatures(peakAccelMagnitude = 13.5f, accelDeltaMagnitude = 18f, meanAccelMagnitude = 9.8f, accelVariance = 2.8f)),
            MotionScenario("train vibration", MotionFeatures(peakAccelMagnitude = 12f, accelDeltaMagnitude = 14f, meanAccelMagnitude = 9.8f, accelVariance = 1.9f))
        )

        for (s in scenarios) {
            val sanitized = SensorDataSanitizer.sanitizeFeatures(s.features)
            val motionResult = MotionAnomalyResult(
                isAnomaly = sanitized.peakAccelMagnitude > 25f,
                anomalyScore = (sanitized.peakAccelMagnitude / 50f).coerceIn(0f, 0.70f),
                anomalyType = if (sanitized.peakAccelMagnitude > 25f) MotionAnomalyType.UNUSUAL_ACCELERATION else MotionAnomalyType.NONE,
                confidence = 0.50f,
                features = sanitized
            )

            val eval = riskEngine.evaluate(
                motionResult = if (motionResult.isAnomaly) motionResult else null,
                audioResult = null,
                visionResult = null,
                temporalPersistence = 0.1f,
                corroborationCount = if (motionResult.isAnomaly) 1 else 0
            )

            assertNotEquals("Motion scenario '${s.name}' must not trigger HIGH_CONFIDENCE",
                RiskLevel.HIGH_CONFIDENCE, eval.riskLevel)
            assertNotEquals("Motion scenario '${s.name}' must not trigger emergency",
                SafetyState.HIGH_CONFIDENCE_EMERGENCY, eval.recommendedState)
            assertTrue("Single motion cannot exceed 0.70 risk", eval.riskScore <= 0.70f)
        }
    }

    // =========================================================================
    // 2. ADVERSARIAL FALSE-POSITIVE TESTING: 15 AUDIO SCENARIOS
    // =========================================================================
    @Test
    fun testAdversarialAudio15ScenariosDoNotTriggerEmergency() {
        val benignAudioTypes = listOf(
            "shouting from television" to AudioEventType.LOUD_ABNORMAL,
            "movie action scenes" to AudioEventType.LOUD_ABNORMAL,
            "music with strong bass" to AudioEventType.LOUD_ABNORMAL,
            "loud laughter" to AudioEventType.NONE,
            "children shouting" to AudioEventType.LOUD_ABNORMAL,
            "crowd noise" to AudioEventType.NONE,
            "construction noise" to AudioEventType.LOUD_ABNORMAL,
            "traffic" to AudioEventType.NONE,
            "horn" to AudioEventType.LOUD_ABNORMAL,
            "door slam" to AudioEventType.LOUD_ABNORMAL,
            "objects falling" to AudioEventType.LOUD_ABNORMAL,
            "kitchen noise" to AudioEventType.NONE,
            "vacuum cleaner" to AudioEventType.NONE,
            "blender" to AudioEventType.NONE,
            "alarm clock" to AudioEventType.LOUD_ABNORMAL
        )

        for ((name, type) in benignAudioTypes) {
            val audioResult = AudioEventResult(
                eventType = type,
                confidence = 0.60f
            )

            val eval = riskEngine.evaluate(
                motionResult = null,
                audioResult = audioResult,
                visionResult = null,
                temporalPersistence = 0.1f,
                corroborationCount = 1
            )

            assertNotEquals("Audio scenario '$name' must not trigger HIGH_CONFIDENCE",
                RiskLevel.HIGH_CONFIDENCE, eval.riskLevel)
            assertTrue("Single audio cannot exceed 0.70 risk", eval.riskScore <= 0.70f)
        }
    }

    // =========================================================================
    // 3. ADVERSARIAL FALSE-POSITIVE TESTING: 17 VISION SCENARIOS
    // =========================================================================
    @Test
    fun testAdversarialVision17ScenariosDoNotTriggerEmergency() {
        val benignVisionTypes = listOf(
            "person lying on floor intentionally" to VisionEventType.PERSON_ON_GROUND,
            "sleeping person" to VisionEventType.PERSON_ON_GROUND,
            "person exercising" to VisionEventType.PHYSICAL_STRUGGLE,
            "yoga" to VisionEventType.PERSON_ON_GROUND,
            "stretching" to VisionEventType.NONE,
            "crawling" to VisionEventType.PERSON_ON_GROUND,
            "sitting on floor" to VisionEventType.PERSON_ON_GROUND,
            "crouching" to VisionEventType.NONE,
            "bending" to VisionEventType.NONE,
            "multiple people" to VisionEventType.NONE,
            "partially occluded person" to VisionEventType.NONE,
            "person entering frame" to VisionEventType.NONE,
            "pets" to VisionEventType.NONE,
            "shadows" to VisionEventType.NONE,
            "low light" to VisionEventType.NONE,
            "backlight" to VisionEventType.NONE,
            "camera obstruction" to VisionEventType.NONE
        )

        for ((name, type) in benignVisionTypes) {
            val visionResult = VisionEventResult(
                eventType = type,
                confidence = 0.65f
            )

            val eval = riskEngine.evaluate(
                motionResult = null,
                audioResult = null,
                visionResult = visionResult,
                temporalPersistence = 0.15f,
                corroborationCount = 1
            )

            assertNotEquals("Vision scenario '$name' must not trigger HIGH_CONFIDENCE",
                RiskLevel.HIGH_CONFIDENCE, eval.riskLevel)
            assertTrue("Single vision cannot exceed 0.70 risk", eval.riskScore <= 0.70f)
        }
    }

    // =========================================================================
    // 4. ADVERSARIAL MULTIMODAL BENIGN COMBINATIONS
    // =========================================================================
    @Test
    fun testBenignMultimodalCombinationsDoNotTriggerEmergency() {
        data class MultimodalBenignCombo(
            val name: String,
            val motionScore: Float,
            val audioScore: Float,
            val visionScore: Float
        )

        val combos = listOf(
            MultimodalBenignCombo("running + loud music", 0.40f, 0.50f, 0.05f),
            MultimodalBenignCombo("stairs + door slam", 0.35f, 0.60f, 0.05f),
            MultimodalBenignCombo("phone drop + television shouting", 0.65f, 0.55f, 0.05f),
            MultimodalBenignCombo("person lying down + conversation", 0.15f, 0.15f, 0.60f),
            MultimodalBenignCombo("crowded room + loud music", 0.10f, 0.50f, 0.20f),
            MultimodalBenignCombo("vehicle vibration + traffic", 0.25f, 0.40f, 0.05f),
            MultimodalBenignCombo("exercise + loud music", 0.45f, 0.50f, 0.35f),
            MultimodalBenignCombo("phone in pocket + speech", 0.20f, 0.25f, 0.00f)
        )

        for (c in combos) {
            val motion = if (c.motionScore > 0.5f) MotionAnomalyResult(true, c.motionScore, MotionAnomalyType.UNUSUAL_ACCELERATION, c.motionScore, MotionFeatures(peakAccelMagnitude = 20f)) else null
            val audio = if (c.audioScore > 0.4f) AudioEventResult(AudioEventType.LOUD_ABNORMAL, c.audioScore) else null
            val vision = if (c.visionScore > 0.5f) VisionEventResult(VisionEventType.PERSON_ON_GROUND, c.visionScore) else null

            val eval = riskEngine.evaluate(
                motionResult = motion,
                audioResult = audio,
                visionResult = vision,
                temporalPersistence = 0.20f,
                corroborationCount = listOfNotNull(motion, audio, vision).size
            )

            assertNotEquals("Combo '${c.name}' must not trigger HIGH_CONFIDENCE emergency",
                RiskLevel.HIGH_CONFIDENCE, eval.riskLevel)
            assertNotEquals("Combo '${c.name}' must not trigger HIGH_CONFIDENCE_EMERGENCY state",
                SafetyState.HIGH_CONFIDENCE_EMERGENCY, eval.recommendedState)
        }
    }

    // =========================================================================
    // 5. ADVERSARIAL FALSE-NEGATIVE TESTING: HIGH-RISK PROGRESSION (SCENARIOS A-F)
    // =========================================================================
    @Test
    fun testAdversarialFalseNegativeScenariosAtoF() {
        // Scenario A: strong motion anomaly + distress audio + persistent fall-like vision
        val evalA = riskEngine.evaluate(
            motionResult = MotionAnomalyResult(true, 0.92f, MotionAnomalyType.FALL_LIKE_MOTION, 0.92f, MotionFeatures(peakAccelMagnitude = 38f)),
            audioResult = AudioEventResult(AudioEventType.SCREAM_DISTRESS, 0.88f),
            visionResult = VisionEventResult(VisionEventType.PERSON_ON_GROUND, 0.90f),
            temporalPersistence = 0.85f,
            corroborationCount = 3,
            eventDurationMs = 5000L
        )
        assertEquals("Scenario A must be HIGH_CONFIDENCE", RiskLevel.HIGH_CONFIDENCE, evalA.riskLevel)
        assertEquals(SafetyState.HIGH_CONFIDENCE_EMERGENCY, evalA.recommendedState)

        // Scenario B: motion anomaly + distress audio + persistent ground posture
        val evalB = riskEngine.evaluate(
            motionResult = MotionAnomalyResult(true, 0.85f, MotionAnomalyType.FALL_LIKE_MOTION, 0.85f, MotionFeatures(peakAccelMagnitude = 32f)),
            audioResult = AudioEventResult(AudioEventType.CRASH_IMPACT, 0.82f),
            visionResult = VisionEventResult(VisionEventType.FALL_DETECTED, 0.85f),
            temporalPersistence = 0.70f,
            corroborationCount = 3,
            eventDurationMs = 4000L
        )
        assertEquals("Scenario B must be HIGH_CONFIDENCE", RiskLevel.HIGH_CONFIDENCE, evalB.riskLevel)

        // Scenario C: strong motion + audio suspicion + vision persistence
        val evalC = riskEngine.evaluate(
            motionResult = MotionAnomalyResult(true, 0.90f, MotionAnomalyType.SUDDEN_IMPACT, 0.90f, MotionFeatures(peakAccelMagnitude = 35f)),
            audioResult = AudioEventResult(AudioEventType.SCREAM_DISTRESS, 0.80f),
            visionResult = VisionEventResult(VisionEventType.PERSON_ON_GROUND, 0.85f),
            temporalPersistence = 0.65f,
            corroborationCount = 3,
            eventDurationMs = 3500L
        )
        assertEquals("Scenario C must be HIGH_CONFIDENCE", RiskLevel.HIGH_CONFIDENCE, evalC.riskLevel)

        // Scenario D: delayed multimodal evidence (two strong corroborated modalities)
        val evalD = riskEngine.evaluate(
            motionResult = MotionAnomalyResult(true, 0.95f, MotionAnomalyType.FALL_LIKE_MOTION, 0.95f, MotionFeatures(peakAccelMagnitude = 40f)),
            audioResult = AudioEventResult(AudioEventType.SCREAM_DISTRESS, 0.90f),
            visionResult = null, // vision delayed or unavailable
            temporalPersistence = 0.60f,
            corroborationCount = 2,
            eventDurationMs = 6000L
        )
        assertTrue("Scenario D dual sensor must be >= 0.75", evalD.riskScore >= 0.75f)

        // Scenario E: intermittent sensor evidence
        val evalE = riskEngine.evaluate(
            motionResult = MotionAnomalyResult(true, 0.88f, MotionAnomalyType.FALL_LIKE_MOTION, 0.88f, MotionFeatures(peakAccelMagnitude = 33f)),
            audioResult = null,
            visionResult = VisionEventResult(VisionEventType.PERSON_ON_GROUND, 0.85f),
            temporalPersistence = 0.50f,
            corroborationCount = 2,
            eventDurationMs = 3000L
        )
        assertTrue("Scenario E dual sensor with persistence must reach SUSPICIOUS or higher",
            evalE.riskLevel == RiskLevel.SUSPICIOUS || evalE.riskLevel == RiskLevel.HIGH_CONFIDENCE)

        // Scenario F: sensor dropout during high-confidence event
        // Never allow 1 dropped sensor to collapse all evidence, but also never 1 sensor alone activating ACTIVE_EMERGENCY
        val evalF = riskEngine.evaluate(
            motionResult = MotionAnomalyResult(true, 0.92f, MotionAnomalyType.FALL_LIKE_MOTION, 0.92f, MotionFeatures(peakAccelMagnitude = 35f)),
            audioResult = null, // dropped out
            visionResult = null, // dropped out
            temporalPersistence = 0.1f,
            corroborationCount = 1
        )
        assertTrue("Single remaining sensor must be capped <= 0.70", evalF.riskScore <= 0.70f)
        assertNotEquals(RiskLevel.HIGH_CONFIDENCE, evalF.riskLevel)
    }

    // =========================================================================
    // 6. SENSOR DISAGREEMENT TESTING (7 COMBINATIONS)
    // =========================================================================
    @Test
    fun testSensorDisagreementAll7Combinations() {
        val highMotion = MotionAnomalyResult(true, 0.90f, MotionAnomalyType.FALL_LIKE_MOTION, 0.90f, MotionFeatures(peakAccelMagnitude = 35f))
        val normMotion = MotionAnomalyResult(false, 0.05f, MotionAnomalyType.NONE, 0.05f, MotionFeatures(peakAccelMagnitude = 9.8f))

        val highAudio = AudioEventResult(AudioEventType.SCREAM_DISTRESS, 0.85f)
        val normAudio = AudioEventResult(AudioEventType.NONE, 0.05f)

        val highVision = VisionEventResult(VisionEventType.PERSON_ON_GROUND, 0.88f)
        val normVision = VisionEventResult(VisionEventType.NONE, 0.05f)

        // 1. motion HIGH + audio NORMAL + vision NORMAL
        val r1 = riskEngine.evaluate(highMotion, normAudio, normVision, 0.1f, 1)
        assertTrue("Disagreement 1 capped <= 0.70", r1.riskScore <= 0.70f)
        assertNotEquals(RiskLevel.HIGH_CONFIDENCE, r1.riskLevel)

        // 2. motion NORMAL + audio HIGH + vision NORMAL
        val r2 = riskEngine.evaluate(normMotion, highAudio, normVision, 0.1f, 1)
        assertTrue("Disagreement 2 capped <= 0.70", r2.riskScore <= 0.70f)
        assertNotEquals(RiskLevel.HIGH_CONFIDENCE, r2.riskLevel)

        // 3. motion NORMAL + audio NORMAL + vision HIGH
        val r3 = riskEngine.evaluate(normMotion, normAudio, highVision, 0.1f, 1)
        assertTrue("Disagreement 3 capped <= 0.70", r3.riskScore <= 0.70f)
        assertNotEquals(RiskLevel.HIGH_CONFIDENCE, r3.riskLevel)

        // 4. motion HIGH + audio HIGH + vision NORMAL
        val r4 = riskEngine.evaluate(highMotion, highAudio, normVision, 0.5f, 2)
        assertTrue("Disagreement 4 dual corroboration >= 0.65", r4.riskScore >= 0.65f)

        // 5. motion HIGH + audio NORMAL + vision HIGH
        val r5 = riskEngine.evaluate(highMotion, normAudio, highVision, 0.5f, 2)
        assertTrue("Disagreement 5 dual corroboration >= 0.65", r5.riskScore >= 0.65f)

        // 6. motion NORMAL + audio HIGH + vision HIGH
        val r6 = riskEngine.evaluate(normMotion, highAudio, highVision, 0.5f, 2)
        assertTrue("Disagreement 6 dual corroboration >= 0.65", r6.riskScore >= 0.65f)

        // 7. motion HIGH + audio HIGH + vision HIGH
        val r7 = riskEngine.evaluate(highMotion, highAudio, highVision, 0.8f, 3)
        assertEquals("Disagreement 7 triple corroboration is HIGH_CONFIDENCE", RiskLevel.HIGH_CONFIDENCE, r7.riskLevel)
    }

    // =========================================================================
    // 7. SENSOR DATA CORRUPTION & MALFORMED INPUT RESILIENCE
    // =========================================================================
    @Test
    fun testSensorDataCorruptionResilience() {
        val corruptCases = listOf(
            "NaN in accel" to MotionFeatures(peakAccelMagnitude = Float.NaN, meanAccelMagnitude = Float.NaN),
            "Positive Infinity in accel" to MotionFeatures(peakAccelMagnitude = Float.POSITIVE_INFINITY, accelDeltaMagnitude = Float.POSITIVE_INFINITY),
            "Negative Infinity in accel" to MotionFeatures(peakAccelMagnitude = Float.NEGATIVE_INFINITY),
            "Extreme impossible values" to MotionFeatures(peakAccelMagnitude = 10_000.0f, peakGyroMagnitude = 5_000.0f),
            "Negative impossible variance" to MotionFeatures(accelVariance = -100.0f, gyroVariance = -50.0f),
            "Future timestamp" to MotionFeatures(timestamp = System.currentTimeMillis() + 1_000_000_000L),
            "Negative timestamp" to MotionFeatures(timestamp = -5000L),
            "Zero timestamp" to MotionFeatures(timestamp = 0L)
        )

        for ((name, corruptFeature) in corruptCases) {
            val sanitized = SensorDataSanitizer.sanitizeFeatures(corruptFeature)

            assertTrue("Sanitized peakAccelMagnitude must be finite in $name", sanitized.peakAccelMagnitude.isFinite())
            assertFalse("Sanitized peakAccelMagnitude must not be NaN in $name", sanitized.peakAccelMagnitude.isNaN())
            assertTrue("Sanitized peakAccelMagnitude must be <= 200 in $name", sanitized.peakAccelMagnitude <= 200.0f)
            assertTrue("Sanitized variance must be >= 0 in $name", sanitized.accelVariance >= 0f)
            assertTrue("Sanitized timestamp must be > 0 in $name", sanitized.timestamp > 0L)

            // Evaluate in risk engine with malformed input
            val motionResult = MotionAnomalyResult(
                isAnomaly = sanitized.peakAccelMagnitude > 50f,
                anomalyScore = 0.5f,
                anomalyType = MotionAnomalyType.NONE,
                confidence = 0.1f,
                features = sanitized
            )

            val eval = riskEngine.evaluate(motionResult, null, null, 0f, 0)
            assertNotEquals("Corrupted input $name must NEVER cause emergency", RiskLevel.HIGH_CONFIDENCE, eval.riskLevel)
            assertFalse("Risk score must be finite in $name", eval.riskScore.isNaN())
        }
    }
}
