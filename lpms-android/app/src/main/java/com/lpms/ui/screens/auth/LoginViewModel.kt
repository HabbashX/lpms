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
 * Sign-in form state.
 *
 * Field errors live in [fieldErrors] (keyed by field name, straight from the
 * backend's `errors[]` envelope) while a form-level failure goes to [error],
 * because "invalid credentials" is not a property of either input.
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    var username by mutableStateOf("")
        private set

    var password by mutableStateOf("")
        private set

    var submitting by mutableStateOf(false)
        private set

    var error by mutableStateOf<ApiError?>(null)
        private set

    var fieldErrors by mutableStateOf<Map<String, String>>(emptyMap())
        private set

    fun onUsernameChange(value: String) {
        username = value
        clearErrors()
    }

    fun onPasswordChange(value: String) {
        password = value
        clearErrors()
    }

    fun clearErrors() {
        error = null
        fieldErrors = emptyMap()
    }

    fun submit() {
        if (submitting) return
        if (username.isBlank() || password.isBlank()) {
            fieldErrors = buildMap {
                if (username.isBlank()) put(KEY_USERNAME, REQUIRED)
                if (password.isBlank()) put(KEY_PASSWORD, REQUIRED)
            }
            return
        }

        submitting = true
        error = null
        fieldErrors = emptyMap()

        viewModelScope.launch {
            when (val result = authRepository.login(username.trim(), password)) {
                is ApiResult.Success -> submitting = false
                is ApiResult.Failure -> {
                    submitting = false
                    onError(result.error)
                }
            }
        }
    }

    private fun onError(error: ApiError) {
        fieldErrors = when (error) {
            is ApiError.Http -> error.fieldErrors
            else -> emptyMap()
        }
        // A 401 on /auth/login is "wrong credentials", not "session expired";
        // do not render it as the generic expired-session message.
        this.error = if (error is ApiError.Http && error.status == 401) {
            ApiError.Http(401, "INVALID_CREDENTIALS", error.message, emptyMap())
        } else {
            error
        }
    }

    private companion object {
        const val KEY_USERNAME = "username"
        const val KEY_PASSWORD = "password"
        const val REQUIRED = "__required__"
    }
}
