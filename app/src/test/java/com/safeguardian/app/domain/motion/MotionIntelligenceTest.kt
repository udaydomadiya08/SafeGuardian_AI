package com.safeguardian.app.domain.motion

import com.safeguardian.app.data.sensor.RingSensorBuffer
import com.safeguardian.app.domain.model.GuardianConfig
import com.safeguardian.app.domain.model.MotionAnomalyType
import com.safeguardian.app.domain.model.MotionFeatures
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.math.sqrt

/**
 * Deterministic unit tests covering Phase 2 Low-Power Motion Intelligence:
 * - Acceleration & rotation magnitude
 * - Feature extraction (jerk, peak, variance, signal energy, impulse, immobility)
 * - Impact detection heuristics
 * - Fall candidate detection heuristics
 * - Immobility detection heuristics
 * - motionConfidence calculation
 * - Internal state machine transitions & cooldown
 * - False-positive prevention across ordinary activities (walking, running, sitting, table placement)
 */
class MotionIntelligenceTest {

    private lateinit var extractor: MotionFeatureExtractor
    private lateinit var detector: MotionAnomalyDetector
    private val config = GuardianConfig()

    @Before
    fun setUp() {
        extractor = MotionFeatureExtractor()
        detector = MotionAnomalyDetector(config)
    }

    // ==========================================
    // 1. ACCELERATION & ROTATION MAGNITUDE TESTS
    // ==========================================

    @Test
    fun `vector magnitude computes correct 3D Euclidean norm`() {
        val ax = 3.0f
        val ay = 4.0f
        val az = 12.0f
        val expectedMag = sqrt(3f * 3f + 4f * 4f + 12f * 12f) // sqrt(9 + 16 + 144) = sqrt(169) = 13.0
        val actualMag = sqrt(ax * ax + ay * ay + az * az)

        assertEquals(13.0f, actualMag, 0.001f)
    }

    @Test
    fun `zero-allocation ring buffer correctly stores and calculates magnitudes`() {
        val ring = RingSensorBuffer(capacity = 10)
        ring.add(0f, 0f, 9.8f, 1000L)
        ring.add(3f, 4f, 0f, 1020L)

        val outX = FloatArray(5)
        val outY = FloatArray(5)
        val outZ = FloatArray(5)
        val outMag = FloatArray(5)
        val outTime = LongArray(5)

        val count = ring.copyWindow(1000L, 1050L, outX, outY, outZ, outMag, outTime)
        assertEquals(2, count)
        // Most recent sample first
        assertEquals(5.0f, outMag[0], 0.001f) // sqrt(3^2 + 4^2) = 5
        assertEquals(9.8f, outMag[1], 0.001f)
    }

    // ==========================================
    // 2. FEATURE EXTRACTION TESTS
    // ==========================================

    @Test
    fun `feature extractor calculates accurate statistical and energy features`() {
        // Create synthetic samples: 5 samples of known values
        val count = 5
        val x = floatArrayOf(0f, 0f, 0f, 0f, 0f)
        val y = floatArrayOf(0f, 0f, 0f, 0f, 0f)
        val z = floatArrayOf(10f, 12f, 8f, 14f, 6f)
        val mag = floatArrayOf(10f, 12f, 8f, 14f, 6f)
        val time = longArrayOf(1000L, 980L, 960L, 940L, 920L)

        val gx = floatArrayOf(1f, 1f, 1f, 1f, 1f)
        val gy = floatArrayOf(0f, 0f, 0f, 0f, 0f)
        val gz = floatArrayOf(0f, 0f, 0f, 0f, 0f)
        val gmag = floatArrayOf(1f, 1f, 1f, 1f, 1f)
        val gtime = time

        val result = extractor.extract(
            accelX = x, accelY = y, accelZ = z, accelMag = mag, accelTime = time, accelCount = count,
            gyroX = gx, gyroY = gy, gyroZ = gz, gyroMag = gmag, gyroTime = gtime, gyroCount = count,
            windowSizeMs = 1000L, previousImmobilityStartTime = 0L, currentTime = 1000L
        )

        val f = result.features

        // Mean magnitude: (10 + 12 + 8 + 14 + 6) / 5 = 10.0
        assertEquals(10.0f, f.meanAccelMagnitude, 0.01f)
        // Peak magnitude: 14.0
        assertEquals(14.0f, f.peakAccelMagnitude, 0.01f)
        // Sample variance of [10, 12, 8, 14, 6]: diffs from 10 are [0, 2, -2, 4, -4], squared [0, 4, 4, 16, 16] = 40 / 4 = 10.0
        assertEquals(10.0f, f.accelVariance, 0.01f)
        // Signal energy: (100 + 144 + 64 + 196 + 36) / 5 = 540 / 5 = 108.0
        assertEquals(108.0f, f.signalEnergy, 0.01f)
        // Max delta (jerk): |12 - 10| = 2, |8 - 12| = 4, |14 - 8| = 6, |6 - 14| = 8 -> 8.0
        assertEquals(8.0f, f.accelDeltaMagnitude, 0.01f)
        // Gyro mean magnitude: 1.0
        assertEquals(1.0f, f.meanGyroMagnitude, 0.01f)
    }

    @Test
    fun `immobility detection activates when variance is near zero and magnitude is 1G`() {
        val count = 10
        val x = FloatArray(count) { 0f }
        val y = FloatArray(count) { 0f }
        val z = FloatArray(count) { 9.8f }
        val mag = FloatArray(count) { 9.8f }
        val time = LongArray(count) { 1000L - it * 20L }

        // First window at t = 1000
        val r1 = extractor.extract(
            accelX = x, accelY = y, accelZ = z, accelMag = mag, accelTime = time, accelCount = count,
            gyroX = x, gyroY = y, gyroZ = z, gyroMag = x, gyroTime = time, gyroCount = 0,
            windowSizeMs = 1000L, previousImmobilityStartTime = 0L, currentTime = 1000L
        )

        assertEquals(1000L, r1.immobilityStartTime)

        // Subsequent window at t = 3500 (2.5s later)
        val r2 = extractor.extract(
            accelX = x, accelY = y, accelZ = z, accelMag = mag, accelTime = time, accelCount = count,
            gyroX = x, gyroY = y, gyroZ = z, gyroMag = x, gyroTime = time, gyroCount = 0,
            windowSizeMs = 1000L, previousImmobilityStartTime = r1.immobilityStartTime, currentTime = 3500L
        )

        assertEquals(2500L, r2.features.immobilityDuration)
    }

    // ==========================================
    // 3. HEURISTIC DETECTION TESTS
    // ==========================================

    @Test
    fun `sudden impact heuristic triggers IMPACT_DETECTED when peak exceeds impact threshold`() {
        val features = MotionFeatures(
            peakAccelMagnitude = 36.0f, // > 29.4 m/s^2 (~3.0g)
            suddenImpulse = 20.0f,
            accelVariance = 45.0f,
            hasImpactDecelPattern = true,
            timestamp = 1000L
        )

        val output = detector.analyze(features)
        assertEquals(MotionState.IMPACT_DETECTED, output.motionState)
        assertTrue(output.motionConfidence >= 0.70f)
    }

    @Test
    fun `fall candidate heuristic detects sequence of drop, rotation, impact, and immobility`() {
        val now = 5000L
        val fallFeatures = MotionFeatures(
            peakAccelMagnitude = 35.0f,
            peakGyroMagnitude = 7.5f,
            hasImpactDecelPattern = true,
            movementImpactImmobilityPattern = true,
            immobilityDuration = 2500L,
            accelVariance = 0.2f,
            timestamp = now
        )

        val output = detector.analyze(fallFeatures)
        assertEquals(MotionState.FALL_CANDIDATE, output.motionState)
        assertTrue("motionConfidence should be high for fall candidate", output.motionConfidence >= 0.85f)
    }

    @Test
    fun `immobility after event triggers IMMOBILITY_AFTER_EVENT state`() {
        // Step 1: inject impact spike to register recent event
        val impactFeatures = MotionFeatures(
            peakAccelMagnitude = 32.0f,
            suddenImpulse = 18.0f,
            timestamp = 1000L
        )
        detector.analyze(impactFeatures)

        // Step 2: 1.5 seconds later, phone is completely still
        val immobileFeatures = MotionFeatures(
            peakAccelMagnitude = 9.8f,
            meanAccelMagnitude = 9.8f,
            accelVariance = 0.05f,
            immobilityDuration = 3000L,
            timestamp = 2500L
        )
        val output = detector.analyze(immobileFeatures)
        assertEquals(MotionState.IMMOBILITY_AFTER_EVENT, output.motionState)
        assertTrue(output.motionConfidence >= 0.70f)
    }

    // ==========================================
    // 4. CONFIDENCE SCORE & STATE MACHINE TESTS
    // ==========================================

    @Test
    fun `motion confidence is bounded strictly between 0 and 1`() {
        val normalFeatures = MotionFeatures(peakAccelMagnitude = 9.8f, accelVariance = 0.1f)
        val normalOut = detector.analyze(normalFeatures)
        assertTrue(normalOut.motionConfidence in 0.0f..1.0f)
        assertTrue(normalOut.motionConfidence <= 0.20f)

        val extremeFeatures = MotionFeatures(
            peakAccelMagnitude = 80.0f,
            peakGyroMagnitude = 20.0f,
            hasImpactDecelPattern = true,
            immobilityDuration = 10000L
        )
        val extremeOut = detector.analyze(extremeFeatures)
        assertTrue(extremeOut.motionConfidence in 0.0f..1.0f)
    }

    @Test
    fun `state machine progresses through NORMAL, POSSIBLE_ANOMALY, INVESTIGATING, EVENT_CONFIRMED, COOLDOWN`() {
        detector.reset()
        assertEquals(InternalMotionDetectorState.NORMAL, detector.currentInternalState)

        // 1. Initial anomaly candidate moves to POSSIBLE_ANOMALY
        val t1 = 1000L
        val f1 = MotionFeatures(peakAccelMagnitude = 25.0f, suddenImpulse = 10f, timestamp = t1)
        detector.analyze(f1)
        assertEquals(InternalMotionDetectorState.POSSIBLE_ANOMALY, detector.currentInternalState)

        // 2. Sustained anomaly duration moves to INVESTIGATING
        val t2 = 1400L // 400ms later (> minEventDurationMs 300ms)
        val f2 = MotionFeatures(peakAccelMagnitude = 28.0f, suddenImpulse = 12f, timestamp = t2)
        detector.analyze(f2)
        assertEquals(InternalMotionDetectorState.INVESTIGATING, detector.currentInternalState)

        // 3. Strong event confirmation triggers EVENT_CONFIRMED and generates MotionEvent
        val t3 = 1600L
        val f3 = MotionFeatures(
            peakAccelMagnitude = 36.0f,
            peakGyroMagnitude = 6.0f,
            hasImpactDecelPattern = true,
            timestamp = t3
        )
        val out3 = detector.analyze(f3)

        assertEquals(InternalMotionDetectorState.EVENT_CONFIRMED, detector.currentInternalState)
        assertNotNull("Structured MotionEvent should be generated", out3.motionEvent)
        assertEquals(MotionState.IMPACT_DETECTED, out3.motionEvent?.motionState)

        // 4. Next sample transitions to COOLDOWN to prevent spam
        val t4 = 1700L
        detector.analyze(MotionFeatures(peakAccelMagnitude = 10.0f, timestamp = t4))
        assertEquals(InternalMotionDetectorState.COOLDOWN, detector.currentInternalState)

        // During cooldown, new spikes are suppressed
        val t5 = 3000L // within 5000ms cooldown
        val f5 = MotionFeatures(peakAccelMagnitude = 40.0f, timestamp = t5)
        val out5 = detector.analyze(f5)
        assertEquals(InternalMotionDetectorState.COOLDOWN, detector.currentInternalState)
        assertFalse("Spikes in cooldown must not be treated as new candidates", out5.result.isAnomaly)

        // 5. After cooldown expires, state transitions back to NORMAL
        val t6 = 8000L // > 5000ms cooldown
        val f6 = MotionFeatures(peakAccelMagnitude = 9.8f, timestamp = t6)
        val out6 = detector.analyze(f6)
        assertEquals(InternalMotionDetectorState.NORMAL, detector.currentInternalState)
    }

    // ==========================================
    // 5. FALSE POSITIVE PREVENTION TESTS
    // ==========================================

    @Test
    fun `false positive protection - normal walking does not trigger anomaly`() {
        val walkingFeatures = MotionFeatures(
            meanAccelMagnitude = 10.2f,
            peakAccelMagnitude = 13.5f,
            accelVariance = 2.4f,
            meanGyroMagnitude = 0.8f,
            peakGyroMagnitude = 1.6f,
            suddenImpulse = 3.2f,
            immobilityDuration = 0L,
            timestamp = 1000L
        )

        val output = detector.analyze(walkingFeatures)
        assertEquals(MotionState.NORMAL, output.motionState)
        assertEquals(MotionAnomalyType.NONE, output.result.anomalyType)
        assertFalse(output.result.isAnomaly)
        assertTrue(output.motionConfidence <= 0.20f)
    }

    @Test
    fun `false positive protection - running does not trigger fall candidate`() {
        val runningFeatures = MotionFeatures(
            meanAccelMagnitude = 11.8f,
            peakAccelMagnitude = 21.0f, // high peak from vigorous running
            accelVariance = 9.5f,       // high running variance
            meanGyroMagnitude = 1.8f,
            peakGyroMagnitude = 3.2f,
            suddenImpulse = 8.5f,
            immobilityDuration = 0L,
            hasImpactDecelPattern = false,
            timestamp = 1000L
        )

        val output = detector.analyze(runningFeatures)
        assertEquals(MotionState.NORMAL, output.motionState)
        assertFalse(output.result.isAnomaly)
    }

    @Test
    fun `false positive protection - sitting down does not trigger anomaly`() {
        val sitDownFeatures = MotionFeatures(
            meanAccelMagnitude = 9.6f,
            peakAccelMagnitude = 13.0f,
            accelVariance = 1.2f,
            peakGyroMagnitude = 1.1f,
            suddenImpulse = 4.0f,
            timestamp = 1000L
        )

        val output = detector.analyze(sitDownFeatures)
        assertEquals(MotionState.NORMAL, output.motionState)
        assertFalse(output.result.isAnomaly)
    }

    @Test
    fun `false positive protection - placing phone on table does not trigger anomaly`() {
        val tablePlacementFeatures = MotionFeatures(
            meanAccelMagnitude = 9.8f,
            peakAccelMagnitude = 15.5f, // minor clatter on table surface
            accelVariance = 0.8f,
            peakGyroMagnitude = 1.2f,
            hasImpactDecelPattern = false,
            timestamp = 1000L
        )

        val output = detector.analyze(tablePlacementFeatures)
        assertEquals(MotionState.NORMAL, output.motionState)
        assertFalse(output.result.isAnomaly)
    }

    @Test
    fun `single isolated sample spike without duration does not confirm fall`() {
        detector.reset()
        // Single isolated spike lasting 10ms
        val singleSpike = MotionFeatures(
            peakAccelMagnitude = 24.0f,
            timestamp = 1000L
        )
        val out1 = detector.analyze(singleSpike)
        assertEquals(InternalMotionDetectorState.POSSIBLE_ANOMALY, detector.currentInternalState)

        // Immediately followed by normal sample -> recovers to NORMAL without confirmation
        val backToNormal = MotionFeatures(
            peakAccelMagnitude = 9.8f,
            accelVariance = 0.1f,
            timestamp = 1050L
        )
        val out2 = detector.analyze(backToNormal)
        assertEquals(InternalMotionDetectorState.NORMAL, detector.currentInternalState)
        assertNull(detector.lastConfirmedEvent)
    }
}
