package com.safeguardian.app.domain.model

/**
 * Raw 3-axis sensor reading with timestamp.
 */
data class SensorReading(
    val x: Float,
    val y: Float,
    val z: Float,
    val timestamp: Long = System.currentTimeMillis(),
    val accuracy: Int = 0
) {
    /** Magnitude of the 3D vector */
    val magnitude: Float
        get() = kotlin.math.sqrt(x * x + y * y + z * z)
}

/**
 * Aggregated motion features computed from a window of sensor readings.
 */
data class MotionFeatures(
    // Accelerometer core features
    val currentAccelX: Float = 0f,
    val currentAccelY: Float = 0f,
    val currentAccelZ: Float = 9.80665f,
    val currentAccelMagnitude: Float = 9.80665f,
    val meanAccelMagnitude: Float = 0f,
    val peakAccelMagnitude: Float = 0f,
    val accelVariance: Float = 0f,
    val accelDeltaMagnitude: Float = 0f,   // change in acceleration (jerk)
    val signalEnergy: Float = 0f,          // mean squared magnitude in window
    val suddenImpulse: Float = 0f,         // peak delta or impulse intensity
    val motionIntensity: Float = 0f,       // overall motion level (stddev / variance)

    // Gyroscope core features
    val currentGyroX: Float = 0f,
    val currentGyroY: Float = 0f,
    val currentGyroZ: Float = 0f,
    val currentGyroMagnitude: Float = 0f,
    val meanGyroMagnitude: Float = 0f,
    val peakGyroMagnitude: Float = 0f,
    val gyroVariance: Float = 0f,
    val rotationalChange: Float = 0f,      // delta angular velocity
    val rotationalEnergy: Float = 0f,      // mean squared angular velocity

    // Derived features
    val gravityNormalizedAccel: Float = 0f, // |magnitude - 9.80665|
    val orientationChange: Float = 0f,      // integrated rotation
    val hasImpactDecelPattern: Boolean = false, // sudden acceleration followed by deceleration
    val suddenRotation: Boolean = false,    // sharp rotation spike
    val movementImpactImmobilityPattern: Boolean = false, // movement -> impact -> immobility sequence

    // Temporal context
    val immobilityDuration: Long = 0L,
    val timestamp: Long = System.currentTimeMillis(),
    val windowSizeMs: Long = 0L
)

/**
 * Result from the motion anomaly detector.
 */
data class MotionAnomalyResult(
    val isAnomaly: Boolean,
    val anomalyScore: Float,  // 0.0 to 1.0
    val anomalyType: MotionAnomalyType,
    val confidence: Float,    // 0.0 to 1.0
    val features: MotionFeatures,
    val timestamp: Long = System.currentTimeMillis()
)

enum class MotionAnomalyType {
    NONE,
    SUDDEN_IMPACT,
    FALL_LIKE_MOTION,
    UNUSUAL_ACCELERATION,
    UNUSUAL_ROTATION,
    ORIENTATION_CHANGE,
    PROLONGED_IMMOBILITY,
    ABNORMAL_PATTERN
}

/**
 * Result from the audio event classifier.
 */
data class AudioEventResult(
    val eventType: AudioEventType,
    val confidence: Float,  // 0.0 to 1.0
    val timestamp: Long = System.currentTimeMillis(),
    val durationMs: Long = 0L
)

enum class AudioEventType {
    NONE,
    SCREAM_DISTRESS,
    CRASH_IMPACT,
    GLASS_BREAKING,
    LOUD_ABNORMAL,
    POTENTIALLY_DANGEROUS,
    UNKNOWN_ANOMALY
}

/**
 * Result from the vision event classifier.
 */
data class VisionEventResult(
    val eventType: VisionEventType,
    val confidence: Float,  // 0.0 to 1.0
    val timestamp: Long = System.currentTimeMillis(),
    val boundingBoxes: List<BoundingBox> = emptyList()
)

enum class VisionEventType {
    NONE,
    FALL_DETECTED,
    PERSON_ON_GROUND,
    PHYSICAL_STRUGGLE,
    ACCIDENT,
    FIRE_SMOKE,
    DANGEROUS_ENVIRONMENT,
    UNCONSCIOUS_PERSON
}

data class BoundingBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val label: String,
    val confidence: Float
)

/**
 * Multimodal fusion result combining all sensor evidence.
 */
data class FusionResult(
    val overallRiskScore: Float,  // 0.0 to 1.0
    val recommendedState: SafetyState,
    val motionContribution: Float,
    val audioContribution: Float,
    val visionContribution: Float,
    val temporalPersistence: Float,
    val confidence: Float,
    val evidenceSummary: String,
    val timestamp: Long = System.currentTimeMillis()
)
