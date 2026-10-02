package com.safeguardian.app.domain.vision

import com.safeguardian.app.domain.model.BoundingBox
import com.safeguardian.app.domain.model.VisionEventType

/**
 * Centralized configuration for on-device computer vision investigation.
 * Prioritizes battery conservation, fast inference, and strict privacy.
 */
data class VisionConfig(
    val targetWidth: Int = 224,                // Efficient mobile input resolution
    val targetHeight: Int = 224,
    val targetFps: Int = 5,                    // Low frame-rate analysis (5-10 FPS during investigation)
    val maxInvestigationDurationMs: Long = 8_000L, // Camera auto-shuts down after 8s
    val fallCandidatePersistenceFrames: Int = 2,
    val strugglePersistenceFrames: Int = 3,
    val minConfidenceThreshold: Float = 0.45f
) {
    val frameIntervalMs: Long
        get() = (1000L / targetFps.coerceAtLeast(1))
}

/**
 * High-level engineering event classes for on-device computer vision.
 *
 * NOTE: These are engineering event categories for multimodal pipeline prototyping,
 * NOT forensic, legal, or definitive human intention determinations.
 */
enum class VisionEventClass {
    NORMAL,
    PERSON_DETECTED,
    PERSON_ON_GROUND,
    FALL_CANDIDATE,
    POSSIBLE_STRUGGLE,
    FIRE_OR_SMOKE,
    DANGEROUS_EVENT;

    fun toVisionEventType(): VisionEventType = when (this) {
        NORMAL -> VisionEventType.NONE
        PERSON_DETECTED -> VisionEventType.NONE
        PERSON_ON_GROUND -> VisionEventType.PERSON_ON_GROUND
        FALL_CANDIDATE -> VisionEventType.FALL_DETECTED
        POSSIBLE_STRUGGLE -> VisionEventType.PHYSICAL_STRUGGLE
        FIRE_OR_SMOKE -> VisionEventType.FIRE_SMOKE
        DANGEROUS_EVENT -> VisionEventType.DANGEROUS_ENVIRONMENT
    }
}

/**
 * Structured vision event output for multimodal safety state engine integration.
 */
data class VisionClassificationResult(
    val eventType: VisionEventClass,
    val confidence: Float,               // 0.0 to 1.0 engineering confidence score
    val timestamp: Long = System.currentTimeMillis(),
    val boundingBoxes: List<BoundingBox> = emptyList(),
    val processingTimeMs: Long = 0L,
    val isConfirmed: Boolean = false,
    val description: String = ""
)
