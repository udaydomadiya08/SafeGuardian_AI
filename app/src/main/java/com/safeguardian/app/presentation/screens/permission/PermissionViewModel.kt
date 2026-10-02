package com.safeguardian.app.presentation.screens.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PermissionItem(
    val permission: String,
    val title: String,
    val description: String,
    val isRequired: Boolean,
    val isGranted: Boolean = false
)

data class PermissionUiState(
    val permissions: List<PermissionItem> = emptyList(),
    val allRequiredGranted: Boolean = false,
    val currentStep: Int = 0
)

class PermissionViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(PermissionUiState())
    val uiState: StateFlow<PermissionUiState> = _uiState.asStateFlow()

    fun checkPermissions(context: Context) {
        val permissions = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(
                    PermissionItem(
                        permission = Manifest.permission.POST_NOTIFICATIONS,
                        title = "Notifications",
                        description = "Required to alert you during emergencies and show monitoring status.",
                        isRequired = true,
                        isGranted = isGranted(context, Manifest.permission.POST_NOTIFICATIONS)
                    )
                )
            }
            add(
                PermissionItem(
                    permission = Manifest.permission.RECORD_AUDIO,
                    title = "Microphone",
                    description = "Used to detect distress sounds, crashes, and breaking glass. Audio is processed on-device and never stored or uploaded.",
                    isRequired = false,
                    isGranted = isGranted(context, Manifest.permission.RECORD_AUDIO)
                )
            )
            add(
                PermissionItem(
                    permission = Manifest.permission.CAMERA,
                    title = "Camera",
                    description = "Used to visually confirm emergencies like falls or fires. Camera only activates briefly after other sensors detect anomalies. Images are never stored or uploaded.",
                    isRequired = false,
                    isGranted = isGranted(context, Manifest.permission.CAMERA)
                )
            )
            add(
                PermissionItem(
                    permission = Manifest.permission.ACCESS_FINE_LOCATION,
                    title = "Location",
                    description = "Used to include your location in emergency alerts sent to your contacts. Location is only accessed during confirmed emergencies.",
                    isRequired = false,
                    isGranted = isGranted(context, Manifest.permission.ACCESS_FINE_LOCATION)
                )
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(
                    PermissionItem(
                        permission = Manifest.permission.BODY_SENSORS,
                        title = "Body Sensors",
                        description = "Optional: enables future integration with wearable health sensors for enhanced detection.",
                        isRequired = false,
                        isGranted = isGranted(context, Manifest.permission.BODY_SENSORS)
                    )
                )
            }
        }

        val allRequired = permissions
            .filter { it.isRequired }
            .all { it.isGranted }

        _uiState.value = PermissionUiState(
            permissions = permissions,
            allRequiredGranted = allRequired
        )
    }

    fun onPermissionResult(context: Context) {
        checkPermissions(context)
    }

    private fun isGranted(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun getUngrantedPermissions(): List<String> {
        return _uiState.value.permissions
            .filter { !it.isGranted }
            .map { it.permission }
    }
}
