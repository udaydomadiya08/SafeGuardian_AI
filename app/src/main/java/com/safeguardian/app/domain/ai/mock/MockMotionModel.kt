package com.safeguardian.app.domain.ai.mock

import com.safeguardian.app.domain.ai.MotionModel
import com.safeguardian.app.domain.model.*

/**
 * Mock motion model for development and testing.
 * Uses simple threshold-based heuristics on motion features.
 * CLEARLY LABELED: This is NOT a trained ML model.
 */
class MockMotionModel : MotionModel {

    override val modelName = "MockMotionModel v0.1 (heuristic)"
    override val isMock = true

    override fun analyze(features: MotionFeatures): MotionAnomalyResult {
        // Simple threshold-based anomaly detection heuristics
        val impactScore = calculateImpactScore(features)
        val fallScore = calculateFallScore(features)
        val rotationScore = calculateRotationScore(features)
        val immobilityScore = calculateImmobilityScore(features)

        // Determine the dominant anomaly type
        val scores = mapOf(
            MotionAnomalyType.SUDDEN_IMPACT to impactScore,
            MotionAnomalyType.FALL_LIKE_MOTION to fallScore,
            MotionAnomalyType.UNUSUAL_ROTATION to rotationScore,
            MotionAnomalyType.PROLONGED_IMMOBILITY to immobilityScore
        )

        val maxEntry = scores.maxByOrNull { it.value } ?: return normalResult(features)
        val bestScore = maxEntry.value
        val bestType = maxEntry.key

        if (bestScore < 0.4f) return normalResult(features)

        return MotionAnomalyResult(
            isAnomaly = bestScore >= 0.6f,
            anomalyScore = bestScore.coerceIn(0f, 1f),
            anomalyType = if (bestScore >= 0.4f) bestType else MotionAnomalyType.NONE,
            confidence = (bestScore * 0.7f).coerceIn(0f, 1f), // mock models have reduced confidence
            features = features
        )
    }

    private fun calculateImpactScore(f: MotionFeatures): Float {
        // High peak acceleration indicates sudden impact
        return when {
            f.peakAccelMagnitude >= 35f -> 0.95f
            f.peakAccelMagnitude >= 25f -> 0.8f
            f.peakAccelMagnitude >= 15f -> 0.6f
            f.peakAccelMagnitude >= 10f -> 0.3f
            else -> 0.05f
        }
    }

    private fun calculateFallScore(f: MotionFeatures): Float {
        // Free-fall: near-zero acceleration followed by impact
        val freefallIndicator = if (f.meanAccelMagnitude < 3f) 0.4f else 0f
        val impactFollowup = if (f.peakAccelMagnitude > 15f) 0.4f else 0f
        val varianceBonus = if (f.accelVariance > 50f) 0.2f else 0f
        return (freefallIndicator + impactFollowup + varianceBonus).coerceIn(0f, 1f)
    }

    private fun calculateRotationScore(f: MotionFeatures): Float {
        return when {
            f.peakGyroMagnitude > 10f -> 0.85f
            f.peakGyroMagnitude > 6f -> 0.6f
            f.peakGyroMagnitude > 3f -> 0.3f
            else -> 0.05f
        }
    }

    private fun calculateImmobilityScore(f: MotionFeatures): Float {
        // Low variance + low magnitude over time
        return when {
            f.immobilityDuration > 30_000 && f.accelVariance < 0.5f -> 0.9f
            f.immobilityDuration > 15_000 && f.accelVariance < 1f -> 0.7f
            f.immobilityDuration > 10_000 && f.accelVariance < 2f -> 0.5f
            else -> 0.05f
        }
    }

    private fun normalResult(features: MotionFeatures) = MotionAnomalyResult(
        isAnomaly = false,
        anomalyScore = 0.05f,
        anomalyType = MotionAnomalyType.NONE,
        confidence = 0.9f,
        features = features
    )
}
