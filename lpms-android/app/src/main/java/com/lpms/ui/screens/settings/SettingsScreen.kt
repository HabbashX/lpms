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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.lpms.R
import com.lpms.ui.components.LpmsCard
import com.lpms.ui.components.LpmsDropdownField
import com.lpms.ui.components.LpmsPrimaryButton
import com.lpms.ui.components.LpmsSectionHeader
import com.lpms.ui.components.LpmsTextField
import com.lpms.ui.components.LpmsTopBar
import com.lpms.ui.locale.AppLocale

/**
 * Device settings: theme, language, and password.
 *
 * Nothing here touches the network settings API — the theme and language are
 * stored on the phone and applied immediately, and the password change goes
 * through the auth repository.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    var showPasswordDialog by remember { mutableStateOf(false) }

    val themeLabel = stringResource(R.string.settings_theme)
    val themeLightLabel = stringResource(R.string.settings_theme_light)
    val themeDarkLabel = stringResource(R.string.settings_theme_dark)
    val languageLabel = stringResource(R.string.language)
    val languageEnglishLabel = stringResource(R.string.language_english)
    val languageArabicLabel = stringResource(R.string.language_arabic)

    Scaffold(
        topBar = { LpmsTopBar(title = stringResource(R.string.settings_title), onBack = onBack) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_section_preferences),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            LpmsCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.settings_theme),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    LpmsDropdownField(
                        value = if (viewModel.darkTheme == true) themeDarkLabel else themeLightLabel,
                        onValueChange = { value ->
                            viewModel.setDarkTheme(value == themeDarkLabel)
                        },
                        label = themeLabel,
                        options = listOf(themeLightLabel, themeDarkLabel),
                        modifier = Modifier.fillMaxWidth(0.5f),
                    )
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.language),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    LpmsDropdownField(
                        value = if (viewModel.language == AppLocale.ARABIC) languageArabicLabel else languageEnglishLabel,
                        onValueChange = { value ->
                            val tag = if (value == languageArabicLabel) AppLocale.ARABIC else AppLocale.ENGLISH
                            viewModel.selectLanguage(tag)
                        },
                        label = languageLabel,
                        options = listOf(languageEnglishLabel, languageArabicLabel),
                        modifier = Modifier.fillMaxWidth(0.5f),
                    )
                }
            }

            Text(
                text = stringResource(R.string.settings_section_account),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            LpmsCard {
                LpmsPrimaryButton(
                    text = stringResource(R.string.change_password_title),
                    onClick = { showPasswordDialog = true },
                )
            }
        }
    }

    if (showPasswordDialog) {
        ChangePasswordDialog(
            onDismiss = {
                showPasswordDialog = false
                viewModel.clearPasswordMessages()
            },
            onConfirm = { current, new ->
                viewModel.changePassword(current, new)
            },
            loading = viewModel.changingPassword,
            error = viewModel.passwordError,
            success = viewModel.passwordSuccess,
        )
    }
}

@Composable
private fun ChangePasswordDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
    loading: Boolean,
    error: String?,
    success: Boolean,
) {
    var current by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.change_password_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LpmsTextField(
                    value = current,
                    onValueChange = { current = it },
                    label = stringResource(R.string.change_password_current),
                    enabled = !loading,
                )
                LpmsTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = stringResource(R.string.change_password_new),
                    enabled = !loading,
                )
                LpmsTextField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    label = stringResource(R.string.change_password_confirm),
                    enabled = !loading,
                )
                if (error != null) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (success) {
                    Text(
                        text = stringResource(R.string.change_password_done),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (newPassword == confirm && newPassword.isNotBlank()) {
                        onConfirm(current, newPassword)
                    }
                },
                enabled = !loading,
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !loading) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
