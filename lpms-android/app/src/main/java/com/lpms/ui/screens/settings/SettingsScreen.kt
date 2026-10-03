package com.lpms.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lpms.R
import com.lpms.data.remote.dto.SettingResponse
import com.lpms.ui.components.LpmsCard
import com.lpms.ui.components.LpmsPrimaryButton
import com.lpms.ui.components.LpmsSectionHeader
import com.lpms.ui.components.LpmsTopBar

/**
 * Settings (ADMIN only): the switches that govern how sales behave.
 *
 * Settings are fetched over the network and cached in Room, so the owner can
 * see the last-known values while offline. Changes are sent to the server.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { LpmsTopBar(title = stringResource(R.string.settings_title), onBack = onBack) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (settings.isEmpty()) {
                com.lpms.ui.components.LpmsLoading()
                return@Column
            }

            Text(
                text = stringResource(R.string.settings_section_inventory),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            settings.forEach { setting ->
                SettingRow(
                    setting = setting,
                    onToggle = { value -> viewModel.update(setting.key, value.toString()) },
                )
            }

            Spacer(Modifier.height(8.dp))

            LpmsPrimaryButton(
                text = stringResource(R.string.action_save),
                onClick = { viewModel.save() },
                enabled = !viewModel.saving,
                loading = viewModel.saving,
            )
        }
    }
}

@Composable
private fun SettingRow(
    setting: SettingResponse,
    onToggle: (Boolean) -> Unit,
) {
    LpmsCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = setting.key,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (setting.description != null) {
                    Text(
                        text = setting.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            val current = setting.value.equals("true", ignoreCase = true)
            Switch(
                checked = current,
                onCheckedChange = onToggle,
            )
        }
    }
}
