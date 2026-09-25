package com.semhas.app.ui.monitoring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.semhas.app.data.model.Channel
import com.semhas.app.data.repository.SemhasRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MonitoringUiState(
    val channels: List<Channel> = emptyList(),
    val totalPower: Double = 0.0,
    val totalEnergy: Double = 0.0,
    val totalEstimatedCost: Double = 0.0,
    val activeChannelsCount: Int = 0,
    val isLoading: Boolean = false
)

class MonitoringViewModel(
    private val repository: SemhasRepository
) : ViewModel() {

    val uiState: StateFlow<MonitoringUiState> = repository.channels.map { channels ->
        val activeCount = channels.count { it.relayState }
        val totalPwr = channels.filter { it.relayState }.sumOf { it.power }
        val totalE = channels.sumOf { it.energy }
        val totalCost = channels.sumOf { it.estimatedCost }

        MonitoringUiState(
            channels = channels,
            totalPower = Math.round(totalPwr * 10.0) / 10.0,
            totalEnergy = totalE,
            totalEstimatedCost = totalCost,
            activeChannelsCount = activeCount,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MonitoringUiState(isLoading = true)
    )

    fun toggleChannel(channelId: Int, currentState: Boolean) {
        viewModelScope.launch {
            repository.toggleChannel(channelId, !currentState, "UI_MONITORING")
        }
    }

    fun setChannelState(channelId: Int, targetState: Boolean) {
        viewModelScope.launch {
            repository.toggleChannel(channelId, targetState, "UI_MONITORING")
        }
    }
}

