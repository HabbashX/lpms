package com.lpms.ui.screens.sales

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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lpms.R
import com.lpms.data.local.SaleEntity
import com.lpms.data.local.SaleItemEntity
import com.lpms.data.repository.RefundLine
import com.lpms.data.remote.dto.SaleStatus
import com.lpms.ui.components.LpmsCard
import com.lpms.ui.components.LpmsDetailRow
import com.lpms.ui.components.LpmsEmptyState
import com.lpms.ui.components.LpmsLoading
import com.lpms.ui.components.LpmsPrimaryButton
import com.lpms.ui.components.LpmsTopBar
import com.lpms.ui.components.money
import com.lpms.ui.components.shortDate

/**
 * A single sale: what was sold, what was paid, and — for a sale the server
 * knows about — the option to refund part of it.
 *
 * Refunds are online-only: they need the server's sale-item ids and the server
 * recomputes stock from its own batch ledger. An offline sale must sync first.
 */
@Composable
fun SaleDetailScreen(
    saleId: String,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: SaleDetailViewModel = hiltViewModel(),
) {
    val sale by viewModel.sale.collectAsStateWithLifecycle()
    val items by viewModel.items.collectAsStateWithLifecycle()
    val error by remember { derivedStateOf { viewModel.error } }

    var showRefundDialog by remember { mutableStateOf(false) }

    // The sale is read from Room, so this has to be requested explicitly — without it
    // `sale` stays null and the screen never gets past its loading state.
    LaunchedEffect(saleId) { viewModel.load(saleId) }

    Scaffold(
        topBar = {
            LpmsTopBar(
                title = sale?.let { shortDate(it.createdAt) }
                    ?: stringResource(R.string.sales_title),
                onBack = onBack,
            )
        },
    ) { padding ->
        val notFound = error != null && sale == null
        when {
            notFound -> LpmsEmptyState(
                title = stringResource(R.string.sale_not_found),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )

            sale == null -> LpmsLoading(modifier = Modifier.padding(padding))
            else -> {
                val currentSale = sale!!

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    LpmsCard {
                        LpmsDetailRow(
                            label = stringResource(R.string.sale_customer),
                            value = currentSale.customerName ?: stringResource(R.string.sale_cash_customer),
                        )
                        LpmsDetailRow(
                            label = stringResource(R.string.sale_created_at),
                            value = shortDate(currentSale.createdAt),
                        )
                        LpmsDetailRow(
                            label = stringResource(R.string.sale_payment_method),
                            value = currentSale.paymentMethod,
                        )
                        LpmsDetailRow(
                            label = stringResource(R.string.sale_created_by),
                            value = currentSale.createdBy ?: "—",
                        )
                        if (currentSale.status == SaleStatus.PARTIALLY_REFUNDED.name ||
                            currentSale.status == SaleStatus.REFUNDED.name
                        ) {
                            LpmsDetailRow(
                                label = stringResource(R.string.sale_refunded_total),
                                value = money(currentSale.refundedTotal),
                            )
                        }
                    }

                    Text(
                        text = stringResource(R.string.sale_items),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    items.forEach { item ->
                        SaleItemCard(item = item)
                    }

                    HorizontalDivider()

                    LpmsCard {
                        LpmsDetailRow(
                            label = stringResource(R.string.sale_subtotal),
                            value = money(currentSale.subtotal),
                        )
                        LpmsDetailRow(
                            label = stringResource(R.string.sale_discount),
                            value = money(currentSale.discount),
                        )
                        LpmsDetailRow(
                            label = stringResource(R.string.sale_total),
                            value = money(currentSale.total),
                        )
                        LpmsDetailRow(
                            label = stringResource(R.string.sale_paid),
                            value = money(currentSale.amountPaid),
                        )
                        LpmsDetailRow(
                            label = stringResource(R.string.sale_due),
                            value = money(currentSale.amountDue),
                        )
                    }

                    if (currentSale.serverId != null &&
                        currentSale.status != SaleStatus.REFUNDED.name
                    ) {
                        LpmsPrimaryButton(
                            text = stringResource(R.string.sale_refund),
                            onClick = { showRefundDialog = true },
                        )
                    }
                }
            }
        }
    }

    if (showRefundDialog) {
        RefundDialog(
            items = items,
            onDismiss = { showRefundDialog = false },
            onConfirm = { lines, reason ->
                viewModel.refund(lines, reason)
                showRefundDialog = false
            },
        )
    }
}

@Composable
fun SaleItemCard(item: SaleItemEntity) {
    LpmsCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = item.drugName, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = "${item.quantity} × ${money(item.unitSellingPrice)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (item.refundedQuantity > 0) {
                    Text(
                        text = stringResource(R.string.sale_refunded_qty, item.refundedQuantity),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Text(text = money(item.revenue), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun RefundDialog(
    items: List<SaleItemEntity>,
    onDismiss: () -> Unit,
    onConfirm: (List<RefundLine>, String?) -> Unit,
) {
    val refundable = items.filter {
        it.serverSaleItemId != null && it.refundedQuantity < it.quantity
    }
    val quantities = remember {
        mutableStateOf(
            refundable.associate { it.serverSaleItemId!! to it.quantity - it.refundedQuantity },
        )
    }
    var reason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sale_refund_title, "")) },
        text = {
            Column {
                refundable.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = item.drugName,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = quantities.value[item.serverSaleItemId!!].toString(),
                            onValueChange = { text ->
                                val qty = text.toIntOrNull() ?: 0
                                val max = item.quantity - item.refundedQuantity
                                quantities.value = quantities.value.toMutableMap().apply {
                                    put(item.serverSaleItemId!!, qty.coerceIn(0, max))
                                }
                            },
                            modifier = Modifier.padding(start = 8.dp),
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                            ),
                        )
                    }
                }
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.sale_refund_reason)) },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val lines = refundable.mapNotNull { item ->
                        val qty = quantities.value[item.serverSaleItemId!!] ?: 0
                        if (qty > 0) {
                            RefundLine(
                                serverSaleItemId = item.serverSaleItemId,
                                quantity = qty,
                            )
                        } else {
                            null
                        }
                    }
                    if (lines.isNotEmpty()) onConfirm(lines, reason.ifBlank { null })
                },
            ) {
                Text(stringResource(R.string.sale_refund_submit))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}