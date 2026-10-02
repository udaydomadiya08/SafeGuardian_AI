package com.safeguardian.app.presentation.screens.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.safeguardian.app.SafeGuardianApp
import com.safeguardian.app.data.audio.MicrophoneStatus
import com.safeguardian.app.domain.audio.AudioEvent
import com.safeguardian.app.domain.model.*
import com.safeguardian.app.domain.motion.MotionEvent
import com.safeguardian.app.domain.motion.MotionState
import kotlinx.coroutines.flow.StateFlow

class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as SafeGuardianApp

    // Overall pipeline state
    val safetyState: StateFlow<SafetyState> = app.stateEngine.safetyState
    val pipelineStage: StateStage = app.stateEngine.pipelineStage
    val riskScore: StateFlow<Float> = app.stateEngine.riskScore

    // Sensor status
    val isMonitoring: StateFlow<Boolean> = app.motionSensorManager.isMonitoring
    val accelStatus: StateFlow<SensorStatus> = app.motionSensorManager.accelStatus
    val gyroStatus: StateFlow<SensorStatus> = app.motionSensorManager.gyroStatus

    // Phase 2 Motion Intelligence live telemetry
    val currentAccelMagnitude: StateFlow<Float> = app.motionSensorManager.currentAccelMagnitude
    val currentGyroMagnitude: StateFlow<Float> = app.motionSensorManager.currentGyroMagnitude
    val currentMotionState: StateFlow<MotionState> = app.motionSensorManager.currentMotionState
    val motionConfidence: StateFlow<Float> = app.motionSensorManager.motionConfidence
    val lastDetectedEvent: StateFlow<MotionEvent?> = app.motionSensorManager.lastDetectedEvent
    val peakAccelMagnitude: StateFlow<Float> = app.motionSensorManager.peakAccelMagnitude
    val peakGyroMagnitude: StateFlow<Float> = app.motionSensorManager.peakGyroMagnitude
    val samplingRate: StateFlow<Float> = app.motionSensorManager.currentSamplingRateHz
    val estimatedProcessingTimeMs: StateFlow<Float> = app.motionSensorManager.estimatedProcessingTimeMs

    // Phase 3 Audio Intelligence live telemetry
    val microphoneStatus: StateFlow<MicrophoneStatus> = app.audioManager.microphoneStatus
    val audioInferenceCount: StateFlow<Int> = app.audioManager.audioInferenceCount
    val lastAudioEvent: StateFlow<AudioEvent?> = app.audioManager.lastAudioEvent
    val audioConfidence: StateFlow<Float> = app.audioManager.audioConfidence

    // Phase 4 Computer Vision live telemetry
    val cameraStatus: StateFlow<com.safeguardian.app.data.vision.CameraStatus> = app.cameraManager.cameraStatus
    val framesAnalyzed: StateFlow<Int> = app.cameraManager.framesAnalyzed
    val visionAvgInferenceTimeMs: StateFlow<Float> = app.cameraManager.averageInferenceTimeMs
    val lastVisionClassification: StateFlow<com.safeguardian.app.domain.vision.VisionClassificationResult?> = app.cameraManager.lastVisionResult
    val visionConfidence: StateFlow<Float> = app.cameraManager.visionConfidence
    val visionInvestigationDurationMs: StateFlow<Long> = app.cameraManager.investigationDurationMs

    // Phase 5 Emergency Response & Verification live telemetry
    val locationState: StateFlow<com.safeguardian.app.domain.response.LocationState> = app.locationManager.locationState
    val currentLocation: StateFlow<com.safeguardian.app.domain.response.GeoLocation?> = app.locationManager.currentLocation
    val contactsList = app.contactRepository.contacts
    val dispatcherName: String = app.emergencyDispatcher.dispatcherName
    val dispatchedRecords = app.emergencyDispatcher.dispatchedRecords

    // Historical features & diagnostic results
    val latestFeatures: StateFlow<MotionFeatures> = app.motionSensorManager.latestFeatures
    val lastMotionResult = app.stateEngine.lastMotionResult
    val lastAudioResult = app.stateEngine.lastAudioResult
    val lastVisionResult = app.stateEngine.lastVisionResult
    val lastFusionResult = app.stateEngine.lastFusionResult

    // Phase 6 Model Intelligence & Benchmarking
    val motionModelName = app.motionMLModel.modelName
    val isMotionRealML = app.motionMLModel.isModelAvailable
    val audioModelName = app.audioMLModel.modelName
    val isAudioRealML = app.audioMLModel.isModelAvailable
    val visionModelName = app.visionMLModel.modelName
    val isVisionRealML = app.visionMLModel.isModelAvailable

    val motionMetadata = app.artifactProvider.getModelMetadata("motion_model.tflite")
    val audioMetadata = app.artifactProvider.getModelMetadata("audio_model.tflite")
    val visionMetadata = app.artifactProvider.getModelMetadata("vision_model.tflite")

    fun runBenchmarks(): List<com.safeguardian.app.domain.ai.ml.ModelBenchmarkResult> {
        return listOf(
            app.benchmarkRunner.benchmarkMotion(iterations = 100),
            app.benchmarkRunner.benchmarkAudio(iterations = 100),
            app.benchmarkRunner.benchmarkVision(iterations = 50)
        )
    }
}

typealias StateStage = StateFlow<PipelineStage>
