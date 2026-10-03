package com.lpms.ui.screens.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lpms.data.remote.ApiError
import com.lpms.data.remote.ApiResult
import com.lpms.data.session.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * Forced password change.
 *
 * `mustChangePassword` comes from `/auth/me`, and the backend revokes every
 * token as soon as the new password lands — so [completed] is a terminal
 * screen state, not a flag that can be dismissed.
 */
@HiltViewModel
class ChangePasswordViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    var current by mutableStateOf("")
        private set

    var newPassword by mutableStateOf("")
        private set

    var confirm by mutableStateOf("")
        private set

    var submitting by mutableStateOf(false)
        private set

    var error by mutableStateOf<ApiError?>(null)
        private set

    var fieldErrors by mutableStateOf<Map<String, String>>(emptyMap())
        private set

    var completed by mutableStateOf(false)
        private set

    fun onCurrentChange(value: String) {
        current = value
        clearErrors()
    }

    fun onNewChange(value: String) {
        newPassword = value
        clearErrors()
    }

    fun onConfirmChange(value: String) {
        confirm = value
        clearErrors()
    }

    fun clearErrors() {
        error = null
        fieldErrors = emptyMap()
    }

    fun submit() {
        if (submitting) return

        val local = buildMap {
            if (current.isBlank()) put(KEY_CURRENT, SENTINEL_REQUIRED)
            if (newPassword.length < MIN_LENGTH) put(KEY_NEW, SENTINEL_SHORT)
            if (confirm != newPassword) put(KEY_CONFIRM, SENTINEL_MISMATCH)
        }
        if (local.isNotEmpty()) {
            fieldErrors = local
            return
        }

        submitting = true
        error = null
        fieldErrors = emptyMap()

        viewModelScope.launch {
            when (val result = authRepository.changePassword(current, newPassword)) {
                is ApiResult.Success -> {
                    submitting = false
                    completed = true
                }
                is ApiResult.Failure -> {
                    submitting = false
                    error = result.error
                    fieldErrors = (result.error as? ApiError.Http)?.fieldErrors.orEmpty()
                }
            }
        }
    }

    /** Drops the now-dead local session and returns to the login form. */
    fun finish() = authRepository.completePasswordChange()

    private companion object {
        const val KEY_CURRENT = "currentPassword"
        const val KEY_NEW = "newPassword"
        const val KEY_CONFIRM = "confirm"
        const val MIN_LENGTH = 8
        const val SENTINEL_REQUIRED = "__required__"
        const val SENTINEL_SHORT = "__short__"
        const val SENTINEL_MISMATCH = "__mismatch__"
    }
}
