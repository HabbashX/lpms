package com.lpms.ui.screens.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.lpms.R
import com.lpms.data.remote.ApiError
import com.lpms.ui.components.LpmsPrimaryButton
import com.lpms.ui.components.LpmsPasswordField
import com.lpms.ui.components.LpmsTextField
import com.lpms.ui.components.toMessage

/**
 * S1 — Sign in.
 *
 * The screen never navigates itself: on success the repository commits the
 * session, `authState` flips to `Authenticated`, and [com.lpms.ui.navigation.LpmsRoot]
 * replaces this screen.
 */
@Composable
fun LoginScreen(viewModel: LoginViewModel) {
    val username = viewModel.username
    val password = viewModel.password

    val requiredText = stringResource(R.string.field_required)
    val credentialsText = stringResource(R.string.login_error_credentials)
    val genericError = viewModel.error.toMessage()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.login_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.login_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(32.dp))

        LpmsTextField(
            value = username,
            onValueChange = viewModel::onUsernameChange,
            label = stringResource(R.string.login_username),
            hint = stringResource(R.string.login_username_hint),
            error = viewModel.fieldErrors[KEY_USERNAME]
                ?.let { if (it == REQUIRED) requiredText else it },
            enabled = !viewModel.submitting,
            imeAction = ImeAction.Next,
        )

        Spacer(Modifier.height(12.dp))

        LpmsPasswordField(
            value = password,
            onValueChange = viewModel::onPasswordChange,
            label = stringResource(R.string.login_password),
            error = viewModel.fieldErrors[KEY_PASSWORD]
                ?.let { if (it == REQUIRED) requiredText else it },
            enabled = !viewModel.submitting,
            imeAction = ImeAction.Done,
            onImeAction = viewModel::submit,
        )

        if (viewModel.error != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = if (isCredentialError(viewModel.error)) credentialsText else genericError,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(Modifier.height(24.dp))

        LpmsPrimaryButton(
            text = if (viewModel.submitting) {
                stringResource(R.string.login_submitting)
            } else {
                stringResource(R.string.login_submit)
            },
            onClick = viewModel::submit,
            enabled = !viewModel.submitting,
            loading = viewModel.submitting,
        )
    }
}

private fun isCredentialError(error: ApiError?): Boolean =
    error is ApiError.Http && error.status == 401

private const val KEY_USERNAME = "username"
private const val KEY_PASSWORD = "password"
private const val REQUIRED = "__required__"
