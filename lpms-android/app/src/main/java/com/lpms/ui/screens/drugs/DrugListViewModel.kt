package com.lpms.ui.screens.drugs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lpms.data.local.DrugEntity
import com.lpms.data.repository.DrugRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Backs [DrugListScreen]. The catalog is a Room flow, so the list updates
 * the moment a drug is created or edited anywhere in the app.
 */
@HiltViewModel
class DrugListViewModel @Inject constructor(
    drugRepository: DrugRepository,
) : ViewModel() {

    val drugs: StateFlow<List<DrugEntity>> =
        drugRepository.observeCatalog("")
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
