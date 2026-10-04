package com.lpms.ui.screens.customers

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lpms.R
import com.lpms.ui.components.LpmsPrimaryButton
import com.lpms.ui.components.LpmsTextField
import com.lpms.ui.components.LpmsTopBar

/**
 * Create or edit a customer.
 *
 * A "customer" is a buyer the pharmacy records debt for, not an app user, so
 * this form has no credentials — only the details a receipt and a ledger entry
 * need. Saving writes to Room first; the sync engine pushes it.
 */
@Composable
fun CustomerEditScreen(
    customerId: String,
    onBack: () -> Unit,
    viewModel: CustomerEditViewModel = hiltViewModel(),
) {
    val isNew = customerId == CustomerEditViewModel.NEW_CUSTOMER_ID

    LaunchedEffect(customerId) {
        viewModel.load(customerId)
    }

    Scaffold(
        topBar = {
            LpmsTopBar(
                title = stringResource(
                    if (isNew) R.string.customer_new else R.string.customer_edit,
                ),
                onBack = onBack,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LpmsTextField(
                value = viewModel.name,
                onValueChange = viewModel::onNameChange,
                label = stringResource(R.string.customer_name),
                required = true,
                error = viewModel.fieldErrors["name"],
            )

            LpmsTextField(
                value = viewModel.phone,
                onValueChange = viewModel::onPhoneChange,
                label = stringResource(R.string.customer_phone),
            )

            LpmsTextField(
                value = viewModel.address,
                onValueChange = viewModel::onAddressChange,
                label = stringResource(R.string.customer_address),
            )

            LpmsTextField(
                value = viewModel.notes,
                onValueChange = viewModel::onNotesChange,
                label = stringResource(R.string.customer_notes),
                singleLine = false,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.customer_active),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Switch(
                    checked = viewModel.active,
                    onCheckedChange = viewModel::onActiveChange,
                )
            }

            if (viewModel.error != null) {
                Text(
                    text = viewModel.errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(8.dp))

            LpmsPrimaryButton(
                text = stringResource(R.string.action_save),
                onClick = { viewModel.save(onBack) },
                enabled = !viewModel.saving,
                loading = viewModel.saving,
            )
        }
    }
}