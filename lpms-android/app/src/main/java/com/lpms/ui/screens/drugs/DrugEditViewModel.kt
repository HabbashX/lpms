package com.lpms.ui.screens.drugs

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lpms.data.remote.ApiError
import com.lpms.data.remote.ApiResult
import com.lpms.data.repository.DrugDraft
import com.lpms.data.repository.DrugRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * Backs [DrugEditScreen]. Loads the existing drug when editing, and on save
 * writes to Room first and lets the sync engine push the change.
 */
@HiltViewModel
class DrugEditViewModel @Inject constructor(
    private val drugRepository: DrugRepository,
) : ViewModel() {

    var name by mutableStateOf("")
        private set
    var genericName by mutableStateOf("")
        private set
    var barcode by mutableStateOf("")
        private set
    var manufacturer by mutableStateOf("")
        private set
    var category by mutableStateOf("")
        private set
    var dosageForm by mutableStateOf("")
        private set
    var strength by mutableStateOf("")
        private set
    var unit by mutableStateOf("")
        private set
    var description by mutableStateOf("")
        private set
    var minimumStockLevel by mutableStateOf("")
        private set
    var active by mutableStateOf(true)
        private set
    var initialStock by mutableStateOf("")
        private set
    var initialStockPrice by mutableStateOf("")
        private set

    var saving by mutableStateOf(false)
        private set
    var error by mutableStateOf<ApiError?>(null)
        private set
    var fieldErrors by mutableStateOf<Map<String, String>>(emptyMap())
        private set

    private var localId: String? = null
    private var loaded = false

    /** Loads the drug once, so recomposition does not reset the form. */
    fun load(drugId: String) {
        if (loaded) return
        loaded = true
        if (drugId == "new") return

        viewModelScope.launch {
            val drug = drugRepository.byLocalId(drugId)
            if (drug == null) {
                error = ApiError.Http(404, "NOT_FOUND", "drug_not_found")
                return@launch
            }
            localId = drug.localId
            name = drug.name
            genericName = drug.genericName ?: ""
            barcode = drug.barcode ?: ""
            manufacturer = drug.manufacturer ?: ""
            category = drug.category ?: ""
            dosageForm = drug.dosageForm ?: ""
            strength = drug.strength ?: ""
            unit = drug.unit ?: ""
            description = drug.description ?: ""
            minimumStockLevel = drug.minimumStockLevel.toString()
            active = drug.active
        }
    }

    fun onNameChange(value: String) { name = value; clearErrors() }
    fun onGenericNameChange(value: String) { genericName = value }
    fun onBarcodeChange(value: String) { barcode = value }
    fun onManufacturerChange(value: String) { manufacturer = value }
    fun onCategoryChange(value: String) { category = value }
    fun onDosageFormChange(value: String) { dosageForm = value }
    fun onStrengthChange(value: String) { strength = value }
    fun onUnitChange(value: String) { unit = value }
    fun onDescriptionChange(value: String) { description = value }
    fun onMinStockChange(value: String) { minimumStockLevel = value }
    fun onActiveChange(value: Boolean) { active = value }
    fun onInitialStockChange(value: String) { initialStock = value }
    fun onInitialStockPriceChange(value: String) { initialStockPrice = value }

    private fun clearErrors() {
        error = null
        fieldErrors = emptyMap()
    }

    val errorMessage: String
        get() = error?.let {
            when (it) {
                is ApiError.Http -> it.message ?: "error_generic"
                else -> "error_generic"
            }
        } ?: ""

    fun save(onBack: () -> Unit) {
        if (saving) return
        if (name.isBlank()) {
            fieldErrors = mapOf("name" to "__required__")
            return
        }

        saving = true
        error = null
        fieldErrors = emptyMap()

        val draft = DrugDraft(
            name = name.trim(),
            genericName = genericName.trim().ifEmpty { null },
            barcode = barcode.trim().ifEmpty { null },
            manufacturer = manufacturer.trim().ifEmpty { null },
            category = category.trim().ifEmpty { null },
            dosageForm = dosageForm.trim().ifEmpty { null },
            strength = strength.trim().ifEmpty { null },
            unit = unit.trim().ifEmpty { null },
            description = description.trim().ifEmpty { null },
            minimumStockLevel = minimumStockLevel.toIntOrNull() ?: 0,
            active = active,
            initialStock = initialStock.toIntOrNull() ?: 0,
            initialStockPrice = initialStockPrice.toDoubleOrNull() ?: 0.0,
        )

        viewModelScope.launch {
            when (val result = drugRepository.save(localId, draft)) {
                is ApiResult.Success -> {
                    saving = false
                    onBack()
                }
                is ApiResult.Failure -> {
                    saving = false
                    error = result.error
                    fieldErrors = if (result.error is ApiError.Http) {
                        result.error.fieldErrors
                    } else {
                        emptyMap()
                    }
                }
            }
        }
    }
}
