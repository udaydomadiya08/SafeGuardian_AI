package com.safeguardian.app.domain.response

import com.safeguardian.app.domain.model.EmergencyEvent

/**
 * State of live location acquisition and reporting during emergency response.
 */
enum class LocationState {
    UNAVAILABLE,
    ACQUIRING,
    AVAILABLE,
    UPDATING,
    STOPPED
}

/**
 * Representation of current geographical location data.
 */
data class GeoLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Status of the verification countdown safety mechanism.
 */
enum class VerificationStatus {
    IDLE,
    COUNTDOWN,
    CANCELLED,
    EXPIRED
}

/**
 * Result of emergency dispatch attempt to trusted contacts.
 */
data class DispatchRecord(
    val id: String = java.util.UUID.randomUUID().toString(),
    val eventId: String,
    val recipientName: String,
    val recipientPhone: String,
    val messageText: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isSimulated: Boolean = true,
    val success: Boolean = true
)
