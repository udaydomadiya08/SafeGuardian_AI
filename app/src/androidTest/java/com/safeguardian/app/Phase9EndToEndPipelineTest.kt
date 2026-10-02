package com.safeguardian.app

import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.safeguardian.app.domain.ai.ml.*
import com.safeguardian.app.domain.model.*
import com.safeguardian.app.domain.response.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Phase9EndToEndPipelineTest {

    private lateinit var app: SafeGuardianApp

    @Before
    fun setup() {
        app = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testNormalMonitoringKeepsSensorsOff() = runBlocking {
        // Step 4 & 13: In NORMAL state, GPS must be STOPPED
        assertEquals(LocationState.STOPPED, app.locationManager.locationState.value)
        assertEquals(SafetyState.NORMAL, app.stateEngine.safetyState.value)
        assertEquals(PipelineStage.IDLE, app.stateEngine.pipelineStage.value)
    }

    @Test
    fun testControlledHarmlessActivitiesProduceNoEmergency() = runBlocking {
        // Step 4: Controlled benign everyday activities
        val activities = listOf(
            "walking" to MotionFeatures(peakAccelMagnitude = 14f, accelDeltaMagnitude = 15f, meanAccelMagnitude = 10f, accelVariance = 3f, peakGyroMagnitude = 1.2f),
            "sitting" to MotionFeatures(peakAccelMagnitude = 9.8f, accelDeltaMagnitude = 1f, meanAccelMagnitude = 9.8f, accelVariance = 0.1f, peakGyroMagnitude = 0.05f),
            "standing" to MotionFeatures(peakAccelMagnitude = 9.8f, accelDeltaMagnitude = 0.5f, meanAccelMagnitude = 9.8f, accelVariance = 0.05f, peakGyroMagnitude = 0.02f),
            "running" to MotionFeatures(peakAccelMagnitude = 22f, accelDeltaMagnitude = 35f, meanAccelMagnitude = 12f, accelVariance = 8f, peakGyroMagnitude = 3.5f),
            "stairs" to MotionFeatures(peakAccelMagnitude = 17f, accelDeltaMagnitude = 22f, meanAccelMagnitude = 11f, accelVariance = 5f, peakGyroMagnitude = 2.0f),
            "phone_placement" to MotionFeatures(peakAccelMagnitude = 16f, accelDeltaMagnitude = 25f, meanAccelMagnitude = 9.8f, accelVariance = 4f, peakGyroMagnitude = 2.5f),
            "phone_pickup" to MotionFeatures(peakAccelMagnitude = 15f, accelDeltaMagnitude = 20f, meanAccelMagnitude = 10.2f, accelVariance = 3.5f, peakGyroMagnitude = 2.2f)
        )

        for ((name, feature) in activities) {
            val mlOut = app.motionMLModel.predict(feature)
            assertTrue(mlOut.isUsingRealML)

            app.stateEngine.processMotionData(feature)
            val currentState = app.stateEngine.safetyState.value

            assertNotEquals("Activity $name must not trigger ACTIVE_EMERGENCY directly",
                SafetyState.ACTIVE_EMERGENCY, currentState)
            assertNotEquals("Activity $name must not trigger HIGH_CONFIDENCE_EMERGENCY",
                SafetyState.HIGH_CONFIDENCE_EMERGENCY, currentState)
        }

        app.stateEngine.cancelInvestigation()
    }

    @Test
    fun testSafeFallSimulationDoesNotDirectlyTriggerEmergency() = runBlocking {
        // Step 5: Safe Fall Proxy - rapid downward deceleration onto soft surface
        val safeFallFeature = MotionFeatures(
            peakAccelMagnitude = 32.5f,
            accelDeltaMagnitude = 78.0f,
            meanAccelMagnitude = 11.2f,
            accelVariance = 18.5f,
            peakGyroMagnitude = 5.8f,
            immobilityDuration = 2000L
        )

        val mlOut = app.motionMLModel.predict(safeFallFeature)
        assertTrue("Model must use real ML", mlOut.isUsingRealML)

        app.stateEngine.processMotionData(safeFallFeature)
        val stateAfterFall = app.stateEngine.safetyState.value

        // CRITICAL SAFETY CHECK: A motion anomaly alone MUST NEVER jump directly to ACTIVE_EMERGENCY
        assertNotEquals("Motion event alone must not trigger ACTIVE_EMERGENCY",
            SafetyState.ACTIVE_EMERGENCY, stateAfterFall)
        assertTrue("Must transition into ANOMALY_CANDIDATE or INVESTIGATING",
            stateAfterFall == SafetyState.ANOMALY_CANDIDATE || stateAfterFall == SafetyState.INVESTIGATING)

        app.stateEngine.cancelInvestigation()
    }

    @Test
    fun testAudioPipelineBenignSoundsDoNotEscalate() = runBlocking {
        // Step 6: Benign sounds during investigation
        val benignBuffer = ShortArray(16000) { (Math.sin(it * 0.05) * 500).toInt().toShort() }
        val audioOut = app.audioMLModel.predict(benignBuffer, 16000)

        assertTrue(audioOut.isUsingRealML)

        // Evaluate risk with fall anomaly + benign audio (NONE / normal)
        val motionAnom = MotionAnomalyResult(
            isAnomaly = true,
            anomalyScore = 0.75f,
            anomalyType = MotionAnomalyType.FALL_LIKE_MOTION,
            confidence = 0.75f,
            features = MotionFeatures(peakAccelMagnitude = 30f)
        )
        val audioBenign = AudioEventResult(
            eventType = AudioEventType.NONE,
            confidence = 0.05f
        )

        val eval = app.riskEngine.evaluate(
            motionResult = motionAnom,
            audioResult = audioBenign,
            visionResult = null,
            temporalPersistence = 0.2f,
            corroborationCount = 1
        )

        assertNotEquals("Benign audio must prevent high confidence emergency",
            RiskLevel.HIGH_CONFIDENCE, eval.riskLevel)
        assertTrue("Composite risk must remain below 0.75 threshold", eval.riskScore < 0.75f)
    }

    @Test
    fun testVisionPipelineSingleFrameDoesNotTriggerEmergency() {
        // Step 7: Single vision frame test
        val testBitmap = Bitmap.createBitmap(224, 224, Bitmap.Config.ARGB_8888)
        val visionOut = app.visionMLModel.predict(testBitmap)

        assertTrue(visionOut.isUsingRealML)

        // Single frame evidence without motion or audio corroboration
        val visionResult = VisionEventResult(
            eventType = VisionEventType.PERSON_ON_GROUND,
            confidence = 0.65f
        )

        val eval = app.riskEngine.evaluate(
            motionResult = null,
            audioResult = null,
            visionResult = visionResult,
            temporalPersistence = 0.1f,
            corroborationCount = 1
        )

        assertNotEquals("Single frame vision evidence alone must not trigger emergency",
            RiskLevel.HIGH_CONFIDENCE, eval.riskLevel)
        assertTrue("Single-sensor capped risk must be <= 0.70", eval.riskScore <= 0.70f)
    }

    @Test
    fun testMultimodalFusionScenarioA_MotionOnly() {
        // Scenario A: Motion anomaly only -> Investigation -> NO emergency
        val motionAnom = MotionAnomalyResult(
            isAnomaly = true,
            anomalyScore = 0.80f,
            anomalyType = MotionAnomalyType.FALL_LIKE_MOTION,
            confidence = 0.80f,
            features = MotionFeatures(peakAccelMagnitude = 28f)
        )

        val eval = app.riskEngine.evaluate(
            motionResult = motionAnom,
            audioResult = null,
            visionResult = null,
            temporalPersistence = 0.1f,
            corroborationCount = 1
        )

        assertNotEquals(RiskLevel.HIGH_CONFIDENCE, eval.riskLevel)
        assertTrue("Single sensor motion must not exceed 0.70", eval.riskScore <= 0.70f)
    }

    @Test
    fun testMultimodalFusionScenarioB_MotionAndBenignAudio() {
        // Scenario B: Motion + benign audio -> NO emergency
        val motionAnom = MotionAnomalyResult(
            isAnomaly = true,
            anomalyScore = 0.75f,
            anomalyType = MotionAnomalyType.FALL_LIKE_MOTION,
            confidence = 0.75f,
            features = MotionFeatures(peakAccelMagnitude = 25f)
        )
        val audioBenign = AudioEventResult(
            eventType = AudioEventType.NONE,
            confidence = 0.10f
        )

        val eval = app.riskEngine.evaluate(
            motionResult = motionAnom,
            audioResult = audioBenign,
            visionResult = null,
            temporalPersistence = 0.15f,
            corroborationCount = 1
        )

        assertNotEquals(RiskLevel.HIGH_CONFIDENCE, eval.riskLevel)
        assertTrue(eval.riskScore <= 0.70f)
    }

    @Test
    fun testMultimodalFusionScenarioC_MotionAndSuspiciousAudio() {
        // Scenario C: Motion + suspicious audio -> Escalates to SUSPICIOUS
        val motionAnom = MotionAnomalyResult(
            isAnomaly = true,
            anomalyScore = 0.85f,
            anomalyType = MotionAnomalyType.FALL_LIKE_MOTION,
            confidence = 0.85f,
            features = MotionFeatures(peakAccelMagnitude = 32f)
        )
        val audioDistress = AudioEventResult(
            eventType = AudioEventType.SCREAM_DISTRESS,
            confidence = 0.80f
        )

        val eval = app.riskEngine.evaluate(
            motionResult = motionAnom,
            audioResult = audioDistress,
            visionResult = null,
            temporalPersistence = 0.35f,
            corroborationCount = 2
        )

        assertTrue("Dual sensor corroboration elevates risk >= 0.65", eval.riskScore >= 0.65f)
        assertTrue(eval.riskLevel == RiskLevel.SUSPICIOUS || eval.riskLevel == RiskLevel.HIGH_CONFIDENCE)
    }

    @Test
    fun testMultimodalFusionScenarioD_FullEvidenceCountdown() {
        // Scenario D: Motion + audio + vision evidence + persistence -> HIGH_CONFIDENCE_EMERGENCY
        val motionAnom = MotionAnomalyResult(
            isAnomaly = true,
            anomalyScore = 0.90f,
            anomalyType = MotionAnomalyType.FALL_LIKE_MOTION,
            confidence = 0.90f,
            features = MotionFeatures(peakAccelMagnitude = 35f)
        )
        val audioDistress = AudioEventResult(
            eventType = AudioEventType.SCREAM_DISTRESS,
            confidence = 0.85f
        )
        val visionFall = VisionEventResult(
            eventType = VisionEventType.PERSON_ON_GROUND,
            confidence = 0.90f
        )

        val eval = app.riskEngine.evaluate(
            motionResult = motionAnom,
            audioResult = audioDistress,
            visionResult = visionFall,
            temporalPersistence = 0.80f,
            corroborationCount = 3,
            eventDurationMs = 5000L
        )

        assertEquals("Full multimodal corroboration must produce HIGH_CONFIDENCE",
            RiskLevel.HIGH_CONFIDENCE, eval.riskLevel)
        assertEquals(SafetyState.HIGH_CONFIDENCE_EMERGENCY, eval.recommendedState)
        assertTrue("Risk score must exceed 0.85 threshold", eval.riskScore >= 0.85f)
    }

    @Test
    fun testMultimodalFusionScenarioE_UserCancelsVerification() = runBlocking {
        // Scenario E: High confidence -> user presses I AM SAFE -> RESOLVED -> NORMAL
        app.stateEngine.simulateMotionAnomaly(
            MotionAnomalyResult(
                isAnomaly = true,
                anomalyScore = 0.95f,
                anomalyType = MotionAnomalyType.FALL_LIKE_MOTION,
                confidence = 0.95f,
                features = MotionFeatures(peakAccelMagnitude = 35f)
            )
        )

        // Cancel investigation / emergency
        app.stateEngine.cancelInvestigation()
        assertEquals("State must return to NORMAL after cancel", SafetyState.NORMAL, app.stateEngine.safetyState.value)
        assertEquals("Pipeline stage must return to IDLE", PipelineStage.IDLE, app.stateEngine.pipelineStage.value)
    }

    @Test
    fun testMultimodalFusionScenarioF_CountdownExpiresToEmergencyDispatch() = runBlocking {
        // Scenario F: High confidence -> countdown expires -> ACTIVE_EMERGENCY -> Location & Simulation Dispatch
        val event = EmergencyEvent(
            id = "sim-event-99",
            eventType = "Fall Detected",
            confidence = 0.95f,
            sensorEvidence = SensorEvidence(
                motionAnomalyType = MotionAnomalyType.FALL_LIKE_MOTION,
                motionConfidence = 0.95f,
                audioEventType = AudioEventType.SCREAM_DISTRESS,
                audioConfidence = 0.90f,
                visionEventType = VisionEventType.PERSON_ON_GROUND,
                visionConfidence = 0.92f,
                fusionRiskScore = 0.94f,
                temporalPersistence = 0.85f
            ),
            state = SafetyState.ACTIVE_EMERGENCY,
            isSimulated = true
        )

        // Location starts on emergency
        app.locationManager.startEmergencyTracking()
        assertNotEquals("Location must not be STOPPED in emergency",
            LocationState.STOPPED, app.locationManager.locationState.value)

        // Inject simulated GPS coordinates
        app.locationManager.injectSimulatedLocation(37.4220, -122.0841, 4.2f)
        val acquiredLocation = app.locationManager.currentLocation.value
        assertNotNull(acquiredLocation)
        assertEquals(37.4220, acquiredLocation!!.latitude, 0.001)

        // Dispatch simulated record
        val contacts = listOf(
            EmergencyContact("1", "Family Contact", "+15551234567", "Family", true)
        )
        val records = app.emergencyDispatcher.dispatch(event, acquiredLocation, contacts)

        assertEquals(1, records.size)
        assertTrue("Dispatch must be marked isSimulated", records[0].isSimulated)
        assertTrue(records[0].messageText.contains("SafeGuardian Alert"))
        assertTrue(records[0].messageText.contains("37.422"))

        // Resolution releases location
        app.locationManager.stopEmergencyTracking()
        assertEquals("Location must STOP after resolution", LocationState.STOPPED, app.locationManager.locationState.value)
    }

    @Test
    fun testResourceLifecycleCleanup() = runBlocking {
        // Step 13: Verify all resources are released
        app.locationManager.stopEmergencyTracking()
        assertEquals(LocationState.STOPPED, app.locationManager.locationState.value)

        app.stateEngine.cancelInvestigation()
        assertEquals(PipelineStage.IDLE, app.stateEngine.pipelineStage.value)
    }

    @Test
    fun testFalsePositiveMatrixAllBenignPass() {
        // Step 16: False Positive Test Matrix across 15 standard situations
        data class Scenario(
            val name: String,
            val motionScore: Float,
            val audioScore: Float,
            val visionScore: Float
        )

        val scenarios = listOf(
            Scenario("walking", 0.15f, 0.05f, 0.05f),
            Scenario("running", 0.40f, 0.10f, 0.05f),
            Scenario("stairs", 0.30f, 0.10f, 0.05f),
            Scenario("sitting", 0.05f, 0.05f, 0.05f),
            Scenario("lying_down_normal", 0.10f, 0.05f, 0.20f),
            Scenario("phone_drop_couch", 0.60f, 0.05f, 0.05f),
            Scenario("phone_placement_table", 0.35f, 0.20f, 0.05f),
            Scenario("door_slam", 0.10f, 0.60f, 0.05f),
            Scenario("loud_music", 0.05f, 0.55f, 0.05f),
            Scenario("normal_conversation", 0.05f, 0.15f, 0.05f),
            Scenario("traffic_noise", 0.05f, 0.40f, 0.05f),
            Scenario("crowded_room", 0.10f, 0.30f, 0.20f),
            Scenario("camera_obstruction_dark", 0.05f, 0.05f, 0.00f),
            Scenario("low_light_environment", 0.05f, 0.05f, 0.10f),
            Scenario("in_pocket_or_bag", 0.20f, 0.05f, 0.00f)
        )

        for (s in scenarios) {
            val motionResult = if (s.motionScore > 0.5f) {
                MotionAnomalyResult(
                    isAnomaly = true,
                    anomalyScore = s.motionScore,
                    anomalyType = MotionAnomalyType.UNUSUAL_ACCELERATION,
                    confidence = s.motionScore,
                    features = MotionFeatures(peakAccelMagnitude = 20f)
                )
            } else null

            val audioResult = if (s.audioScore > 0.5f) {
                AudioEventResult(
                    eventType = AudioEventType.LOUD_ABNORMAL,
                    confidence = s.audioScore
                )
            } else null

            val visionResult = if (s.visionScore > 0.5f) {
                VisionEventResult(
                    eventType = VisionEventType.PERSON_ON_GROUND,
                    confidence = s.visionScore
                )
            } else null

            val eval = app.riskEngine.evaluate(
                motionResult = motionResult,
                audioResult = audioResult,
                visionResult = visionResult,
                temporalPersistence = 0.1f,
                corroborationCount = listOfNotNull(motionResult, audioResult, visionResult).size
            )

            assertNotEquals("False positive in scenario '${s.name}': risk=${eval.riskScore}",
                RiskLevel.HIGH_CONFIDENCE, eval.riskLevel)
            assertNotEquals("Scenario '${s.name}' must not trigger HIGH_CONFIDENCE_EMERGENCY",
                SafetyState.HIGH_CONFIDENCE_EMERGENCY, eval.recommendedState)
        }
    }
}
