package com.safeguardian.app.domain.ai

import com.safeguardian.app.domain.model.MotionAnomalyResult
import com.safeguardian.app.domain.model.MotionFeatures

/**
 * Interface for the motion anomaly detection model.
 * Stage 0 of the cascade — must be extremely lightweight.
 *
 * Implementations:
 * - MockMotionModel: deterministic test/demo model
 * - (Future) TFLiteMotionModel: real quantized on-device model
 */
interface MotionModel {
    /** Analyze motion features and return anomaly detection result. */
    fun analyze(features: MotionFeatures): MotionAnomalyResult

    /** Model name for diagnostics display. */
    val modelName: String

    /** Whether this is a mock/placeholder model. */
    val isMock: Boolean
}
