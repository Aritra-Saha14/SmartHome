package com.semhas.app.ui.billing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.semhas.app.data.model.Billing
import com.semhas.app.data.model.Channel
import com.semhas.app.data.model.EnergyUsage
import com.semhas.app.data.repository.SemhasRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BillingUiState(
    val billing: Billing = Billing("", "", 8.0, 0.0, 0.0),
    val todayEnergyUsage: EnergyUsage = EnergyUsage("Today", 0.0, 0.0, 8.0),
    val channels: List<Channel> = emptyList(),
    val isEditingRate: Boolean = false,
    val isLoading: Boolean = false
)

class BillingViewModel(
    private val repository: SemhasRepository
) : ViewModel() {

    private val _isEditingRate = MutableStateFlow(false)
    val isEditingRate: StateFlow<Boolean> = _isEditingRate.asStateFlow()

    val uiState: StateFlow<BillingUiState> = combine(
        repository.billing,
        repository.energyUsage,
        repository.channels,
        _isEditingRate
    ) { billing, energyUsage, channels, isEditing ->
        BillingUiState(
            billing = billing,
            todayEnergyUsage = energyUsage,
            channels = channels,
            isEditingRate = isEditing,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = BillingUiState(isLoading = true)
    )

    fun startEditingRate() {
        _isEditingRate.value = true
    }

    fun dismissEditingRate() {
        _isEditingRate.value = false
    }

    fun updateRate(newRate: Double) {
        viewModelScope.launch {
            repository.setElectricityRate(newRate)
            _isEditingRate.value = false
        }
    }
}
