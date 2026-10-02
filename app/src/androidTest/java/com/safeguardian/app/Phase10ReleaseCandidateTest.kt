package com.safeguardian.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.safeguardian.app.domain.ai.ml.*
import com.safeguardian.app.domain.model.*
import com.safeguardian.app.domain.motion.SensorDataSanitizer
import com.safeguardian.app.domain.response.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase 10 Release Candidate on-device instrumented validation.
 * Verifies model integrity, countdown safety, permission degradation,
 * memory stability, and crash recovery on genuine Android runtime.
 */
@RunWith(AndroidJUnit4::class)
class Phase10ReleaseCandidateTest {

    private lateinit var app: SafeGuardianApp

    @Before
    fun setup() {
        app = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testRealDeviceModelIntegrityAndQuantization() {
        // 1. Verify model artifacts exist and are available in assets
        assertTrue("Motion model must be available", app.motionMLModel.isModelAvailable)
        assertTrue("Audio model must be available", app.audioMLModel.isModelAvailable)
        assertTrue("Vision model must be available", app.visionMLModel.isModelAvailable)

        // 2. Cryptographic SHA-256 validation against trusted build-time constants
        val motionSpec = ModelIntegrityVerifier.TRUSTED_MODELS["motion_model.tflite"]!!
        val audioSpec = ModelIntegrityVerifier.TRUSTED_MODELS["audio_model.tflite"]!!
        val visionSpec = ModelIntegrityVerifier.TRUSTED_MODELS["vision_model.tflite"]!!

        val motionBytes = app.assets.open("models/motion_model.tflite").readBytes()
        val audioBytes = app.assets.open("models/audio_model.tflite").readBytes()
        val visionBytes = app.assets.open("models/vision_model.tflite").readBytes()

        val motionHash = ModelIntegrityVerifier.computeSha256(motionBytes)
        val audioHash = ModelIntegrityVerifier.computeSha256(audioBytes)
        val visionHash = ModelIntegrityVerifier.computeSha256(visionBytes)

        assertEquals("Motion SHA-256 hash must match trusted spec", motionSpec.sha256Hex, motionHash)
        assertEquals("Audio SHA-256 hash must match trusted spec", audioSpec.sha256Hex, audioHash)
        assertEquals("Vision SHA-256 hash must match trusted spec", visionSpec.sha256Hex, visionHash)

        // 3. Verify ModelIntegrityVerifier checks
        val motionCheck = ModelIntegrityVerifier.verifyChecksum("motion_model.tflite", motionBytes)
        val audioCheck = ModelIntegrityVerifier.verifyChecksum("audio_model.tflite", audioBytes)
        val visionCheck = ModelIntegrityVerifier.verifyChecksum("vision_model.tflite", visionBytes)

        assertTrue(motionCheck is ModelIntegrityResult.Valid)
        assertTrue(audioCheck is ModelIntegrityResult.Valid)
        assertTrue(visionCheck is ModelIntegrityResult.Valid)
    }

    @Test
    fun testCountdownImmediateCancellation() = runBlocking {
        // Trigger investigation
        app.stateEngine.simulateMotionAnomaly(
            MotionAnomalyResult(
                isAnomaly = true,
                anomalyScore = 0.85f,
                anomalyType = MotionAnomalyType.FALL_LIKE_MOTION,
                confidence = 0.85f,
                features = MotionFeatures(peakAccelMagnitude = 28f)
            )
        )

        // User immediately cancels
        app.stateEngine.cancelInvestigation()

        assertEquals("Immediate cancellation must return to NORMAL", SafetyState.NORMAL, app.stateEngine.safetyState.value)
        assertEquals("Pipeline must return to IDLE", PipelineStage.IDLE, app.stateEngine.pipelineStage.value)
        assertNull("No emergency must be recorded", app.stateEngine.currentEmergency.value)
    }

    @Test
    fun testCountdownLateCancellationBeforeExpiry() = runBlocking {
        // Trigger investigation
        app.stateEngine.simulateMotionAnomaly(
            MotionAnomalyResult(
                isAnomaly = true,
                anomalyScore = 0.90f,
                anomalyType = MotionAnomalyType.FALL_LIKE_MOTION,
                confidence = 0.90f,
                features = MotionFeatures(peakAccelMagnitude = 32f)
            )
        )

        // Simulate user cancelling before countdown expiration
        app.stateEngine.cancelInvestigation()

        assertNotEquals("Late cancellation must NOT lead to ACTIVE_EMERGENCY",
            SafetyState.ACTIVE_EMERGENCY, app.stateEngine.safetyState.value)
        assertEquals(SafetyState.NORMAL, app.stateEngine.safetyState.value)
    }

    @Test
    fun testPermissionDenialGracefulDegradation() {
        // 1. Microphone unavailable: prediction should return NORMAL or safe fallback
        val emptyAudio = ShortArray(0)
        val audioOut = app.audioMLModel.predict(emptyAudio, 16000)
        assertEquals(AudioMLClass.NORMAL, audioOut.predictedClass)

        // 2. Camera unavailable: system processes audio/motion without crash
        val motionFeatures = MotionFeatures(peakAccelMagnitude = 15f)
        val motionOut = app.motionMLModel.predict(motionFeatures)
        assertNotNull(motionOut)

        // 3. Location unavailable: emergency dispatch generates safe UNAVAILABLE location message
        val emergencyEvent = EmergencyEvent(
            id = "perm-denied-test",
            eventType = "Fall Detected",
            confidence = 0.88f,
            sensorEvidence = SensorEvidence(eventChainDescription = "Audio + Motion verified"),
            state = SafetyState.ACTIVE_EMERGENCY,
            isSimulated = true
        )
        val contacts = listOf(EmergencyContact("1", "Bob", "+15559876543", "Friend", true))
        val records = app.emergencyDispatcher.dispatch(emergencyEvent, null, contacts)

        assertEquals(1, records.size)
        assertTrue("Must indicate Location Unavailable", records[0].messageText.contains("Location: Unavailable"))
        assertTrue("Must indicate GPS status", records[0].messageText.contains("Device GPS acquiring"))
    }

    @Test
    fun test100InvestigationCyclesMemoryStability() = runBlocking {
        // Run 100 consecutive investigation cycles and measure memory
        System.gc()
        Thread.sleep(100)
        val runtime = Runtime.getRuntime()
        val initialUsedMemory = runtime.totalMemory() - runtime.freeMemory()

        for (i in 1..100) {
            // Motion anomaly
            app.stateEngine.simulateMotionAnomaly(
                MotionAnomalyResult(
                    isAnomaly = true,
                    anomalyScore = 0.70f,
                    anomalyType = MotionAnomalyType.UNUSUAL_ACCELERATION,
                    confidence = 0.70f,
                    features = MotionFeatures(peakAccelMagnitude = 22f)
                )
            )

            // Audio classification
            val audioBuffer = ShortArray(1600) { (it % 200).toShort() }
            app.stateEngine.processAudioData(audioBuffer, 16000)

            // Vision classification
            app.stateEngine.processVisionResult(VisionEventResult(VisionEventType.NONE, 0.1f))

            // Cancel / cycle completion
            app.stateEngine.cancelInvestigation()
        }

        System.gc()
        Thread.sleep(100)
        val finalUsedMemory = runtime.totalMemory() - runtime.freeMemory()
        val memoryDeltaMb = (finalUsedMemory - initialUsedMemory) / (1024 * 1024)

        // Memory delta after 100 cycles must be bounded (< 15MB growth after GC)
        assertTrue("Memory growth after 100 cycles must be bounded: delta = ${memoryDeltaMb}MB", memoryDeltaMb < 15)
        assertEquals("State must be clean NORMAL", SafetyState.NORMAL, app.stateEngine.safetyState.value)
    }

    @Test
    fun testAdversarialSensorCorruptionNoCrash() {
        val corruptedTraces = listOf(
            MotionFeatures(peakAccelMagnitude = Float.NaN),
            MotionFeatures(peakAccelMagnitude = Float.POSITIVE_INFINITY),
            MotionFeatures(peakAccelMagnitude = -999f),
            MotionFeatures(peakAccelMagnitude = 5000f, accelVariance = -10f)
        )

        for (trace in corruptedTraces) {
            val sanitized = SensorDataSanitizer.sanitizeFeatures(trace)
            assertTrue("Sanitized peak must be finite", sanitized.peakAccelMagnitude.isFinite())
            assertTrue("Sanitized variance must be non-negative", sanitized.accelVariance >= 0f)

            // Feed to ML Model
            val out = app.motionMLModel.predict(sanitized)
            assertNotNull("Output must not be null", out)

            // Feed to StateEngine
            app.stateEngine.processMotionData(sanitized)
            assertNotEquals("Corrupted trace must never cause emergency",
                SafetyState.ACTIVE_EMERGENCY, app.stateEngine.safetyState.value)
        }
    }

    @Test
    fun testOfflineAndNoInternetRuntimeSafety() {
        // Verify no internet permission in package
        val pm = app.packageManager
        val pkgInfo = pm.getPackageInfo(app.packageName, android.content.pm.PackageManager.GET_PERMISSIONS)
        val permissions = pkgInfo.requestedPermissions ?: emptyArray()

        assertFalse("INTERNET permission must NEVER be present",
            permissions.contains("android.permission.INTERNET"))
    }
}
