package com.lpms.ui.screens.pos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.QrCodeScanner
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lpms.R
import com.lpms.data.local.DrugEntity
import com.lpms.data.remote.dto.PaymentMethod
import com.lpms.ui.components.BarcodeScanner
import com.lpms.ui.components.LpmsDropdownField
import com.lpms.ui.components.LpmsPrimaryButton
import com.lpms.ui.components.LpmsSecondaryButton
import com.lpms.ui.components.LpmsTextField
import com.lpms.ui.components.LpmsTopBar
import com.lpms.ui.components.money
import com.lpms.ui.components.shortDate

/**
 * The point of sale: pick drugs, take payment, done.
 *
 * The screen is split into a searchable catalog on one side and the running
 * cart on the other, so a sale is a sequence of taps with no intermediate
 * screens. Everything here works offline — the catalog comes from Room and the
 * sale is queued, not sent.
 */
@Composable
fun PosScreen(
    onNavigate: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: PosViewModel = hiltViewModel(),
) {
    val drugs by viewModel.drugs.collectAsStateWithLifecycle()
    val customers by viewModel.customers.collectAsStateWithLifecycle()
    val online by viewModel.online.collectAsStateWithLifecycle()

    var showScanner by remember { mutableStateOf(false) }
    var showCustomerPicker by remember { mutableStateOf(false) }
    var showReceipt by remember { mutableStateOf(false) }

    val completedSaleId = viewModel.completedSaleId
    if (completedSaleId != null && !showReceipt) {
        showReceipt = true
    }

    if (showScanner) {
        BarcodeScanner(
            onDetected = { code ->
                showScanner = false
                viewModel.onSearchChange(code)
            },
            modifier = Modifier.fillMaxSize(),
        )
        return
    }

    Scaffold(
        topBar = {
            LpmsTopBar(
                title = stringResource(R.string.pos_title),
                onBack = onBack,
                actions = {
                    IconButton(onClick = { showScanner = true }) {
                        Icon(
                            imageVector = Icons.Filled.QrCodeScanner,
                            contentDescription = stringResource(R.string.barcode_scan),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (!online) {
                OfflineBanner()
            }

            PosSearchField(
                value = viewModel.search,
                onValueChange = viewModel::onSearchChange,
            )

            val filtered = viewModel.filteredDrugs()

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                items(filtered, key = { it.localId }) { drug ->
                    DrugRow(
                        drug = drug,
                        onAdd = { viewModel.addToCart(drug) },
                    )
                }
            }

            CartSection(
                viewModel = viewModel,
                onShowCustomerPicker = { showCustomerPicker = true },
            )
        }
    }

    if (showCustomerPicker) {
        CustomerPickerDialog(
            customers = customers,
            search = viewModel.customerSearch,
            onSearchChange = viewModel::onCustomerSearchChange,
            onSelect = {
                viewModel.selectCustomer(it)
                showCustomerPicker = false
            },
            onDismiss = { showCustomerPicker = false },
        )
    }

    if (showReceipt && completedSaleId != null) {
        SaleReceiptDialog(
            total = viewModel.total,
            paid = viewModel.paidValue,
            change = viewModel.change,
            onNewSale = {
                viewModel.reset()
                showReceipt = false
            },
            onDismiss = {
                viewModel.reset()
                showReceipt = false
            },
        )
    }
}

@Composable
private fun OfflineBanner() {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(R.string.offline_banner),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun PosSearchField(
    value: String,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        placeholder = { Text(stringResource(R.string.pos_search_hint)) },
        singleLine = true,
    )
}

@Composable
private fun DrugRow(
    drug: DrugEntity,
    onAdd: () -> Unit,
) {
    val outOfStock = drug.currentQuantity <= 0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = drug.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = drug.genericName ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${drug.currentQuantity} ${drug.unit ?: ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (outOfStock) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            Text(
                text = money(drug.lastSellPrice),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(end = 8.dp),
            )

            IconButton(
                onClick = onAdd,
                enabled = !outOfStock,
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    tint = if (outOfStock) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
        }
    }
}

@Composable
private fun CartSection(
    viewModel: PosViewModel,
    onShowCustomerPicker: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            if (viewModel.cart.isEmpty()) {
                Text(
                    text = stringResource(R.string.pos_cart_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                viewModel.cart.forEach { line ->
                    CartRow(
                        line = line,
                        onQuantityChange = { qty ->
                            viewModel.updateQuantity(line.drugLocalId, qty)
                        },
                        onPriceChange = { price ->
                            viewModel.updateUnitPrice(line.drugLocalId, price)
                        },
                        onRemove = { viewModel.removeFromCart(line.drugLocalId) },
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }

            CartSummary(viewModel = viewModel)

            Spacer(Modifier.height(12.dp))

            CustomerSelector(
                customerName = viewModel.customer?.name,
                onClick = onShowCustomerPicker,
            )

            Spacer(Modifier.height(8.dp))

            PaymentMethodSelector(
                selected = viewModel.paymentMethod,
                onSelect = viewModel::onPaymentMethodChange,
            )

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LpmsTextField(
                    value = viewModel.discount,
                    onValueChange = viewModel::onDiscountChange,
                    label = stringResource(R.string.pos_discount),
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f),
                )
                LpmsTextField(
                    value = viewModel.amountPaid,
                    onValueChange = viewModel::onAmountPaidChange,
                    label = stringResource(R.string.pos_amount_paid),
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(12.dp))

            LpmsPrimaryButton(
                text = stringResource(R.string.pos_checkout),
                onClick = { viewModel.checkout() },
                enabled = viewModel.cart.isNotEmpty() && !viewModel.submitting,
                loading = viewModel.submitting,
            )
        }
    }
}

@Composable
private fun CartRow(
    line: com.lpms.data.repository.CartLine,
    onQuantityChange: (Int) -> Unit,
    onPriceChange: (Double) -> Unit,
    onRemove: () -> Unit,
) {
    var qtyText by remember(line.quantity) {
        mutableStateOf(line.quantity.toString())
    }
    var priceText by remember(line.unitSellingPrice) {
        mutableStateOf(line.unitSellingPrice.toString())
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = line.drugName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = money(line.unitSellingPrice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        OutlinedTextField(
            value = qtyText,
            onValueChange = {
                qtyText = it
                it.toIntOrNull()?.let(onQuantityChange)
            },
            modifier = Modifier.width(72.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )

        Spacer(Modifier.width(8.dp))

        OutlinedTextField(
            value = priceText,
            onValueChange = {
                priceText = it
                it.toDoubleOrNull()?.let(onPriceChange)
            },
            modifier = Modifier.width(96.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )

        Spacer(Modifier.width(8.dp))

        Text(
            text = money(line.quantity * line.unitSellingPrice),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(80.dp),
        )

        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun CartSummary(viewModel: PosViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(R.string.pos_line_total),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = money(viewModel.subtotal),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    if (viewModel.discountValue > 0) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.pos_discount),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "-${money(viewModel.discountValue)}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(R.string.sale_total),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = money(viewModel.total),
            style = MaterialTheme.typography.titleMedium,
        )
    }
    if (viewModel.paidValue > 0) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.pos_change),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = money(viewModel.change),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun CustomerSelector(
    customerName: String?,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.pos_customer),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = customerName ?: stringResource(R.string.pos_walk_in),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaymentMethodSelector(
    selected: String,
    onSelect: (String) -> Unit,
) {
    val options = PaymentMethod.entries.map { it.name }
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            readOnly = true,
            label = { Text(stringResource(R.string.pos_payment_method)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            singleLine = true,
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun CustomerPickerDialog(
    customers: List<com.lpms.data.local.CustomerEntity>,
    search: String,
    onSearchChange: (String) -> Unit,
    onSelect: (com.lpms.data.local.CustomerEntity?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pos_customer)) },
        text = {
            Column {
                OutlinedTextField(
                    value = search,
                    onValueChange = onSearchChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.customers_search_hint)) },
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { onSelect(null) }) {
                    Text(stringResource(R.string.pos_walk_in))
                }
                customers.forEach { customer ->
                    TextButton(
                        onClick = { onSelect(customer) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = customer.name,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        },
    )
}

@Composable
private fun SaleReceiptDialog(
    total: Double,
    paid: Double,
    change: Double,
    onNewSale: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pos_sale_completed)) },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.sale_total))
                    Text(money(total))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.pos_amount_paid))
                    Text(money(paid))
                }
                if (change > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(stringResource(R.string.pos_change))
                        Text(money(change))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onNewSale) {
                Text(stringResource(R.string.action_new))
            }
        },
    )
}
