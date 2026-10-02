package com.safeguardian.app.presentation.screens.battery

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.safeguardian.app.domain.model.*
import com.safeguardian.app.presentation.components.*
import com.safeguardian.app.presentation.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatteryScreen(
    onNavigateBack: () -> Unit,
    viewModel: BatteryViewModel = viewModel()
) {
    val context = LocalContext.current
    val pipelineStage by viewModel.pipelineStage.collectAsState()
    val samplingRate by viewModel.samplingRate.collectAsState()
    val safetyState by viewModel.safetyState.collectAsState()
    val riskScore by viewModel.riskScore.collectAsState()
    val batteryInfo by viewModel.batteryInfo.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.refreshBatteryInfo(context)
    }

    Scaffold(
        containerColor = GuardianDark,
        topBar = {
            TopAppBar(
                title = {
                    Text("Battery Diagnostics", fontWeight = FontWeight.Bold, color = GuardianTextPrimary)
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = GuardianTextSecondary)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshBatteryInfo(context) }) {
                        Icon(Icons.Filled.Refresh, "Refresh", tint = GuardianBlue)
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
            Spacer(modifier = Modifier.height(8.dp))

            // Device battery
            StatusCard(title = "DEVICE BATTERY") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (batteryInfo.level >= 0) "${batteryInfo.level}%" else "Unknown",
                            style = MaterialTheme.typography.headlineLarge,
                            color = when {
                                batteryInfo.level > 50 -> GuardianGreen
                                batteryInfo.level > 20 -> GuardianYellow
                                batteryInfo.level > 0 -> GuardianRed
                                else -> GuardianTextMuted
                            },
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (batteryInfo.isCharging) "Charging" else "On battery",
                            style = MaterialTheme.typography.bodySmall,
                            color = GuardianTextSecondary
                        )
                    }

                    Icon(
                        imageVector = when {
                            batteryInfo.isCharging -> Icons.Filled.BatteryChargingFull
                            batteryInfo.level > 50 -> Icons.Filled.BatteryFull
                            batteryInfo.level > 20 -> Icons.Filled.Battery4Bar
                            else -> Icons.Filled.Battery1Bar
                        },
                        contentDescription = null,
                        tint = when {
                            batteryInfo.level > 50 -> GuardianGreen
                            batteryInfo.level > 20 -> GuardianYellow
                            else -> GuardianRed
                        },
                        modifier = Modifier.size(48.dp)
                    )
                }

                if (batteryInfo.temperature > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    DiagnosticRow("Temperature", "%.1f°C".format(batteryInfo.temperature),
                        if (batteryInfo.temperature > 40) GuardianRed else GuardianTextPrimary)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Current strategy
            StatusCard(title = "CURRENT BATTERY STRATEGY") {
                val impact = viewModel.getEstimatedImpact(pipelineStage)
                DiagnosticRow("Pipeline stage", pipelineStage.displayName, GuardianBlue)
                DiagnosticRow("Power level", "${pipelineStage.powerLevel}/5")
                DiagnosticRow("Sampling rate", "%.1f Hz".format(samplingRate))
                DiagnosticRow("Safety state", safetyState.name)
                DiagnosticRow("Current risk", "${(riskScore * 100).toInt()}%")

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = GuardianBorder
                )

                DiagnosticRow("Estimated battery impact", impact.displayName,
                    when (impact) {
                        BatteryImpact.MINIMAL -> GuardianGreen
                        BatteryImpact.LOW -> GuardianGreen
                        BatteryImpact.MODERATE -> GuardianYellow
                        BatteryImpact.HIGH -> GuardianOrange
                        BatteryImpact.VERY_HIGH -> GuardianRed
                    })
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Cascade power breakdown
            StatusCard(title = "CASCADE POWER BREAKDOWN") {
                PipelineStage.entries.forEach { stage ->
                    val impact = viewModel.getEstimatedImpact(stage)
                    val isCurrentStage = stage == pipelineStage
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isCurrentStage) {
                                Icon(
                                    Icons.Filled.PlayArrow,
                                    null,
                                    tint = GuardianBlue,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                            }
                            Text(
                                text = stage.displayName,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isCurrentStage) GuardianBlue else GuardianTextSecondary,
                                fontWeight = if (isCurrentStage) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                        Text(
                            text = impact.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            color = when (impact) {
                                BatteryImpact.MINIMAL -> GuardianGreen
                                BatteryImpact.LOW -> GuardianGreen
                                BatteryImpact.MODERATE -> GuardianYellow
                                BatteryImpact.HIGH -> GuardianOrange
                                BatteryImpact.VERY_HIGH -> GuardianRed
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Optimization notes
            StatusCard(title = "OPTIMIZATION NOTES") {
                Text(
                    text = """
                        • Stage 0 (Idle): Accelerometer + gyroscope at ~5 Hz. Minimal battery impact.
                        • Stage 1 (Audio): Short audio windows only after motion trigger. Mic powers down after analysis.
                        • Stage 2 (Vision): Camera activates only after audio corroboration. Brief frame capture, then shutdown.
                        • Stage 3 (Fusion): Lightweight weighted averaging. Negligible CPU cost.
                        • Emergency: GPS activates for location updates. Highest battery use but time-limited.
                        
                        Mock models have near-zero inference cost. Real TFLite models will use INT8 quantization and NNAPI acceleration.
                    """.trimIndent(),
                    style = MaterialTheme.typography.bodySmall,
                    color = GuardianTextSecondary,
                    lineHeight = MaterialTheme.typography.bodySmall.lineHeight
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
