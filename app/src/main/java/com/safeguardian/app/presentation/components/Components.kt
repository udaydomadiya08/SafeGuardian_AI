package com.safeguardian.app.presentation.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.safeguardian.app.domain.model.SafetyState
import com.safeguardian.app.domain.model.SensorStatus
import com.safeguardian.app.presentation.theme.*

/**
 * A status card with a subtle gradient border and glassmorphism-like surface.
 */
@Composable
fun StatusCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = GuardianBorder,
                shape = RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = GuardianSurface.copy(alpha = 0.95f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = GuardianTextSecondary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
            content()
        }
    }
}

/**
 * Animated indicator dot for sensor/system status.
 */
@Composable
fun StatusDot(
    status: SensorStatus,
    modifier: Modifier = Modifier
) {
    val color = when (status) {
        SensorStatus.ACTIVE -> SensorActive
        SensorStatus.STANDBY -> SensorStandby
        SensorStatus.OFF -> SensorOff
        SensorStatus.UNAVAILABLE -> SensorUnavailable
        SensorStatus.ERROR -> GuardianRed
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")

    val alpha by if (status == SensorStatus.ACTIVE) {
        infiniteTransition.animateFloat(
            initialValue = 0.6f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = EaseInOutSine),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseAlpha"
        )
    } else {
        remember { mutableFloatStateOf(1f) }
    }

    Box(
        modifier = modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha))
    )
}

/**
 * Sensor status row with label, status dot, and status text.
 */
@Composable
fun SensorStatusRow(
    label: String,
    status: SensorStatus,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusDot(status = status)
        Spacer(modifier = Modifier.width(12.dp))

        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = GuardianTextSecondary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = GuardianTextPrimary,
            modifier = Modifier.weight(1f)
        )

        Text(
            text = status.name,
            style = MaterialTheme.typography.labelSmall,
            color = when (status) {
                SensorStatus.ACTIVE -> SensorActive
                SensorStatus.STANDBY -> SensorStandby
                SensorStatus.OFF -> GuardianTextMuted
                SensorStatus.UNAVAILABLE -> SensorUnavailable
                SensorStatus.ERROR -> GuardianRed
            }
        )
    }
}

/**
 * Large animated safety state indicator.
 */
@Composable
fun SafetyStateIndicator(
    state: SafetyState,
    riskScore: Float,
    modifier: Modifier = Modifier
) {
    val stateColor = when (state) {
        SafetyState.NORMAL -> StateNormal
        SafetyState.ANOMALY_CANDIDATE -> StateAnomalyCandidate
        SafetyState.INVESTIGATING -> StateInvestigating
        SafetyState.SUSPICIOUS -> StateSuspicious
        SafetyState.HIGH_CONFIDENCE_EMERGENCY -> StateEmergency
        SafetyState.ACTIVE_EMERGENCY -> StateEmergency
        SafetyState.RESOLVED -> StateResolved
    }

    val stateLabel = when (state) {
        SafetyState.NORMAL -> "NORMAL"
        SafetyState.ANOMALY_CANDIDATE -> "ANOMALY DETECTED"
        SafetyState.INVESTIGATING -> "INVESTIGATING"
        SafetyState.SUSPICIOUS -> "SUSPICIOUS"
        SafetyState.HIGH_CONFIDENCE_EMERGENCY -> "EMERGENCY"
        SafetyState.ACTIVE_EMERGENCY -> "ACTIVE EMERGENCY"
        SafetyState.RESOLVED -> "RESOLVED"
    }

    val infiniteTransition = rememberInfiniteTransition(label = "stateAnim")
    val isEmergency = state == SafetyState.ACTIVE_EMERGENCY || state == SafetyState.HIGH_CONFIDENCE_EMERGENCY

    val glowAlpha by if (isEmergency) {
        infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = 0.8f,
            animationSpec = infiniteRepeatable(
                animation = tween(800, easing = EaseInOutSine),
                repeatMode = RepeatMode.Reverse
            ),
            label = "emergencyGlow"
        )
    } else {
        remember { mutableFloatStateOf(0.15f) }
    }

    val animatedColor by animateColorAsState(
        targetValue = stateColor,
        animationSpec = tween(500),
        label = "stateColor"
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Glow ring
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            animatedColor.copy(alpha = glowAlpha),
                            animatedColor.copy(alpha = 0f)
                        )
                    )
                )
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(GuardianSurface)
                    .border(2.dp, animatedColor, CircleShape)
            ) {
                Text(
                    text = "${(riskScore * 100).toInt()}",
                    style = MaterialTheme.typography.headlineLarge,
                    color = animatedColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stateLabel,
            style = MaterialTheme.typography.titleMedium,
            color = animatedColor,
            fontWeight = FontWeight.Bold
        )

        if (state != SafetyState.NORMAL) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Risk: ${(riskScore * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = GuardianTextSecondary
            )
        }
    }
}

/**
 * Key-value diagnostic row.
 */
@Composable
fun DiagnosticRow(
    label: String,
    value: String,
    valueColor: Color = GuardianTextPrimary,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = GuardianTextSecondary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = valueColor,
            fontWeight = FontWeight.Medium
        )
    }
}
