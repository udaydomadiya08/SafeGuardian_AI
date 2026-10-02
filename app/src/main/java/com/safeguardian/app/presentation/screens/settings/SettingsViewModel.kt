package com.safeguardian.app.presentation.screens.settings

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import com.safeguardian.app.SafeGuardianApp
import com.safeguardian.app.domain.model.GuardianConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CapabilityInfo(
    val name: String,
    val isAvailable: Boolean,
    val detail: String = ""
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as SafeGuardianApp

    val config: GuardianConfig get() = app.config

    private val _capabilities = MutableStateFlow<List<CapabilityInfo>>(emptyList())
    val capabilities: StateFlow<List<CapabilityInfo>> = _capabilities.asStateFlow()

    val currentSamplingRateHz: StateFlow<Float> = app.motionSensorManager.currentSamplingRateHz

    private val _impactThreshold = MutableStateFlow(app.motionSensorManager.anomalyDetector.impactThreshold / 9.80665f)
    val impactThreshold: StateFlow<Float> = _impactThreshold.asStateFlow()

    private val _rotationThreshold = MutableStateFlow(app.motionSensorManager.anomalyDetector.rotationThreshold)
    val rotationThreshold: StateFlow<Float> = _rotationThreshold.asStateFlow()

    private val _cooldownDuration = MutableStateFlow(app.motionSensorManager.anomalyDetector.cooldownDurationMs)
    val cooldownDuration: StateFlow<Long> = _cooldownDuration.asStateFlow()

    fun setSamplingRate(rateHz: Float) {
        app.motionSensorManager.setSamplingRate(rateHz)
    }

    fun setImpactThreshold(g: Float) {
        val newG = g.coerceIn(1.5f, 6.0f)
        _impactThreshold.value = newG
        app.motionSensorManager.anomalyDetector.impactThreshold = newG * 9.80665f
    }

    fun setRotationThreshold(radS: Float) {
        val newRadS = radS.coerceIn(2.0f, 10.0f)
        _rotationThreshold.value = newRadS
        app.motionSensorManager.anomalyDetector.rotationThreshold = newRadS
    }

    fun setCooldownDuration(ms: Long) {
        val newMs = ms.coerceIn(1000L, 30_000L)
        _cooldownDuration.value = newMs
        app.motionSensorManager.anomalyDetector.cooldownDurationMs = newMs
    }

    val contacts = app.contactRepository.contacts

    fun addContact(name: String, phone: String, relationship: String) {
        if (name.isBlank() || phone.isBlank()) return
        app.contactRepository.addContact(
            com.safeguardian.app.domain.model.EmergencyContact(
                name = name.trim(),
                phone = phone.trim(),
                relationship = relationship.trim(),
                isEnabled = true
            )
        )
    }

    fun deleteContact(id: String) {
        app.contactRepository.deleteContact(id)
    }

    fun toggleContact(id: String, enabled: Boolean) {
        app.contactRepository.setContactEnabled(id, enabled)
    }

    fun checkCapabilities(context: Context) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

        val caps = buildList {
            add(CapabilityInfo(
                "Accelerometer",
                sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null,
                "Required for motion anomaly detection"
            ))
            add(CapabilityInfo(
                "Gyroscope",
                sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null,
                "Required for fall/rotation detection"
            ))
            add(CapabilityInfo(
                "Microphone Permission",
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED,
                "Required for audio event classification (Phase 3)"
            ))
            add(CapabilityInfo(
                "Camera Permission",
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
                "Required for visual event confirmation (Phase 4)"
            ))
            add(CapabilityInfo(
                "Location Permission",
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED,
                "Required for emergency location reporting (Phase 5)"
            ))
            add(CapabilityInfo(
                "Notification Permission",
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                } else true,
                "Required for foreground service & alerts"
            ))
        }

        _capabilities.value = caps
    }
}
