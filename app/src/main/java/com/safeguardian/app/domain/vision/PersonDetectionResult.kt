package com.safeguardian.app.domain.vision

import com.safeguardian.app.domain.model.BoundingBox

/**
 * Person detection result representation.
 * Designed to be drop-in compatible with future mobile detectors
 * (e.g., MobileNet-SSD, YOLO-Nano, EfficientDet-Lite).
 */
data class PersonDetectionResult(
    val detected: Boolean,
    val confidence: Float,
    val boundingBox: BoundingBox?,
    val estimatedOrientation: BodyOrientation = BodyOrientation.UNKNOWN,
    val centerMovement: Float = 0f,
    val timestamp: Long = System.currentTimeMillis()
)

enum class BodyOrientation {
    UPRIGHT,
    HORIZONTAL,
    LOW_CROUCHED,
    UNKNOWN
}

/**
 * Extracted frame features used for heuristic analysis and temporal reasoning.
 */
data class FrameFeatures(
    val hasPerson: Boolean = false,
    val personConfidence: Float = 0f,
    val primaryPersonBox: BoundingBox? = null,
    val bodyOrientation: BodyOrientation = BodyOrientation.UNKNOWN,
    val centerMovement: Float = 0f,
    val aspectRatio: Float = 1.0f,          // width / height
    val fireSmokeScore: Float = 0f,
    val struggleScore: Float = 0f,
    val timestamp: Long = System.currentTimeMillis(),
    val preprocessingTimeMs: Long = 0L
)
