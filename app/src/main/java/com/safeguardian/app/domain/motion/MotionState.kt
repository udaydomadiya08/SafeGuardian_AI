package com.safeguardian.app.domain.motion

/**
 * Engineering event categories for low-power motion intelligence prototype.
 *
 * NOTE: These are engineering categories for algorithmic prototyping and feature extraction,
 * NOT medically or scientifically validated classifications.
 */
enum class MotionState {
    NORMAL,
    POSSIBLE_ANOMALY,
    IMPACT_DETECTED,
    FALL_CANDIDATE,
    IMMOBILITY_AFTER_EVENT,
    MOTION_ANOMALY
}

/**
 * Motion internal state machine states used for temporal verification and false-positive prevention.
 *
 * Flow:
 * NORMAL -> POSSIBLE_ANOMALY -> INVESTIGATING -> EVENT_CONFIRMED -> COOLDOWN -> NORMAL
 */
enum class InternalMotionDetectorState {
    NORMAL,
    POSSIBLE_ANOMALY,
    INVESTIGATING,
    EVENT_CONFIRMED,
    COOLDOWN
}
