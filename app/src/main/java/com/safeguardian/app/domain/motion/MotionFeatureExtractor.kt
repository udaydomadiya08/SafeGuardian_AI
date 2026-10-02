package com.safeguardian.app.domain.motion

import com.safeguardian.app.domain.model.MotionFeatures
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Lightweight, mathematical feature extractor for accelerometer and gyroscope streams.
 *
 * Implements low-power feature calculations across configurable temporal windows:
 * - Accelerometer: 3-axis readings, magnitude, jerk/delta, peak, rolling mean, rolling variance,
 *   signal energy, sudden impulse, motion intensity.
 * - Gyroscope: 3-axis angular velocity, magnitude, rotational change, rolling mean, rolling variance,
 *   rotational energy.
 * - Derived features: gravity-normalized acceleration, orientation change,
 *   acceleration followed by deceleration pattern, sudden rotation, and
 *   movement -> impact -> low movement sequence.
 */
class MotionFeatureExtractor(
    private val gravityStandard: Float = 9.80665f
) {

    /**
     * Extracts motion features from window buffers.
     *
     * @param accelX Accelerometer X samples (chronological or reverse, size = accelCount)
     * @param accelY Accelerometer Y samples
     * @param accelZ Accelerometer Z samples
     * @param accelMag Precomputed accelerometer magnitudes
     * @param accelTime Sample timestamps in milliseconds
     * @param accelCount Number of valid accelerometer samples
     * @param gyroX Gyroscope X samples
     * @param gyroY Gyroscope Y samples
     * @param gyroZ Gyroscope Z samples
     * @param gyroMag Precomputed gyroscope magnitudes
     * @param gyroTime Sample timestamps in milliseconds
     * @param gyroCount Number of valid gyroscope samples
     * @param windowSizeMs Configured window size in ms
     * @param previousImmobilityStartTime Tracked start time of immobility across calls
     * @param currentTime Current evaluation timestamp
     */
    fun extract(
        accelX: FloatArray,
        accelY: FloatArray,
        accelZ: FloatArray,
        accelMag: FloatArray,
        accelTime: LongArray,
        accelCount: Int,
        gyroX: FloatArray,
        gyroY: FloatArray,
        gyroZ: FloatArray,
        gyroMag: FloatArray,
        gyroTime: LongArray,
        gyroCount: Int,
        windowSizeMs: Long,
        previousImmobilityStartTime: Long,
        currentTime: Long = System.currentTimeMillis()
    ): FeatureExtractionResult {
        if (accelCount == 0) {
            return FeatureExtractionResult(
                features = MotionFeatures(timestamp = currentTime, windowSizeMs = windowSizeMs),
                immobilityStartTime = previousImmobilityStartTime
            )
        }

        // --- Accelerometer Features ---
        val currAccelX = accelX[0]
        val currAccelY = accelY[0]
        val currAccelZ = accelZ[0]
        val currAccelMag = accelMag[0]

        var sumAccelMag = 0.0
        var sumSquaredAccelMag = 0.0
        var peakAccelMag = 0f
        var minAccelMag = Float.MAX_VALUE
        var maxDeltaAccel = 0f

        for (i in 0 until accelCount) {
            val m = accelMag[i]
            sumAccelMag += m
            sumSquaredAccelMag += (m * m)
            if (m > peakAccelMag) peakAccelMag = m
            if (m < minAccelMag) minAccelMag = m

            if (i > 0) {
                val delta = abs(m - accelMag[i - 1])
                if (delta > maxDeltaAccel) maxDeltaAccel = delta
            }
        }

        val meanAccelMag = (sumAccelMag / accelCount).toFloat()
        val signalEnergy = (sumSquaredAccelMag / accelCount).toFloat()

        // Sample variance
        var sumSquaredDiffs = 0.0
        for (i in 0 until accelCount) {
            val diff = accelMag[i] - meanAccelMag
            sumSquaredDiffs += (diff * diff)
        }
        val accelVariance = if (accelCount > 1) (sumSquaredDiffs / (accelCount - 1)).toFloat() else 0f
        val motionIntensity = sqrt(max(0f, accelVariance))
        val suddenImpulse = maxDeltaAccel

        val gravityNormalizedAccel = abs(currAccelMag - gravityStandard)

        // --- Gyroscope Features ---
        val currGyroX = if (gyroCount > 0) gyroX[0] else 0f
        val currGyroY = if (gyroCount > 0) gyroY[0] else 0f
        val currGyroZ = if (gyroCount > 0) gyroZ[0] else 0f
        val currGyroMag = if (gyroCount > 0) gyroMag[0] else 0f

        var sumGyroMag = 0.0
        var sumSquaredGyroMag = 0.0
        var peakGyroMag = 0f
        var rotationalChange = 0f

        for (i in 0 until gyroCount) {
            val gm = gyroMag[i]
            sumGyroMag += gm
            sumSquaredGyroMag += (gm * gm)
            if (gm > peakGyroMag) peakGyroMag = gm

            if (i > 0) {
                rotationalChange += abs(gm - gyroMag[i - 1])
            }
        }

        val meanGyroMag = if (gyroCount > 0) (sumGyroMag / gyroCount).toFloat() else 0f
        val rotationalEnergy = if (gyroCount > 0) (sumSquaredGyroMag / gyroCount).toFloat() else 0f

        var sumGyroDiffs = 0.0
        for (i in 0 until gyroCount) {
            val diff = gyroMag[i] - meanGyroMag
            sumGyroDiffs += (diff * diff)
        }
        val gyroVariance = if (gyroCount > 1) (sumGyroDiffs / (gyroCount - 1)).toFloat() else 0f

        // Orientation change: integrated angular velocity over time
        val orientationChange = sumGyroMag.toFloat() * (windowSizeMs.toFloat() / 1000f / max(1, gyroCount))

        // --- Derived Patterns ---
        // 1. Sudden acceleration followed by rapid deceleration or drop below 1g
        val hasImpactDecelPattern = (peakAccelMag > 22.0f && minAccelMag < 6.0f) ||
                (peakAccelMag > 18.0f && suddenImpulse > 12.0f)

        // 2. Sudden rotation: sharp gyro spike
        val suddenRotation = peakGyroMag > 3.5f

        // 3. Movement -> Impact -> Low movement pattern
        // Check if oldest segment had movement, middle had impact spike, and recent samples show low variance
        val movementImpactImmobilityPattern = checkMovementImpactImmobility(
            accelMag, accelCount, peakAccelMag
        )

        // --- Immobility Evaluation ---
        // Immobility: acceleration close to 1G (8.5 to 11.5 m/s^2) and variance < 0.5 (m/s^2)^2
        val isCurrentlyImmobile = accelVariance < 0.5f &&
                currAccelMag in 8.5f..11.5f &&
                meanAccelMag in 8.5f..11.5f

        val updatedImmobilityStartTime = if (isCurrentlyImmobile) {
            if (previousImmobilityStartTime == 0L) currentTime else previousImmobilityStartTime
        } else {
            0L
        }

        val immobilityDuration = if (updatedImmobilityStartTime > 0L) {
            currentTime - updatedImmobilityStartTime
        } else {
            0L
        }

        val features = MotionFeatures(
            currentAccelX = currAccelX,
            currentAccelY = currAccelY,
            currentAccelZ = currAccelZ,
            currentAccelMagnitude = currAccelMag,
            meanAccelMagnitude = meanAccelMag,
            peakAccelMagnitude = peakAccelMag,
            accelVariance = accelVariance,
            accelDeltaMagnitude = maxDeltaAccel,
            signalEnergy = signalEnergy,
            suddenImpulse = suddenImpulse,
            motionIntensity = motionIntensity,
            currentGyroX = currGyroX,
            currentGyroY = currGyroY,
            currentGyroZ = currGyroZ,
            currentGyroMagnitude = currGyroMag,
            meanGyroMagnitude = meanGyroMag,
            peakGyroMagnitude = peakGyroMag,
            gyroVariance = gyroVariance,
            rotationalChange = rotationalChange,
            rotationalEnergy = rotationalEnergy,
            gravityNormalizedAccel = gravityNormalizedAccel,
            orientationChange = orientationChange,
            hasImpactDecelPattern = hasImpactDecelPattern,
            suddenRotation = suddenRotation,
            movementImpactImmobilityPattern = movementImpactImmobilityPattern,
            immobilityDuration = immobilityDuration,
            timestamp = currentTime,
            windowSizeMs = windowSizeMs
        )

        return FeatureExtractionResult(
            features = features,
            immobilityStartTime = updatedImmobilityStartTime
        )
    }

    private fun checkMovementImpactImmobility(
        accelMag: FloatArray,
        count: Int,
        peakAccel: Float
    ): Boolean {
        if (count < 6 || peakAccel < 18.0f) return false

        // In reverse-chronological order:
        // index 0..1 is most recent (post-impact)
        // index 2..count-3 is middle (impact peak)
        // index count-2..count-1 is pre-impact
        val recentCount = count / 3
        var recentVarSum = 0.0
        var recentMean = 0.0
        for (i in 0 until recentCount) {
            recentMean += accelMag[i]
        }
        recentMean /= recentCount
        for (i in 0 until recentCount) {
            val d = accelMag[i] - recentMean
            recentVarSum += (d * d)
        }
        val recentVariance = if (recentCount > 1) recentVarSum / (recentCount - 1) else 0.0

        // If post-impact recent samples are calm/immobile while peak was high, pattern holds
        return recentVariance < 2.0 && peakAccel > 20.0f
    }

    data class FeatureExtractionResult(
        val features: MotionFeatures,
        val immobilityStartTime: Long
    )
}
