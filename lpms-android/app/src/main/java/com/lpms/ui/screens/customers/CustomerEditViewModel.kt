package com.lpms.ui.screens.customers

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lpms.data.remote.ApiError
import com.lpms.data.remote.ApiResult
import com.lpms.data.repository.CustomerDraft
import com.lpms.data.repository.CustomerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * Backs [CustomerEditScreen]. Loads the existing customer when editing, and on
 * save writes to Room first and lets the sync engine push the change.
 */
@HiltViewModel
class CustomerEditViewModel @Inject constructor(
    private val customerRepository: CustomerRepository,
) : ViewModel() {

    var name by mutableStateOf("")
        private set
    var phone by mutableStateOf("")
        private set
    var address by mutableStateOf("")
        private set
    var notes by mutableStateOf("")
        private set
    var active by mutableStateOf(true)
        private set

    var saving by mutableStateOf(false)
        private set
    var error by mutableStateOf<ApiError?>(null)
        private set
    var fieldErrors by mutableStateOf<Map<String, String>>(emptyMap())
        private set

    private var localId: String? = null
    private var loaded = false

    /** Loads the customer once, so recomposition does not reset the form. */
    fun load(customerId: String) {
        if (loaded) return
        loaded = true
        if (customerId == NEW_CUSTOMER_ID) return

        viewModelScope.launch {
            val customer = customerRepository.byLocalId(customerId)
            if (customer == null) {
                error = ApiError.Http(404, "NOT_FOUND", "customer_not_found")
                return@launch
            }
            localId = customer.localId
            name = customer.name
            phone = customer.phone ?: ""
            address = customer.address ?: ""
            notes = customer.notes ?: ""
            active = customer.active
        }
    }

    fun onNameChange(value: String) { name = value; clearErrors() }
    fun onPhoneChange(value: String) { phone = value }
    fun onAddressChange(value: String) { address = value }
    fun onNotesChange(value: String) { notes = value }
    fun onActiveChange(value: Boolean) { active = value }

    private fun clearErrors() {
        error = null
        fieldErrors = emptyMap()
    }

    val errorMessage: String
        get() = error?.let {
            when (it) {
                is ApiError.Http -> it.message ?: "error_generic"
                else -> "error_generic"
            }
        } ?: ""

    fun save(onBack: () -> Unit) {
        if (saving) return
        if (name.isBlank()) {
            fieldErrors = mapOf("name" to "__required__")
            return
        }

        saving = true
        error = null
        fieldErrors = emptyMap()

        val draft = CustomerDraft(
            name = name.trim(),
            phone = phone.trim().ifEmpty { null },
            address = address.trim().ifEmpty { null },
            notes = notes.trim().ifEmpty { null },
            active = active,
        )

        viewModelScope.launch {
            when (val result = customerRepository.save(localId, draft)) {
                is ApiResult.Success -> {
                    saving = false
                    onBack()
                }
                is ApiResult.Failure -> {
                    saving = false
                    error = result.error
                    fieldErrors = if (result.error is ApiError.Http) {
                        result.error.fieldErrors
                    } else {
                        emptyMap()
                    }
                }
            }
        }
    }

    companion object {
        const val NEW_CUSTOMER_ID = "new"
    }
}