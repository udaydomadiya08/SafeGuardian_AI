package com.safeguardian.app.domain.ai.mock

import com.safeguardian.app.domain.ai.FusionEngine
import com.safeguardian.app.domain.model.*

/**
 * Mock fusion engine for development and testing.
 * Uses weighted averaging of available sensor scores.
 * CLEARLY LABELED: This is NOT a trained ML model.
 */
class MockFusionEngine : FusionEngine {

    override val modelName = "MockFusionEngine v0.1 (weighted average)"
    override val isMock = true

    override fun evaluate(
        motionResult: MotionAnomalyResult?,
        audioResult: AudioEventResult?,
        visionResult: VisionEventResult?,
        temporalPersistence: Float,
        currentState: SafetyState
    ): FusionResult {
        // Weight each sensor's contribution
        val motionScore = motionResult?.anomalyScore ?: 0f
        val motionWeight = if (motionResult != null && motionResult.isAnomaly) 0.35f else 0.15f

        val audioScore = if (audioResult != null && audioResult.eventType != AudioEventType.NONE) {
            audioResult.confidence
        } else 0f
        val audioWeight = if (audioScore > 0f) 0.3f else 0.1f

        val visionScore = if (visionResult != null && visionResult.eventType != VisionEventType.NONE) {
            visionResult.confidence
        } else 0f
        val visionWeight = if (visionScore > 0f) 0.25f else 0.05f

        val temporalWeight = 0.1f

        // Weighted risk score
        val totalWeight = motionWeight + audioWeight + visionWeight + temporalWeight
        val weightedScore = (
            motionScore * motionWeight +
            audioScore * audioWeight +
            visionScore * visionWeight +
            temporalPersistence * temporalWeight
        ) / totalWeight

        // Count corroborating sensors
        val corroboratingSensors = listOf(
            motionScore > 0.5f,
            audioScore > 0.3f,
            visionScore > 0.3f
        ).count { it }

        // Multi-sensor bonus
        val multiSensorBonus = when (corroboratingSensors) {
            3 -> 0.15f
            2 -> 0.08f
            else -> 0f
        }

        val finalScore = (weightedScore + multiSensorBonus).coerceIn(0f, 1f)

        // Determine recommended state
        val recommendedState = when {
            finalScore >= 0.9f && corroboratingSensors >= 2 -> SafetyState.HIGH_CONFIDENCE_EMERGENCY
            finalScore >= 0.75f -> SafetyState.SUSPICIOUS
            finalScore >= 0.5f -> SafetyState.INVESTIGATING
            finalScore >= 0.3f -> SafetyState.ANOMALY_CANDIDATE
            else -> SafetyState.NORMAL
        }

        // Build evidence summary
        val evidence = buildString {
            motionResult?.let {
                if (it.isAnomaly) append("Motion: ${it.anomalyType.name} (${(it.anomalyScore * 100).toInt()}%). ")
            }
            audioResult?.let {
                if (it.eventType != AudioEventType.NONE) append("Audio: ${it.eventType.name} (${(it.confidence * 100).toInt()}%). ")
            }
            visionResult?.let {
                if (it.eventType != VisionEventType.NONE) append("Vision: ${it.eventType.name} (${(it.confidence * 100).toInt()}%). ")
            }
            if (temporalPersistence > 0.3f) append("Temporal persistence: ${(temporalPersistence * 100).toInt()}%. ")
            if (isEmpty()) append("No significant events detected.")
        }

        return FusionResult(
            overallRiskScore = finalScore,
            recommendedState = recommendedState,
            motionContribution = motionScore * motionWeight / totalWeight,
            audioContribution = audioScore * audioWeight / totalWeight,
            visionContribution = visionScore * visionWeight / totalWeight,
            temporalPersistence = temporalPersistence,
            confidence = (finalScore * 0.7f).coerceIn(0f, 1f), // mock has reduced confidence
            evidenceSummary = evidence
        )
    }
}
