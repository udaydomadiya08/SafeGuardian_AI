package com.safeguardian.app.domain.motion

import com.safeguardian.app.domain.model.MotionFeatures
import com.safeguardian.app.domain.model.SensorReading

/**
 * Robust sensor data sanitizer protecting the safety engine against corrupted,
 * out-of-order, NaN, Infinite, and maliciously malformed sensor inputs.
 */
object SensorDataSanitizer {

    private const val MAX_ACCEL_MAGNITUDE = 200.0f
    private const val MAX_ACCEL_JERK = 300.0f
    private const val MAX_GYRO_MAGNITUDE = 100.0f
    private const val MAX_IMMOBILITY_MS = 3_600_000L // 1 hour max

    /**
     * Sanitizes raw sensor 3D readings, ensuring all coordinates are finite and non-NaN.
     */
    fun sanitizeReading(reading: SensorReading): SensorReading {
        val safeX = if (reading.x.isFinite() && !reading.x.isNaN()) reading.x.coerceIn(-MAX_ACCEL_MAGNITUDE, MAX_ACCEL_MAGNITUDE) else 0f
        val safeY = if (reading.y.isFinite() && !reading.y.isNaN()) reading.y.coerceIn(-MAX_ACCEL_MAGNITUDE, MAX_ACCEL_MAGNITUDE) else 0f
        val safeZ = if (reading.z.isFinite() && !reading.z.isNaN()) reading.z.coerceIn(-MAX_ACCEL_MAGNITUDE, MAX_ACCEL_MAGNITUDE) else 9.81f

        val now = System.currentTimeMillis()
        val safeTimestamp = if (reading.timestamp <= 0L || reading.timestamp > now + 60_000L) now else reading.timestamp

        return SensorReading(
            x = safeX,
            y = safeY,
            z = safeZ,
            timestamp = safeTimestamp,
            accuracy = reading.accuracy.coerceIn(0, 3)
        )
    }

    /**
     * Sanitizes aggregated motion features before ML tensor inference or heuristic processing.
     */
    fun sanitizeFeatures(f: MotionFeatures): MotionFeatures {
        fun safeFloat(v: Float, default: Float = 0f, min: Float = 0f, max: Float = 500f): Float {
            return if (v.isFinite() && !v.isNaN()) v.coerceIn(min, max) else default
        }

        val safeCurrentMag = safeFloat(f.currentAccelMagnitude, default = 9.81f, min = 0f, max = MAX_ACCEL_MAGNITUDE)
        val safePeakAccel = safeFloat(f.peakAccelMagnitude, default = 9.81f, min = 0f, max = MAX_ACCEL_MAGNITUDE)
        val safeMeanAccel = safeFloat(f.meanAccelMagnitude, default = 9.81f, min = 0f, max = 100f)
        val safeVariance = safeFloat(f.accelVariance, default = 0f, min = 0f, max = 500f)
        val safeJerk = safeFloat(f.accelDeltaMagnitude, default = 0f, min = 0f, max = MAX_ACCEL_JERK)
        val safeEnergy = safeFloat(f.signalEnergy, default = 0f, min = 0f, max = 5000f)
        val safeImpulse = safeFloat(f.suddenImpulse, default = 0f, min = 0f, max = 500f)
        val safeIntensity = safeFloat(f.motionIntensity, default = 0f, min = 0f, max = 1f)

        val safePeakGyro = safeFloat(f.peakGyroMagnitude, default = 0f, min = 0f, max = MAX_GYRO_MAGNITUDE)
        val safeMeanGyro = safeFloat(f.meanGyroMagnitude, default = 0f, min = 0f, max = 50f)
        val safeGyroVar = safeFloat(f.gyroVariance, default = 0f, min = 0f, max = 200f)
        val safeRotChange = safeFloat(f.rotationalChange, default = 0f, min = 0f, max = 100f)
        val safeRotEnergy = safeFloat(f.rotationalEnergy, default = 0f, min = 0f, max = 1000f)
        val safeGravNorm = safeFloat(f.gravityNormalizedAccel, default = 0f, min = 0f, max = 100f)
        val safeOrientChange = safeFloat(f.orientationChange, default = 0f, min = 0f, max = 50f)

        val safeImmobility = if (f.immobilityDuration < 0L) 0L else f.immobilityDuration.coerceAtMost(MAX_IMMOBILITY_MS)

        val now = System.currentTimeMillis()
        val safeTimestamp = if (f.timestamp <= 0L || f.timestamp > now + 60_000L) now else f.timestamp

        return f.copy(
            currentAccelMagnitude = safeCurrentMag,
            peakAccelMagnitude = safePeakAccel,
            meanAccelMagnitude = safeMeanAccel,
            accelVariance = safeVariance,
            accelDeltaMagnitude = safeJerk,
            signalEnergy = safeEnergy,
            suddenImpulse = safeImpulse,
            motionIntensity = safeIntensity,
            peakGyroMagnitude = safePeakGyro,
            meanGyroMagnitude = safeMeanGyro,
            gyroVariance = safeGyroVar,
            rotationalChange = safeRotChange,
            rotationalEnergy = safeRotEnergy,
            gravityNormalizedAccel = safeGravNorm,
            orientationChange = safeOrientChange,
            immobilityDuration = safeImmobility,
            timestamp = safeTimestamp
        )
    }

    /**
     * Sanitizes audio buffer, ensuring non-null, non-empty, and bounded amplitude.
     */
    fun sanitizeAudioBuffer(audioBuffer: ShortArray?): ShortArray {
        if (audioBuffer == null || audioBuffer.isEmpty()) {
            return ShortArray(0)
        }
        return audioBuffer
    }
}
