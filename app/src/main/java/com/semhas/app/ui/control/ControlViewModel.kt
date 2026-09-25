package com.semhas.app.ui.control

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.semhas.app.data.model.Channel
import com.semhas.app.data.repository.SemhasRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ControlUiState(
    val channels: List<Channel> = emptyList(),
    val editingChannel: Channel? = null,
    val isLoading: Boolean = false
)

class ControlViewModel(
    private val repository: SemhasRepository
) : ViewModel() {

    private val _editingChannel = MutableStateFlow<Channel?>(null)
    val editingChannel: StateFlow<Channel?> = _editingChannel.asStateFlow()

    val uiState: StateFlow<ControlUiState> = combine(
        repository.channels,
        _editingChannel
    ) { channels, editingChannel ->
        ControlUiState(
            channels = channels,
            editingChannel = editingChannel,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ControlUiState(isLoading = true)
    )

    fun toggleChannel(channelId: Int, currentState: Boolean) {
        viewModelScope.launch {
            repository.toggleChannel(channelId, !currentState, "UI_CONTROL")
        }
    }

    fun setChannelState(channelId: Int, targetState: Boolean) {
        viewModelScope.launch {
            repository.toggleChannel(channelId, targetState, "UI_CONTROL")
        }
    }

    fun startRenaming(channel: Channel) {
        _editingChannel.value = channel
    }

    fun dismissRenaming() {
        _editingChannel.value = null
    }

    fun saveRenamedChannel(channelId: Int, newName: String) {
        viewModelScope.launch {
            repository.renameChannel(channelId, newName)
            _editingChannel.value = null
        }
    }

    fun setAllChannels(state: Boolean) {
        viewModelScope.launch {
            repository.setAllChannels(state)
        }
    }

    fun setChannelIntensity(channelId: Int, intensity: Int) {
        viewModelScope.launch {
            repository.setChannelIntensity(channelId, intensity)
        }
    }
}
