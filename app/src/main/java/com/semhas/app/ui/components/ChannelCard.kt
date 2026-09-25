package com.semhas.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.semhas.app.data.model.Channel
import com.semhas.app.data.model.intensityDisplayLabel
import com.semhas.app.data.model.isIntensitySupported
import com.semhas.app.ui.theme.Dimensions
import com.semhas.app.utils.Formatters

@Composable
fun ChannelCard(
    channel: Channel,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onRenameClick: (() -> Unit)? = null,
    showDetails: Boolean = true,
    onIntensityChange: ((Int) -> Unit)? = null
) {
    AppCard(
        modifier = modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column {
            // Header: Channel Number, Appliance Name, Edit action, and Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    StatusIndicator(
                        status = if (channel.relayState) StatusType.ACTIVE else StatusType.INACTIVE,
                        label = "Ch ${channel.channelNumber}"
                    )
                    Spacer(modifier = Modifier.width(Dimensions.spaceSmall))
                    Text(
                        text = channel.applianceName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    if (onRenameClick != null) {
                        IconButton(
                            onClick = onRenameClick,
                            modifier = Modifier.size(Dimensions.minTouchTarget)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Rename ${channel.applianceName}",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // ON/OFF Switch
                Switch(
                    checked = channel.relayState,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            }

            if (showDetails) {
                Spacer(modifier = Modifier.height(Dimensions.spaceMedium))

                // Real-time Measurements Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Power",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (channel.relayState) Formatters.formatPower(channel.power) else "0.0 W",
                            style = MaterialTheme.typography.titleSmall,
                            color = if (channel.relayState) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Energy",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = Formatters.formatEnergy(channel.energy),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Cost",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = Formatters.formatCurrency(channel.estimatedCost),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Stepped Speed / Intensity Control (Exclusively supported on CH4 and CH5)
            if (channel.isIntensitySupported && onIntensityChange != null) {
                IntensityControl(
                    currentIntensity = channel.intensity,
                    onIntensitySelected = onIntensityChange,
                    label = channel.intensityDisplayLabel,
                    enabled = true
                )
            }
        }
    }
}
