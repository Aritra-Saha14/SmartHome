package com.semhas.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.semhas.app.data.repository.SemhasRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val electricityRate: Double = 0.008,
    val notificationsEnabled: Boolean = true,
    val highUsageAlertsEnabled: Boolean = true,
    val deviceFirmware: String = "v1.0.4-esp32",
    val appVersion: String = "1.0.0 (Phase 1 Mock)",
    val isEditingRate: Boolean = false
)

class SettingsViewModel(
    private val repository: SemhasRepository
) : ViewModel() {

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _notificationsEnabled = MutableStateFlow(true)
    private val _highUsageAlerts = MutableStateFlow(true)
    private val _isEditingRate = MutableStateFlow(false)

    val uiState: StateFlow<SettingsUiState> = combine(
        repository.billing,
        _themeMode,
        _notificationsEnabled,
        _highUsageAlerts,
        _isEditingRate
    ) { billing, theme, notifs, highUsage, isEditingRate ->
        SettingsUiState(
            themeMode = theme,
            electricityRate = billing.ratePerWh,
            notificationsEnabled = notifs,
            highUsageAlertsEnabled = highUsage,
            isEditingRate = isEditingRate
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        _notificationsEnabled.value = enabled
    }

    fun setHighUsageAlertsEnabled(enabled: Boolean) {
        _highUsageAlerts.value = enabled
    }

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
