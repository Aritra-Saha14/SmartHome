package com.semhas.app.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.semhas.app.data.model.Notification
import com.semhas.app.data.repository.SemhasRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class NotificationsUiState(
    val notifications: List<Notification> = emptyList(),
    val unreadCount: Int = 0,
    val isLoading: Boolean = false
)

class NotificationsViewModel(
    private val repository: SemhasRepository
) : ViewModel() {

    val uiState: StateFlow<NotificationsUiState> = repository.notifications.map { list ->
        NotificationsUiState(
            notifications = list,
            unreadCount = list.count { !it.isRead },
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = NotificationsUiState(isLoading = true)
    )

    fun markAsRead(id: String) {
        viewModelScope.launch {
            repository.markNotificationAsRead(id)
        }
    }

    fun markAllAsRead() {
        viewModelScope.launch {
            repository.clearAllNotifications()
        }
    }
}
