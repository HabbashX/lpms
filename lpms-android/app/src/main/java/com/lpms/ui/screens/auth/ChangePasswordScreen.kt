package com.lpms.ui.screens.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.lpms.R
import com.lpms.ui.components.LpmsPrimaryButton
import com.lpms.ui.components.LpmsPasswordField
import com.lpms.ui.components.toMessage

/**
 * S2 — Change password (forced on first sign-in).
 *
 * There is no back affordance: the backend has already revoked the previous
 * token, so leaving this screen any other way is not a valid state.
 */
@Composable
fun ChangePasswordScreen(viewModel: ChangePasswordViewModel) {
    val requiredText = stringResource(R.string.field_required)
    val shortText = stringResource(R.string.change_password_rule)
    val mismatchText = stringResource(R.string.change_password_mismatch)

    if (viewModel.completed) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.change_password_done),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(24.dp))
            LpmsPrimaryButton(
                text = stringResource(R.string.change_password_sign_in_again),
                onClick = viewModel::finish,
            )
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.change_password_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.change_password_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(32.dp))

        LpmsPasswordField(
            value = viewModel.current,
            onValueChange = viewModel::onCurrentChange,
            label = stringResource(R.string.change_password_current),
            error = viewModel.fieldErrors[KEY_CURRENT].asMessage(requiredText),
            enabled = !viewModel.submitting,
            imeAction = ImeAction.Next,
        )

        Spacer(Modifier.height(12.dp))

        LpmsPasswordField(
            value = viewModel.newPassword,
            onValueChange = viewModel::onNewChange,
            label = stringResource(R.string.change_password_new),
            error = viewModel.fieldErrors[KEY_NEW].asMessage(shortText),
            enabled = !viewModel.submitting,
            imeAction = ImeAction.Next,
        )

        Spacer(Modifier.height(12.dp))

        LpmsPasswordField(
            value = viewModel.confirm,
            onValueChange = viewModel::onConfirmChange,
            label = stringResource(R.string.change_password_confirm),
            error = viewModel.fieldErrors[KEY_CONFIRM].asMessage(mismatchText),
            enabled = !viewModel.submitting,
            imeAction = ImeAction.Done,
            onImeAction = viewModel::submit,
        )

        if (viewModel.error != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = viewModel.error.toMessage(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(Modifier.height(24.dp))

        LpmsPrimaryButton(
            text = if (viewModel.submitting) {
                stringResource(R.string.login_submitting)
            } else {
                stringResource(R.string.change_password_submit)
            },
            onClick = viewModel::submit,
            enabled = !viewModel.submitting,
            loading = viewModel.submitting,
        )
    }
}

/** Maps the view model's validation sentinels onto localized strings. */
private fun String?.asMessage(sentinelText: String): String? = when (this) {
    SENTINEL_REQUIRED, SENTINEL_SHORT -> sentinelText
    SENTINEL_MISMATCH -> sentinelText
    null -> null
    else -> this
}

private const val KEY_CURRENT = "currentPassword"
private const val KEY_NEW = "newPassword"
private const val KEY_CONFIRM = "confirm"
private const val SENTINEL_REQUIRED = "__required__"
private const val SENTINEL_SHORT = "__short__"
private const val SENTINEL_MISMATCH = "__mismatch__"
