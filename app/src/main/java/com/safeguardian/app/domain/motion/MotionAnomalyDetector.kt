package com.safeguardian.app.domain.motion

import com.safeguardian.app.domain.model.GuardianConfig
import com.safeguardian.app.domain.model.MotionAnomalyResult
import com.safeguardian.app.domain.model.MotionAnomalyType
import com.safeguardian.app.domain.model.MotionFeatures
import kotlin.math.max
import kotlin.math.min

/**
 * Lightweight heuristic motion anomaly detector implementing Phase 2 Motion Intelligence.
 *
 * Evaluates temporal windows of motion features without expensive ML inference:
 * - Identifies sudden impact, freefall drop, severe rotation, and post-event immobility.
 * - Filters false positives (walking, running, sitting, placing phone on table, shaking).
 * - Manages the internal motion state machine:
 *     NORMAL -> POSSIBLE_ANOMALY -> INVESTIGATING -> EVENT_CONFIRMED -> COOLDOWN -> NORMAL
 * - Emits structured MotionEvent candidates and computes motionConfidence.
 *
 * NOTE: The motionConfidence is an engineering score for algorithmic prototyping,
 * NOT a medically or scientifically validated probability.
 */
class MotionAnomalyDetector(
    private val config: GuardianConfig = GuardianConfig()
) {

    // Configurable thresholds (in m/s^2, rad/s, and ms)
    var accelAnomalyThreshold: Float = config.impactThresholdG * 6.5f   // ~19.5 m/s^2 (~2.0g)
    var impactThreshold: Float = config.impactThresholdG * 9.80665f      // ~29.4 m/s^2 (~3.0g)
    var rotationThreshold: Float = config.rotationThresholdRadS          // default ~5.0 rad/s
    var immobilityVarianceThreshold: Float = 0.5f                        // variance < 0.5 (m/s^2)^2
    var minEventDurationMs: Long = 300L
    var cooldownDurationMs: Long = 5_000L

    // Internal state machine tracking
    private var internalState = InternalMotionDetectorState.NORMAL
    private var stateEnterTime = System.currentTimeMillis()
    private var cooldownUntilTime = 0L
    private var eventStartTime = 0L

    // History for temporal persistence and sequence checking
    private var consecutiveAnomalousWindows = 0
    private var recentHighImpactDetected = false
    private var recentHighImpactTimestamp = 0L
    private var recentHighRotationDetected = false
    private var recentHighRotationTimestamp = 0L

    // Last confirmed motion event
    private var lastMotionEvent: MotionEvent? = null

    val currentInternalState: InternalMotionDetectorState
        get() = internalState

    val lastConfirmedEvent: MotionEvent?
        get() = lastMotionEvent

    /**
     * Resets detector state (useful for deterministic tests).
     */
    fun reset() {
        internalState = InternalMotionDetectorState.NORMAL
        stateEnterTime = System.currentTimeMillis()
        cooldownUntilTime = 0L
        eventStartTime = 0L
        consecutiveAnomalousWindows = 0
        recentHighImpactDetected = false
        recentHighImpactTimestamp = 0L
        recentHighRotationDetected = false
        recentHighRotationTimestamp = 0L
        lastMotionEvent = null
    }

    /**
     * Analyzes a window of motion features and determines if an anomaly candidate exists.
     */
    fun analyze(features: MotionFeatures): MotionAnalysisOutput {
        val now = features.timestamp

        // Handle active COOLDOWN
        if (internalState == InternalMotionDetectorState.COOLDOWN) {
            if (now < cooldownUntilTime) {
                return MotionAnalysisOutput(
                    result = normalResult(features, MotionAnomalyType.NONE),
                    motionState = MotionState.NORMAL,
                    internalState = InternalMotionDetectorState.COOLDOWN,
                    motionConfidence = 0.05f,
                    motionEvent = null
                )
            } else {
                transitionInternalState(InternalMotionDetectorState.NORMAL, now)
            }
        }

        // 1. False positive check: Is this regular physical activity?
        val isOrdinaryActivity = checkOrdinaryActivity(features)

        // 2. Track transient flags within a 2-second retention window
        if (features.peakAccelMagnitude >= impactThreshold) {
            recentHighImpactDetected = true
            recentHighImpactTimestamp = now
        } else if (now - recentHighImpactTimestamp > 2500L) {
            recentHighImpactDetected = false
        }

        if (features.peakGyroMagnitude >= rotationThreshold) {
            recentHighRotationDetected = true
            recentHighRotationTimestamp = now
        } else if (now - recentHighRotationTimestamp > 2500L) {
            recentHighRotationDetected = false
        }

        // 3. Evaluate heuristic pattern conditions
        val hasSevereImpact = features.peakAccelMagnitude >= impactThreshold
        val hasAccelAnomaly = features.peakAccelMagnitude >= accelAnomalyThreshold
        val hasSevereRotation = features.peakGyroMagnitude >= rotationThreshold
        val hasImmobility = features.immobilityDuration >= 2000L && features.accelVariance < immobilityVarianceThreshold

        // Fall candidate signature:
        // (Pre-impact drop / decel pattern OR severe rotation) + high impact + subsequent immobility
        val isFallCandidate = !isOrdinaryActivity && (
                (features.hasImpactDecelPattern && recentHighImpactDetected && hasImmobility) ||
                        (recentHighRotationDetected && recentHighImpactDetected && hasImmobility) ||
                        (features.movementImpactImmobilityPattern && features.peakAccelMagnitude >= 22f)
                )

        // Sudden Impact signature:
        // Severe impact spike without normal cyclic walking/running activity
        val isSuddenImpact = !isOrdinaryActivity && hasSevereImpact && (features.suddenImpulse > 15f || features.hasImpactDecelPattern)

        // Rotation Anomaly:
        val isRotationAnomaly = !isOrdinaryActivity && hasSevereRotation && features.rotationalChange > 10f

        // Immobility After Event:
        val isImmobilityAfterEvent = hasImmobility && (recentHighImpactDetected || recentHighRotationDetected)

        // Determine specific MotionState and AnomalyType
        val detectedMotionState: MotionState
        val detectedAnomalyType: MotionAnomalyType

        when {
            isFallCandidate -> {
                detectedMotionState = MotionState.FALL_CANDIDATE
                detectedAnomalyType = MotionAnomalyType.FALL_LIKE_MOTION
            }
            isSuddenImpact -> {
                detectedMotionState = MotionState.IMPACT_DETECTED
                detectedAnomalyType = MotionAnomalyType.SUDDEN_IMPACT
            }
            isImmobilityAfterEvent -> {
                detectedMotionState = MotionState.IMMOBILITY_AFTER_EVENT
                detectedAnomalyType = MotionAnomalyType.PROLONGED_IMMOBILITY
            }
            isRotationAnomaly -> {
                detectedMotionState = MotionState.MOTION_ANOMALY
                detectedAnomalyType = MotionAnomalyType.UNUSUAL_ROTATION
            }
            hasAccelAnomaly && !isOrdinaryActivity -> {
                detectedMotionState = MotionState.POSSIBLE_ANOMALY
                detectedAnomalyType = MotionAnomalyType.UNUSUAL_ACCELERATION
            }
            else -> {
                detectedMotionState = MotionState.NORMAL
                detectedAnomalyType = MotionAnomalyType.NONE
            }
        }

        // Calculate motion confidence score (0.0 to 1.0)
        val motionConfidence = calculateMotionConfidence(
            features = features,
            motionState = detectedMotionState,
            hasSevereImpact = hasSevereImpact,
            hasSevereRotation = hasSevereRotation,
            hasImmobility = hasImmobility,
            isOrdinaryActivity = isOrdinaryActivity
        )

        // Drive the internal state machine:
        // NORMAL -> POSSIBLE_ANOMALY -> INVESTIGATING -> EVENT_CONFIRMED -> COOLDOWN -> NORMAL
        val motionEvent = driveStateMachine(
            detectedState = detectedMotionState,
            anomalyType = detectedAnomalyType,
            confidence = motionConfidence,
            features = features,
            currentTime = now
        )

        val isCandidateForPipeline = (internalState == InternalMotionDetectorState.EVENT_CONFIRMED ||
                (internalState == InternalMotionDetectorState.INVESTIGATING && motionConfidence >= config.anomalyScoreThreshold))

        val result = MotionAnomalyResult(
            isAnomaly = isCandidateForPipeline,
            anomalyScore = motionConfidence,
            anomalyType = detectedAnomalyType,
            confidence = motionConfidence,
            features = features,
            timestamp = now
        )

        return MotionAnalysisOutput(
            result = result,
            motionState = detectedMotionState,
            internalState = internalState,
            motionConfidence = motionConfidence,
            motionEvent = motionEvent
        )
    }

    /**
     * Checks if motion reflects ordinary daily activities:
     * - Walking (periodic 1-2Hz, variance 0.5-4.0, peak < 16, mean ~9.8)
     * - Running (high cyclic variance, peak < 25, no post-event immobility)
     * - Placing on table (small impact < 18, low gyro < 2.0, zero pre-drop)
     * - Sitting / Standing (smooth change, peak < 15, low gyro)
     * - Shaking phone (continuous high variance, no impact-to-immobility signature)
     */
    private fun checkOrdinaryActivity(f: MotionFeatures): Boolean {
        // 1. Normal rest or sitting still: variance close to 0, accel ~9.8
        if (f.peakAccelMagnitude < 13f && f.accelVariance < 0.6f && f.peakGyroMagnitude < 1.5f) {
            return true
        }

        // 2. Normal walking: moderate variance, no excessive peaks, low gyro
        val isWalking = f.peakAccelMagnitude in 10.5f..16.5f &&
                f.accelVariance in 0.8f..5.5f &&
                f.peakGyroMagnitude < 3.0f &&
                f.suddenImpulse < 8.0f
        if (isWalking) return true

        // 3. Normal running: rhythmic, higher variance, but peak stays below high impact and no immobility
        val isRunning = f.peakAccelMagnitude in 14f..25f &&
                f.accelVariance in 4.0f..18.0f &&
                f.suddenImpulse < 14.0f &&
                f.immobilityDuration == 0L &&
                !f.hasImpactDecelPattern
        if (isRunning) return true

        // 4. Placing phone on a table: minor impact (<18 m/s^2), low rotation (<2 rad/s)
        val isTablePlacement = f.peakAccelMagnitude in 11f..18f &&
                f.peakGyroMagnitude < 2.5f &&
                !f.hasImpactDecelPattern &&
                !f.suddenRotation
        if (isTablePlacement) return true

        // 5. Sitting down / standing up: smooth transition, peak < 14 m/s^2, gyro < 2.0 rad/s
        val isSitStand = f.peakAccelMagnitude < 14.5f &&
                f.peakGyroMagnitude < 2.0f &&
                f.suddenImpulse < 6.0f
        if (isSitStand) return true

        return false
    }

    /**
     * Calculates motionConfidence engineering score (0.0 to 1.0).
     */
    private fun calculateMotionConfidence(
        features: MotionFeatures,
        motionState: MotionState,
        hasSevereImpact: Boolean,
        hasSevereRotation: Boolean,
        hasImmobility: Boolean,
        isOrdinaryActivity: Boolean
    ): Float {
        if (isOrdinaryActivity && motionState == MotionState.NORMAL) {
            return 0.05f
        }

        var score = 0.0f

        // Acceleration contribution (up to 0.40)
        val accelRatio = (features.peakAccelMagnitude / impactThreshold).coerceIn(0f, 2f)
        score += (accelRatio * 0.20f).coerceAtMost(0.40f)

        // Rotation contribution (up to 0.25)
        val gyroRatio = (features.peakGyroMagnitude / rotationThreshold).coerceIn(0f, 2f)
        score += (gyroRatio * 0.125f).coerceAtMost(0.25f)

        // Impact & pattern characteristics (up to 0.20)
        if (features.hasImpactDecelPattern) score += 0.10f
        if (features.movementImpactImmobilityPattern) score += 0.10f

        // Post-event movement / immobility (up to 0.15)
        if (hasImmobility) {
            val immobilityBonus = (features.immobilityDuration.toFloat() / 5000f).coerceIn(0f, 1f) * 0.15f
            score += immobilityBonus
        }

        // Adjust based on motion classification
        score = when (motionState) {
            MotionState.FALL_CANDIDATE -> max(score, 0.85f)
            MotionState.IMPACT_DETECTED -> max(score, 0.75f)
            MotionState.IMMOBILITY_AFTER_EVENT -> max(score, 0.70f)
            MotionState.MOTION_ANOMALY -> max(score, 0.60f)
            MotionState.POSSIBLE_ANOMALY -> max(score, 0.45f)
            MotionState.NORMAL -> min(score, 0.20f)
        }

        return score.coerceIn(0f, 1f)
    }

    /**
     * Drives internal state machine:
     * NORMAL -> POSSIBLE_ANOMALY -> INVESTIGATING -> EVENT_CONFIRMED -> COOLDOWN -> NORMAL
     */
    private fun driveStateMachine(
        detectedState: MotionState,
        anomalyType: MotionAnomalyType,
        confidence: Float,
        features: MotionFeatures,
        currentTime: Long
    ): MotionEvent? {
        var generatedEvent: MotionEvent? = null

        when (internalState) {
            InternalMotionDetectorState.NORMAL -> {
                if (detectedState != MotionState.NORMAL && confidence >= 0.40f) {
                    transitionInternalState(InternalMotionDetectorState.POSSIBLE_ANOMALY, currentTime)
                    eventStartTime = currentTime
                    consecutiveAnomalousWindows = 1
                }
            }

            InternalMotionDetectorState.POSSIBLE_ANOMALY -> {
                if (detectedState != MotionState.NORMAL) {
                    consecutiveAnomalousWindows++
                    val duration = currentTime - eventStartTime
                    if (duration >= minEventDurationMs || confidence >= 0.75f) {
                        transitionInternalState(InternalMotionDetectorState.INVESTIGATING, currentTime)
                    }
                } else {
                    // Quick recovery: single sample spike did NOT sustain
                    transitionInternalState(InternalMotionDetectorState.NORMAL, currentTime)
                }
            }

            InternalMotionDetectorState.INVESTIGATING -> {
                val duration = currentTime - eventStartTime
                if (detectedState in listOf(MotionState.FALL_CANDIDATE, MotionState.IMPACT_DETECTED, MotionState.IMMOBILITY_AFTER_EVENT) ||
                    confidence >= config.anomalyScoreThreshold
                ) {
                    transitionInternalState(InternalMotionDetectorState.EVENT_CONFIRMED, currentTime)
                    generatedEvent = MotionEvent(
                        timestamp = currentTime,
                        type = anomalyType,
                        motionState = detectedState,
                        motionConfidence = confidence,
                        durationMs = duration,
                        peakAcceleration = features.peakAccelMagnitude,
                        peakRotation = features.peakGyroMagnitude,
                        movementLevel = features.motionIntensity,
                        description = "Confirmed motion event: $detectedState with confidence ${"%.2f".format(confidence)}"
                    )
                    lastMotionEvent = generatedEvent
                } else if (duration > 5_000L) {
                    // Timed out investigating without confirmation -> return to normal
                    transitionInternalState(InternalMotionDetectorState.NORMAL, currentTime)
                }
            }

            InternalMotionDetectorState.EVENT_CONFIRMED -> {
                // Event confirmed -> enter cooldown to protect against repeated spam
                cooldownUntilTime = currentTime + cooldownDurationMs
                transitionInternalState(InternalMotionDetectorState.COOLDOWN, currentTime)
            }

            InternalMotionDetectorState.COOLDOWN -> {
                if (currentTime >= cooldownUntilTime) {
                    transitionInternalState(InternalMotionDetectorState.NORMAL, currentTime)
                }
            }
        }

        return generatedEvent
    }

    private fun transitionInternalState(newState: InternalMotionDetectorState, time: Long) {
        internalState = newState
        stateEnterTime = time
    }

    private fun normalResult(features: MotionFeatures, type: MotionAnomalyType): MotionAnomalyResult {
        return MotionAnomalyResult(
            isAnomaly = false,
            anomalyScore = 0.05f,
            anomalyType = type,
            confidence = 0.90f,
            features = features,
            timestamp = features.timestamp
        )
    }

    data class MotionAnalysisOutput(
        val result: MotionAnomalyResult,
        val motionState: MotionState,
        val internalState: InternalMotionDetectorState,
        val motionConfidence: Float,
        val motionEvent: MotionEvent?
    )
}
