package com.safeguardian.app.domain.ai.mock

import android.graphics.Bitmap
import com.safeguardian.app.domain.ai.VisionEventModel
import com.safeguardian.app.domain.model.VisionEventResult
import com.safeguardian.app.domain.model.VisionEventType

/**
 * Mock vision event model for development and testing.
 * Always returns NONE since we have no real visual classifier yet.
 * CLEARLY LABELED: This is NOT a trained ML model.
 */
class MockVisionEventModel : VisionEventModel {

    override val modelName = "MockVisionEventModel v0.1 (placeholder)"
    override val isMock = true

    override fun classify(bitmap: Bitmap): VisionEventResult {
        // Placeholder: no actual classification
        // Real implementation will use a TFLite model
        return VisionEventResult(
            eventType = VisionEventType.NONE,
            confidence = 0.0f
        )
    }
}
