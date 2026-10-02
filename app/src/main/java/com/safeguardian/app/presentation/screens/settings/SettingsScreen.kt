package com.safeguardian.app.presentation.screens.settings

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
import com.safeguardian.app.presentation.components.DiagnosticRow
import com.safeguardian.app.presentation.components.StatusCard
import com.safeguardian.app.presentation.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val capabilities by viewModel.capabilities.collectAsState()
    val samplingRate by viewModel.currentSamplingRateHz.collectAsState()
    val impactThresholdG by viewModel.impactThreshold.collectAsState()
    val rotationThresholdRadS by viewModel.rotationThreshold.collectAsState()
    val cooldownMs by viewModel.cooldownDuration.collectAsState()
    val config = viewModel.config

    LaunchedEffect(Unit) {
        viewModel.checkCapabilities(context)
    }

    Scaffold(
        containerColor = GuardianDark,
        topBar = {
            TopAppBar(
                title = {
                    Text("Settings", fontWeight = FontWeight.Bold, color = GuardianTextPrimary)
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
            Spacer(modifier = Modifier.height(8.dp))

            // Capabilities
            StatusCard(title = "HARDWARE & PERMISSIONS") {
                capabilities.forEach { cap ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (cap.isAvailable) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
                            contentDescription = null,
                            tint = if (cap.isAvailable) GuardianGreen else GuardianRed,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = cap.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = GuardianTextPrimary
                            )
                            Text(
                                text = cap.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = GuardianTextMuted
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Configurable Thresholds (Phase 2 Requirement)
            StatusCard(title = "PHASE 2 CONFIGURABLE MOTION THRESHOLDS") {
                Text(
                    text = "Configure detector sensitivity for your device and physical profile.",
                    style = MaterialTheme.typography.bodySmall,
                    color = GuardianTextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Sampling rate selector
                Text(
                    text = "Sensor Sampling Rate: ${samplingRate.toInt()} Hz",
                    style = MaterialTheme.typography.labelMedium,
                    color = GuardianTextPrimary
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(5f to "5Hz (Low Power)", 15f to "15Hz (Normal)", 25f to "25Hz (Active)", 50f to "50Hz (Test)").forEach { (rate, label) ->
                        val isSelected = (samplingRate == rate)
                        OutlinedButton(
                            onClick = { viewModel.setSamplingRate(rate) },
                            modifier = Modifier.weight(1f).height(40.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isSelected) GuardianBlue.copy(alpha = 0.2f) else androidx.compose.ui.graphics.Color.Transparent,
                                contentColor = if (isSelected) GuardianBlue else GuardianTextSecondary
                            ),
                            border = ButtonDefaults.outlinedButtonBorder
                        ) {
                            Text(
                                text = "${rate.toInt()}Hz",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = GuardianBorder)

                // Impact Threshold
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Impact Threshold", style = MaterialTheme.typography.bodyMedium, color = GuardianTextPrimary)
                    Text("%.1f G (%.1f m/s²)".format(impactThresholdG, impactThresholdG * 9.8f),
                        style = MaterialTheme.typography.bodyMedium, color = GuardianYellow)
                }
                Slider(
                    value = impactThresholdG,
                    onValueChange = { viewModel.setImpactThreshold(it) },
                    valueRange = 1.5f..6.0f,
                    steps = 8,
                    colors = SliderDefaults.colors(thumbColor = GuardianYellow, activeTrackColor = GuardianYellow)
                )

                // Rotation Threshold
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Rotation Threshold", style = MaterialTheme.typography.bodyMedium, color = GuardianTextPrimary)
                    Text("%.1f rad/s".format(rotationThresholdRadS),
                        style = MaterialTheme.typography.bodyMedium, color = GuardianBlue)
                }
                Slider(
                    value = rotationThresholdRadS,
                    onValueChange = { viewModel.setRotationThreshold(it) },
                    valueRange = 2.0f..10.0f,
                    steps = 7,
                    colors = SliderDefaults.colors(thumbColor = GuardianBlue, activeTrackColor = GuardianBlue)
                )

                // Cooldown Duration
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Cooldown Duration", style = MaterialTheme.typography.bodyMedium, color = GuardianTextPrimary)
                    Text("${cooldownMs / 1000}s",
                        style = MaterialTheme.typography.bodyMedium, color = GuardianGreen)
                }
                Slider(
                    value = (cooldownMs / 1000).toFloat(),
                    onValueChange = { viewModel.setCooldownDuration((it * 1000).toLong()) },
                    valueRange = 1f..15f,
                    steps = 13,
                    colors = SliderDefaults.colors(thumbColor = GuardianGreen, activeTrackColor = GuardianGreen)
                )

                DiagnosticRow("Temporal window", "${config.motionWindowSizeMs}ms")
                DiagnosticRow("Anomaly score threshold", "%.0f%%".format(config.anomalyScoreThreshold * 100))
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- PHASE 5 TRUSTED EMERGENCY CONTACTS ---
            val contacts by viewModel.contacts.collectAsState()
            var showAddDialog by remember { mutableStateOf(false) }
            var newName by remember { mutableStateOf("") }
            var newPhone by remember { mutableStateOf("") }
            var newRel by remember { mutableStateOf("") }

            StatusCard(title = "TRUSTED EMERGENCY CONTACTS (PHASE 5)") {
                Text(
                    text = "Contacts to notify in an emergency. Stored securely on-device only.",
                    style = MaterialTheme.typography.bodySmall,
                    color = GuardianTextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))

                contacts.forEach { contact ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${contact.name} (${contact.relationship.ifEmpty { "Trusted" }})",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (contact.isEnabled) GuardianTextPrimary else GuardianTextMuted
                            )
                            Text(
                                text = contact.phone,
                                style = MaterialTheme.typography.bodySmall,
                                color = GuardianTextSecondary
                            )
                        }

                        Switch(
                            checked = contact.isEnabled,
                            onCheckedChange = { viewModel.toggleContact(contact.id, it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = GuardianGreen,
                                checkedTrackColor = GuardianGreenDim.copy(alpha = 0.5f)
                            )
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        IconButton(onClick = { viewModel.deleteContact(contact.id) }) {
                            Icon(Icons.Filled.Delete, "Delete", tint = GuardianRed.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
                        }
                    }
                    HorizontalDivider(color = GuardianBorder.copy(alpha = 0.5f))
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = GuardianBlue),
                    border = ButtonDefaults.outlinedButtonBorder
                ) {
                    Icon(Icons.Filled.Add, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Emergency Contact", style = MaterialTheme.typography.labelSmall)
                }
            }

            if (showAddDialog) {
                AlertDialog(
                    onDismissRequest = { showAddDialog = false },
                    title = { Text("Add Trusted Contact", color = GuardianTextPrimary) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = newName,
                                onValueChange = { newName = it },
                                label = { Text("Full Name") },
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = newPhone,
                                onValueChange = { newPhone = it },
                                label = { Text("Phone Number") },
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = newRel,
                                onValueChange = { newRel = it },
                                label = { Text("Relationship (e.g. Spouse, Friend)") },
                                singleLine = true
                            )
                        }
                    },
                    confirmButton = {
                        Button(onClick = {
                            viewModel.addContact(newName, newPhone, newRel)
                            newName = ""
                            newPhone = ""
                            newRel = ""
                            showAddDialog = false
                        }) {
                            Text("Save")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showAddDialog = false }) {
                            Text("Cancel")
                        }
                    },
                    containerColor = GuardianSurface
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // About
            StatusCard(title = "ABOUT") {
                DiagnosticRow("Phase", "Phase 5 — Emergency Response & Verification")
                DiagnosticRow("Version", "0.5.0-prototype")
                DiagnosticRow("Architecture", "MVVM + Clean Architecture")
                DiagnosticRow("Sensor Pipeline", "Zero-allocation ring buffers")
                DiagnosticRow("Privacy", "100% on-device processing, zero cloud streaming")
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
