package com.safeguardian.app.data.sensor

import kotlin.math.sqrt

/**
 * High-performance, zero-allocation ring buffer for raw 3-axis sensor readings.
 * Uses flat primitive arrays to completely avoid heap allocations in onSensorChanged.
 * Raw sensor data is discarded automatically as new samples arrive; it is never persisted.
 */
class RingSensorBuffer(val capacity: Int = 256) {

    private val timestamps = LongArray(capacity)
    private val xValues = FloatArray(capacity)
    private val yValues = FloatArray(capacity)
    private val zValues = FloatArray(capacity)
    private val magnitudes = FloatArray(capacity)

    private var head = 0
    private var count = 0

    @Synchronized
    fun add(x: Float, y: Float, z: Float, timestamp: Long) {
        val mag = sqrt(x * x + y * y + z * z)
        timestamps[head] = timestamp
        xValues[head] = x
        yValues[head] = y
        zValues[head] = z
        magnitudes[head] = mag

        head = (head + 1) % capacity
        if (count < capacity) {
            count++
        }
    }

    @Synchronized
    fun size(): Int = count

    @Synchronized
    fun clear() {
        head = 0
        count = 0
    }

    /**
     * Extracts samples from the last [windowMs] milliseconds into caller-provided arrays.
     * Returns the number of samples copied into the destination arrays.
     * Zero object allocation during extraction.
     */
    @Synchronized
    fun copyWindow(
        windowMs: Long,
        currentTime: Long,
        outX: FloatArray,
        outY: FloatArray,
        outZ: FloatArray,
        outMag: FloatArray,
        outTimestamps: LongArray
    ): Int {
        if (count == 0) return 0

        val cutoff = currentTime - windowMs
        var copied = 0
        val maxCopy = minOf(outX.size, count)

        // Iterate backwards from the latest sample
        var idx = (head - 1 + capacity) % capacity
        for (i in 0 until count) {
            val t = timestamps[idx]
            if (t < cutoff || copied >= maxCopy) break

            // Store in reverse-chronological order or forward; we'll write from copied index
            outX[copied] = xValues[idx]
            outY[copied] = yValues[idx]
            outZ[copied] = zValues[idx]
            outMag[copied] = magnitudes[idx]
            outTimestamps[copied] = t
            copied++

            idx = (idx - 1 + capacity) % capacity
        }

        return copied
    }

    @Synchronized
    fun getLatest(out: FloatArray): Boolean {
        if (count == 0) return false
        val latestIdx = (head - 1 + capacity) % capacity
        out[0] = xValues[latestIdx]
        out[1] = yValues[latestIdx]
        out[2] = zValues[latestIdx]
        out[3] = magnitudes[latestIdx]
        return true
    }
}
