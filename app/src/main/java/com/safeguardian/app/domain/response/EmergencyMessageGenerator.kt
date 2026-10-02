package com.safeguardian.app.domain.response

import com.safeguardian.app.domain.model.EmergencyContact
import com.safeguardian.app.domain.model.EmergencyEvent
import java.text.SimpleDateFormat
import java.util.*

/**
 * Generates privacy-preserving, concise emergency text messages for trusted contacts.
 *
 * Excludes unnecessary private telemetry or raw sensor data.
 * Focuses on:
 * - Event category
 * - Timestamp
 * - Current location coordinates & accuracy
 * - Risk assessment level
 * - Live location indicator
 */
object EmergencyMessageGenerator {

    fun generateMessage(
        event: EmergencyEvent,
        location: GeoLocation?,
        customTemplate: String? = null
    ): String {
        val dateFormat = SimpleDateFormat("HH:mm:ss yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }
        val timeStr = dateFormat.format(Date(event.timestamp))

        val locString = if (location != null) {
            "Location: https://maps.google.com/?q=${location.latitude},${location.longitude} (accuracy: ±${location.accuracyMeters.toInt()}m)"
        } else {
            "Location: Unavailable (Device GPS acquiring)"
        }

        return buildString {
            append("SafeGuardian Alert: Possible emergency detected.\n")
            append("Event: ${event.eventType}\n")
            append("Time: $timeStr\n")
            append("Risk Level: HIGH CONFIDENCE (${(event.confidence * 100).toInt()}%)\n")
            append("$locString\n")
            append("Status: Active Emergency Verification. If safe, user can resolve this alert.")
        }
    }
}
