package com.safeguardian.app.domain.ai

import android.graphics.Bitmap
import com.safeguardian.app.domain.model.VisionEventResult
import com.safeguardian.app.domain.vision.VisionClassificationResult
import com.safeguardian.app.domain.vision.VisionEventClass

/**
 * Interface for the vision event classification model.
 * Stage 2 of the cascade — activated only after audio corroboration.
 */
interface VisionEventModel {
    /** Classify a camera frame for dangerous events. */
    fun classify(bitmap: Bitmap): VisionEventResult

    val modelName: String
    val isMock: Boolean

    fun reset() {}

    fun classifyDetailed(bitmap: Bitmap): VisionClassificationResult {
        val result = classify(bitmap)
        return VisionClassificationResult(
            eventType = when (result.eventType) {
                com.safeguardian.app.domain.model.VisionEventType.PERSON_ON_GROUND -> VisionEventClass.PERSON_ON_GROUND
                com.safeguardian.app.domain.model.VisionEventType.FALL_DETECTED -> VisionEventClass.FALL_CANDIDATE
                com.safeguardian.app.domain.model.VisionEventType.PHYSICAL_STRUGGLE -> VisionEventClass.POSSIBLE_STRUGGLE
                com.safeguardian.app.domain.model.VisionEventType.FIRE_SMOKE -> VisionEventClass.FIRE_OR_SMOKE
                else -> VisionEventClass.NORMAL
            },
            confidence = result.confidence,
            processingTimeMs = 15L
        )
    }
}
