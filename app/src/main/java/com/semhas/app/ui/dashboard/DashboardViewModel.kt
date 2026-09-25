package com.semhas.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.semhas.app.data.model.Billing
import com.semhas.app.data.model.Channel
import com.semhas.app.data.model.Device
import com.semhas.app.data.model.EnergyUsage
import com.semhas.app.data.model.Notification
import com.semhas.app.data.repository.SemhasRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DashboardUiState(
    val device: Device = Device(),
    val channels: List<Channel> = emptyList(),
    val totalPowerWatts: Double = 0.0,
    val averageVoltage: Double = 230.0,
    val totalCurrentAmps: Double = 0.0,
    val activeChannelsCount: Int = 0,
    val todayEnergyUsage: EnergyUsage = EnergyUsage("Today", 0.0, 0.0, 0.008),
    val billing: Billing = Billing("", "", 0.008, 0.0, 0.0),
    val alerts: List<Notification> = emptyList(),
    val isLoading: Boolean = false
)

class DashboardViewModel(
    private val repository: SemhasRepository
) : ViewModel() {

    val uiState: StateFlow<DashboardUiState> = combine(
        repository.device,
        repository.channels,
        repository.energyUsage,
        repository.billing,
        repository.notifications
    ) { device, channels, energyUsage, billing, notifications ->
        val activeChannels = channels.filter { it.relayState }
        val totalPower = activeChannels.sumOf { it.power }
        val totalCurrent = activeChannels.sumOf { it.current }
        val avgVoltage = if (channels.isNotEmpty()) channels.map { it.voltage }.average() else 230.0
        val recentAlerts = notifications.filter { it.severity == "WARNING" || it.severity == "ERROR" }

        DashboardUiState(
            device = device,
            channels = channels,
            totalPowerWatts = Math.round(totalPower * 10.0) / 10.0,
            averageVoltage = Math.round(avgVoltage * 10.0) / 10.0,
            totalCurrentAmps = Math.round(totalCurrent * 100.0) / 100.0,
            activeChannelsCount = activeChannels.size,
            todayEnergyUsage = energyUsage,
            billing = billing,
            alerts = recentAlerts,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState(isLoading = true)
    )

    fun toggleChannel(channelId: Int, currentState: Boolean) {
        viewModelScope.launch {
            repository.toggleChannel(channelId, !currentState, "UI_DASHBOARD")
        }
    }

    fun setChannelState(channelId: Int, targetState: Boolean) {
        viewModelScope.launch {
            repository.toggleChannel(channelId, targetState, "UI_DASHBOARD")
        }
    }

    fun setChannelIntensity(channelId: Int, intensity: Int) {
        viewModelScope.launch {
            repository.setChannelIntensity(channelId, intensity)
        }
    }
}
