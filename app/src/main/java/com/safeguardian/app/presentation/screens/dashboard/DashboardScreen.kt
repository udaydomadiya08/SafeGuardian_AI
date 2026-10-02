package com.safeguardian.app.presentation.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.safeguardian.app.domain.model.*
import com.safeguardian.app.domain.motion.MotionState
import com.safeguardian.app.presentation.components.*
import com.safeguardian.app.presentation.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateBack: () -> Unit,
    viewModel: DashboardViewModel = viewModel()
) {
    val safetyState by viewModel.safetyState.collectAsState()
    val pipelineStage by viewModel.pipelineStage.collectAsState()
    val riskScore by viewModel.riskScore.collectAsState()

    // Phase 2 Motion Intelligence states
    val isMonitoring by viewModel.isMonitoring.collectAsState()
    val accelStatus by viewModel.accelStatus.collectAsState()
    val gyroStatus by viewModel.gyroStatus.collectAsState()
    val currentAccelMag by viewModel.currentAccelMagnitude.collectAsState()
    val currentGyroMag by viewModel.currentGyroMagnitude.collectAsState()
    val motionState by viewModel.currentMotionState.collectAsState()
    val motionConfidence by viewModel.motionConfidence.collectAsState()
    val lastEvent by viewModel.lastDetectedEvent.collectAsState()
    val peakAccel by viewModel.peakAccelMagnitude.collectAsState()
    val peakGyro by viewModel.peakGyroMagnitude.collectAsState()
    val samplingRate by viewModel.samplingRate.collectAsState()
    val processingTimeMs by viewModel.estimatedProcessingTimeMs.collectAsState()

    // Phase 3 Audio Intelligence states
    val micStatus by viewModel.microphoneStatus.collectAsState()
    val audioInferenceCount by viewModel.audioInferenceCount.collectAsState()
    val lastAudioEvent by viewModel.lastAudioEvent.collectAsState()
    val audioConfidence by viewModel.audioConfidence.collectAsState()

    // Phase 4 Computer Vision states
    val cameraStatus by viewModel.cameraStatus.collectAsState()
    val framesAnalyzed by viewModel.framesAnalyzed.collectAsState()
    val visionAvgInferenceMs by viewModel.visionAvgInferenceTimeMs.collectAsState()
    val lastVisionClassification by viewModel.lastVisionClassification.collectAsState()
    val visionConfidence by viewModel.visionConfidence.collectAsState()
    val visionInvestigationDurationMs by viewModel.visionInvestigationDurationMs.collectAsState()

    // Phase 5 Emergency Response & Verification states
    val locationState by viewModel.locationState.collectAsState()
    val currentLocation by viewModel.currentLocation.collectAsState()
    val contactsList by viewModel.contactsList.collectAsState()
    val dispatcherName = viewModel.dispatcherName
    val dispatchedRecords by viewModel.dispatchedRecords.collectAsState()

    val features by viewModel.latestFeatures.collectAsState()
    val motionResult by viewModel.lastMotionResult.collectAsState()
    val audioResult by viewModel.lastAudioResult.collectAsState()
    val visionResult by viewModel.lastVisionResult.collectAsState()
    val fusionResult by viewModel.lastFusionResult.collectAsState()

    Scaffold(
        containerColor = GuardianDark,
        topBar = {
            TopAppBar(
                title = {
                    Text("Developer Dashboard", fontWeight = FontWeight.Bold, color = GuardianTextPrimary)
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = GuardianTextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = GuardianDark)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Dev mode banner
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = GuardianYellow.copy(alpha = 0.15f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Code, null, tint = GuardianYellow, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "SafeGuardian Intelligence System — Engineering Prototype (Phases 1–5)",
                        style = MaterialTheme.typography.labelSmall,
                        color = GuardianYellow
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- PHASE 2 LOW-POWER MOTION INTELLIGENCE ---
            StatusCard(title = "MOTION INTELLIGENCE (PHASE 2)") {
                DiagnosticRow(
                    label = "Motion monitoring",
                    value = if (isMonitoring) "ACTIVE" else "INACTIVE",
                    valueColor = if (isMonitoring) GuardianGreen else GuardianTextMuted
                )
                DiagnosticRow(
                    label = "Accelerometer",
                    value = accelStatus.name,
                    valueColor = if (accelStatus == SensorStatus.ACTIVE) GuardianGreen else GuardianTextMuted
                )
                DiagnosticRow(
                    label = "Gyroscope",
                    value = gyroStatus.name,
                    valueColor = if (gyroStatus == SensorStatus.ACTIVE) GuardianGreen else GuardianTextMuted
                )
                DiagnosticRow(
                    label = "Current acceleration magnitude",
                    value = "%.2f m/s²".format(currentAccelMag),
                    valueColor = GuardianBlue
                )
                DiagnosticRow(
                    label = "Current rotation magnitude",
                    value = "%.2f rad/s".format(currentGyroMag),
                    valueColor = GuardianBlue
                )
                DiagnosticRow(
                    label = "Current motion state",
                    value = motionState.name,
                    valueColor = when (motionState) {
                        MotionState.NORMAL -> GuardianGreen
                        MotionState.POSSIBLE_ANOMALY -> GuardianYellow
                        MotionState.IMPACT_DETECTED -> GuardianOrange
                        MotionState.FALL_CANDIDATE -> GuardianRed
                        MotionState.IMMOBILITY_AFTER_EVENT -> StateSuspicious
                        MotionState.MOTION_ANOMALY -> GuardianOrange
                    }
                )
                DiagnosticRow(
                    label = "Motion confidence",
                    value = "${(motionConfidence * 100).toInt()}% (engineering score)",
                    valueColor = if (motionConfidence > 0.6f) GuardianRed else GuardianGreen
                )
                DiagnosticRow(
                    label = "Last detected event",
                    value = lastEvent?.let { "${it.motionState.name} (${it.durationMs}ms)" } ?: "None",
                    valueColor = if (lastEvent != null) GuardianOrange else GuardianTextMuted
                )
                DiagnosticRow(
                    label = "Peak acceleration",
                    value = "%.2f m/s²".format(peakAccel)
                )
                DiagnosticRow(
                    label = "Peak rotation",
                    value = "%.2f rad/s".format(peakGyro)
                )
                DiagnosticRow(
                    label = "Current sampling rate",
                    value = "${samplingRate} Hz"
                )
                DiagnosticRow(
                    label = "Estimated sensor processing activity",
                    value = "%.2f ms / window".format(processingTimeMs),
                    valueColor = GuardianGreen
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- PHASE 3 ON-DEVICE AUDIO INTELLIGENCE ---
            StatusCard(title = "AUDIO INTELLIGENCE (PHASE 3)") {
                DiagnosticRow(
                    label = "MICROPHONE",
                    value = micStatus.name,
                    valueColor = when (micStatus) {
                        com.safeguardian.app.data.audio.MicrophoneStatus.INVESTIGATING -> GuardianRed
                        com.safeguardian.app.data.audio.MicrophoneStatus.OFF -> GuardianTextMuted
                        com.safeguardian.app.data.audio.MicrophoneStatus.UNAVAILABLE -> GuardianYellow
                    }
                )
                DiagnosticRow(
                    label = "Audio inference count",
                    value = "$audioInferenceCount"
                )
                DiagnosticRow(
                    label = "Last audio event",
                    value = lastAudioEvent?.let { "${it.eventClass.name} (${it.durationMs}ms)" } ?: "None",
                    valueColor = if (lastAudioEvent != null && lastAudioEvent?.eventClass != com.safeguardian.app.domain.audio.AudioEventClass.NORMAL) GuardianOrange else GuardianTextMuted
                )
                DiagnosticRow(
                    label = "Audio confidence",
                    value = "${(audioConfidence * 100).toInt()}%",
                    valueColor = if (audioConfidence > 0.5f) GuardianOrange else GuardianGreen
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- PHASE 4 ON-DEVICE COMPUTER VISION INTELLIGENCE ---
            StatusCard(title = "COMPUTER VISION INTELLIGENCE (PHASE 4)") {
                DiagnosticRow(
                    label = "CAMERA",
                    value = cameraStatus.name,
                    valueColor = when (cameraStatus) {
                        com.safeguardian.app.data.vision.CameraStatus.INVESTIGATING -> GuardianRed
                        com.safeguardian.app.data.vision.CameraStatus.OFF -> GuardianTextMuted
                        com.safeguardian.app.data.vision.CameraStatus.UNAVAILABLE -> GuardianYellow
                    }
                )
                DiagnosticRow(
                    label = "Vision model",
                    value = if (cameraStatus == com.safeguardian.app.data.vision.CameraStatus.INVESTIGATING) "RUNNING" else "STANDBY",
                    valueColor = if (cameraStatus == com.safeguardian.app.data.vision.CameraStatus.INVESTIGATING) GuardianGreen else GuardianTextMuted
                )
                DiagnosticRow(
                    label = "Frames analyzed",
                    value = "$framesAnalyzed"
                )
                DiagnosticRow(
                    label = "Average inference time",
                    value = "%.1f ms".format(visionAvgInferenceMs),
                    valueColor = GuardianGreen
                )
                DiagnosticRow(
                    label = "Last visual event",
                    value = lastVisionClassification?.let { "${it.eventType.name} (${(it.confidence * 100).toInt()}%)" } ?: "None",
                    valueColor = if (lastVisionClassification != null && lastVisionClassification?.eventType != com.safeguardian.app.domain.vision.VisionEventClass.NORMAL) GuardianOrange else GuardianTextMuted
                )
                DiagnosticRow(
                    label = "Vision confidence",
                    value = "${(visionConfidence * 100).toInt()}%",
                    valueColor = if (visionConfidence > 0.5f) GuardianOrange else GuardianGreen
                )
                DiagnosticRow(
                    label = "Investigation duration",
                    value = "${visionInvestigationDurationMs} ms"
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- PHASE 5 EMERGENCY RESPONSE & VERIFICATION ---
            StatusCard(title = "EMERGENCY RESPONSE & VERIFICATION (PHASE 5)") {
                val locStateColor = when (locationState) {
                    com.safeguardian.app.domain.response.LocationState.AVAILABLE,
                    com.safeguardian.app.domain.response.LocationState.UPDATING -> GuardianGreen
                    com.safeguardian.app.domain.response.LocationState.ACQUIRING -> GuardianYellow
                    com.safeguardian.app.domain.response.LocationState.STOPPED,
                    com.safeguardian.app.domain.response.LocationState.UNAVAILABLE -> GuardianTextMuted
                }

                DiagnosticRow(
                    label = "LOCATION",
                    value = locationState.name,
                    valueColor = locStateColor
                )
                DiagnosticRow(
                    label = "Current Coordinates",
                    value = currentLocation?.let { "%.4f, %.4f (±%.0fm)".format(it.latitude, it.longitude, it.accuracyMeters) } ?: "GPS Off / Awaiting Trigger",
                    valueColor = if (currentLocation != null) GuardianGreen else GuardianTextMuted
                )
                val enabledCount = contactsList.count { it.isEnabled }
                DiagnosticRow(
                    label = "Trusted Contacts",
                    value = "$enabledCount enabled (${contactsList.size} configured)",
                    valueColor = if (enabledCount > 0) GuardianGreen else GuardianYellow
                )
                DiagnosticRow(
                    label = "Emergency Dispatcher",
                    value = dispatcherName,
                    valueColor = GuardianBlue
                )
                DiagnosticRow(
                    label = "Simulated Dispatches",
                    value = "${dispatchedRecords.size} sent",
                    valueColor = if (dispatchedRecords.isNotEmpty()) GuardianRed else GuardianTextMuted
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- PHASE 6 MODEL INTELLIGENCE & BENCHMARKING ---
            StatusCard(title = "TINY ON-DEVICE MODEL INTELLIGENCE (PHASE 6)") {
                DiagnosticRow(
                    label = "Motion Model",
                    value = if (viewModel.isMotionRealML) "REAL MODEL" else "PROTOTYPE FALLBACK",
                    valueColor = if (viewModel.isMotionRealML) GuardianGreen else GuardianYellow
                )
                DiagnosticRow("Motion Architecture", viewModel.motionMetadata.targetArchitecture)
                DiagnosticRow("Motion Input Tensor", viewModel.motionMetadata.inputDimensions.joinToString("x", prefix = "[", postfix = "]"))

                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = GuardianBorder)

                DiagnosticRow(
                    label = "Audio Model",
                    value = if (viewModel.isAudioRealML) "REAL MODEL" else "PROTOTYPE FALLBACK",
                    valueColor = if (viewModel.isAudioRealML) GuardianGreen else GuardianYellow
                )
                DiagnosticRow("Audio Architecture", viewModel.audioMetadata.targetArchitecture)
                DiagnosticRow("Audio Input Tensor", viewModel.audioMetadata.inputDimensions.joinToString("x", prefix = "[", postfix = "]"))

                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = GuardianBorder)

                DiagnosticRow(
                    label = "Vision Model",
                    value = if (viewModel.isVisionRealML) "REAL MODEL" else "PROTOTYPE FALLBACK",
                    valueColor = if (viewModel.isVisionRealML) GuardianGreen else GuardianYellow
                )
                DiagnosticRow("Vision Architecture", viewModel.visionMetadata.targetArchitecture)
                DiagnosticRow("Vision Input Tensor", viewModel.visionMetadata.inputDimensions.joinToString("x", prefix = "[", postfix = "]"))

                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = GuardianBorder)

                DiagnosticRow(
                    label = "Cloud Inference",
                    value = "NEVER (100% On-Device)",
                    valueColor = GuardianGreen
                )
                DiagnosticRow(
                    label = "Network Required",
                    value = "NO (Full Offline Operation)",
                    valueColor = GuardianGreen
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Detailed Motion Features (live window)
            StatusCard(title = "TEMPORAL WINDOW FEATURES") {
                DiagnosticRow("Mean accel magnitude", "%.2f m/s²".format(features.meanAccelMagnitude))
                DiagnosticRow("Accel variance", "%.4f (m/s²)²".format(features.accelVariance))
                DiagnosticRow("Jerk / delta magnitude", "%.2f m/s²".format(features.accelDeltaMagnitude))
                DiagnosticRow("Signal energy", "%.2f".format(features.signalEnergy))
                DiagnosticRow("Sudden impulse", "%.2f".format(features.suddenImpulse))
                DiagnosticRow("Mean gyro magnitude", "%.2f rad/s".format(features.meanGyroMagnitude))
                DiagnosticRow("Gyro variance", "%.4f".format(features.gyroVariance))
                DiagnosticRow("Orientation change", "%.2f".format(features.orientationChange))
                DiagnosticRow("Immobility duration", "${features.immobilityDuration} ms")
                DiagnosticRow("Impact-decel pattern", if (features.hasImpactDecelPattern) "YES" else "NO")
                DiagnosticRow("Movement->Impact->Immobile", if (features.movementImpactImmobilityPattern) "YES" else "NO")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // AI Pipeline (Cascade Stages)
            StatusCard(title = "AI PIPELINE CASCADE") {
                val motionRunner = if (accelStatus == SensorStatus.ACTIVE) "RUNNING" else "STANDBY"
                val audioRunner = if (pipelineStage.powerLevel >= 2) "RUNNING" else "STANDBY"
                val visionRunner = if (pipelineStage.powerLevel >= 3) "RUNNING" else "STANDBY"
                val fusionRunner = if (pipelineStage.powerLevel >= 4) "RUNNING" else "STANDBY"

                DiagnosticRow("Stage 0: Low-power Motion", motionRunner,
                    if (motionRunner == "RUNNING") GuardianGreen else GuardianTextMuted)
                DiagnosticRow("Stage 1: Audio classifier", audioRunner,
                    if (audioRunner == "RUNNING") GuardianGreen else GuardianTextMuted)
                DiagnosticRow("Stage 2: Vision classifier", visionRunner,
                    if (visionRunner == "RUNNING") GuardianGreen else GuardianTextMuted)
                DiagnosticRow("Stage 3: Multimodal fusion", fusionRunner,
                    if (fusionRunner == "RUNNING") GuardianGreen else GuardianTextMuted)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Battery Strategy
            StatusCard(title = "BATTERY STRATEGY") {
                DiagnosticRow("Current stage", pipelineStage.displayName, GuardianBlue)
                DiagnosticRow("Power level", "${pipelineStage.powerLevel}/5")
                DiagnosticRow("Sampling rate", "${samplingRate} Hz")
                DiagnosticRow("Current risk score", "${(riskScore * 100).toInt()}%",
                    when {
                        riskScore > 0.75f -> GuardianRed
                        riskScore > 0.5f -> GuardianOrange
                        riskScore > 0.25f -> GuardianYellow
                        else -> GuardianGreen
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Event State
            StatusCard(title = "STATE ENGINE") {
                val stateColor = when (safetyState) {
                    SafetyState.NORMAL -> GuardianGreen
                    SafetyState.ANOMALY_CANDIDATE -> GuardianYellow
                    SafetyState.INVESTIGATING -> GuardianOrange
                    SafetyState.SUSPICIOUS -> StateSuspicious
                    SafetyState.HIGH_CONFIDENCE_EMERGENCY -> GuardianRed
                    SafetyState.ACTIVE_EMERGENCY -> GuardianRed
                    SafetyState.RESOLVED -> GuardianBlue
                }
                DiagnosticRow("Safety state", safetyState.name, stateColor)
                DiagnosticRow("Pipeline stage", pipelineStage.name)
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
