package com.safeguardian.app.domain.response

import com.safeguardian.app.domain.model.EmergencyContact
import com.safeguardian.app.domain.model.EmergencyEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Interface for emergency contact dispatch.
 * Decouples actual communication mechanisms (SMS, webhook, call) from pipeline state.
 */
interface EmergencyDispatcher {
    val dispatchedRecords: StateFlow<List<DispatchRecord>>
    val dispatcherName: String
    fun dispatch(event: EmergencyEvent, location: GeoLocation?, contacts: List<EmergencyContact>): List<DispatchRecord>
    fun clearRecords()
}

/**
 * Safe local simulation emergency dispatcher.
 * Records exactly what WOULD have been sent without transmitting real network or SMS packets.
 */
class SimulationEmergencyDispatcher : EmergencyDispatcher {

    override val dispatcherName: String = "SIMULATION (Safe Local Logging)"

    private val _dispatchedRecords = MutableStateFlow<List<DispatchRecord>>(emptyList())
    override val dispatchedRecords: StateFlow<List<DispatchRecord>> = _dispatchedRecords.asStateFlow()

    override fun dispatch(
        event: EmergencyEvent,
        location: GeoLocation?,
        contacts: List<EmergencyContact>
    ): List<DispatchRecord> {
        val messageText = EmergencyMessageGenerator.generateMessage(event, location)
        val records = contacts.filter { it.isEnabled }.map { contact ->
            DispatchRecord(
                eventId = event.id,
                recipientName = contact.name,
                recipientPhone = contact.phone,
                messageText = messageText,
                isSimulated = true,
                success = true
            )
        }

        _dispatchedRecords.value = _dispatchedRecords.value + records
        return records
    }

    override fun clearRecords() {
        _dispatchedRecords.value = emptyList()
    }
}
