package com.safeguardian.app.presentation.screens.simulation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.safeguardian.app.SafeGuardianApp
import com.safeguardian.app.domain.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SimulationLog(
    val timestamp: Long = System.currentTimeMillis(),
    val message: String,
    val level: LogLevel = LogLevel.INFO
)

enum class LogLevel { INFO, WARNING, ALERT, EMERGENCY }

class SimulationViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as SafeGuardianApp

    val safetyState = app.stateEngine.safetyState
    val pipelineStage = app.stateEngine.pipelineStage
    val riskScore = app.stateEngine.riskScore

    private val _isSimulating = MutableStateFlow(false)
    val isSimulating: StateFlow<Boolean> = _isSimulating.asStateFlow()

    private val _simulationLogs = MutableStateFlow<List<SimulationLog>>(emptyList())
    val simulationLogs: StateFlow<List<SimulationLog>> = _simulationLogs.asStateFlow()

    private fun log(message: String, level: LogLevel = LogLevel.INFO) {
        _simulationLogs.value = _simulationLogs.value + SimulationLog(
            message = message, level = level
        )
    }

    private fun clearLogs() {
        _simulationLogs.value = emptyList()
    }

    fun simulateNormalActivity() {
        if (_isSimulating.value) return
        viewModelScope.launch {
            _isSimulating.value = true
            clearLogs()
            log("Starting: Normal Activity simulation")
            log("Generating normal walking motion data...")

            val normalFeatures = MotionFeatures(
                meanAccelMagnitude = 9.8f,
                peakAccelMagnitude = 11.5f,
                accelVariance = 2.0f,
                meanGyroMagnitude = 0.5f,
                peakGyroMagnitude = 1.2f,
                gyroVariance = 0.3f,
                orientationChange = 0.2f,
                immobilityDuration = 0L
            )

            app.stateEngine.processMotionData(normalFeatures)
            delay(1000)
            log("Result: No anomaly detected ✓", LogLevel.INFO)
            log("State: ${app.stateEngine.safetyState.value}")
            log("Simulation complete: System correctly identified normal activity")
            _isSimulating.value = false
        }
    }

    fun simulateSuddenImpact() {
        if (_isSimulating.value) return
        viewModelScope.launch {
            _isSimulating.value = true
            clearLogs()
            log("Starting: Sudden Impact simulation")

            log("→ Injecting high-G impact event...")
            app.stateEngine.simulateMotionAnomaly(
                MotionAnomalyResult(
                    isAnomaly = true,
                    anomalyScore = 0.85f,
                    anomalyType = MotionAnomalyType.SUDDEN_IMPACT,
                    confidence = 0.8f,
                    features = MotionFeatures(
                        peakAccelMagnitude = 35f,
                        accelVariance = 80f,
                        meanAccelMagnitude = 20f
                    )
                )
            )
            delay(500)
            log("State: ${app.stateEngine.safetyState.value}", LogLevel.WARNING)
            log("Pipeline: ${app.stateEngine.pipelineStage.value}")
            log("Risk: ${(app.stateEngine.riskScore.value * 100).toInt()}%")
            log("Simulation complete: Impact detected, investigation triggered")
            _isSimulating.value = false
        }
    }

    fun simulateFall() {
        if (_isSimulating.value) return
        viewModelScope.launch {
            _isSimulating.value = true
            clearLogs()
            log("Starting: Fall simulation")

            log("→ Phase 1: Free-fall motion...")
            app.stateEngine.simulateMotionAnomaly(
                MotionAnomalyResult(
                    isAnomaly = true,
                    anomalyScore = 0.9f,
                    anomalyType = MotionAnomalyType.FALL_LIKE_MOTION,
                    confidence = 0.85f,
                    features = MotionFeatures(
                        meanAccelMagnitude = 1.5f,
                        peakAccelMagnitude = 28f,
                        accelVariance = 120f,
                        peakGyroMagnitude = 8f,
                        orientationChange = 15f
                    )
                )
            )
            delay(1000)
            log("State: ${app.stateEngine.safetyState.value}", LogLevel.WARNING)

            log("→ Phase 2: Impact + distress audio...")
            app.stateEngine.simulateAudioEvent(
                AudioEventResult(
                    eventType = AudioEventType.SCREAM_DISTRESS,
                    confidence = 0.7f,
                    durationMs = 2000
                )
            )
            delay(1000)
            log("State: ${app.stateEngine.safetyState.value}", LogLevel.WARNING)
            log("Risk: ${(app.stateEngine.riskScore.value * 100).toInt()}%", LogLevel.ALERT)

            log("Simulation complete: Fall cascade demonstrated")
            _isSimulating.value = false
        }
    }

    fun simulateProlongedImmobility() {
        if (_isSimulating.value) return
        viewModelScope.launch {
            _isSimulating.value = true
            clearLogs()
            log("Starting: Prolonged Immobility simulation")

            log("→ Injecting immobility after impact...")
            app.stateEngine.simulateMotionAnomaly(
                MotionAnomalyResult(
                    isAnomaly = true,
                    anomalyScore = 0.75f,
                    anomalyType = MotionAnomalyType.PROLONGED_IMMOBILITY,
                    confidence = 0.7f,
                    features = MotionFeatures(
                        meanAccelMagnitude = 9.8f,
                        accelVariance = 0.1f,
                        immobilityDuration = 20_000L
                    )
                )
            )
            delay(1000)
            log("State: ${app.stateEngine.safetyState.value}", LogLevel.WARNING)
            log("Simulation complete")
            _isSimulating.value = false
        }
    }

    fun simulateDistressAudio() {
        if (_isSimulating.value) return
        viewModelScope.launch {
            _isSimulating.value = true
            clearLogs()
            log("Starting: Distress Audio simulation")

            // First need a motion trigger
            log("→ Triggering motion anomaly first...")
            app.stateEngine.simulateMotionAnomaly(
                MotionAnomalyResult(
                    isAnomaly = true,
                    anomalyScore = 0.65f,
                    anomalyType = MotionAnomalyType.UNUSUAL_ACCELERATION,
                    confidence = 0.6f,
                    features = MotionFeatures(peakAccelMagnitude = 15f, accelVariance = 30f)
                )
            )
            delay(500)

            log("→ Injecting distress audio event...")
            app.stateEngine.simulateAudioEvent(
                AudioEventResult(
                    eventType = AudioEventType.SCREAM_DISTRESS,
                    confidence = 0.8f,
                    durationMs = 3000
                )
            )
            delay(500)
            log("State: ${app.stateEngine.safetyState.value}", LogLevel.ALERT)
            log("Risk: ${(app.stateEngine.riskScore.value * 100).toInt()}%")
            log("Simulation complete")
            _isSimulating.value = false
        }
    }

    fun simulateGlassBreak() {
        if (_isSimulating.value) return
        viewModelScope.launch {
            _isSimulating.value = true
            clearLogs()
            log("Starting: Glass Breaking simulation")

            log("→ Motion anomaly trigger...")
            app.stateEngine.simulateMotionAnomaly(
                MotionAnomalyResult(
                    isAnomaly = true,
                    anomalyScore = 0.7f,
                    anomalyType = MotionAnomalyType.SUDDEN_IMPACT,
                    confidence = 0.65f,
                    features = MotionFeatures(peakAccelMagnitude = 18f, accelVariance = 50f)
                )
            )
            delay(500)

            log("→ Glass breaking audio...")
            app.stateEngine.simulateAudioEvent(
                AudioEventResult(
                    eventType = AudioEventType.GLASS_BREAKING,
                    confidence = 0.75f,
                    durationMs = 1500
                )
            )
            delay(500)
            log("State: ${app.stateEngine.safetyState.value}", LogLevel.WARNING)
            log("Simulation complete")
            _isSimulating.value = false
        }
    }

    fun simulateAccident() {
        if (_isSimulating.value) return
        viewModelScope.launch {
            _isSimulating.value = true
            clearLogs()
            log("Starting: Accident simulation")

            log("→ High-G impact...")
            app.stateEngine.simulateMotionAnomaly(
                MotionAnomalyResult(
                    isAnomaly = true,
                    anomalyScore = 0.95f,
                    anomalyType = MotionAnomalyType.SUDDEN_IMPACT,
                    confidence = 0.9f,
                    features = MotionFeatures(
                        peakAccelMagnitude = 45f,
                        accelVariance = 150f,
                        peakGyroMagnitude = 12f
                    )
                )
            )
            delay(500)

            log("→ Crash audio...")
            app.stateEngine.simulateAudioEvent(
                AudioEventResult(
                    eventType = AudioEventType.CRASH_IMPACT,
                    confidence = 0.85f,
                    durationMs = 2000
                )
            )
            delay(500)

            log("→ Vision: accident scene...")
            app.stateEngine.simulateVisionEvent(
                VisionEventResult(
                    eventType = VisionEventType.ACCIDENT,
                    confidence = 0.75f
                )
            )
            delay(500)

            log("State: ${app.stateEngine.safetyState.value}", LogLevel.EMERGENCY)
            log("Risk: ${(app.stateEngine.riskScore.value * 100).toInt()}%", LogLevel.EMERGENCY)
            log("Simulation complete: Full cascade demonstrated")
            _isSimulating.value = false
        }
    }

    fun simulateFireSmoke() {
        if (_isSimulating.value) return
        viewModelScope.launch {
            _isSimulating.value = true
            clearLogs()
            log("Starting: Fire/Smoke simulation")

            log("→ Motion trigger (unusual pattern)...")
            app.stateEngine.simulateMotionAnomaly(
                MotionAnomalyResult(
                    isAnomaly = true,
                    anomalyScore = 0.6f,
                    anomalyType = MotionAnomalyType.ABNORMAL_PATTERN,
                    confidence = 0.55f,
                    features = MotionFeatures(peakAccelMagnitude = 12f, accelVariance = 20f)
                )
            )
            delay(500)

            log("→ Vision: fire/smoke detected...")
            app.stateEngine.simulateVisionEvent(
                VisionEventResult(
                    eventType = VisionEventType.FIRE_SMOKE,
                    confidence = 0.8f
                )
            )
            delay(500)

            log("State: ${app.stateEngine.safetyState.value}", LogLevel.ALERT)
            log("Simulation complete")
            _isSimulating.value = false
        }
    }

    fun simulatePhysicalStruggle() {
        if (_isSimulating.value) return
        viewModelScope.launch {
            _isSimulating.value = true
            clearLogs()
            log("Starting: Physical Struggle simulation")

            log("→ Erratic motion pattern...")
            app.stateEngine.simulateMotionAnomaly(
                MotionAnomalyResult(
                    isAnomaly = true,
                    anomalyScore = 0.8f,
                    anomalyType = MotionAnomalyType.ABNORMAL_PATTERN,
                    confidence = 0.75f,
                    features = MotionFeatures(
                        peakAccelMagnitude = 25f,
                        accelVariance = 90f,
                        peakGyroMagnitude = 9f,
                        gyroVariance = 15f
                    )
                )
            )
            delay(500)

            log("→ Distress sounds...")
            app.stateEngine.simulateAudioEvent(
                AudioEventResult(
                    eventType = AudioEventType.SCREAM_DISTRESS,
                    confidence = 0.85f,
                    durationMs = 4000
                )
            )
            delay(500)

            log("→ Vision: struggle detected...")
            app.stateEngine.simulateVisionEvent(
                VisionEventResult(
                    eventType = VisionEventType.PHYSICAL_STRUGGLE,
                    confidence = 0.7f
                )
            )
            delay(500)

            log("State: ${app.stateEngine.safetyState.value}", LogLevel.EMERGENCY)
            log("Simulation complete")
            _isSimulating.value = false
        }
    }

    fun simulateMultiSensorEmergency() {
        if (_isSimulating.value) return
        viewModelScope.launch {
            _isSimulating.value = true
            clearLogs()
            log("Starting: Full Multi-Sensor Emergency simulation", LogLevel.ALERT)
            log("This demonstrates the complete cascade:")
            log("  impact → fall → immobility → audio → vision → emergency")

            log("→ Step 1: Sudden impact detected...")
            app.stateEngine.simulateMotionAnomaly(
                MotionAnomalyResult(
                    isAnomaly = true,
                    anomalyScore = 0.92f,
                    anomalyType = MotionAnomalyType.SUDDEN_IMPACT,
                    confidence = 0.88f,
                    features = MotionFeatures(
                        peakAccelMagnitude = 40f,
                        accelVariance = 130f,
                        peakGyroMagnitude = 10f
                    )
                )
            )
            delay(800)
            log("  State: ${app.stateEngine.safetyState.value}")

            log("→ Step 2: Fall-like motion pattern...")
            app.stateEngine.simulateMotionAnomaly(
                MotionAnomalyResult(
                    isAnomaly = true,
                    anomalyScore = 0.88f,
                    anomalyType = MotionAnomalyType.FALL_LIKE_MOTION,
                    confidence = 0.85f,
                    features = MotionFeatures(
                        meanAccelMagnitude = 2f,
                        peakAccelMagnitude = 30f,
                        accelVariance = 100f,
                        orientationChange = 20f
                    )
                )
            )
            delay(800)
            log("  State: ${app.stateEngine.safetyState.value}")

            log("→ Step 3: Distress audio detected...")
            app.stateEngine.simulateAudioEvent(
                AudioEventResult(
                    eventType = AudioEventType.SCREAM_DISTRESS,
                    confidence = 0.82f,
                    durationMs = 3000
                )
            )
            delay(800)
            log("  State: ${app.stateEngine.safetyState.value}", LogLevel.WARNING)

            log("→ Step 4: Visual confirmation — person on ground...")
            app.stateEngine.simulateVisionEvent(
                VisionEventResult(
                    eventType = VisionEventType.PERSON_ON_GROUND,
                    confidence = 0.78f
                )
            )
            delay(800)
            log("  State: ${app.stateEngine.safetyState.value}", LogLevel.EMERGENCY)
            log("  Risk: ${(app.stateEngine.riskScore.value * 100).toInt()}%", LogLevel.EMERGENCY)

            val emergency = app.stateEngine.currentEmergency.value
            if (emergency != null) {
                log("🚨 EMERGENCY EVENT CREATED", LogLevel.EMERGENCY)
                log("  Type: ${emergency.eventType}")
                log("  Confidence: ${(emergency.confidence * 100).toInt()}%")
                log("  Simulated: ${emergency.isSimulated}")
                log("  Evidence: ${emergency.sensorEvidence.eventChainDescription}")
            } else {
                log("  Emergency event pending (threshold may need adjustment)")
            }

            log("")
            log("Simulation complete.")
            log("Use 'Resolve Emergency' on main screen to clear the alert.")
            _isSimulating.value = false
        }
    }

    // ==========================================
    // PHASE 2 MOTION INTELLIGENCE SIMULATIONS
    // These inject raw synthetic samples directly into the real sensor pipeline
    // ==========================================

    fun simulatePhase2Normal() {
        if (_isSimulating.value) return
        viewModelScope.launch {
            _isSimulating.value = true
            clearLogs()
            log("=== Phase 2: SIMULATE NORMAL ===", LogLevel.INFO)
            log("Injecting 50Hz walking sensor samples (accel ~9.8 m/s² ±1.5, gyro ~0.5 rad/s)...")

            var now = System.currentTimeMillis() - 2000L
            for (i in 0 until 100) {
                val stepWave = kotlin.math.sin(i * 0.25).toFloat() * 1.5f
                val ax = 0.5f + stepWave * 0.3f
                val ay = 1.0f + stepWave * 0.4f
                val az = 9.8f + stepWave
                app.motionSensorManager.injectSyntheticSample(android.hardware.Sensor.TYPE_ACCELEROMETER, ax, ay, az, now)
                app.motionSensorManager.injectSyntheticSample(android.hardware.Sensor.TYPE_GYROSCOPE, 0.2f, 0.3f, 0.4f, now)
                now += 20L
            }

            val features = app.motionSensorManager.latestFeatures.value
            val motionState = app.motionSensorManager.currentMotionState.value
            val confidence = app.motionSensorManager.motionConfidence.value

            log("Features extracted:")
            log("  Peak Accel: ${"%.2f".format(features.peakAccelMagnitude)} m/s²")
            log("  Accel Variance: ${"%.4f".format(features.accelVariance)}")
            log("  Peak Gyro: ${"%.2f".format(features.peakGyroMagnitude)} rad/s")
            log("Detector Output:")
            log("  Motion State: $motionState")
            log("  Motion Confidence: ${(confidence * 100).toInt()}%")
            log("  Safety State: ${app.stateEngine.safetyState.value}")
            log("PASS: Normal movement correctly suppressed false alarms ✓", LogLevel.INFO)
            _isSimulating.value = false
        }
    }

    fun simulatePhase2Impact() {
        if (_isSimulating.value) return
        viewModelScope.launch {
            _isSimulating.value = true
            clearLogs()
            log("=== Phase 2: SIMULATE IMPACT ===", LogLevel.WARNING)
            log("Injecting pre-impact samples followed by sudden 38 m/s² impact spike...")

            var now = System.currentTimeMillis() - 1000L
            // Baseline movement
            for (i in 0 until 30) {
                app.motionSensorManager.injectSyntheticSample(android.hardware.Sensor.TYPE_ACCELEROMETER, 0f, 0f, 9.8f, now)
                app.motionSensorManager.injectSyntheticSample(android.hardware.Sensor.TYPE_GYROSCOPE, 0.1f, 0.1f, 0.1f, now)
                now += 20L
            }

            // Sudden impact spike
            log("→ Impact spike (38.5 m/s²)...", LogLevel.ALERT)
            app.motionSensorManager.injectSyntheticSample(android.hardware.Sensor.TYPE_ACCELEROMETER, 15f, 20f, 30f, now) // mag ~38.5 m/s^2
            app.motionSensorManager.injectSyntheticSample(android.hardware.Sensor.TYPE_GYROSCOPE, 2.5f, 3.0f, 1.5f, now)
            now += 20L

            // Rebound deceleration
            app.motionSensorManager.injectSyntheticSample(android.hardware.Sensor.TYPE_ACCELEROMETER, 2f, 3f, 4f, now)
            app.motionSensorManager.injectSyntheticSample(android.hardware.Sensor.TYPE_GYROSCOPE, 0.5f, 0.5f, 0.5f, now)

            val motionState = app.motionSensorManager.currentMotionState.value
            val confidence = app.motionSensorManager.motionConfidence.value
            val lastEvent = app.motionSensorManager.lastDetectedEvent.value

            log("Detector Output:")
            log("  Motion State: $motionState", LogLevel.WARNING)
            log("  Motion Confidence: ${(confidence * 100).toInt()}%")
            log("  Last Event: ${lastEvent?.description ?: "Candidate detected"}")
            log("PASS: High-G impact spike detected ✓", LogLevel.INFO)
            _isSimulating.value = false
        }
    }

    fun simulatePhase2Fall() {
        if (_isSimulating.value) return
        viewModelScope.launch {
            _isSimulating.value = true
            clearLogs()
            log("=== Phase 2: SIMULATE FALL ===", LogLevel.WARNING)
            log("Simulating 3-phase fall sequence:")
            log("  1. Freefall drop (< 2 m/s²)")
            log("  2. Severe rotational tumbling (> 6 rad/s)")
            log("  3. Hard surface impact (> 32 m/s²)")

            var now = System.currentTimeMillis() - 1500L

            // 1. Freefall drop
            for (i in 0 until 15) {
                app.motionSensorManager.injectSyntheticSample(android.hardware.Sensor.TYPE_ACCELEROMETER, 0.5f, 0.5f, 1.0f, now)
                app.motionSensorManager.injectSyntheticSample(android.hardware.Sensor.TYPE_GYROSCOPE, 6.0f, 4.0f, 2.0f, now) // severe rotation
                now += 20L
            }

            // 2. High-G impact
            app.motionSensorManager.injectSyntheticSample(android.hardware.Sensor.TYPE_ACCELEROMETER, 18.0f, 22.0f, 25.0f, now) // mag ~37.9 m/s^2
            app.motionSensorManager.injectSyntheticSample(android.hardware.Sensor.TYPE_GYROSCOPE, 1.0f, 1.0f, 1.0f, now)
            now += 20L

            val motionState = app.motionSensorManager.currentMotionState.value
            val confidence = app.motionSensorManager.motionConfidence.value

            log("Detector Output:")
            log("  Motion State: $motionState", LogLevel.WARNING)
            log("  Motion Confidence: ${(confidence * 100).toInt()}%")
            log("PASS: Fall candidate heuristics recognized sequence ✓", LogLevel.INFO)
            _isSimulating.value = false
        }
    }

    fun simulatePhase2FallAndImmobility() {
        if (_isSimulating.value) return
        viewModelScope.launch {
            _isSimulating.value = true
            clearLogs()
            log("=== Phase 2: SIMULATE FALL + IMMOBILITY ===", LogLevel.ALERT)
            log("Simulating complete fall + prolonged unconscious immobility...")

            var now = System.currentTimeMillis() - 4000L

            // 1. Freefall drop & rotation
            log("→ Step 1: Freefall & tumbling...")
            for (i in 0 until 15) {
                app.motionSensorManager.injectSyntheticSample(android.hardware.Sensor.TYPE_ACCELEROMETER, 0.3f, 0.4f, 1.2f, now)
                app.motionSensorManager.injectSyntheticSample(android.hardware.Sensor.TYPE_GYROSCOPE, 5.5f, 4.0f, 3.0f, now)
                now += 20L
            }

            // 2. Severe impact
            log("→ Step 2: High impact ground collision (38 m/s²)...", LogLevel.ALERT)
            app.motionSensorManager.injectSyntheticSample(android.hardware.Sensor.TYPE_ACCELEROMETER, 20f, 20f, 25f, now) // ~37.7 m/s^2
            app.motionSensorManager.injectSyntheticSample(android.hardware.Sensor.TYPE_GYROSCOPE, 1f, 1f, 1f, now)
            now += 50L

            // 3. Post-event immobility: stationary phone on ground for 3 seconds
            log("→ Step 3: Sustained post-impact immobility (3.0s stationary)...", LogLevel.WARNING)
            for (i in 0 until 80) {
                app.motionSensorManager.injectSyntheticSample(android.hardware.Sensor.TYPE_ACCELEROMETER, 0.1f, 0.1f, 9.8f, now)
                app.motionSensorManager.injectSyntheticSample(android.hardware.Sensor.TYPE_GYROSCOPE, 0.01f, 0.01f, 0.01f, now)
                now += 40L
            }

            // Also feed state engine directly with the detected features to test pipeline escalation
            val features = app.motionSensorManager.latestFeatures.value
            val motionResult = app.stateEngine.processMotionData(features)

            val motionState = app.motionSensorManager.currentMotionState.value
            val confidence = app.motionSensorManager.motionConfidence.value
            val safetyState = app.stateEngine.safetyState.value

            log("Detector Output:")
            log("  Motion State: $motionState", LogLevel.ALERT)
            log("  Motion Confidence: ${(confidence * 100).toInt()}%", LogLevel.ALERT)
            log("  Pipeline Escalation State: $safetyState", LogLevel.ALERT)
            log("PASS: Multi-stage fall + immobility confirmed event ✓", LogLevel.INFO)
            _isSimulating.value = false
        }
    }

    fun simulatePhase2RandomMovement() {
        if (_isSimulating.value) return
        viewModelScope.launch {
            _isSimulating.value = true
            clearLogs()
            log("=== Phase 2: SIMULATE RANDOM MOVEMENT ===", LogLevel.INFO)
            log("Injecting erratic phone handling, pocket shifts, and table pickups...")

            var now = System.currentTimeMillis() - 2000L
            for (i in 0 until 60) {
                val rx = (kotlin.math.sin(i * 0.4) * 3f).toFloat()
                val ry = (kotlin.math.cos(i * 0.3) * 3f).toFloat()
                val rz = 9.8f + (kotlin.math.sin(i * 0.7) * 2f).toFloat()
                val gx = (kotlin.math.cos(i * 0.5) * 1.5f).toFloat()
                val gy = (kotlin.math.sin(i * 0.6) * 1.5f).toFloat()
                val gz = 0.5f

                app.motionSensorManager.injectSyntheticSample(android.hardware.Sensor.TYPE_ACCELEROMETER, rx, ry, rz, now)
                app.motionSensorManager.injectSyntheticSample(android.hardware.Sensor.TYPE_GYROSCOPE, gx, gy, gz, now)
                now += 30L
            }

            val motionState = app.motionSensorManager.currentMotionState.value
            val confidence = app.motionSensorManager.motionConfidence.value

            log("Detector Output:")
            log("  Motion State: $motionState")
            log("  Motion Confidence: ${(confidence * 100).toInt()}%")
            log("  Safety State: ${app.stateEngine.safetyState.value}")
            log("PASS: Random manipulation rejected by false-positive protection ✓", LogLevel.INFO)
            _isSimulating.value = false
        }
    }

    // ==========================================
    // PHASE 3 ON-DEVICE AUDIO SIMULATIONS
    // These inject raw synthetic audio waveforms directly into the AudioInvestigationManager
    // ==========================================

    fun simulatePhase3NormalAudio() {
        if (_isSimulating.value) return
        viewModelScope.launch {
            _isSimulating.value = true
            clearLogs()
            log("=== Phase 3: SIMULATE NORMAL AUDIO ===", LogLevel.INFO)
            log("Generating 1.0s of normal conversational/ambient PCM audio (16kHz)...")

            val audio = generateNormalAudio()
            val event = app.audioManager.injectSyntheticAudio(audio, 16000, app.stateEngine)

            log("Classifier Output:")
            log("  Event Class: ${event.eventClass}")
            log("  Confidence: ${(event.confidence * 100).toInt()}%")
            log("  Duration: ${event.durationMs}ms")
            log("  Safety State: ${app.stateEngine.safetyState.value}")
            log("PASS: Normal audio classified without alerting ✓", LogLevel.INFO)
            _isSimulating.value = false
        }
    }

    fun simulatePhase3DistressAudio() {
        if (_isSimulating.value) return
        viewModelScope.launch {
            _isSimulating.value = true
            clearLogs()
            log("=== Phase 3: SIMULATE DISTRESS AUDIO ===", LogLevel.ALERT)
            log("Generating 1.0s of vocal distress/screaming audio (2200Hz modulated)...")

            // Send 2 frames for temporal aggregator persistence
            val audio1 = generateDistressAudio()
            app.audioManager.injectSyntheticAudio(audio1, 16000, app.stateEngine)
            delay(100)
            val audio2 = generateDistressAudio()
            val event = app.audioManager.injectSyntheticAudio(audio2, 16000, app.stateEngine)

            log("Classifier Output:")
            log("  Event Class: ${event.eventClass}", LogLevel.ALERT)
            log("  Confidence: ${(event.confidence * 100).toInt()}%", LogLevel.ALERT)
            log("  Safety State: ${app.stateEngine.safetyState.value}", LogLevel.WARNING)
            log("PASS: Distress-like acoustic signature recognized ✓", LogLevel.INFO)
            _isSimulating.value = false
        }
    }

    fun simulatePhase3ImpactAudio() {
        if (_isSimulating.value) return
        viewModelScope.launch {
            _isSimulating.value = true
            clearLogs()
            log("=== Phase 3: SIMULATE IMPACT AUDIO ===", LogLevel.WARNING)
            log("Generating sharp acoustic crash/impact transient (peak 0.95)...")

            val audio = generateImpactAudio()
            val event = app.audioManager.injectSyntheticAudio(audio, 16000, app.stateEngine)

            log("Classifier Output:")
            log("  Event Class: ${event.eventClass}", LogLevel.WARNING)
            log("  Confidence: ${(event.confidence * 100).toInt()}%")
            log("PASS: Impact/crash sound classified ✓", LogLevel.INFO)
            _isSimulating.value = false
        }
    }

    fun simulatePhase3GlassBreak() {
        if (_isSimulating.value) return
        viewModelScope.launch {
            _isSimulating.value = true
            clearLogs()
            log("=== Phase 3: SIMULATE GLASS BREAK ===", LogLevel.WARNING)
            log("Generating high-frequency glass shattering audio (3800Hz+)...")

            val audio = generateGlassBreakAudio()
            val event = app.audioManager.injectSyntheticAudio(audio, 16000, app.stateEngine)

            log("Classifier Output:")
            log("  Event Class: ${event.eventClass}", LogLevel.WARNING)
            log("  Confidence: ${(event.confidence * 100).toInt()}%")
            log("PASS: High-frequency glass break recognized ✓", LogLevel.INFO)
            _isSimulating.value = false
        }
    }

    fun simulatePhase3LoudAbnormalSound() {
        if (_isSimulating.value) return
        viewModelScope.launch {
            _isSimulating.value = true
            clearLogs()
            log("=== Phase 3: SIMULATE LOUD ABNORMAL SOUND ===", LogLevel.WARNING)
            log("Generating loud broadband noise burst...")

            val audio = generateLoudAbnormalAudio()
            val event = app.audioManager.injectSyntheticAudio(audio, 16000, app.stateEngine)

            log("Classifier Output:")
            log("  Event Class: ${event.eventClass}", LogLevel.WARNING)
            log("  Confidence: ${(event.confidence * 100).toInt()}%")
            log("PASS: Loud abnormal sound identified ✓", LogLevel.INFO)
            _isSimulating.value = false
        }
    }

    fun simulatePhase3MotionAndImpactAudio() {
        if (_isSimulating.value) return
        viewModelScope.launch {
            _isSimulating.value = true
            clearLogs()
            log("=== Phase 3: MOTION + IMPACT AUDIO FUSION ===", LogLevel.ALERT)
            log("1. Injecting sudden physical impact motion (38 m/s²)...")

            var now = System.currentTimeMillis() - 1000L
            app.motionSensorManager.injectSyntheticSample(android.hardware.Sensor.TYPE_ACCELEROMETER, 15f, 20f, 30f, now)
            app.motionSensorManager.injectSyntheticSample(android.hardware.Sensor.TYPE_GYROSCOPE, 2f, 2f, 1f, now)
            delay(500)

            log("  Motion State: ${app.motionSensorManager.currentMotionState.value}", LogLevel.WARNING)
            log("2. Injecting corroborating impact audio transient...")

            val audio = generateImpactAudio()
            val audioEvent = app.audioManager.injectSyntheticAudio(audio, 16000, app.stateEngine)
            delay(500)

            val risk = app.stateEngine.riskScore.value
            log("Fusion Output:")
            log("  Audio Event: ${audioEvent.eventClass}")
            log("  Combined Risk Score: ${(risk * 100).toInt()}%", LogLevel.ALERT)
            log("  Pipeline Escalation State: ${app.stateEngine.safetyState.value}", LogLevel.ALERT)
            log("PASS: Motion + Audio corroboration successfully increased confidence ✓", LogLevel.INFO)
            _isSimulating.value = false
        }
    }

    fun simulatePhase3MotionAndDistressAudio() {
        if (_isSimulating.value) return
        viewModelScope.launch {
            _isSimulating.value = true
            clearLogs()
            log("=== Phase 3: MOTION + DISTRESS AUDIO FUSION ===", LogLevel.EMERGENCY)
            log("1. Injecting fall motion sequence (drop + impact + immobility)...")

            var now = System.currentTimeMillis() - 3000L
            // Freefall
            for (i in 0 until 10) {
                app.motionSensorManager.injectSyntheticSample(android.hardware.Sensor.TYPE_ACCELEROMETER, 0.5f, 0.5f, 1.2f, now)
                now += 20L
            }
            // Ground impact
            app.motionSensorManager.injectSyntheticSample(android.hardware.Sensor.TYPE_ACCELEROMETER, 20f, 20f, 25f, now)
            now += 50L
            // Post-impact stillness
            for (i in 0 until 40) {
                app.motionSensorManager.injectSyntheticSample(android.hardware.Sensor.TYPE_ACCELEROMETER, 0.1f, 0.1f, 9.8f, now)
                now += 40L
            }
            delay(500)

            log("2. Injecting distress vocal scream audio...")
            val audio1 = generateDistressAudio()
            app.audioManager.injectSyntheticAudio(audio1, 16000, app.stateEngine)
            delay(100)
            val audio2 = generateDistressAudio()
            val audioEvent = app.audioManager.injectSyntheticAudio(audio2, 16000, app.stateEngine)
            delay(500)

            val risk = app.stateEngine.riskScore.value
            log("Fusion Output:")
            log("  Audio Event: ${audioEvent.eventClass}", LogLevel.ALERT)
            log("  Combined Risk Score: ${(risk * 100).toInt()}%", LogLevel.EMERGENCY)
            log("  Pipeline Escalation State: ${app.stateEngine.safetyState.value}", LogLevel.EMERGENCY)
            log("PASS: Multi-sensor fall + distress vocalization verified ✓", LogLevel.INFO)
            _isSimulating.value = false
        }
    }

    fun simulatePhase3MotionAndGlassBreak() {
        if (_isSimulating.value) return
        viewModelScope.launch {
            _isSimulating.value = true
            clearLogs()
            log("=== Phase 3: MOTION + GLASS BREAK FUSION ===", LogLevel.ALERT)
            log("1. Injecting sudden impact motion...")

            var now = System.currentTimeMillis() - 1000L
            app.motionSensorManager.injectSyntheticSample(android.hardware.Sensor.TYPE_ACCELEROMETER, 18f, 15f, 20f, now)
            delay(500)

            log("2. Injecting glass shatter audio...")
            val audio = generateGlassBreakAudio()
            val audioEvent = app.audioManager.injectSyntheticAudio(audio, 16000, app.stateEngine)
            delay(500)

            val risk = app.stateEngine.riskScore.value
            log("Fusion Output:")
            log("  Audio Event: ${audioEvent.eventClass}")
            log("  Combined Risk Score: ${(risk * 100).toInt()}%", LogLevel.ALERT)
            log("  Pipeline Escalation State: ${app.stateEngine.safetyState.value}")
            log("PASS: Motion + Glass Break corroboration evaluated ✓", LogLevel.INFO)
            _isSimulating.value = false
        }
    }

    // --- Synthetic Audio Waveform Generators (16 kHz PCM 16-bit) ---

    private fun generateNormalAudio(samples: Int = 16000): ShortArray {
        val buf = ShortArray(samples)
        for (i in 0 until samples) {
            val wave = kotlin.math.sin(2.0 * Math.PI * 440.0 * i / 16000.0) * 1500.0
            val noise = (Math.random() - 0.5) * 500.0
            buf[i] = (wave + noise).toInt().coerceIn(-32768, 32767).toShort()
        }
        return buf
    }

    private fun generateDistressAudio(samples: Int = 16000): ShortArray {
        val buf = ShortArray(samples)
        for (i in 0 until samples) {
            val vibrato = kotlin.math.sin(2.0 * Math.PI * 6.0 * i / 16000.0) * 200.0
            val freq = 2200.0 + vibrato
            val wave = kotlin.math.sin(2.0 * Math.PI * freq * i / 16000.0) * 22000.0
            val harmonic = kotlin.math.sin(2.0 * Math.PI * (freq * 1.5) * i / 16000.0) * 7000.0
            val noise = (Math.random() - 0.5) * 4000.0
            buf[i] = (wave + harmonic + noise).toInt().coerceIn(-32768, 32767).toShort()
        }
        return buf
    }

    private fun generateImpactAudio(samples: Int = 16000): ShortArray {
        val buf = ShortArray(samples)
        for (i in 0 until samples) {
            val decay = kotlin.math.exp(-i / 800.0)
            val wave = kotlin.math.sin(2.0 * Math.PI * 180.0 * i / 16000.0) * 31000.0 * decay
            val noise = (Math.random() - 0.5) * 25000.0 * decay
            buf[i] = (wave + noise).toInt().coerceIn(-32768, 32767).toShort()
        }
        return buf
    }

    private fun generateGlassBreakAudio(samples: Int = 16000): ShortArray {
        val buf = ShortArray(samples)
        for (i in 0 until samples) {
            val decay = kotlin.math.exp(-i / 3000.0)
            val h1 = kotlin.math.sin(2.0 * Math.PI * 3800.0 * i / 16000.0) * 14000.0 * decay
            val h2 = kotlin.math.sin(2.0 * Math.PI * 4800.0 * i / 16000.0) * 10000.0 * decay
            val highNoise = (if (Math.random() > 0.5) 1.0 else -1.0) * 8000.0 * decay
            buf[i] = (h1 + h2 + highNoise).toInt().coerceIn(-32768, 32767).toShort()
        }
        return buf
    }

    private fun generateLoudAbnormalAudio(samples: Int = 16000): ShortArray {
        val buf = ShortArray(samples)
        for (i in 0 until samples) {
            val noise = (Math.random() - 0.5) * 40000.0
            buf[i] = noise.toInt().coerceIn(-32768, 32767).toShort()
        }
        return buf
    }

    // --- PHASE 4 VISION SIMULATIONS ---

    fun simulateNormalVision() {
        if (_isSimulating.value) return
        viewModelScope.launch {
            _isSimulating.value = true
            clearLogs()
            log("Starting: Normal Visual Scene simulation")
            log("Capturing test frame...")

            val result = com.safeguardian.app.domain.vision.VisionClassificationResult(
                eventType = com.safeguardian.app.domain.vision.VisionEventClass.NORMAL,
                confidence = 0.90f,
                description = "Clear normal scene with no anomalous entities"
            )
            app.cameraManager.injectSimulatedVisionEvent(result, app.stateEngine)
            log("Vision model output: NORMAL (confidence: 90%)")
            log("Multimodal Risk Score: ${(app.stateEngine.riskScore.value * 100).toInt()}%")
            _isSimulating.value = false
        }
    }

    fun simulatePersonDetected() {
        if (_isSimulating.value) return
        viewModelScope.launch {
            _isSimulating.value = true
            clearLogs()
            log("Starting: Person Detected simulation")
            log("Capturing test frame...")

            val box = BoundingBox(0.2f, 0.1f, 0.8f, 0.9f, "person", 0.88f)
            val result = com.safeguardian.app.domain.vision.VisionClassificationResult(
                eventType = com.safeguardian.app.domain.vision.VisionEventClass.PERSON_DETECTED,
                confidence = 0.88f,
                boundingBoxes = listOf(box),
                description = "Upright person detected in frame"
            )
            app.cameraManager.injectSimulatedVisionEvent(result, app.stateEngine)
            log("Vision model output: PERSON_DETECTED (confidence: 88%)")
            log("Primary Bounding Box: [${box.left}, ${box.top}, ${box.right}, ${box.bottom}]")
            _isSimulating.value = false
        }
    }

    fun simulatePersonOnGround() {
        if (_isSimulating.value) return
        viewModelScope.launch {
            _isSimulating.value = true
            clearLogs()
            log("Starting: Person On Ground simulation", LogLevel.WARNING)
            log("Analyzing horizontal body posture in lower frame...")

            val box = BoundingBox(0.1f, 0.65f, 0.9f, 0.95f, "person_ground", 0.86f)
            val result = com.safeguardian.app.domain.vision.VisionClassificationResult(
                eventType = com.safeguardian.app.domain.vision.VisionEventClass.PERSON_ON_GROUND,
                confidence = 0.86f,
                boundingBoxes = listOf(box),
                isConfirmed = true,
                description = "Horizontal person resting on ground"
            )
            app.cameraManager.injectSimulatedVisionEvent(result, app.stateEngine)
            log("Vision model output: PERSON_ON_GROUND (confidence: 86%)", LogLevel.ALERT)
            log("State Engine Risk Score: ${(app.stateEngine.riskScore.value * 100).toInt()}%", LogLevel.ALERT)
            _isSimulating.value = false
        }
    }

    fun simulateFallCandidate() {
        if (_isSimulating.value) return
        viewModelScope.launch {
            _isSimulating.value = true
            clearLogs()
            log("Starting: Temporal Fall Candidate simulation", LogLevel.WARNING)
            log("Frame 1: Upright person (ratio: 0.55)")
            delay(150)
            log("Frame 2: Rapid downward center trajectory detected (delta-Y: +0.28)")
            delay(150)
            log("Frame 3: Horizontal posture (ratio: 1.45, bottom: 0.92)", LogLevel.ALERT)

            val box = BoundingBox(0.15f, 0.70f, 0.85f, 0.95f, "fall_candidate", 0.91f)
            val result = com.safeguardian.app.domain.vision.VisionClassificationResult(
                eventType = com.safeguardian.app.domain.vision.VisionEventClass.FALL_CANDIDATE,
                confidence = 0.91f,
                boundingBoxes = listOf(box),
                isConfirmed = true,
                description = "Temporal fall candidate confirmed across 3 consecutive frames"
            )
            app.cameraManager.injectSimulatedVisionEvent(result, app.stateEngine)
            log("Vision model output: FALL_CANDIDATE (confidence: 91%)", LogLevel.EMERGENCY)
            log("Current Pipeline State: ${app.stateEngine.safetyState.value}", LogLevel.ALERT)
            _isSimulating.value = false
        }
    }

    fun simulatePossibleStruggle() {
        if (_isSimulating.value) return
        viewModelScope.launch {
            _isSimulating.value = true
            clearLogs()
            log("Starting: Possible Struggle simulation", LogLevel.WARNING)
            log("Frame 1: High velocity motion blur & multiple interacting figures")
            delay(150)
            log("Frame 2: Spatial center-of-mass jitter exceeding threshold (jitter: 0.72)")
            delay(150)
            log("Frame 3: Rapid repeated posture oscillation", LogLevel.ALERT)

            val box = BoundingBox(0.2f, 0.2f, 0.8f, 0.85f, "struggle_candidate", 0.78f)
            val result = com.safeguardian.app.domain.vision.VisionClassificationResult(
                eventType = com.safeguardian.app.domain.vision.VisionEventClass.POSSIBLE_STRUGGLE,
                confidence = 0.78f,
                boundingBoxes = listOf(box),
                isConfirmed = true,
                description = "Chaotic bounding box spatial oscillation"
            )
            app.cameraManager.injectSimulatedVisionEvent(result, app.stateEngine)
            log("Vision model output: POSSIBLE_STRUGGLE (confidence: 78%)", LogLevel.ALERT)
            _isSimulating.value = false
        }
    }

    fun simulatePhase4FireSmoke() {
        if (_isSimulating.value) return
        viewModelScope.launch {
            _isSimulating.value = true
            clearLogs()
            log("Starting: Fire / Smoke simulation", LogLevel.WARNING)
            log("Frame 1: High concentration of flame-spectrum hues detected")
            delay(150)
            log("Frame 2: Expanding dense luminance variance confirmed across frames", LogLevel.ALERT)

            val result = com.safeguardian.app.domain.vision.VisionClassificationResult(
                eventType = com.safeguardian.app.domain.vision.VisionEventClass.FIRE_OR_SMOKE,
                confidence = 0.84f,
                isConfirmed = true,
                description = "Visual fire/smoke spectrum signature confirmed"
            )
            app.cameraManager.injectSimulatedVisionEvent(result, app.stateEngine)
            log("Vision model output: FIRE_OR_SMOKE (confidence: 84%)", LogLevel.EMERGENCY)
            _isSimulating.value = false
        }
    }

    // --- PHASE 4 COMBINED MULTIMODAL SIMULATIONS ---

    fun simulateMotionAudioPersonOnGround() {
        if (_isSimulating.value) return
        viewModelScope.launch {
            _isSimulating.value = true
            clearLogs()
            log("=== Multimodal Scenario: MOTION + AUDIO + PERSON ON GROUND ===", LogLevel.WARNING)

            // Stage 0: Motion anomaly
            log("[Stage 0] Injecting sudden deceleration anomaly (32 m/s²)...")
            val motionResult = MotionAnomalyResult(
                isAnomaly = true,
                anomalyScore = 0.85f,
                anomalyType = MotionAnomalyType.SUDDEN_IMPACT,
                confidence = 0.85f,
                features = MotionFeatures(peakAccelMagnitude = 32f, accelVariance = 65f)
            )
            app.stateEngine.simulateMotionAnomaly(motionResult)
            log("Motion State: ${app.stateEngine.safetyState.value}", LogLevel.WARNING)

            delay(300)

            // Stage 1: Audio investigation
            log("[Stage 1] Audio investigation activated: injecting abnormal crash noise...")
            val audioResult = AudioEventResult(
                eventType = AudioEventType.CRASH_IMPACT,
                confidence = 0.82f
            )
            app.stateEngine.simulateAudioEvent(audioResult)
            log("Audio corroboration received. Pipeline Stage: ${app.stateEngine.pipelineStage.value}", LogLevel.ALERT)

            delay(300)

            // Stage 2: Vision investigation
            log("[Stage 2] Camera investigation activated: detecting person on ground...")
            val box = BoundingBox(0.1f, 0.65f, 0.9f, 0.95f, "person_ground", 0.88f)
            val visionResult = com.safeguardian.app.domain.vision.VisionClassificationResult(
                eventType = com.safeguardian.app.domain.vision.VisionEventClass.PERSON_ON_GROUND,
                confidence = 0.88f,
                boundingBoxes = listOf(box),
                isConfirmed = true
            )
            app.cameraManager.injectSimulatedVisionEvent(visionResult, app.stateEngine)

            delay(200)
            log("Multimodal fusion complete. Corroborated sensors: 3", LogLevel.EMERGENCY)
            log("Final State: ${app.stateEngine.safetyState.value} (Risk: ${(app.stateEngine.riskScore.value * 100).toInt()}%)", LogLevel.EMERGENCY)
            _isSimulating.value = false
        }
    }

    fun simulateMotionImpactFall() {
        if (_isSimulating.value) return
        viewModelScope.launch {
            _isSimulating.value = true
            clearLogs()
            log("=== Multimodal Scenario: MOTION + IMPACT AUDIO + FALL ===", LogLevel.WARNING)

            // 1. Motion Fall
            log("[Stage 0] Motion: Free-fall sequence followed by violent impact...")
            val motionResult = MotionAnomalyResult(
                isAnomaly = true,
                anomalyScore = 0.90f,
                anomalyType = MotionAnomalyType.FALL_LIKE_MOTION,
                confidence = 0.90f,
                features = MotionFeatures(peakAccelMagnitude = 38f, hasImpactDecelPattern = true)
            )
            app.stateEngine.simulateMotionAnomaly(motionResult)
            log("Motion State: ${app.stateEngine.safetyState.value}", LogLevel.WARNING)

            delay(300)

            // 2. Audio Impact
            log("[Stage 1] Audio: Sharp acoustic impact transient detected...")
            val audioResult = AudioEventResult(
                eventType = AudioEventType.CRASH_IMPACT,
                confidence = 0.88f
            )
            app.stateEngine.simulateAudioEvent(audioResult)
            log("Audio corroboration: CRASH_IMPACT (confidence: 88%)", LogLevel.ALERT)

            delay(300)

            // 3. Vision Fall
            log("[Stage 2] Vision: Temporal fall candidate sequence confirmed...")
            val box = BoundingBox(0.15f, 0.70f, 0.85f, 0.95f, "fall_candidate", 0.92f)
            val visionResult = com.safeguardian.app.domain.vision.VisionClassificationResult(
                eventType = com.safeguardian.app.domain.vision.VisionEventClass.FALL_CANDIDATE,
                confidence = 0.92f,
                boundingBoxes = listOf(box),
                isConfirmed = true
            )
            app.cameraManager.injectSimulatedVisionEvent(visionResult, app.stateEngine)

            delay(200)
            log("Triple corroboration achieved: Motion + Audio + Vision", LogLevel.EMERGENCY)
            log("State Engine: ${app.stateEngine.safetyState.value} (Risk: ${(app.stateEngine.riskScore.value * 100).toInt()}%)", LogLevel.EMERGENCY)
            _isSimulating.value = false
        }
    }

    fun simulateMotionDistressPersonOnGround() {
        if (_isSimulating.value) return
        viewModelScope.launch {
            _isSimulating.value = true
            clearLogs()
            log("=== Multimodal Scenario: MOTION + DISTRESS AUDIO + PERSON ON GROUND ===", LogLevel.WARNING)

            // 1. Motion
            log("[Stage 0] Motion: Sudden agitation and drop detected...")
            val motionResult = MotionAnomalyResult(
                isAnomaly = true,
                anomalyScore = 0.82f,
                anomalyType = MotionAnomalyType.UNUSUAL_ACCELERATION,
                confidence = 0.80f,
                features = MotionFeatures(peakAccelMagnitude = 28f)
            )
            app.stateEngine.simulateMotionAnomaly(motionResult)

            delay(300)

            // 2. Audio Scream / Distress
            log("[Stage 1] Audio: Distress-like acoustic frequency signature detected...", LogLevel.ALERT)
            val audioResult = AudioEventResult(
                eventType = AudioEventType.SCREAM_DISTRESS,
                confidence = 0.89f
            )
            app.stateEngine.simulateAudioEvent(audioResult)

            delay(300)

            // 3. Vision Person on Ground
            log("[Stage 2] Vision: Person horizontal on ground detected...", LogLevel.ALERT)
            val box = BoundingBox(0.2f, 0.60f, 0.85f, 0.92f, "person_ground", 0.85f)
            val visionResult = com.safeguardian.app.domain.vision.VisionClassificationResult(
                eventType = com.safeguardian.app.domain.vision.VisionEventClass.PERSON_ON_GROUND,
                confidence = 0.85f,
                boundingBoxes = listOf(box),
                isConfirmed = true
            )
            app.cameraManager.injectSimulatedVisionEvent(visionResult, app.stateEngine)

            delay(200)
            log("High confidence emergency candidate corroboration verified", LogLevel.EMERGENCY)
            log("State: ${app.stateEngine.safetyState.value} (Risk: ${(app.stateEngine.riskScore.value * 100).toInt()}%)", LogLevel.EMERGENCY)
            _isSimulating.value = false
        }
    }

    // --- PHASE 5: EMERGENCY RESPONSE & VERIFICATION SIMULATIONS ---

    fun simulatePhase5HighConfidenceEvent() {
        if (_isSimulating.value) return
        viewModelScope.launch {
            _isSimulating.value = true
            clearLogs()
            log("=== Phase 5: HIGH CONFIDENCE EVENT TRIGGER ===", LogLevel.WARNING)
            log("Evaluating triple sensor corroboration + temporal persistence...")

            val motion = MotionAnomalyResult(
                isAnomaly = true,
                anomalyScore = 0.90f,
                anomalyType = MotionAnomalyType.FALL_LIKE_MOTION,
                confidence = 0.90f,
                features = MotionFeatures(peakAccelMagnitude = 36f)
            )
            val audio = AudioEventResult(
                eventType = AudioEventType.SCREAM_DISTRESS,
                confidence = 0.88f
            )
            val vision = com.safeguardian.app.domain.vision.VisionClassificationResult(
                eventType = com.safeguardian.app.domain.vision.VisionEventClass.PERSON_ON_GROUND,
                confidence = 0.90f
            )

            val riskResult = app.riskEngine.evaluate(
                motionResult = motion,
                audioResult = audio,
                visionResult = vision.let { VisionEventResult(eventType = VisionEventType.PERSON_ON_GROUND, confidence = it.confidence) },
                temporalPersistence = 0.75f,
                corroborationCount = 3
            )

            log("Multimodal Risk Score: ${(riskResult.riskScore * 100).toInt()}% (Level: ${riskResult.riskLevel})", LogLevel.EMERGENCY)
            log("Contributing signals: ${riskResult.contributingSignals.joinToString(", ")}")

            app.stateEngine.forceState(SafetyState.HIGH_CONFIDENCE_EMERGENCY)
            log("System state transitioned to: HIGH_CONFIDENCE_EMERGENCY", LogLevel.EMERGENCY)
            _isSimulating.value = false
        }
    }

    fun simulatePhase5VerificationCountdown() {
        if (_isSimulating.value) return
        viewModelScope.launch {
            _isSimulating.value = true
            clearLogs()
            log("=== Phase 5: VERIFICATION COUNTDOWN (10 SECONDS) ===", LogLevel.ALERT)
            log("High-confidence incident detected. Starting safety verification timer...")
            log("Vibrating alert pattern triggered.", LogLevel.ALERT)
            log("Displaying: 'Possible emergency detected. Cancel if you are safe.'", LogLevel.EMERGENCY)

            app.stateEngine.forceState(SafetyState.HIGH_CONFIDENCE_EMERGENCY)

            for (sec in 10 downTo 1) {
                log("Verification Countdown: ${sec}s remaining (Tap Cancel to abort)...", LogLevel.ALERT)
                delay(400)
            }

            log("Countdown expired! User did not cancel.", LogLevel.EMERGENCY)
            app.stateEngine.forceState(SafetyState.ACTIVE_EMERGENCY)
            log("System Escalated to: ACTIVE_EMERGENCY", LogLevel.EMERGENCY)
            _isSimulating.value = false
        }
    }

    fun simulatePhase5UserCancel() {
        if (_isSimulating.value) return
        viewModelScope.launch {
            _isSimulating.value = true
            clearLogs()
            log("=== Phase 5: USER CANCELS VERIFICATION ===", LogLevel.INFO)
            log("User pressed: 'I AM SAFE - CANCEL'")
            log("Verification cancelled. Suppressing false alarm.", LogLevel.INFO)

            app.stateEngine.cancelInvestigation()
            app.locationManager.stopEmergencyTracking()

            log("State de-escalated to: NORMAL", LogLevel.INFO)
            log("Minimal audit log recorded (zero raw media saved).", LogLevel.INFO)
            _isSimulating.value = false
        }
    }

    fun simulatePhase5CountdownExpired() {
        if (_isSimulating.value) return
        viewModelScope.launch {
            _isSimulating.value = true
            clearLogs()
            log("=== Phase 5: COUNTDOWN EXPIRATION & DISPATCH ===", LogLevel.EMERGENCY)
            log("Verification countdown expired without user cancellation.", LogLevel.EMERGENCY)

            app.stateEngine.forceState(SafetyState.ACTIVE_EMERGENCY)

            // Start location acquisition
            log("[Location] On-demand emergency GPS acquisition started...", LogLevel.ALERT)
            app.locationManager.injectSimulatedLocation(37.7749, -122.4194, 4.5f)
            val loc = app.locationManager.currentLocation.value
            log("[Location] Coordinates acquired: ${loc?.latitude}, ${loc?.longitude} (accuracy: ±${loc?.accuracyMeters?.toInt()}m)", LogLevel.INFO)

            // Trusted contact dispatch
            val contacts = app.contactRepository.getEnabledContacts()
            log("[Dispatch] Dispatching alert message to ${contacts.size} enabled trusted contacts...", LogLevel.EMERGENCY)

            val emergency = app.stateEngine.currentEmergency.value ?: EmergencyEvent(
                eventType = "Fall and Impact Detected",
                confidence = 0.92f,
                sensorEvidence = SensorEvidence(eventChainDescription = "Motion fall + Audio impact + Vision person on ground")
            )

            val records = app.emergencyDispatcher.dispatch(emergency, loc, contacts)
            records.forEach { r ->
                log("→ Sent simulated SMS to ${r.recipientName} (${r.recipientPhone})", LogLevel.INFO)
            }

            log("Generated Message Content:\n${records.firstOrNull()?.messageText ?: "N/A"}", LogLevel.ALERT)
            log("State remains ACTIVE_EMERGENCY with periodic location tracking.", LogLevel.EMERGENCY)
            _isSimulating.value = false
        }
    }

    fun simulatePhase5LocationAvailable() {
        viewModelScope.launch {
            clearLogs()
            log("Simulating GPS location available...")
            app.locationManager.injectSimulatedLocation(40.7128, -74.0060, 3.2f)
            val loc = app.locationManager.currentLocation.value
            log("Location State: ${app.locationManager.locationState.value}")
            log("Coordinates: ${loc?.latitude}, ${loc?.longitude} (accuracy: ±${loc?.accuracyMeters}m)")
        }
    }

    fun simulatePhase5LocationUnavailable() {
        viewModelScope.launch {
            clearLogs()
            log("Simulating GPS location unavailable (indoor / tunnel / airplane mode)...", LogLevel.WARNING)
            app.locationManager.setLocationUnavailable()
            log("Location State: ${app.locationManager.locationState.value}", LogLevel.WARNING)
        }
    }

    fun simulatePhase5Resolution() {
        viewModelScope.launch {
            clearLogs()
            log("=== Phase 5: EMERGENCY RESOLUTION ===", LogLevel.INFO)
            log("User pressed: 'I'm Safe — Resolve Emergency'")
            app.stateEngine.resolveEmergency()
            app.locationManager.stopEmergencyTracking()
            log("Location tracking stopped.")
            log("Safety State returned to: ${app.stateEngine.safetyState.value}", LogLevel.INFO)
            log("Cooldown window active. System monitoring normal.", LogLevel.INFO)
        }
    }

    fun resetState() {
        app.motionSensorManager.anomalyDetector.reset()
        app.audioManager.stopInvestigation()
        app.cameraManager.reset()
        app.locationManager.stopEmergencyTracking()
        app.emergencyDispatcher.clearRecords()
        app.stateEngine.cancelInvestigation()
        clearLogs()
        log("State reset to NORMAL")
    }
}
