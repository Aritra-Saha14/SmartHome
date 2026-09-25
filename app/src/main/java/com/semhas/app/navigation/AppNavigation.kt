package com.semhas.app.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.semhas.app.data.repository.SemhasRepository
import com.semhas.app.ui.analytics.AnalyticsScreen
import com.semhas.app.ui.analytics.AnalyticsViewModel
import com.semhas.app.ui.billing.BillingScreen
import com.semhas.app.ui.billing.BillingViewModel
import com.semhas.app.ui.components.AppCard
import com.semhas.app.ui.components.SectionHeader
import com.semhas.app.ui.control.ControlScreen
import com.semhas.app.ui.control.ControlViewModel
import com.semhas.app.ui.dashboard.DashboardScreen
import com.semhas.app.ui.dashboard.DashboardViewModel
import com.semhas.app.ui.health.HealthScreen
import com.semhas.app.ui.health.HealthViewModel
import com.semhas.app.ui.history.HistoryScreen
import com.semhas.app.ui.history.HistoryViewModel
import com.semhas.app.ui.monitoring.MonitoringScreen
import com.semhas.app.ui.monitoring.MonitoringViewModel
import com.semhas.app.ui.notifications.NotificationsScreen
import com.semhas.app.ui.notifications.NotificationsViewModel
import com.semhas.app.ui.settings.SettingsScreen
import com.semhas.app.ui.settings.SettingsViewModel
import com.semhas.app.ui.theme.Dimensions
import com.semhas.app.ui.voice.VoiceScreen
import com.semhas.app.ui.voice.VoiceViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(
    navController: NavHostController,
    repository: SemhasRepository,
    dashboardViewModel: DashboardViewModel,
    monitoringViewModel: MonitoringViewModel,
    controlViewModel: ControlViewModel,
    analyticsViewModel: AnalyticsViewModel,
    historyViewModel: HistoryViewModel,
    billingViewModel: BillingViewModel,
    notificationsViewModel: NotificationsViewModel,
    healthViewModel: HealthViewModel,
    voiceViewModel: VoiceViewModel,
    settingsViewModel: SettingsViewModel
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: Screen.Home.route

    val isTopLevelDestination = Screen.bottomNavScreens.any { it.route == currentRoute }
    val currentScreen = listOf(
        Screen.Home, Screen.Monitor, Screen.Control, Screen.Voice, Screen.More,
        Screen.Analytics, Screen.History, Screen.Billing, Screen.Notifications,
        Screen.Health, Screen.Settings
    ).find { it.route == currentRoute } ?: Screen.Home

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    val notificationsState by notificationsViewModel.uiState.collectAsState()

    // Voice Assistant In-App Navigation Observer
    val navTarget by voiceViewModel.navigationTarget.collectAsState()
    LaunchedEffect(navTarget) {
        navTarget?.let { targetRoute ->
            navController.navigate(targetRoute) {
                launchSingleTop = true
            }
            voiceViewModel.clearNavigationTarget()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen || isTopLevelDestination,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                drawerContentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Spacer(modifier = Modifier.height(Dimensions.spaceLarge))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimensions.spaceDefault),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Hub,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = Dimensions.spaceMedium)
                    )
                    Column {
                        Text(
                            text = "SEMHAS",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Energy Management Hub",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Dimensions.spaceDefault))
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                    modifier = Modifier.padding(horizontal = Dimensions.spaceDefault)
                )
                Spacer(modifier = Modifier.height(Dimensions.spaceSmall))

                Text(
                    text = "Modules",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(
                        horizontal = Dimensions.spaceDefault,
                        vertical = Dimensions.spaceSmall
                    )
                )

                Screen.drawerMenuScreens.forEach { screen ->
                    val isSelected = currentRoute == screen.route
                    NavigationDrawerItem(
                        icon = {
                            Icon(
                                imageVector = screen.icon,
                                contentDescription = screen.title
                            )
                        },
                        label = { Text(screen.title) },
                        selected = isSelected,
                        onClick = {
                            coroutineScope.launch { drawerState.close() }
                            if (currentRoute != screen.route) {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = if (isTopLevelDestination) "SEMHAS — ${currentScreen.title}" else currentScreen.title,
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    navigationIcon = {
                        if (!isTopLevelDestination) {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back"
                                )
                            }
                        } else {
                            IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Menu"
                                )
                            }
                        }
                    },
                    actions = {
                        val unreadCount = notificationsState.unreadCount
                        IconButton(
                            onClick = {
                                if (currentRoute != Screen.Notifications.route) {
                                    navController.navigate(Screen.Notifications.route) {
                                        launchSingleTop = true
                                    }
                                }
                            }
                        ) {
                            if (unreadCount > 0) {
                                BadgedBox(
                                    badge = {
                                        Badge(
                                            containerColor = MaterialTheme.colorScheme.error,
                                            contentColor = MaterialTheme.colorScheme.onError
                                        ) {
                                            Text(
                                                text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Notifications,
                                        contentDescription = "Notifications",
                                        tint = if (currentRoute == Screen.Notifications.route)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Notifications,
                                    contentDescription = "Notifications",
                                    tint = if (currentRoute == Screen.Notifications.route)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    Screen.bottomNavScreens.forEach { screen ->
                        val selected = currentRoute == screen.route ||
                                (screen == Screen.More && Screen.moreMenuScreens.any { it.route == currentRoute })

                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.title) },
                            label = { Text(screen.title) },
                            selected = selected,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            onClick = {
                                if (currentRoute != screen.route) {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                        )
                    }
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
                modifier = Modifier.padding(innerPadding)
            ) {
                // 5 Primary Bottom Destinations
                composable(Screen.Home.route) {
                    DashboardScreen(
                        viewModel = dashboardViewModel,
                        onNavigateToMonitor = { navController.navigate(Screen.Monitor.route) },
                        onNavigateToControl = { navController.navigate(Screen.Control.route) },
                        onNavigateToBilling = { navController.navigate(Screen.Billing.route) },
                        onNavigateToNotifications = { navController.navigate(Screen.Notifications.route) }
                    )
                }

                composable(Screen.Monitor.route) {
                    MonitoringScreen(viewModel = monitoringViewModel)
                }

                composable(Screen.Control.route) {
                    ControlScreen(viewModel = controlViewModel)
                }

                composable(Screen.Voice.route) {
                    VoiceScreen(viewModel = voiceViewModel)
                }

                composable(Screen.More.route) {
                    MoreMenuScreen(
                        onNavigate = { route -> navController.navigate(route) }
                    )
                }

                // Secondary Destinations
                composable(Screen.Analytics.route) {
                    AnalyticsScreen(viewModel = analyticsViewModel)
                }

                composable(Screen.History.route) {
                    HistoryScreen(viewModel = historyViewModel)
                }

                composable(Screen.Billing.route) {
                    BillingScreen(viewModel = billingViewModel)
                }

                composable(Screen.Notifications.route) {
                    NotificationsScreen(viewModel = notificationsViewModel)
                }

                composable(Screen.Health.route) {
                    HealthScreen(viewModel = healthViewModel)
                }

                composable(Screen.Settings.route) {
                    SettingsScreen(
                        viewModel = settingsViewModel,
                        onNavigateToControl = { navController.navigate(Screen.Control.route) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MoreMenuScreen(
    onNavigate: (String) -> Unit
) {
    val items = listOf(
        Screen.History to "Operational event logs, alerts & historical milestones",
        Screen.Health to "Controller connectivity, 5 INA219 sensors & relay diagnostics",
        Screen.Settings to "Appearance theme, channel naming, preferences & security"
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Dimensions.screenHorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(Dimensions.spaceMedium)
    ) {
        item {
            Spacer(modifier = Modifier.height(Dimensions.spaceSmall))
            SectionHeader(
                title = "More Modules",
                subtitle = "Access secondary system controls and settings"
            )
        }

        items(items) { (screen, subtitle) ->
            AppCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onNavigate(screen.route) }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = screen.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = Dimensions.spaceMedium)
                        )
                        Column {
                            Text(
                                text = screen.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(Dimensions.spaceExtraSmall))
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(Dimensions.spaceLarge))
        }
    }
}
