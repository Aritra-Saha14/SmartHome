package com.semhas.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.semhas.app.data.model.HistoryEvent
import com.semhas.app.data.repository.SemhasRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HistoryUiState(
    val events: List<HistoryEvent> = emptyList(),
    val selectedFilter: String = "ALL", // "ALL", "CONTROL", "ALERT", "SYSTEM"
    val isLoading: Boolean = false
)

class HistoryViewModel(
    private val repository: SemhasRepository
) : ViewModel() {

    private val _selectedFilter = MutableStateFlow("ALL")
    val selectedFilter: StateFlow<String> = _selectedFilter.asStateFlow()

    val uiState: StateFlow<HistoryUiState> = combine(
        repository.historyEvents,
        _selectedFilter
    ) { events, filter ->
        val filtered = if (filter == "ALL") {
            events
        } else {
            events.filter { it.eventType.equals(filter, ignoreCase = true) }
        }

        HistoryUiState(
            events = filtered,
            selectedFilter = filter,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HistoryUiState(isLoading = true)
    )

    fun selectFilter(filter: String) {
        _selectedFilter.value = filter
    }
}
