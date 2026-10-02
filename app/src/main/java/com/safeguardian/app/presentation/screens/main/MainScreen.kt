package com.safeguardian.app.presentation.screens.main

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.safeguardian.app.domain.model.*
import com.safeguardian.app.presentation.components.*
import com.safeguardian.app.presentation.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onNavigateToDashboard: () -> Unit,
    onNavigateToSimulation: () -> Unit,
    onNavigateToBattery: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: MainViewModel = viewModel()
) {
    val isActive by viewModel.isProtectionActive.collectAsState()
    val safetyState by viewModel.safetyState.collectAsState()
    val pipelineStage by viewModel.pipelineStage.collectAsState()
    val riskScore by viewModel.riskScore.collectAsState()
    val accelStatus by viewModel.accelStatus.collectAsState()
    val gyroStatus by viewModel.gyroStatus.collectAsState()
    val emergency by viewModel.currentEmergency.collectAsState()

    Scaffold(
        containerColor = GuardianDark,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Shield,
                            contentDescription = null,
                            tint = GuardianBlue,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "SafeGuardian",
                            fontWeight = FontWeight.Bold,
                            color = GuardianTextPrimary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = "Settings",
                            tint = GuardianTextSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = GuardianDark
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Safety state indicator
            SafetyStateIndicator(
                state = safetyState,
                riskScore = riskScore
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Protection toggle
            StatusCard(title = "PROTECTION STATUS") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (isActive) "Active" else "Inactive",
                            style = MaterialTheme.typography.titleLarge,
                            color = if (isActive) GuardianGreen else GuardianTextMuted,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isActive) "Monitoring your safety" else "Tap to start protection",
                            style = MaterialTheme.typography.bodySmall,
                            color = GuardianTextSecondary
                        )
                    }

                    Switch(
                        checked = isActive,
                        onCheckedChange = { checked ->
                            if (checked) viewModel.startProtection()
                            else viewModel.stopProtection()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = GuardianGreen,
                            checkedTrackColor = GuardianGreenDim.copy(alpha = 0.5f)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Privacy and Battery info
            Row(modifier = Modifier.fillMaxWidth()) {
                StatusCard(
                    modifier = Modifier.weight(1f),
                    title = "PRIVACY"
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Lock,
                            contentDescription = null,
                            tint = GuardianGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "On-Device",
                            style = MaterialTheme.typography.bodyMedium,
                            color = GuardianGreen,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                StatusCard(
                    modifier = Modifier.weight(1f),
                    title = "BATTERY"
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.BatteryFull,
                            contentDescription = null,
                            tint = GuardianGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = pipelineStage.displayName,
                            style = MaterialTheme.typography.bodySmall,
                            color = GuardianTextPrimary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Sensors
            StatusCard(title = "SENSORS") {
                SensorStatusRow(
                    label = "Motion Monitoring",
                    status = if (isActive) accelStatus else SensorStatus.OFF,
                    icon = Icons.Filled.Sensors
                )
                SensorStatusRow(
                    label = "Gyroscope",
                    status = if (isActive) gyroStatus else SensorStatus.OFF,
                    icon = Icons.Filled.ScreenRotation
                )
                SensorStatusRow(
                    label = "Audio Analysis",
                    status = if (isActive && pipelineStage.powerLevel >= 2) SensorStatus.ACTIVE
                    else if (isActive) SensorStatus.STANDBY
                    else SensorStatus.OFF,
                    icon = Icons.Filled.Mic
                )
                SensorStatusRow(
                    label = "Camera",
                    status = if (isActive && pipelineStage.powerLevel >= 3) SensorStatus.ACTIVE
                    else if (isActive) SensorStatus.STANDBY
                    else SensorStatus.OFF,
                    icon = Icons.Filled.CameraAlt
                )
                SensorStatusRow(
                    label = "Location",
                    status = if (safetyState == SafetyState.ACTIVE_EMERGENCY) SensorStatus.ACTIVE
                    else if (isActive) SensorStatus.STANDBY
                    else SensorStatus.OFF,
                    icon = Icons.Filled.LocationOn
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // AI Models info
            StatusCard(title = "AI MODELS") {
                Text(
                    text = if (viewModel.isRealML) "✓ REAL INT8 TFLITE MODELS ACTIVE" else "⚠ Using mock models",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (viewModel.isRealML) GuardianGreen else GuardianYellow
                )
                Spacer(modifier = Modifier.height(8.dp))
                DiagnosticRow("Motion", viewModel.motionModelName, if (viewModel.isRealML) GuardianGreen else GuardianTextSecondary)
                DiagnosticRow("Audio", viewModel.audioModelName, if (viewModel.isRealML) GuardianGreen else GuardianTextSecondary)
                DiagnosticRow("Vision", viewModel.visionModelName, if (viewModel.isRealML) GuardianGreen else GuardianTextSecondary)
                DiagnosticRow("Fusion", "MultimodalRiskEngine v1.0", GuardianTextSecondary)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Emergency & Verification Countdown card
            AnimatedVisibility(
                visible = safetyState == SafetyState.ACTIVE_EMERGENCY || safetyState == SafetyState.HIGH_CONFIDENCE_EMERGENCY,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = GuardianRedDim.copy(alpha = 0.4f)
                    )
                ) {
                    Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (safetyState == SafetyState.HIGH_CONFIDENCE_EMERGENCY)
                                "⚠️ POSSIBLE EMERGENCY DETECTED"
                            else
                                "🚨 ACTIVE EMERGENCY",
                            style = MaterialTheme.typography.titleLarge,
                            color = GuardianRed,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (safetyState == SafetyState.HIGH_CONFIDENCE_EMERGENCY)
                                "VERIFICATION COUNTDOWN: 10s\nCancel if you are safe"
                            else
                                "Trusted contacts notified via simulated dispatch.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = GuardianTextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )

                        emergency?.let { ev ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Category: ${ev.eventType}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = GuardianTextPrimary
                            )
                            Text(
                                text = "Risk Score: ${(ev.confidence * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = GuardianTextSecondary
                            )
                            if (ev.isSimulated) {
                                Text(
                                    text = "⚙ SIMULATION MODE — No real SMS/call sent",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = GuardianYellow
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                if (safetyState == SafetyState.HIGH_CONFIDENCE_EMERGENCY) {
                                    viewModel.cancelInvestigation()
                                } else {
                                    viewModel.resolveEmergency()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (safetyState == SafetyState.HIGH_CONFIDENCE_EMERGENCY) GuardianGreen else GuardianRed
                            )
                        ) {
                            Text(
                                text = if (safetyState == SafetyState.HIGH_CONFIDENCE_EMERGENCY)
                                    "I AM SAFE — CANCEL"
                                else
                                    "I'M SAFE — RESOLVE EMERGENCY",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Cancel investigation button
            AnimatedVisibility(
                visible = safetyState in listOf(
                    SafetyState.ANOMALY_CANDIDATE,
                    SafetyState.INVESTIGATING,
                    SafetyState.SUSPICIOUS
                )
            ) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { viewModel.cancelInvestigation() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cancel Investigation", color = GuardianTextSecondary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Navigation buttons
            Row(modifier = Modifier.fillMaxWidth()) {
                NavButton(
                    text = "Dashboard",
                    icon = Icons.Filled.Dashboard,
                    onClick = onNavigateToDashboard,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(12.dp))
                NavButton(
                    text = "Simulate",
                    icon = Icons.Filled.Science,
                    onClick = onNavigateToSimulation,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            NavButton(
                text = "Battery Diagnostics",
                icon = Icons.Filled.BatteryChargingFull,
                onClick = onNavigateToBattery,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun NavButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = GuardianTextPrimary
        ),
        border = ButtonDefaults.outlinedButtonBorder
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}
