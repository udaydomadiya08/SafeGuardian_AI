package com.safeguardian.app.domain.model

/**
 * Battery diagnostics snapshot for the developer dashboard.
 */
data class BatteryDiagnostics(
    val currentStage: PipelineStage = PipelineStage.IDLE,
    val accelSamplingRateHz: Float = 5f,
    val gyroSamplingRateHz: Float = 5f,
    val motionInferenceCount: Long = 0,
    val audioInferenceCount: Long = 0,
    val visionInferenceCount: Long = 0,
    val fusionInferenceCount: Long = 0,
    val lastMotionInferenceMs: Long = 0,
    val lastAudioInferenceMs: Long = 0,
    val lastVisionInferenceMs: Long = 0,
    val totalProcessingTimeMs: Long = 0,
    val uptimeMs: Long = 0,
    val estimatedBatteryImpact: BatteryImpact = BatteryImpact.MINIMAL
)

enum class BatteryImpact(val displayName: String) {
    MINIMAL("< 1% / hour"),
    LOW("1-3% / hour"),
    MODERATE("3-5% / hour"),
    HIGH("5-10% / hour"),
    VERY_HIGH("> 10% / hour")
}

/**
 * Guardian system configuration with tunable thresholds.
 */
data class GuardianConfig(
    // Motion detection thresholds
    val impactThresholdG: Float = 3.0f,
    val fallThresholdG: Float = 0.3f,
    val rotationThresholdRadS: Float = 5.0f,
    val immobilityTimeoutMs: Long = 10_000L,
    val motionWindowSizeMs: Long = 2_000L,

    // Anomaly detection
    val anomalyScoreThreshold: Float = 0.6f,
    val suspiciousScoreThreshold: Float = 0.75f,
    val emergencyScoreThreshold: Float = 0.9f,

    // Temporal confirmation
    val minConfirmationTimeMs: Long = 5_000L,
    val maxInvestigationTimeMs: Long = 30_000L,
    val cooldownPeriodMs: Long = 60_000L,
    val duplicateSuppressionWindowMs: Long = 5_000L,

    // Sampling rates (Hz)
    val idleSamplingRateHz: Float = 5f,
    val activeSamplingRateHz: Float = 50f,

    // Audio
    val audioWindowMs: Long = 2_000L,
    val audioActivationDurationMs: Long = 15_000L,

    // Vision
    val visionActivationDurationMs: Long = 10_000L,

    // Multi-sensor confirmation
    val requiredCorroboratingSensors: Int = 2,

    // Emergency
    val locationUpdateIntervalMs: Long = 10_000L,
    val emergencyAutoResolveMs: Long = 300_000L  // 5 minutes
)
