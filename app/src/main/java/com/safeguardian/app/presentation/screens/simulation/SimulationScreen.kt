package com.safeguardian.app.presentation.screens.simulation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.safeguardian.app.presentation.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimulationScreen(
    onNavigateBack: () -> Unit,
    viewModel: SimulationViewModel = viewModel()
) {
    val isSimulating by viewModel.isSimulating.collectAsState()
    val logs by viewModel.simulationLogs.collectAsState()
    val safetyState by viewModel.safetyState.collectAsState()
    val riskScore by viewModel.riskScore.collectAsState()
    val listState = rememberLazyListState()

    // Auto-scroll to bottom when new logs arrive
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Scaffold(
        containerColor = GuardianDark,
        topBar = {
            TopAppBar(
                title = {
                    Text("Emergency Simulation", fontWeight = FontWeight.Bold, color = GuardianTextPrimary)
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = GuardianTextSecondary)
                    }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.resetState() },
                        enabled = !isSimulating
                    ) {
                        Text("Reset", color = if (!isSimulating) GuardianBlue else GuardianTextMuted)
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
        ) {
            // Warning banner
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                colors = CardDefaults.cardColors(containerColor = GuardianOrange.copy(alpha = 0.15f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Science, null, tint = GuardianOrange, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Simulation Mode — Evaluates live state machine with synthetic streams",
                        style = MaterialTheme.typography.labelSmall,
                        color = GuardianOrange
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Current state bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .background(GuardianSurfaceVariant, RoundedCornerShape(8.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "State: ${safetyState.name}",
                    style = MaterialTheme.typography.labelLarge,
                    color = GuardianTextPrimary
                )
                Text(
                    "Risk: ${(riskScore * 100).toInt()}%",
                    style = MaterialTheme.typography.labelLarge,
                    color = when {
                        riskScore > 0.75f -> GuardianRed
                        riskScore > 0.5f -> GuardianOrange
                        else -> GuardianGreen
                    }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // PHASE 2 SENSOR STREAM SIMULATIONS
            Text(
                text = "PHASE 2: MOTION SENSOR STREAM SIMULATIONS",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = GuardianBlue,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                // Phase 2 Row 1: SIMULATE NORMAL, SIMULATE IMPACT, SIMULATE FALL
                Row(modifier = Modifier.fillMaxWidth()) {
                    SimButton("SIMULATE\nNORMAL", GuardianGreen, !isSimulating, Modifier.weight(1f)) {
                        viewModel.simulatePhase2Normal()
                    }
                    Spacer(Modifier.width(8.dp))
                    SimButton("SIMULATE\nIMPACT", GuardianYellow, !isSimulating, Modifier.weight(1f)) {
                        viewModel.simulatePhase2Impact()
                    }
                    Spacer(Modifier.width(8.dp))
                    SimButton("SIMULATE\nFALL", GuardianOrange, !isSimulating, Modifier.weight(1f)) {
                        viewModel.simulatePhase2Fall()
                    }
                }
                Spacer(Modifier.height(8.dp))

                // Phase 2 Row 2: SIMULATE FALL + IMMOBILITY, SIMULATE RANDOM MOVEMENT
                Row(modifier = Modifier.fillMaxWidth()) {
                    SimButton("SIMULATE FALL +\nIMMOBILITY", GuardianRed, !isSimulating, Modifier.weight(1.2f)) {
                        viewModel.simulatePhase2FallAndImmobility()
                    }
                    Spacer(Modifier.width(8.dp))
                    SimButton("SIMULATE RANDOM\nMOVEMENT", GuardianBlue, !isSimulating, Modifier.weight(1f)) {
                        viewModel.simulatePhase2RandomMovement()
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // PHASE 3 ON-DEVICE AUDIO SIMULATIONS
            Text(
                text = "PHASE 3: ON-DEVICE AUDIO SIMULATIONS",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = GuardianOrange,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                // Audio Row 1: Normal, Distress, Impact
                Row(modifier = Modifier.fillMaxWidth()) {
                    SimButton("NORMAL\nAUDIO", GuardianGreen, !isSimulating, Modifier.weight(1f)) {
                        viewModel.simulatePhase3NormalAudio()
                    }
                    Spacer(Modifier.width(8.dp))
                    SimButton("DISTRESS\nAUDIO", GuardianRed, !isSimulating, Modifier.weight(1f)) {
                        viewModel.simulatePhase3DistressAudio()
                    }
                    Spacer(Modifier.width(8.dp))
                    SimButton("IMPACT\nAUDIO", GuardianOrange, !isSimulating, Modifier.weight(1f)) {
                        viewModel.simulatePhase3ImpactAudio()
                    }
                }
                Spacer(Modifier.height(8.dp))

                // Audio Row 2: Glass break, Loud abnormal
                Row(modifier = Modifier.fillMaxWidth()) {
                    SimButton("GLASS\nBREAK", GuardianYellow, !isSimulating, Modifier.weight(1f)) {
                        viewModel.simulatePhase3GlassBreak()
                    }
                    Spacer(Modifier.width(8.dp))
                    SimButton("LOUD ABNORMAL\nSOUND", GuardianYellow, !isSimulating, Modifier.weight(1f)) {
                        viewModel.simulatePhase3LoudAbnormalSound()
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // PHASE 3 MULTIMODAL FUSION SCENARIOS
            Text(
                text = "PHASE 3: MOTION + AUDIO FUSION SCENARIOS",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = GuardianRed,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    SimButton("MOTION +\nIMPACT AUDIO", GuardianOrange, !isSimulating, Modifier.weight(1f)) {
                        viewModel.simulatePhase3MotionAndImpactAudio()
                    }
                    Spacer(Modifier.width(8.dp))
                    SimButton("MOTION +\nDISTRESS AUDIO", GuardianRed, !isSimulating, Modifier.weight(1f)) {
                        viewModel.simulatePhase3MotionAndDistressAudio()
                    }
                    Spacer(Modifier.width(8.dp))
                    SimButton("MOTION +\nGLASS BREAK", StateSuspicious, !isSimulating, Modifier.weight(1f)) {
                        viewModel.simulatePhase3MotionAndGlassBreak()
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // PHASE 4: ON-DEVICE COMPUTER VISION SIMULATIONS
            Text(
                text = "PHASE 4: ON-DEVICE VISION SIMULATIONS",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = GuardianBlue,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                // Vision Row 1: Normal, Person, Person on Ground
                Row(modifier = Modifier.fillMaxWidth()) {
                    SimButton("NORMAL\nVISION", GuardianGreen, !isSimulating, Modifier.weight(1f)) {
                        viewModel.simulateNormalVision()
                    }
                    Spacer(Modifier.width(8.dp))
                    SimButton("PERSON\nDETECTED", GuardianBlue, !isSimulating, Modifier.weight(1f)) {
                        viewModel.simulatePersonDetected()
                    }
                    Spacer(Modifier.width(8.dp))
                    SimButton("PERSON ON\nGROUND", GuardianOrange, !isSimulating, Modifier.weight(1f)) {
                        viewModel.simulatePersonOnGround()
                    }
                }
                Spacer(Modifier.height(8.dp))

                // Vision Row 2: Fall, Struggle, Fire/Smoke
                Row(modifier = Modifier.fillMaxWidth()) {
                    SimButton("FALL\nCANDIDATE", GuardianRed, !isSimulating, Modifier.weight(1f)) {
                        viewModel.simulateFallCandidate()
                    }
                    Spacer(Modifier.width(8.dp))
                    SimButton("POSSIBLE\nSTRUGGLE", StateSuspicious, !isSimulating, Modifier.weight(1f)) {
                        viewModel.simulatePossibleStruggle()
                    }
                    Spacer(Modifier.width(8.dp))
                    SimButton("FIRE OR\nSMOKE", GuardianRed, !isSimulating, Modifier.weight(1f)) {
                        viewModel.simulatePhase4FireSmoke()
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // PHASE 4 COMBINED TRIPLE-SENSOR MULTIMODAL SIMULATIONS
            Text(
                text = "PHASE 4: MOTION + AUDIO + VISION FUSION",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = GuardianRed,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    SimButton("MOTION + AUDIO +\nPERSON ON GROUND", GuardianOrange, !isSimulating, Modifier.weight(1f)) {
                        viewModel.simulateMotionAudioPersonOnGround()
                    }
                    Spacer(Modifier.width(8.dp))
                    SimButton("MOTION + IMPACT +\nFALL CANDIDATE", GuardianRed, !isSimulating, Modifier.weight(1f)) {
                        viewModel.simulateMotionImpactFall()
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    SimButton("MOTION + DISTRESS +\nPERSON ON GROUND", StateSuspicious, !isSimulating, Modifier.weight(1f)) {
                        viewModel.simulateMotionDistressPersonOnGround()
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // MULTI-STAGE CASCADE SIMULATION (Phase 1)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CASCADE ESCALATION SIMULATION (PHASE 1)",
                    style = MaterialTheme.typography.labelSmall,
                    color = GuardianTextSecondary
                )
            }
            Spacer(modifier = Modifier.height(4.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                Button(
                    onClick = { viewModel.simulateMultiSensorEmergency() },
                    enabled = !isSimulating,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GuardianRedDim,
                        disabledContainerColor = GuardianRedDim.copy(alpha = 0.3f)
                    )
                ) {
                    Icon(Icons.Filled.Warning, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Simulate Multi-Sensor Cascade Escalation", style = MaterialTheme.typography.labelMedium)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // PHASE 5 EMERGENCY RESPONSE & VERIFICATION SIMULATIONS
            Text(
                text = "PHASE 5: EMERGENCY RESPONSE & VERIFICATION",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = GuardianRed,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                // Phase 5 Row 1: High Confidence, Verification Countdown, User Cancel
                Row(modifier = Modifier.fillMaxWidth()) {
                    SimButton("HIGH CONFIDENCE\nEVENT", GuardianOrange, !isSimulating, Modifier.weight(1f)) {
                        viewModel.simulatePhase5HighConfidenceEvent()
                    }
                    Spacer(Modifier.width(8.dp))
                    SimButton("VERIFICATION\nCOUNTDOWN", GuardianYellow, !isSimulating, Modifier.weight(1f)) {
                        viewModel.simulatePhase5VerificationCountdown()
                    }
                    Spacer(Modifier.width(8.dp))
                    SimButton("USER\nCANCEL", GuardianGreen, !isSimulating, Modifier.weight(1f)) {
                        viewModel.simulatePhase5UserCancel()
                    }
                }
                Spacer(Modifier.height(8.dp))

                // Phase 5 Row 2: Countdown Expired, Active Emergency, Location Available
                Row(modifier = Modifier.fillMaxWidth()) {
                    SimButton("COUNTDOWN\nEXPIRED", GuardianRed, !isSimulating, Modifier.weight(1f)) {
                        viewModel.simulatePhase5CountdownExpired()
                    }
                    Spacer(Modifier.width(8.dp))
                    SimButton("GPS LOCATION\nAVAILABLE", GuardianBlue, !isSimulating, Modifier.weight(1f)) {
                        viewModel.simulatePhase5LocationAvailable()
                    }
                    Spacer(Modifier.width(8.dp))
                    SimButton("GPS\nUNAVAILABLE", GuardianTextMuted, !isSimulating, Modifier.weight(1f)) {
                        viewModel.simulatePhase5LocationUnavailable()
                    }
                }
                Spacer(Modifier.height(8.dp))

                // Phase 5 Row 3: Resolve Emergency
                OutlinedButton(
                    onClick = { viewModel.simulatePhase5Resolution() },
                    enabled = !isSimulating,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = GuardianGreen,
                        disabledContentColor = GuardianGreen.copy(alpha = 0.3f)
                    ),
                    border = ButtonDefaults.outlinedButtonBorder
                ) {
                    Text("SIMULATE RESOLUTION (RETURN TO NORMAL)", style = MaterialTheme.typography.labelMedium)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Simulation log header
            Text(
                text = "SIMULATION LOG",
                style = MaterialTheme.typography.labelLarge,
                color = GuardianTextSecondary,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Simulation log list
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = GuardianSurfaceVariant)
            ) {
                if (logs.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Select a simulation scenario above to observe pipeline behavior.",
                            style = MaterialTheme.typography.bodySmall,
                            color = GuardianTextMuted
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(logs) { log ->
                            LogItem(log)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SimButton(
    text: String,
    accentColor: androidx.compose.ui.graphics.Color,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(60.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = accentColor,
            disabledContentColor = accentColor.copy(alpha = 0.3f)
        ),
        border = ButtonDefaults.outlinedButtonBorder
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) accentColor else accentColor.copy(alpha = 0.3f)
        )
    }
}

@Composable
private fun LogItem(log: SimulationLog) {
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.US) }
    val timeStr = timeFormat.format(Date(log.timestamp))

    val color = when (log.level) {
        LogLevel.INFO -> GuardianTextSecondary
        LogLevel.WARNING -> GuardianYellow
        LogLevel.ALERT -> GuardianOrange
        LogLevel.EMERGENCY -> GuardianRed
    }

    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = timeStr,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = GuardianTextMuted,
            modifier = Modifier.width(90.dp)
        )
        Text(
            text = log.message,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = color,
            modifier = Modifier.weight(1f)
        )
    }
}
