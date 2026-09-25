package com.semhas.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    // 5 Primary Bottom Navigation Destinations
    object Home : Screen("home", "Home", Icons.Default.Home)
    object Monitor : Screen("monitor", "Monitor", Icons.Default.Bolt)
    object Control : Screen("control", "Control", Icons.Default.PowerSettingsNew)
    object Voice : Screen("voice", "Voice", Icons.Default.Mic)
    object More : Screen("more", "More", Icons.Default.MoreHoriz)

    // Secondary Destinations
    object Analytics : Screen("analytics", "Analytics", Icons.Default.Insights)
    object History : Screen("history", "History", Icons.Default.History)
    object Billing : Screen("billing", "Billing", Icons.Default.ReceiptLong)
    object Notifications : Screen("notifications", "Notifications", Icons.Default.Notifications)
    object Health : Screen("health", "System Health", Icons.Default.MonitorHeart)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)

    companion object {
        val bottomNavScreens: List<Screen> by lazy {
            listOf(Home, Monitor, Control, Voice, More)
        }
        val moreMenuScreens: List<Screen> by lazy {
            listOf(
                History,
                Health,
                Settings
            )
        }
        val drawerMenuScreens: List<Screen> by lazy {
            listOf(
                Analytics,
                Billing
            )
        }
    }
}
