package com.safeguardian.app.domain.model

/**
 * Represents the hierarchical safety states in the detection pipeline.
 * Transitions follow a strict escalation state machine with temporal
 * and multi-sensor confirmation requirements.
 */
enum class SafetyState {
    /** No anomaly detected. Low-power monitoring only. */
    NORMAL,

    /** Motion anomaly detected. Awaiting confirmation. */
    ANOMALY_CANDIDATE,

    /** Audio/vision analysis in progress after anomaly trigger. */
    INVESTIGATING,

    /** Multiple signals suggest potential danger. Elevated monitoring. */
    SUSPICIOUS,

    /** Strong multi-sensor corroboration. Preparing emergency response. */
    HIGH_CONFIDENCE_EMERGENCY,

    /** Emergency response active: location tracking, contact notification. */
    ACTIVE_EMERGENCY,

    /** Emergency resolved by user or timeout. Returning to normal. */
    RESOLVED;

    fun canTransitionTo(next: SafetyState): Boolean = when (this) {
        NORMAL -> next == ANOMALY_CANDIDATE
        ANOMALY_CANDIDATE -> next in setOf(INVESTIGATING, NORMAL)
        INVESTIGATING -> next in setOf(SUSPICIOUS, ANOMALY_CANDIDATE, NORMAL)
        SUSPICIOUS -> next in setOf(HIGH_CONFIDENCE_EMERGENCY, INVESTIGATING, NORMAL)
        HIGH_CONFIDENCE_EMERGENCY -> next in setOf(ACTIVE_EMERGENCY, SUSPICIOUS, NORMAL)
        ACTIVE_EMERGENCY -> next in setOf(RESOLVED, NORMAL)
        RESOLVED -> next == NORMAL
    }
}

/**
 * The current AI pipeline stage, determining which models are active.
 */
enum class PipelineStage(val displayName: String, val powerLevel: Int) {
    IDLE("Ultra Low Power", 0),
    MOTION_ANALYSIS("Motion Analysis", 1),
    AUDIO_ANALYSIS("Audio Analysis", 2),
    VISION_ANALYSIS("Vision Analysis", 3),
    MULTIMODAL_FUSION("Multimodal Fusion", 4),
    EMERGENCY_RESPONSE("Emergency Response", 5);
}

/**
 * Individual sensor operational status.
 */
enum class SensorStatus {
    ACTIVE, STANDBY, OFF, UNAVAILABLE, ERROR
}
