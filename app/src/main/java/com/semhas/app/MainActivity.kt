package com.semhas.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.semhas.app.data.mock.MockSemhasRepository
import com.semhas.app.navigation.AppNavigation
import com.semhas.app.ui.analytics.AnalyticsViewModel
import com.semhas.app.ui.billing.BillingViewModel
import com.semhas.app.ui.control.ControlViewModel
import com.semhas.app.ui.dashboard.DashboardViewModel
import com.semhas.app.ui.health.HealthViewModel
import com.semhas.app.ui.history.HistoryViewModel
import com.semhas.app.ui.monitoring.MonitoringViewModel
import com.semhas.app.ui.notifications.NotificationsViewModel
import com.semhas.app.ui.settings.SettingsViewModel
import com.semhas.app.ui.settings.ThemeMode
import com.semhas.app.ui.theme.SemhasTheme
import com.semhas.app.ui.voice.VoiceViewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.semhas.app.data.repository.SemhasRepository
import com.semhas.app.voice.VoiceAssistantManager

class SemhasViewModelFactory(
    private val repository: SemhasRepository,
    private val voiceAssistantManager: VoiceAssistantManager
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(DashboardViewModel::class.java) -> DashboardViewModel(repository) as T
            modelClass.isAssignableFrom(MonitoringViewModel::class.java) -> MonitoringViewModel(repository) as T
            modelClass.isAssignableFrom(ControlViewModel::class.java) -> ControlViewModel(repository) as T
            modelClass.isAssignableFrom(AnalyticsViewModel::class.java) -> AnalyticsViewModel(repository) as T
            modelClass.isAssignableFrom(HistoryViewModel::class.java) -> HistoryViewModel(repository) as T
            modelClass.isAssignableFrom(BillingViewModel::class.java) -> BillingViewModel(repository) as T
            modelClass.isAssignableFrom(NotificationsViewModel::class.java) -> NotificationsViewModel(repository) as T
            modelClass.isAssignableFrom(HealthViewModel::class.java) -> HealthViewModel(repository) as T
            modelClass.isAssignableFrom(VoiceViewModel::class.java) -> VoiceViewModel(repository, voiceAssistantManager) as T
            modelClass.isAssignableFrom(SettingsViewModel::class.java) -> SettingsViewModel(repository) as T
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            // Obtain shared repository and voice assistant manager from SemhasApplication
            val semhasApp = application as SemhasApplication
            val repository = semhasApp.repository
            val voiceAssistantManager = semhasApp.voiceAssistantManager
            val factory = remember { SemhasViewModelFactory(repository, voiceAssistantManager) }

            // Lifecycle-aware ViewModel instances using standard Android ViewModelProvider
            val dashboardViewModel: DashboardViewModel = viewModel(factory = factory)
            val monitoringViewModel: MonitoringViewModel = viewModel(factory = factory)
            val controlViewModel: ControlViewModel = viewModel(factory = factory)
            val analyticsViewModel: AnalyticsViewModel = viewModel(factory = factory)
            val historyViewModel: HistoryViewModel = viewModel(factory = factory)
            val billingViewModel: BillingViewModel = viewModel(factory = factory)
            val notificationsViewModel: NotificationsViewModel = viewModel(factory = factory)
            val healthViewModel: HealthViewModel = viewModel(factory = factory)
            val voiceViewModel: VoiceViewModel = viewModel(factory = factory)
            val settingsViewModel: SettingsViewModel = viewModel(factory = factory)

            val settingsState by settingsViewModel.uiState.collectAsState()
            val isDarkTheme = when (settingsState.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            SemhasTheme(darkTheme = isDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    AppNavigation(
                        navController = navController,
                        repository = repository,
                        dashboardViewModel = dashboardViewModel,
                        monitoringViewModel = monitoringViewModel,
                        controlViewModel = controlViewModel,
                        analyticsViewModel = analyticsViewModel,
                        historyViewModel = historyViewModel,
                        billingViewModel = billingViewModel,
                        notificationsViewModel = notificationsViewModel,
                        healthViewModel = healthViewModel,
                        voiceViewModel = voiceViewModel,
                        settingsViewModel = settingsViewModel
                    )
                }
            }
        }
    }
}
