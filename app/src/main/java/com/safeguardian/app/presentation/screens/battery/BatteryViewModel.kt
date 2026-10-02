package com.safeguardian.app.presentation.screens.battery

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.lifecycle.AndroidViewModel
import com.safeguardian.app.SafeGuardianApp
import com.safeguardian.app.domain.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DeviceBatteryInfo(
    val level: Int = -1,
    val isCharging: Boolean = false,
    val temperature: Float = 0f
)

class BatteryViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as SafeGuardianApp

    val pipelineStage = app.stateEngine.pipelineStage
    val samplingRate = app.motionSensorManager.currentSamplingRateHz
    val safetyState = app.stateEngine.safetyState
    val riskScore = app.stateEngine.riskScore

    private val _batteryInfo = MutableStateFlow(DeviceBatteryInfo())
    val batteryInfo: StateFlow<DeviceBatteryInfo> = _batteryInfo.asStateFlow()

    fun refreshBatteryInfo(context: Context) {
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { filter ->
            context.registerReceiver(null, filter)
        }

        val level = batteryStatus?.let { intent ->
            val lvl = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (lvl >= 0 && scale > 0) (lvl * 100 / scale) else -1
        } ?: -1

        val isCharging = batteryStatus?.let { intent ->
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        } ?: false

        val temperature = batteryStatus?.let { intent ->
            intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f
        } ?: 0f

        _batteryInfo.value = DeviceBatteryInfo(
            level = level,
            isCharging = isCharging,
            temperature = temperature
        )
    }

    fun getEstimatedImpact(stage: PipelineStage): BatteryImpact {
        return when (stage) {
            PipelineStage.IDLE -> BatteryImpact.MINIMAL
            PipelineStage.MOTION_ANALYSIS -> BatteryImpact.LOW
            PipelineStage.AUDIO_ANALYSIS -> BatteryImpact.MODERATE
            PipelineStage.VISION_ANALYSIS -> BatteryImpact.HIGH
            PipelineStage.MULTIMODAL_FUSION -> BatteryImpact.HIGH
            PipelineStage.EMERGENCY_RESPONSE -> BatteryImpact.VERY_HIGH
        }
    }
}
