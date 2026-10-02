package com.safeguardian.app.domain.vision

import com.safeguardian.app.domain.model.BoundingBox
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Temporal visual reasoning across multiple consecutive frames.
 *
 * Implements:
 * - Fall candidate temporal reasoning (upright posture -> downward center trajectory -> horizontal/low posture)
 * - Possible struggle temporal reasoning (rapid chaotic bounding box jitter / posture oscillation)
 * - Fire / smoke temporal persistence
 * - Rejection of single-frame false alarms
 */
class VisionTemporalAggregator(
    val maxHistoryFrames: Int = 10,
    val fallPersistenceFrames: Int = 2,
    val strugglePersistenceFrames: Int = 3,
    val minConfidenceThreshold: Float = 0.45f
) {
    private val frameHistory = mutableListOf<FrameFeatures>()

    @Synchronized
    fun addFrame(features: FrameFeatures): VisionClassificationResult {
        frameHistory.add(features)
        if (frameHistory.size > maxHistoryFrames) {
            frameHistory.removeAt(0)
        }

        // 1. Evaluate Fall Candidate Sequence
        val fallResult = evaluateFallSequence()
        if (fallResult != null) {
            return fallResult
        }

        // 2. Evaluate Possible Struggle Sequence
        val struggleResult = evaluateStruggleSequence()
        if (struggleResult != null) {
            return struggleResult
        }

        // 3. Evaluate Fire / Smoke Persistence
        val fireSmokeResult = evaluateFireSmokeSequence()
        if (fireSmokeResult != null) {
            return fireSmokeResult
        }

        // 4. Evaluate Person on Ground
        val groundResult = evaluatePersonOnGround()
        if (groundResult != null) {
            return groundResult
        }

        // 5. Normal or Person Detected
        if (features.hasPerson && features.personConfidence >= minConfidenceThreshold) {
            return VisionClassificationResult(
                eventType = VisionEventClass.PERSON_DETECTED,
                confidence = features.personConfidence,
                timestamp = features.timestamp,
                boundingBoxes = features.primaryPersonBox?.let { listOf(it) } ?: emptyList(),
                isConfirmed = true,
                description = "Person present in view"
            )
        }

        return VisionClassificationResult(
            eventType = VisionEventClass.NORMAL,
            confidence = 0.90f,
            timestamp = features.timestamp,
            isConfirmed = true,
            description = "Normal scene"
        )
    }

    /**
     * Fall sequence reasoning:
     * Requires:
     * 1. Earlier frame with UPRIGHT person (or aspect ratio height > width)
     * 2. Downward center trajectory (center Y increasing significantly)
     * 3. Current frame with HORIZONTAL posture (aspect ratio width >= height or bottom box near ground)
     */
    private fun evaluateFallSequence(): VisionClassificationResult? {
        if (frameHistory.size < 3) return null

        val current = frameHistory.last()
        val earlierFrames = frameHistory.dropLast(1)

        val hadUpright = earlierFrames.any { it.bodyOrientation == BodyOrientation.UPRIGHT || it.aspectRatio < 0.75f }
        val isNowHorizontalOrLow = current.bodyOrientation == BodyOrientation.HORIZONTAL ||
                current.bodyOrientation == BodyOrientation.LOW_CROUCHED ||
                current.aspectRatio >= 1.0f

        val boxCurrent = current.primaryPersonBox
        val boxFirst = earlierFrames.first().primaryPersonBox

        val hasDownwardTrajectory = if (boxCurrent != null && boxFirst != null) {
            val centerCurrentY = (boxCurrent.top + boxCurrent.bottom) / 2f
            val centerFirstY = (boxFirst.top + boxFirst.bottom) / 2f
            centerCurrentY - centerFirstY > 0.15f // moved lower in the frame
        } else false

        if (hadUpright && isNowHorizontalOrLow && hasDownwardTrajectory) {
            // Check persistence of low/horizontal frames
            val lowFrameCount = frameHistory.takeLast(fallPersistenceFrames).count {
                it.bodyOrientation == BodyOrientation.HORIZONTAL || it.aspectRatio >= 1.0f
            }

            if (lowFrameCount >= fallPersistenceFrames) {
                val confidence = (0.75f + (lowFrameCount.toFloat() / maxHistoryFrames) * 0.15f).coerceIn(0f, 0.95f)
                return VisionClassificationResult(
                    eventType = VisionEventClass.FALL_CANDIDATE,
                    confidence = confidence,
                    timestamp = current.timestamp,
                    boundingBoxes = current.primaryPersonBox?.let { listOf(it) } ?: emptyList(),
                    isConfirmed = true,
                    description = "Visual fall candidate detected: downward trajectory confirmed"
                )
            }
        }

        return null
    }

    /**
     * Struggle sequence reasoning:
     * Tracks high spatial variance and rapid bounding box oscillation across frames.
     */
    private fun evaluateStruggleSequence(): VisionClassificationResult? {
        if (frameHistory.size < strugglePersistenceFrames) return null

        val recentStruggleFrames = frameHistory.takeLast(strugglePersistenceFrames)
        val highStruggleCount = recentStruggleFrames.count { it.struggleScore > 0.5f }

        // Also evaluate center-of-mass jitter
        var totalJitter = 0f
        for (i in 1 until recentStruggleFrames.size) {
            val b1 = recentStruggleFrames[i - 1].primaryPersonBox
            val b2 = recentStruggleFrames[i].primaryPersonBox
            if (b1 != null && b2 != null) {
                val dx = ((b2.left + b2.right) / 2f) - ((b1.left + b1.right) / 2f)
                val dy = ((b2.top + b2.bottom) / 2f) - ((b1.top + b1.bottom) / 2f)
                totalJitter += hypot(dx, dy)
            }
        }

        if (highStruggleCount >= strugglePersistenceFrames || totalJitter > 0.6f) {
            val current = frameHistory.last()
            return VisionClassificationResult(
                eventType = VisionEventClass.POSSIBLE_STRUGGLE,
                confidence = 0.70f,
                timestamp = current.timestamp,
                boundingBoxes = current.primaryPersonBox?.let { listOf(it) } ?: emptyList(),
                isConfirmed = true,
                description = "Possible rapid physical struggle movement detected"
            )
        }

        return null
    }

    /**
     * Fire / smoke sequence reasoning:
     * Requires persistent elevated fire/smoke score across consecutive frames.
     */
    private fun evaluateFireSmokeSequence(): VisionClassificationResult? {
        if (frameHistory.size < 2) return null

        val recentFrames = frameHistory.takeLast(2)
        val persistentFire = recentFrames.all { it.fireSmokeScore > 0.65f }

        if (persistentFire) {
            val current = frameHistory.last()
            return VisionClassificationResult(
                eventType = VisionEventClass.FIRE_OR_SMOKE,
                confidence = recentFrames.map { it.fireSmokeScore }.average().toFloat(),
                timestamp = current.timestamp,
                isConfirmed = true,
                description = "Visual fire or dense smoke signature detected"
            )
        }

        return null
    }

    /**
     * Person on ground without active falling motion (e.g. stationary horizontal body).
     * Requires persistence across at least 2 frames to reject single-frame transient glitches.
     */
    private fun evaluatePersonOnGround(): VisionClassificationResult? {
        if (frameHistory.size < 2) return null

        val recentFrames = frameHistory.takeLast(2)
        val allHorizontal = recentFrames.all {
            it.hasPerson &&
            (it.bodyOrientation == BodyOrientation.HORIZONTAL || it.aspectRatio > 1.2f) &&
            it.personConfidence >= minConfidenceThreshold
        }

        if (allHorizontal) {
            val current = frameHistory.last()
            return VisionClassificationResult(
                eventType = VisionEventClass.PERSON_ON_GROUND,
                confidence = current.personConfidence,
                timestamp = current.timestamp,
                boundingBoxes = current.primaryPersonBox?.let { listOf(it) } ?: emptyList(),
                isConfirmed = true,
                description = "Person on ground detected across consecutive frames"
            )
        }

        return null
    }

    @Synchronized
    fun reset() {
        frameHistory.clear()
    }
}
