package com.lpms.ui.screens.drugs

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

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lpms.R
import com.lpms.data.remote.dto.DosageForm
import com.lpms.ui.components.LpmsDropdownField
import com.lpms.ui.components.LpmsPrimaryButton
import com.lpms.ui.components.LpmsTextField
import com.lpms.ui.components.LpmsTopBar

/**
 * Create or edit a drug.
 *
 * A new drug is saved locally and queued; it appears in the catalog
 * immediately and is pushed to the server by the sync engine. Editing a drug
 * the server has never seen still works — the queue collapses the update into
 * a create on replay.
 */
@Composable
fun DrugEditScreen(
    drugId: String,
    onBack: () -> Unit,
    viewModel: DrugEditViewModel = hiltViewModel(),
) {
    val isNew = drugId == "new"

    androidx.compose.runtime.LaunchedEffect(drugId) {
        viewModel.load(drugId)
    }

    Scaffold(
        topBar = {
            LpmsTopBar(
                title = stringResource(
                    if (isNew) R.string.drug_new else R.string.drug_edit,
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
                label = stringResource(R.string.drug_name),
                hint = stringResource(R.string.drug_name_hint),
                required = true,
                error = viewModel.fieldErrors["name"],
            )

            LpmsTextField(
                value = viewModel.genericName,
                onValueChange = viewModel::onGenericNameChange,
                label = stringResource(R.string.drug_generic_name),
            )

            LpmsTextField(
                value = viewModel.barcode,
                onValueChange = viewModel::onBarcodeChange,
                label = stringResource(R.string.drug_barcode),
            )

            LpmsTextField(
                value = viewModel.manufacturer,
                onValueChange = viewModel::onManufacturerChange,
                label = stringResource(R.string.drug_manufacturer),
            )

            LpmsTextField(
                value = viewModel.category,
                onValueChange = viewModel::onCategoryChange,
                label = stringResource(R.string.drug_category),
            )

            LpmsDropdownField(
                value = viewModel.dosageForm,
                onValueChange = viewModel::onDosageFormChange,
                label = stringResource(R.string.drug_dosage_form),
                options = DosageForm.entries.map { it.name },
            )

            LpmsTextField(
                value = viewModel.strength,
                onValueChange = viewModel::onStrengthChange,
                label = stringResource(R.string.drug_strength),
            )

            LpmsTextField(
                value = viewModel.unit,
                onValueChange = viewModel::onUnitChange,
                label = stringResource(R.string.drug_unit),
            )

            LpmsTextField(
                value = viewModel.description,
                onValueChange = viewModel::onDescriptionChange,
                label = stringResource(R.string.drug_description),
                singleLine = false,
            )

            LpmsTextField(
                value = viewModel.minimumStockLevel,
                onValueChange = viewModel::onMinStockChange,
                label = stringResource(R.string.drug_min_stock),
                keyboardType = KeyboardType.Number,
            )

            if (isNew) {
                LpmsTextField(
                    value = viewModel.initialStock,
                    onValueChange = viewModel::onInitialStockChange,
                    label = stringResource(R.string.drug_initial_stock),
                    keyboardType = KeyboardType.Number,
                )

                LpmsTextField(
                    value = viewModel.initialStockPrice,
                    onValueChange = viewModel::onInitialStockPriceChange,
                    label = stringResource(R.string.drug_initial_stock_price),
                    keyboardType = KeyboardType.Decimal,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.drug_active),
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
