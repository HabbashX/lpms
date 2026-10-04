package com.lpms.ui.screens.customers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lpms.R
import com.lpms.data.local.CustomerEntity
import com.lpms.data.repository.CustomerAccountSnapshot
import com.lpms.data.remote.dto.TransactionResponse
import com.lpms.ui.components.LpmsCard
import com.lpms.ui.components.LpmsDetailRow
import com.lpms.ui.components.LpmsEmptyState
import com.lpms.ui.components.LpmsLoading
import com.lpms.ui.components.LpmsDropdownField
import com.lpms.ui.components.LpmsPrimaryButton
import com.lpms.ui.components.LpmsSecondaryButton
import com.lpms.ui.components.LpmsTextField
import com.lpms.ui.components.LpmsTopBar
import com.lpms.ui.components.money
import com.lpms.ui.components.shortDate

/**
 * A customer's account: who they are, what they owe, and the ledger.
 *
 * This is the debit screen. The owner records a payment when the customer
 * settles part of their debt, or an adjustment to correct the balance. The
 * account itself is a server-side aggregate, so it is fetched over the network;
 * the customer's cached debt is what shows while offline.
 */
@Composable
fun CustomerDetailScreen(
    customerId: String,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: CustomerDetailViewModel = hiltViewModel(),
) {
    val customer by viewModel.customer.collectAsStateWithLifecycle()
    val account by viewModel.account.collectAsStateWithLifecycle()
    val notFound by remember {
        derivedStateOf { viewModel.error != null && customer == null }
    }

    var showPaymentDialog by remember { mutableStateOf(false) }
    var showAdjustmentDialog by remember { mutableStateOf(false) }

    LaunchedEffect(customerId) { viewModel.load(customerId) }

    Scaffold(
        topBar = {
            LpmsTopBar(
                title = customer?.name ?: stringResource(R.string.customer_account),
                onBack = onBack,
            )
        },
    ) { padding ->
        when {
            // A missing customer must not leave this screen spinning forever.
            notFound -> LpmsEmptyState(
                title = stringResource(R.string.customer_not_found),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )

            customer == null -> LpmsLoading(modifier = Modifier.padding(padding))
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CustomerSummaryCard(customer = customer!!, account = account)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        LpmsPrimaryButton(
                            text = stringResource(R.string.customer_record_payment),
                            onClick = { showPaymentDialog = true },
                            modifier = Modifier.weight(1f),
                        )
                        LpmsSecondaryButton(
                            text = stringResource(R.string.customer_record_adjustment),
                            onClick = { showAdjustmentDialog = true },
                            modifier = Modifier.weight(1f),
                        )
                    }

                    Text(
                        text = stringResource(R.string.customer_transactions),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    if (account == null || account!!.transactions.isEmpty()) {
                        Text(
                            text = stringResource(R.string.customer_transactions_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        account!!.transactions.forEach { transaction ->
                            TransactionRow(transaction = transaction)
                        }
                    }
                }
            }
        }
    }

    if (showPaymentDialog) {
        PaymentDialog(
            onDismiss = { showPaymentDialog = false },
            onConfirm = { amount, method, notes ->
                viewModel.recordPayment(amount, method, notes)
                showPaymentDialog = false
            },
        )
    }

    if (showAdjustmentDialog) {
        AdjustmentDialog(
            onDismiss = { showAdjustmentDialog = false },
            onConfirm = { amount, direction, description ->
                viewModel.recordAdjustment(amount, direction, description)
                showAdjustmentDialog = false
            },
        )
    }
}

@Composable
fun CustomerSummaryCard(
    customer: CustomerEntity,
    account: CustomerAccountSnapshot?,
) {
    LpmsCard {
        LpmsDetailRow(
            label = stringResource(R.string.customer_name),
            value = customer.name,
        )
        if (customer.phone != null) {
            LpmsDetailRow(
                label = stringResource(R.string.customer_phone),
                value = customer.phone,
            )
        }
        if (customer.address != null) {
            LpmsDetailRow(
                label = stringResource(R.string.customer_address),
                value = customer.address,
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        LpmsDetailRow(
            label = stringResource(R.string.customer_total_purchases),
            value = money(account?.totalPurchases),
        )
        LpmsDetailRow(
            label = stringResource(R.string.customer_total_paid),
            value = money(account?.totalPaid),
        )
        LpmsDetailRow(
            label = stringResource(R.string.customer_total_refunds),
            value = money(account?.totalRefunds),
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        val debt = account?.currentDebt ?: customer.totalDebt ?: 0.0
        LpmsDetailRow(
            label = stringResource(R.string.customer_current_debt),
            value = money(debt),
            valueColor = if (debt > 0) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

@Composable
fun TransactionRow(transaction: TransactionResponse) {
    LpmsCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transaction.type,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = transaction.description ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = shortDate(
                        com.lpms.data.local.parseIsoToMillis(transaction.date),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                if (transaction.debit > 0) {
                    Text(
                        text = "-${money(transaction.debit)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    Text(
                        text = "+${money(transaction.credit)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    text = money(transaction.balance),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun PaymentDialog(
    onDismiss: () -> Unit,
    onConfirm: (Double, String, String?) -> Unit,
) {
    var amount by remember { mutableStateOf("") }
    var method by remember { mutableStateOf("CASH") }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.customer_record_payment)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LpmsTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = stringResource(R.string.customer_amount),
                    keyboardType = KeyboardType.Decimal,
                    required = true,
                )
                LpmsDropdownField(
                    value = method,
                    onValueChange = { method = it },
                    label = stringResource(R.string.pos_payment_method),
                    options = listOf("CASH", "CARD", "BANK_TRANSFER", "INSURANCE"),
                )
                LpmsTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = stringResource(R.string.customer_notes),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val value = amount.toDoubleOrNull() ?: 0.0
                    if (value > 0) onConfirm(value, method, notes.ifBlank { null })
                },
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
fun AdjustmentDialog(
    onDismiss: () -> Unit,
    onConfirm: (Double, String, String) -> Unit,
) {
    var amount by remember { mutableStateOf("") }
    var direction by remember { mutableStateOf("DEBIT") }
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.customer_record_adjustment)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LpmsTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = stringResource(R.string.customer_amount),
                    keyboardType = KeyboardType.Decimal,
                    required = true,
                )
                LpmsDropdownField(
                    value = direction,
                    onValueChange = { direction = it },
                    label = stringResource(R.string.customer_adjustment_direction),
                    options = listOf("DEBIT", "CREDIT"),
                )
                LpmsTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = stringResource(R.string.customer_adjustment_description),
                    required = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val value = amount.toDoubleOrNull() ?: 0.0
                    if (value > 0 && description.isNotBlank()) {
                        onConfirm(value, direction, description)
                    }
                },
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}