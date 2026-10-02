package com.safeguardian.app.domain.response

import com.safeguardian.app.domain.model.*

enum class RiskLevel {
    NORMAL,
    SUSPICIOUS,
    HIGH_CONFIDENCE
}

/**
 * Output of the MultimodalRiskEngine.
 *
 * NOTE: riskScore is an algorithmic engineering metric (0.0 to 1.0)
 * reflecting sensor corroboration and persistence, NOT a statistically validated probability.
 */
data class MultimodalRiskResult(
    val riskScore: Float,
    val riskLevel: RiskLevel,
    val contributingSignals: List<String>,
    val timestamp: Long = System.currentTimeMillis(),
    val eventDurationMs: Long = 0L,
    val corroborationCount: Int = 0,
    val persistenceScore: Float = 0f,
    val recommendedState: SafetyState = SafetyState.NORMAL,
    val summary: String = ""
)

/**
 * Multimodal risk engine evaluating cross-sensor corroboration, weights, and persistence.
 *
 * Rules:
 * - Never escalates to high confidence on one weak sensor signal alone.
 * - Single-sensor triggers request investigation only.
 * - Weighted corroboration between motion + audio + vision + persistence.
 * - Multi-sensor corroboration bonuses.
 */
class MultimodalRiskEngine(
    val motionWeight: Float = 0.35f,
    val audioWeight: Float = 0.30f,
    val visionWeight: Float = 0.25f,
    val temporalWeight: Float = 0.10f,
    val suspiciousThreshold: Float = 0.65f,
    val highConfidenceThreshold: Float = 0.85f,
    val minPersistenceForEmergency: Float = 0.40f
) : com.safeguardian.app.domain.ai.FusionEngine {

    override val modelName: String = "MultimodalRiskEngine v1.0 (Weighted Multi-Sensor Corroboration)"
    override val isMock: Boolean = false

    override fun evaluate(
        motionResult: MotionAnomalyResult?,
        audioResult: AudioEventResult?,
        visionResult: VisionEventResult?,
        temporalPersistence: Float,
        currentState: SafetyState
    ): FusionResult {
        val corroboratingSensors = listOfNotNull(
            if (motionResult != null && motionResult.isAnomaly && motionResult.anomalyScore > 0.5f) true else null,
            if (audioResult != null && audioResult.eventType != AudioEventType.NONE && audioResult.confidence > 0.3f) true else null,
            if (visionResult != null && visionResult.eventType != VisionEventType.NONE && visionResult.confidence > 0.3f) true else null
        ).size

        val risk = evaluate(
            motionResult = motionResult,
            audioResult = audioResult,
            visionResult = visionResult,
            temporalPersistence = temporalPersistence,
            corroborationCount = corroboratingSensors
        )

        return FusionResult(
            overallRiskScore = risk.riskScore,
            recommendedState = risk.recommendedState,
            motionContribution = if (motionResult != null && motionResult.isAnomaly) motionResult.anomalyScore else 0f,
            audioContribution = if (audioResult != null && audioResult.eventType != AudioEventType.NONE) audioResult.confidence else 0f,
            visionContribution = if (visionResult != null && visionResult.eventType != VisionEventType.NONE) visionResult.confidence else 0f,
            temporalPersistence = temporalPersistence,
            confidence = risk.riskScore,
            evidenceSummary = risk.summary
        )
    }

    fun evaluate(
        motionResult: MotionAnomalyResult?,
        audioResult: AudioEventResult?,
        visionResult: VisionEventResult?,
        temporalPersistence: Float,
        corroborationCount: Int,
        eventDurationMs: Long = 0L
    ): MultimodalRiskResult {
        val contributing = mutableListOf<String>()

        // 1. Motion Signal Contribution
        val motionScore = if (motionResult != null && motionResult.isAnomaly) {
            contributing.add("Motion: ${motionResult.anomalyType.name} (${(motionResult.anomalyScore * 100).toInt()}%)")
            motionResult.anomalyScore
        } else 0f
        val effectiveMotionWeight = if (motionScore > 0f) motionWeight else 0.10f

        // 2. Audio Signal Contribution
        val audioScore = if (audioResult != null && audioResult.eventType != AudioEventType.NONE) {
            contributing.add("Audio: ${audioResult.eventType.name} (${(audioResult.confidence * 100).toInt()}%)")
            audioResult.confidence
        } else 0f
        val effectiveAudioWeight = if (audioScore > 0f) audioWeight else 0.05f

        // 3. Vision Signal Contribution
        val visionScore = if (visionResult != null && visionResult.eventType != VisionEventType.NONE) {
            contributing.add("Vision: ${visionResult.eventType.name} (${(visionResult.confidence * 100).toInt()}%)")
            visionResult.confidence
        } else 0f
        val effectiveVisionWeight = if (visionScore > 0f) visionWeight else 0.05f

        val totalWeight = effectiveMotionWeight + effectiveAudioWeight + effectiveVisionWeight + temporalWeight

        val baseWeightedScore = (
            motionScore * effectiveMotionWeight +
            audioScore * effectiveAudioWeight +
            visionScore * effectiveVisionWeight +
            temporalPersistence * temporalWeight
        ) / totalWeight

        // 4. Corroboration Count & Bonuses
        // Count active non-zero signals
        val activeSensors = listOf(motionScore > 0.5f, audioScore > 0.3f, visionScore > 0.3f).count { it }
        val corroborationBonus = when (activeSensors) {
            3 -> 0.18f  // Triple sensor corroboration (e.g. Impact + Distress/Crash audio + Person on ground)
            2 -> 0.08f  // Dual sensor corroboration
            else -> 0.0f
        }

        // 5. High-confidence persistence penalty / bonus
        val persistenceBonus = if (temporalPersistence >= minPersistenceForEmergency) 0.05f else -0.10f

        // Prevent single weak signals from escalating above suspicious threshold
        var finalRisk = (baseWeightedScore + corroborationBonus + persistenceBonus).coerceIn(0f, 1.0f)

        // Strict Single-Sensor Capping: An event with only 1 corroborating sensor CANNOT exceed 0.70 risk
        if (activeSensors <= 1) {
            finalRisk = finalRisk.coerceAtMost(0.70f)
        }

        val riskLevel = when {
            finalRisk >= highConfidenceThreshold && activeSensors >= 2 -> RiskLevel.HIGH_CONFIDENCE
            finalRisk >= suspiciousThreshold -> RiskLevel.SUSPICIOUS
            else -> RiskLevel.NORMAL
        }

        val recommendedState = when (riskLevel) {
            RiskLevel.HIGH_CONFIDENCE -> SafetyState.HIGH_CONFIDENCE_EMERGENCY
            RiskLevel.SUSPICIOUS -> SafetyState.SUSPICIOUS
            RiskLevel.NORMAL -> if (finalRisk > 0.35f) SafetyState.ANOMALY_CANDIDATE else SafetyState.NORMAL
        }

        val summary = if (contributing.isEmpty()) "No anomalous sensor signals detected." else contributing.joinToString("; ")

        return MultimodalRiskResult(
            riskScore = finalRisk,
            riskLevel = riskLevel,
            contributingSignals = contributing,
            eventDurationMs = eventDurationMs,
            corroborationCount = activeSensors,
            persistenceScore = temporalPersistence,
            recommendedState = recommendedState,
            summary = summary
        )
    }
}
