package com.safeguardian.app.domain.ai

import com.safeguardian.app.domain.model.*

/**
 * Interface for multimodal fusion engine.
 * Stage 3 of the cascade — combines all sensor evidence into a final risk assessment.
 */
interface FusionEngine {
    fun evaluate(
        motionResult: MotionAnomalyResult?,
        audioResult: AudioEventResult?,
        visionResult: VisionEventResult?,
        temporalPersistence: Float,
        currentState: SafetyState
    ): FusionResult

    val modelName: String
    val isMock: Boolean
}
