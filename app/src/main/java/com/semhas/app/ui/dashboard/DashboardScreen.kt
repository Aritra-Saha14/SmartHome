package com.semhas.app.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.semhas.app.ui.components.AppButton
import com.semhas.app.ui.components.AppCard
import com.semhas.app.ui.components.ChannelCard
import com.semhas.app.ui.components.PowerCard
import com.semhas.app.ui.components.SectionHeader
import com.semhas.app.ui.components.StatusIndicator
import com.semhas.app.ui.components.StatusType
import com.semhas.app.ui.theme.Dimensions
import com.semhas.app.utils.Formatters

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToMonitor: () -> Unit,
    onNavigateToControl: () -> Unit,
    onNavigateToBilling: () -> Unit,
    onNavigateToNotifications: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    if (state.isLoading) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Dimensions.screenHorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(Dimensions.spaceMedium)
    ) {
        // 1. Device and Hub Status Header
        item {
            Spacer(modifier = Modifier.height(Dimensions.spaceSmall))
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Hub,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = Dimensions.spaceSmall)
                        )
                        Column {
                            Text(
                                text = state.device.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "SEMHAS ${if (state.device.isActive) "Active" else "Inactive"} • 5 Channels",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    StatusIndicator(
                        status = if (state.device.connectivityStatus == "Connected") StatusType.CONNECTED else StatusType.DISCONNECTED,
                        label = state.device.connectivityStatus
                    )
                }
            }
        }

        // 2. Realtime Power Load
        item {
            PowerCard(
                title = "Live Power Consumption",
                powerWatts = state.totalPowerWatts,
                voltage = state.averageVoltage,
                current = state.totalCurrentAmps,
                subtitle = "${state.activeChannelsCount} of 5 ON"
            )
        }

        // 3. Today's Energy & Running Monthly Estimate
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimensions.spaceMedium)
            ) {
                AppCard(
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToBilling
                ) {
                    Column {
                        Text(
                            text = "Today's Energy",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(Dimensions.spaceExtraSmall))
                        Text(
                            text = Formatters.formatEnergy(state.todayEnergyUsage.totalEnergy),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(Dimensions.spaceExtraSmall))
                        Text(
                            text = Formatters.formatCurrency(state.todayEnergyUsage.estimatedCost),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                AppCard(
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToBilling
                ) {
                    Column {
                        Text(
                            text = "Estimated Month Bill",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(Dimensions.spaceExtraSmall))
                        Text(
                            text = Formatters.formatCurrency(state.billing.estimatedCost),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(Dimensions.spaceExtraSmall))
                        Text(
                            text = "${Formatters.formatEnergy(state.billing.consumedEnergyWh)} @ ${Formatters.formatRate(state.billing.ratePerWh)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 4. Abnormal Consumption / Alerts (if any)
        if (state.alerts.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "System Alerts",
                    actionLabel = "View All",
                    onActionClick = onNavigateToNotifications
                )
                AppCard(
                    containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.08f),
                    borderColor = MaterialTheme.colorScheme.error.copy(alpha = 0.3f),
                    onClick = onNavigateToNotifications
                ) {
                    val topAlert = state.alerts.first()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(end = Dimensions.spaceSmall)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = topAlert.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = topAlert.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 5. Five Appliance Channels Quick Summary
        item {
            SectionHeader(
                title = "Appliance Channels",
                subtitle = "5 Connected Channels",
                actionLabel = "Control",
                onActionClick = onNavigateToControl
            )
        }

        items(state.channels, key = { it.channelId }) { channel ->
            ChannelCard(
                channel = channel,
                onToggle = { isChecked -> viewModel.setChannelState(channel.channelId, isChecked) },
                showDetails = true,
                onIntensityChange = { newIntensity ->
                    viewModel.setChannelIntensity(channel.channelId, newIntensity)
                }
            )
        }

        // 6. Quick Access Actions
        item {
            Spacer(modifier = Modifier.height(Dimensions.spaceSmall))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimensions.spaceMedium)
            ) {
                AppButton(
                    text = "Live Monitor",
                    onClick = onNavigateToMonitor,
                    modifier = Modifier.weight(1f),
                    isPrimary = false
                )
                AppButton(
                    text = "Channel Control",
                    onClick = onNavigateToControl,
                    modifier = Modifier.weight(1f),
                    isPrimary = true
                )
            }
            Spacer(modifier = Modifier.height(Dimensions.spaceLarge))
        }
    }
}
