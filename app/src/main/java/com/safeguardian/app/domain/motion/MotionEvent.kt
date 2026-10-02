package com.safeguardian.app.domain.motion

import com.safeguardian.app.domain.model.MotionAnomalyType

/**
 * Structured motion event emitted by the low-power motion intelligence module.
 *
 * NOTE: The motion module does NOT trigger an emergency response directly.
 * It produces structured events as candidates for subsequent multi-stage verification.
 * The motionConfidence field is an engineering heuristic score requiring validation.
 */
data class MotionEvent(
    val timestamp: Long = System.currentTimeMillis(),
    val type: MotionAnomalyType,
    val motionState: MotionState,
    val motionConfidence: Float, // 0.0 to 1.0 (Engineering score requiring validation)
    val durationMs: Long,
    val peakAcceleration: Float, // m/s^2
    val peakRotation: Float,     // rad/s
    val movementLevel: Float,    // 0.0 (immobile) to 1.0+ (vigorous)
    val description: String = ""
)
