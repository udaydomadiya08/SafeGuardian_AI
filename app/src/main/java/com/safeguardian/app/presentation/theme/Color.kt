package com.safeguardian.app.presentation.theme

import androidx.compose.ui.graphics.Color

// Primary palette — serious, professional safety system
val GuardianDark = Color(0xFF0D1117)
val GuardianSurface = Color(0xFF161B22)
val GuardianSurfaceVariant = Color(0xFF21262D)
val GuardianBorder = Color(0xFF30363D)

// Accent colors
val GuardianBlue = Color(0xFF58A6FF)
val GuardianBlueDim = Color(0xFF1F6FEB)
val GuardianGreen = Color(0xFF3FB950)
val GuardianGreenDim = Color(0xFF238636)
val GuardianYellow = Color(0xFFD29922)
val GuardianOrange = Color(0xFFDB6D28)
val GuardianRed = Color(0xFFF85149)
val GuardianRedDim = Color(0xFFDA3633)
val GuardianPurple = Color(0xFFBC8CFF)

// Text
val GuardianTextPrimary = Color(0xFFC9D1D9)
val GuardianTextSecondary = Color(0xFF8B949E)
val GuardianTextMuted = Color(0xFF6E7681)

// State indicator colors
val StateNormal = GuardianGreen
val StateAnomalyCandidate = GuardianYellow
val StateInvestigating = GuardianOrange
val StateSuspicious = Color(0xFFFF7B72)
val StateEmergency = GuardianRed
val StateResolved = GuardianBlue

// Sensor status colors
val SensorActive = GuardianGreen
val SensorStandby = GuardianYellow
val SensorOff = GuardianTextMuted
val SensorUnavailable = GuardianRed
