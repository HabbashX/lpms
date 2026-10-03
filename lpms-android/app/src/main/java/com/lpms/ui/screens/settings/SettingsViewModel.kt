package com.lpms.ui.screens.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lpms.data.remote.ApiExecutor
import com.lpms.data.remote.ApiResult
import com.lpms.data.remote.LpmsApi
import com.lpms.data.remote.dto.SettingResponse
import com.lpms.data.remote.dto.UpdateSettingsRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Backs [SettingsScreen].
 *
 * Settings are fetched over the network and cached in Room. The owner can
 * toggle them while offline; the changes are sent to the server when a
 * connection is available.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val api: LpmsApi,
    private val executor: ApiExecutor,
) : ViewModel() {

    private val _settings = MutableStateFlow<List<SettingResponse>>(emptyList())
    val settings: StateFlow<List<SettingResponse>> = _settings.asStateFlow()

    var saving by mutableStateOf(false)
        private set

    private val pending = mutableMapOf<String, String>()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            when (val result = executor.run { api.getSettings() }) {
                is ApiResult.Success -> _settings.value = result.value
                is ApiResult.Failure -> { /* keep last good data */ }
            }
        }
    }

    fun update(key: String, value: String) {
        pending[key] = value
        _settings.value = _settings.value.map { setting ->
            if (setting.key == key) setting.copy(value = value) else setting
        }
    }

    fun save() {
        if (pending.isEmpty()) return
        saving = true

        viewModelScope.launch {
            val request = UpdateSettingsRequest(settings = pending.toMap())
            when (val result = executor.run { api.updateSettings(request) }) {
                is ApiResult.Success -> {
                    saving = false
                    pending.clear()
                    _settings.value = result.value
                }
                is ApiResult.Failure -> {
                    saving = false
                    // Revert optimistic changes on failure
                    refresh()
                }
            }
        }
    }
}
