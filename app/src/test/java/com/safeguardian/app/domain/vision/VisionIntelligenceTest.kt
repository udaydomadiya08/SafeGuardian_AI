package com.safeguardian.app.domain.vision

import android.graphics.Bitmap
import com.safeguardian.app.domain.ai.mock.MockFusionEngine
import com.safeguardian.app.domain.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VisionIntelligenceTest {

    private lateinit var preprocessor: FramePreprocessor
    private lateinit var temporalAggregator: VisionTemporalAggregator
    private lateinit var prototypeClassifier: PrototypeVisionClassifier

    @Before
    fun setUp() {
        preprocessor = FramePreprocessor(targetWidth = 224, targetHeight = 224)
        temporalAggregator = VisionTemporalAggregator(
            maxHistoryFrames = 10,
            fallPersistenceFrames = 2,
            strugglePersistenceFrames = 3,
            minConfidenceThreshold = 0.45f
        )
        prototypeClassifier = PrototypeVisionClassifier(
            preprocessor = preprocessor,
            temporalAggregator = temporalAggregator
        )
    }

    private fun createSyntheticBitmap(width: Int = 100, height: Int = 100, color: Int = android.graphics.Color.GRAY): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(color)
        return bitmap
    }

    @Test
    fun testFramePreprocessingResizeAndBuffer() {
        val rgbBytes = ByteArray(320 * 240 * 3) { (it % 255).toByte() }
        val buffer = preprocessor.preprocessRgb(rgbBytes, 320, 240)

        assertNotNull(buffer)
        assertEquals(224 * 224 * 3 * 4, buffer.capacity())
    }

    @Test
    fun testPersonDetectionResultModel() {
        val box = BoundingBox(0.2f, 0.1f, 0.8f, 0.9f, "person", 0.92f)
        val result = PersonDetectionResult(
            detected = true,
            confidence = 0.92f,
            boundingBox = box,
            estimatedOrientation = BodyOrientation.UPRIGHT,
            centerMovement = 0.05f
        )

        assertTrue(result.detected)
        assertEquals(BodyOrientation.UPRIGHT, result.estimatedOrientation)
        assertEquals(0.92f, result.confidence, 0.001f)
    }

    @Test
    fun testTemporalFallCandidateDetection() {
        // Frame 1: Upright person (center Y ~ 0.35, aspect ratio 0.5)
        val frame1 = FrameFeatures(
            hasPerson = true,
            personConfidence = 0.9f,
            primaryPersonBox = BoundingBox(0.3f, 0.1f, 0.7f, 0.6f, "person", 0.9f),
            bodyOrientation = BodyOrientation.UPRIGHT,
            aspectRatio = 0.4f / 0.5f // 0.8
        )
        val res1 = temporalAggregator.addFrame(frame1)
        assertEquals(VisionEventClass.PERSON_DETECTED, res1.eventType)

        // Frame 2: Moving downward (center Y ~ 0.55)
        val frame2 = FrameFeatures(
            hasPerson = true,
            personConfidence = 0.9f,
            primaryPersonBox = BoundingBox(0.25f, 0.3f, 0.75f, 0.8f, "person", 0.9f),
            bodyOrientation = BodyOrientation.LOW_CROUCHED,
            aspectRatio = 1.0f
        )
        val res2 = temporalAggregator.addFrame(frame2)

        // Frame 3: Low and horizontal on ground (center Y ~ 0.82, aspect ratio 2.0)
        val frame3 = FrameFeatures(
            hasPerson = true,
            personConfidence = 0.92f,
            primaryPersonBox = BoundingBox(0.1f, 0.70f, 0.9f, 0.95f, "person", 0.92f),
            bodyOrientation = BodyOrientation.HORIZONTAL,
            aspectRatio = 0.8f / 0.25f // 3.2
        )
        val res3 = temporalAggregator.addFrame(frame3)

        // Frame 4: Persistent horizontal posture confirms fall candidate
        val frame4 = frame3.copy(timestamp = System.currentTimeMillis() + 100)
        val res4 = temporalAggregator.addFrame(frame4)

        assertEquals(VisionEventClass.FALL_CANDIDATE, res4.eventType)
        assertTrue(res4.confidence >= 0.75f)
        assertTrue(res4.isConfirmed)
    }

    @Test
    fun testPossibleStruggleTemporalDetection() {
        // Sequence of frames with high struggleScore and rapid bounding box jitter
        val frame1 = FrameFeatures(
            hasPerson = true,
            primaryPersonBox = BoundingBox(0.2f, 0.2f, 0.5f, 0.8f, "person", 0.8f),
            struggleScore = 0.8f
        )
        val frame2 = FrameFeatures(
            hasPerson = true,
            primaryPersonBox = BoundingBox(0.4f, 0.2f, 0.7f, 0.8f, "person", 0.8f),
            struggleScore = 0.8f
        )
        val frame3 = FrameFeatures(
            hasPerson = true,
            primaryPersonBox = BoundingBox(0.1f, 0.3f, 0.6f, 0.9f, "person", 0.8f),
            struggleScore = 0.8f
        )

        temporalAggregator.addFrame(frame1)
        temporalAggregator.addFrame(frame2)
        val struggleResult = temporalAggregator.addFrame(frame3)

        assertEquals(VisionEventClass.POSSIBLE_STRUGGLE, struggleResult.eventType)
        assertTrue(struggleResult.confidence >= 0.65f)
    }

    @Test
    fun testFireSmokePersistence() {
        val frame1 = FrameFeatures(fireSmokeScore = 0.85f)
        val frame2 = FrameFeatures(fireSmokeScore = 0.88f)

        temporalAggregator.addFrame(frame1)
        val result = temporalAggregator.addFrame(frame2)

        assertEquals(VisionEventClass.FIRE_OR_SMOKE, result.eventType)
        assertTrue(result.confidence > 0.8f)
    }

    @Test
    fun testFalsePositiveSingleFrameRejection() {
        // Single glitch frame should not confirm a fall candidate or emergency state
        val singleGlitch = FrameFeatures(
            hasPerson = true,
            primaryPersonBox = BoundingBox(0.1f, 0.8f, 0.9f, 0.95f, "glitch", 0.9f),
            bodyOrientation = BodyOrientation.HORIZONTAL,
            aspectRatio = 3.0f
        )
        val result = prototypeClassifier.classifyFeatures(singleGlitch)

        // Without prior upright frames, downward trajectory, or second corroborating frame, classified as NORMAL
        assertFalse(result.eventType == VisionEventClass.FALL_CANDIDATE)
        assertFalse(result.eventType == VisionEventClass.PERSON_ON_GROUND)
        assertEquals(VisionEventClass.NORMAL, result.eventType)
    }

    @Test
    fun testMotionPlusAudioPlusVisionMultimodalFusion() {
        val fusionEngine = MockFusionEngine()

        val motionAnomaly = MotionAnomalyResult(
            isAnomaly = true,
            anomalyScore = 0.85f,
            anomalyType = MotionAnomalyType.FALL_LIKE_MOTION,
            confidence = 0.85f,
            features = MotionFeatures(peakAccelMagnitude = 35f)
        )

        val audioImpact = AudioEventResult(
            eventType = AudioEventType.CRASH_IMPACT,
            confidence = 0.85f
        )

        val visionFall = VisionEventResult(
            eventType = VisionEventType.FALL_DETECTED,
            confidence = 0.90f
        )

        // Triple sensor corroboration
        val tripleFusion = fusionEngine.evaluate(
            motionResult = motionAnomaly,
            audioResult = audioImpact,
            visionResult = visionFall,
            temporalPersistence = 0.7f,
            currentState = SafetyState.SUSPICIOUS
        )

        // Verify multi-sensor escalation and high confidence risk score
        assertTrue("Risk score with 3 corroborating sensors should be very high", tripleFusion.overallRiskScore >= 0.85f)
        assertTrue(tripleFusion.evidenceSummary.contains("Motion"))
        assertTrue(tripleFusion.evidenceSummary.contains("Audio"))
        assertTrue(tripleFusion.evidenceSummary.contains("Vision"))
    }

    @Test
    fun testMotionAnomalyWithNormalVisionReducesRisk() {
        val fusionEngine = MockFusionEngine()

        val motionAnomaly = MotionAnomalyResult(
            isAnomaly = true,
            anomalyScore = 0.70f,
            anomalyType = MotionAnomalyType.UNUSUAL_ACCELERATION,
            confidence = 0.70f,
            features = MotionFeatures(peakAccelMagnitude = 20f)
        )

        val normalAudio = AudioEventResult(
            eventType = AudioEventType.NONE,
            confidence = 0.05f
        )

        val normalVision = VisionEventResult(
            eventType = VisionEventType.NONE,
            confidence = 0.05f
        )

        val result = fusionEngine.evaluate(
            motionResult = motionAnomaly,
            audioResult = normalAudio,
            visionResult = normalVision,
            temporalPersistence = 0.1f,
            currentState = SafetyState.INVESTIGATING
        )

        // Without audio or visual corroboration, risk score remains below suspicious threshold
        assertFalse("Risk score should not escalate without corroborating evidence", result.overallRiskScore >= 0.75f)
    }
}
