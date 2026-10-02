package com.safeguardian.app.domain.response

import android.content.Context
import android.content.SharedPreferences
import com.safeguardian.app.domain.model.EmergencyContact
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Repository interface for managing trusted emergency contacts.
 * Enforces local storage only — never uploaded to external servers.
 */
interface EmergencyContactRepository {
    val contacts: StateFlow<List<EmergencyContact>>
    fun addContact(contact: EmergencyContact)
    fun updateContact(contact: EmergencyContact)
    fun deleteContact(contactId: String)
    fun setContactEnabled(contactId: String, enabled: Boolean)
    fun getEnabledContacts(): List<EmergencyContact>
}

/**
 * Local secure SharedPreferences implementation of EmergencyContactRepository.
 */
class LocalEmergencyContactRepository(
    private val context: Context? = null
) : EmergencyContactRepository {

    private val prefs: SharedPreferences? = context?.getSharedPreferences("guardian_emergency_contacts", Context.MODE_PRIVATE)

    private val _contacts = MutableStateFlow<List<EmergencyContact>>(emptyList())
    override val contacts: StateFlow<List<EmergencyContact>> = _contacts.asStateFlow()

    init {
        loadContacts()
    }

    private fun loadContacts() {
        val jsonStr = prefs?.getString("contacts_list", null)
        if (jsonStr != null) {
            try {
                val list = mutableListOf<EmergencyContact>()
                val arr = JSONArray(jsonStr)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(
                        EmergencyContact(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            phone = obj.getString("phone"),
                            relationship = obj.optString("relationship", ""),
                            isEnabled = obj.optBoolean("isEnabled", true)
                        )
                    )
                }
                _contacts.value = list
                return
            } catch (e: Exception) {
                // fallback to default
            }
        }

        // Default demo contacts if empty
        if (_contacts.value.isEmpty()) {
            _contacts.value = listOf(
                EmergencyContact(
                    name = "Primary Trusted Contact",
                    phone = "+15550100200",
                    relationship = "Family",
                    isEnabled = true
                )
            )
            saveContacts()
        }
    }

    private fun saveContacts() {
        if (prefs == null) return
        val arr = JSONArray()
        for (c in _contacts.value) {
            val obj = JSONObject().apply {
                put("id", c.id)
                put("name", c.name)
                put("phone", c.phone)
                put("relationship", c.relationship)
                put("isEnabled", c.isEnabled)
            }
            arr.put(obj)
        }
        prefs.edit().putString("contacts_list", arr.toString()).apply()
    }

    override fun addContact(contact: EmergencyContact) {
        _contacts.value = _contacts.value + contact
        saveContacts()
    }

    override fun updateContact(contact: EmergencyContact) {
        _contacts.value = _contacts.value.map { if (it.id == contact.id) contact else it }
        saveContacts()
    }

    override fun deleteContact(contactId: String) {
        _contacts.value = _contacts.value.filter { it.id != contactId }
        saveContacts()
    }

    override fun setContactEnabled(contactId: String, enabled: Boolean) {
        _contacts.value = _contacts.value.map { if (it.id == contactId) it.copy(isEnabled = enabled) else it }
        saveContacts()
    }

    override fun getEnabledContacts(): List<EmergencyContact> {
        return _contacts.value.filter { it.isEnabled }
    }
}
