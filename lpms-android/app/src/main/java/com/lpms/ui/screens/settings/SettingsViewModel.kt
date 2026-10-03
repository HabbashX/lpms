package com.lpms.ui.screens.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lpms.data.remote.ApiResult
import com.lpms.data.session.AuthRepository
import com.lpms.data.session.UiPreferences
import com.lpms.ui.locale.AppLocale
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * Backs [SettingsScreen].
 *
 * Everything here is device-local: the theme and language are stored on the
 * phone and applied immediately, and the change-password flow goes through the
 * auth repository. Nothing in this screen calls the settings API.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val uiPreferences: UiPreferences,
    private val authRepository: AuthRepository,
) : ViewModel() {

    var darkTheme by mutableStateOf(uiPreferences.darkTheme)
        private set

    var language by mutableStateOf(AppLocale.currentTag())
        private set

    var changingPassword by mutableStateOf(false)
        private set

    var passwordError by mutableStateOf<String?>(null)
        private set

    var passwordSuccess by mutableStateOf(false)
        private set

    fun setDarkTheme(value: Boolean) {
        darkTheme = value
        uiPreferences.setDarkTheme(value)
    }

    fun selectLanguage(tag: String) {
        language = tag
        AppLocale.set(tag)
    }

    fun changePassword(currentPassword: String, newPassword: String) {
        if (changingPassword) return
        changingPassword = true
        passwordError = null
        passwordSuccess = false

        viewModelScope.launch {
            when (val result = authRepository.changePassword(currentPassword, newPassword)) {
                is ApiResult.Success -> {
                    changingPassword = false
                    passwordSuccess = true
                }
                is ApiResult.Failure -> {
                    changingPassword = false
                    passwordError = when (result.error) {
                        is com.lpms.data.remote.ApiError.Http -> result.error.message
                        else -> "error_generic"
                    }
                }
            }
        }
    }

    fun clearPasswordMessages() {
        passwordError = null
        passwordSuccess = false
    }
}
