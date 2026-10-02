package com.safeguardian.app.presentation.screens.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.safeguardian.app.SafeGuardianApp
import com.safeguardian.app.data.service.GuardianForegroundService
import com.safeguardian.app.domain.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MainUiState(
    val isProtectionActive: Boolean = false,
    val safetyState: SafetyState = SafetyState.NORMAL,
    val pipelineStage: PipelineStage = PipelineStage.IDLE,
    val riskScore: Float = 0f,
    val accelStatus: SensorStatus = SensorStatus.OFF,
    val gyroStatus: SensorStatus = SensorStatus.OFF,
    val micStatus: SensorStatus = SensorStatus.STANDBY,
    val cameraStatus: SensorStatus = SensorStatus.OFF,
    val gpsStatus: SensorStatus = SensorStatus.OFF,
    val currentEmergency: EmergencyEvent? = null,
    val isUsingMockModels: Boolean = true
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SafeGuardianApp

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    val safetyState = app.stateEngine.safetyState
    val pipelineStage = app.stateEngine.pipelineStage
    val riskScore = app.stateEngine.riskScore
    val accelStatus = app.motionSensorManager.accelStatus
    val gyroStatus = app.motionSensorManager.gyroStatus
    val currentEmergency = app.stateEngine.currentEmergency

    val isRealML: Boolean = app.motionMLModel.isModelAvailable && app.audioMLModel.isModelAvailable && app.visionMLModel.isModelAvailable
    val motionModelName: String = app.motionMLModel.modelName
    val audioModelName: String = app.audioMLModel.modelName
    val visionModelName: String = app.visionMLModel.modelName

    private val _isProtectionActive = MutableStateFlow(false)
    val isProtectionActive: StateFlow<Boolean> = _isProtectionActive.asStateFlow()

    fun startProtection() {
        GuardianForegroundService.startService(getApplication())
        _isProtectionActive.value = true
    }

    fun stopProtection() {
        GuardianForegroundService.stopService(getApplication())
        _isProtectionActive.value = false
    }

    fun resolveEmergency() {
        app.stateEngine.resolveEmergency()
    }

    fun cancelInvestigation() {
        app.stateEngine.cancelInvestigation()
    }
}
