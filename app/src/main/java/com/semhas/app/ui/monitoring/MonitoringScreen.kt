package com.semhas.app.ui.monitoring

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
import androidx.compose.material.icons.filled.ElectricMeter
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.semhas.app.data.model.Channel
import com.semhas.app.ui.components.AppCard
import com.semhas.app.ui.components.SectionHeader
import com.semhas.app.ui.components.StatusIndicator
import com.semhas.app.ui.components.StatusType
import com.semhas.app.ui.theme.Dimensions
import com.semhas.app.utils.Formatters

@Composable
fun MonitoringScreen(
    viewModel: MonitoringViewModel
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
        item {
            Spacer(modifier = Modifier.height(Dimensions.spaceSmall))
            // Telemetry Overview Card
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.ElectricMeter,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(end = Dimensions.spaceSmall)
                            )
                            Text(
                                text = "Live Telemetry Overview",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        StatusIndicator(
                            status = StatusType.CONNECTED,
                            label = "${state.activeChannelsCount}/5 Online"
                        )
                    }

                    Spacer(modifier = Modifier.height(Dimensions.spaceMedium))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Total Active Load",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = Formatters.formatPower(state.totalPower),
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Accumulated Energy",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = Formatters.formatEnergy(state.totalEnergy),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        item {
            SectionHeader(
                title = "Channel Telemetry",
                subtitle = "Real-time INA219 sensor metrics (Voltage, Current, Power, Energy, Runtime, Cost)"
            )
        }

        items(state.channels, key = { it.channelId }) { channel ->
            DetailedChannelTelemetryCard(channel = channel)
        }

        item {
            Spacer(modifier = Modifier.height(Dimensions.spaceLarge))
        }
    }
}

@Composable
private fun DetailedChannelTelemetryCard(channel: Channel) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            // Header: Channel Number, Name, and Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusIndicator(
                        status = if (channel.relayState) StatusType.ACTIVE else StatusType.INACTIVE,
                        label = "Ch ${channel.channelNumber}"
                    )
                    Text(
                        text = channel.applianceName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = Dimensions.spaceSmall)
                    )
                }

                Text(
                    text = if (channel.relayState) Formatters.formatPower(channel.power) else "0.0 W",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (channel.relayState) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(Dimensions.spaceMedium))

            // 6-metric grid (Voltage, Current, Power, Energy, Runtime, Cost)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricColumn(
                    label = "Voltage",
                    value = Formatters.formatVoltage(channel.voltage)
                )
                MetricColumn(
                    label = "Current",
                    value = if (channel.relayState) Formatters.formatCurrent(channel.current) else "0.00 A"
                )
                MetricColumn(
                    label = "Energy",
                    value = Formatters.formatEnergy(channel.energy)
                )
            }

            Spacer(modifier = Modifier.height(Dimensions.spaceSmall))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricColumn(
                    label = "Runtime",
                    value = Formatters.formatRuntime(channel.runtime)
                )
                MetricColumn(
                    label = "Est. Cost",
                    value = Formatters.formatCurrency(channel.estimatedCost)
                )
                MetricColumn(
                    label = "Sensor Health",
                    value = if (channel.isHealthy) "Good" else "Fault"
                )
            }
        }
    }
}

@Composable
private fun MetricColumn(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

