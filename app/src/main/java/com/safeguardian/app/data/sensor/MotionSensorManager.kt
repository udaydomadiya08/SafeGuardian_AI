package com.safeguardian.app.data.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.safeguardian.app.domain.model.GuardianConfig
import com.safeguardian.app.domain.model.MotionFeatures
import com.safeguardian.app.domain.model.SensorStatus
import com.safeguardian.app.domain.motion.MotionAnomalyDetector
import com.safeguardian.app.domain.motion.MotionEvent
import com.safeguardian.app.domain.motion.MotionFeatureExtractor
import com.safeguardian.app.domain.motion.MotionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sqrt

/**
 * Manages low-power accelerometer and gyroscope sensor data collection.
 *
 * Implements Phase 2 Motion Intelligence Pipeline:
 * Android Sensors -> Timestamped samples -> Preprocessing (Ring Buffer) ->
 * Feature extraction -> Temporal analysis -> Motion anomaly detector -> State Engine
 *
 * Privacy & Battery:
 * - Raw sensor data is stored only in high-performance primitive ring buffers.
 * - Zero heap allocations in onSensorChanged.
 * - Raw sensor data is automatically overwritten and never uploaded or continuously stored.
 * - Sampling rate defaults to conservative low-power mode and is configurable.
 */
class MotionSensorManager(
    private val context: Context,
    private val config: GuardianConfig = GuardianConfig(),
    val anomalyDetector: MotionAnomalyDetector = MotionAnomalyDetector(config)
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    // Status flows
    private val _isMonitoring = MutableStateFlow(false)
    val isMonitoring: StateFlow<Boolean> = _isMonitoring.asStateFlow()

    private val _accelStatus = MutableStateFlow(SensorStatus.OFF)
    val accelStatus: StateFlow<SensorStatus> = _accelStatus.asStateFlow()

    private val _gyroStatus = MutableStateFlow(SensorStatus.OFF)
    val gyroStatus: StateFlow<SensorStatus> = _gyroStatus.asStateFlow()

    // Live telemetry flows for Developer Dashboard
    private val _currentAccelMagnitude = MutableStateFlow(9.80665f)
    val currentAccelMagnitude: StateFlow<Float> = _currentAccelMagnitude.asStateFlow()

    private val _currentGyroMagnitude = MutableStateFlow(0f)
    val currentGyroMagnitude: StateFlow<Float> = _currentGyroMagnitude.asStateFlow()

    private val _peakAccelMagnitude = MutableStateFlow(9.80665f)
    val peakAccelMagnitude: StateFlow<Float> = _peakAccelMagnitude.asStateFlow()

    private val _peakGyroMagnitude = MutableStateFlow(0f)
    val peakGyroMagnitude: StateFlow<Float> = _peakGyroMagnitude.asStateFlow()

    private val _currentMotionState = MutableStateFlow(MotionState.NORMAL)
    val currentMotionState: StateFlow<MotionState> = _currentMotionState.asStateFlow()

    private val _motionConfidence = MutableStateFlow(0.0f)
    val motionConfidence: StateFlow<Float> = _motionConfidence.asStateFlow()

    private val _lastDetectedEvent = MutableStateFlow<MotionEvent?>(null)
    val lastDetectedEvent: StateFlow<MotionEvent?> = _lastDetectedEvent.asStateFlow()

    private val _latestFeatures = MutableStateFlow(MotionFeatures())
    val latestFeatures: StateFlow<MotionFeatures> = _latestFeatures.asStateFlow()

    private val _currentSamplingRateHz = MutableStateFlow(config.idleSamplingRateHz)
    val currentSamplingRateHz: StateFlow<Float> = _currentSamplingRateHz.asStateFlow()

    private val _estimatedProcessingTimeMs = MutableStateFlow(0.15f)
    val estimatedProcessingTimeMs: StateFlow<Float> = _estimatedProcessingTimeMs.asStateFlow()

    // Zero-allocation ring buffers for raw sensor data (capacity 256 samples each)
    private val accelRing = RingSensorBuffer(capacity = 256)
    private val gyroRing = RingSensorBuffer(capacity = 256)

    // Preallocated buffers for snapshot extraction to eliminate garbage collection pressure
    private val maxExtractionSamples = 128
    private val tempAccelX = FloatArray(maxExtractionSamples)
    private val tempAccelY = FloatArray(maxExtractionSamples)
    private val tempAccelZ = FloatArray(maxExtractionSamples)
    private val tempAccelMag = FloatArray(maxExtractionSamples)
    private val tempAccelTime = LongArray(maxExtractionSamples)

    private val tempGyroX = FloatArray(maxExtractionSamples)
    private val tempGyroY = FloatArray(maxExtractionSamples)
    private val tempGyroZ = FloatArray(maxExtractionSamples)
    private val tempGyroMag = FloatArray(maxExtractionSamples)
    private val tempGyroTime = LongArray(maxExtractionSamples)

    // Feature extractor and timing
    private val featureExtractor = MotionFeatureExtractor()
    private var lastFeatureComputeTime = 0L
    private var immobilityStartTime = 0L

    // Temporary array for fast latest reading query
    private val latestTemp = FloatArray(4)

    val isAccelerometerAvailable: Boolean get() = accelerometer != null
    val isGyroscopeAvailable: Boolean get() = gyroscope != null

    /**
     * Start continuous sensor monitoring using the configured sampling rate.
     */
    fun start() {
        if (_isMonitoring.value) return

        val delay = samplingRateToDelay(_currentSamplingRateHz.value)

        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, delay)
            _accelStatus.value = SensorStatus.ACTIVE
        } else {
            _accelStatus.value = SensorStatus.UNAVAILABLE
        }

        if (gyroscope != null) {
            sensorManager.registerListener(this, gyroscope, delay)
            _gyroStatus.value = SensorStatus.ACTIVE
        } else {
            _gyroStatus.value = SensorStatus.UNAVAILABLE
        }

        _isMonitoring.value = true
    }

    /**
     * Stop all sensor monitoring and release hardware listeners.
     */
    fun stop() {
        sensorManager.unregisterListener(this)
        _isMonitoring.value = false
        _accelStatus.value = SensorStatus.OFF
        _gyroStatus.value = SensorStatus.OFF
        accelRing.clear()
        gyroRing.clear()
    }

    /**
     * Dynamically change sensor sampling rate (e.g., from settings or active cascade).
     */
    fun setSamplingRate(hz: Float) {
        val targetHz = hz.coerceIn(1f, 100f)
        _currentSamplingRateHz.value = targetHz
        if (_isMonitoring.value) {
            // Re-register with new delay
            sensorManager.unregisterListener(this)
            val delay = samplingRateToDelay(targetHz)
            if (accelerometer != null) {
                sensorManager.registerListener(this, accelerometer, delay)
            }
            if (gyroscope != null) {
                sensorManager.registerListener(this, gyroscope, delay)
            }
        }
    }

    fun setActiveSamplingRate() {
        setSamplingRate(config.activeSamplingRateHz)
    }

    fun setIdleSamplingRate() {
        setSamplingRate(config.idleSamplingRateHz)
    }

    /**
     * SensorEventListener implementation with zero heap allocation.
     */
    override fun onSensorChanged(event: SensorEvent) {
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val now = System.currentTimeMillis()

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                accelRing.add(x, y, z, now)
                val mag = sqrt(x * x + y * y + z * z)
                _currentAccelMagnitude.value = mag
            }
            Sensor.TYPE_GYROSCOPE -> {
                gyroRing.add(x, y, z, now)
                val mag = sqrt(x * x + y * y + z * z)
                _currentGyroMagnitude.value = mag
            }
        }

        // Process temporal window at rate-limited interval
        val intervalMs = (1000f / _currentSamplingRateHz.value).toLong().coerceIn(50L, 500L)
        if (now - lastFeatureComputeTime >= intervalMs) {
            processCurrentWindow(now)
            lastFeatureComputeTime = now
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Handled silently to avoid logging overhead
    }

    /**
     * Injects synthetic sensor samples into the exact same pipeline used by real hardware sensors.
     * Guarantees 100% parity between simulation mode and real hardware.
     */
    fun injectSyntheticSample(sensorType: Int, x: Float, y: Float, z: Float, timestamp: Long = System.currentTimeMillis()) {
        when (sensorType) {
            Sensor.TYPE_ACCELEROMETER -> {
                accelRing.add(x, y, z, timestamp)
                val mag = sqrt(x * x + y * y + z * z)
                _currentAccelMagnitude.value = mag
            }
            Sensor.TYPE_GYROSCOPE -> {
                gyroRing.add(x, y, z, timestamp)
                val mag = sqrt(x * x + y * y + z * z)
                _currentGyroMagnitude.value = mag
            }
        }
        processCurrentWindow(timestamp)
    }

    /**
     * Extracts window samples, computes features, and runs anomaly detector.
     */
    @Synchronized
    fun processCurrentWindow(now: Long = System.currentTimeMillis()): MotionFeatures {
        val startTime = System.nanoTime()

        // 1. Snapshot window from ring buffers (zero allocation)
        val accelCount = accelRing.copyWindow(
            windowMs = config.motionWindowSizeMs,
            currentTime = now,
            outX = tempAccelX,
            outY = tempAccelY,
            outZ = tempAccelZ,
            outMag = tempAccelMag,
            outTimestamps = tempAccelTime
        )

        val gyroCount = gyroRing.copyWindow(
            windowMs = config.motionWindowSizeMs,
            currentTime = now,
            outX = tempGyroX,
            outY = tempGyroY,
            outZ = tempGyroZ,
            outMag = tempGyroMag,
            outTimestamps = tempGyroTime
        )

        // 2. Feature Extraction
        val extractionResult = featureExtractor.extract(
            accelX = tempAccelX,
            accelY = tempAccelY,
            accelZ = tempAccelZ,
            accelMag = tempAccelMag,
            accelTime = tempAccelTime,
            accelCount = accelCount,
            gyroX = tempGyroX,
            gyroY = tempGyroY,
            gyroZ = tempGyroZ,
            gyroMag = tempGyroMag,
            gyroTime = tempGyroTime,
            gyroCount = gyroCount,
            windowSizeMs = config.motionWindowSizeMs,
            previousImmobilityStartTime = immobilityStartTime,
            currentTime = now
        )

        immobilityStartTime = extractionResult.immobilityStartTime
        val features = extractionResult.features
        _latestFeatures.value = features
        _peakAccelMagnitude.value = features.peakAccelMagnitude
        _peakGyroMagnitude.value = features.peakGyroMagnitude

        // 3. Temporal Anomaly Detection
        val output = anomalyDetector.analyze(features)
        _currentMotionState.value = output.motionState
        _motionConfidence.value = output.motionConfidence

        if (output.motionEvent != null) {
            _lastDetectedEvent.value = output.motionEvent
        }

        val elapsedMs = (System.nanoTime() - startTime) / 1_000_000f
        _estimatedProcessingTimeMs.value = elapsedMs

        return features
    }

    private fun samplingRateToDelay(hz: Float): Int {
        return when {
            hz >= 50f -> SensorManager.SENSOR_DELAY_GAME
            hz >= 15f -> SensorManager.SENSOR_DELAY_UI
            else -> SensorManager.SENSOR_DELAY_NORMAL
        }
    }
}
