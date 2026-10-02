package com.safeguardian.app.domain.model

/**
 * Represents an emergency event with all relevant context.
 */
data class EmergencyEvent(
    val id: String = java.util.UUID.randomUUID().toString(),
    val eventType: String,
    val confidence: Float,
    val timestamp: Long = System.currentTimeMillis(),
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationAccuracy: Float? = null,
    val sensorEvidence: SensorEvidence,
    val state: SafetyState = SafetyState.HIGH_CONFIDENCE_EMERGENCY,
    val resolvedAt: Long? = null,
    val isSimulated: Boolean = false
)

/**
 * Summary of sensor evidence supporting an emergency detection.
 * Does NOT contain raw sensor data — only metadata/scores.
 */
data class SensorEvidence(
    val motionAnomalyType: MotionAnomalyType = MotionAnomalyType.NONE,
    val motionConfidence: Float = 0f,
    val audioEventType: AudioEventType = AudioEventType.NONE,
    val audioConfidence: Float = 0f,
    val visionEventType: VisionEventType = VisionEventType.NONE,
    val visionConfidence: Float = 0f,
    val fusionRiskScore: Float = 0f,
    val temporalPersistence: Float = 0f,
    val eventChainDescription: String = ""
)

/**
 * Emergency contact configured by the user.
 */
data class EmergencyContact(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val phone: String,
    val relationship: String = "",
    val isEnabled: Boolean = true
)
