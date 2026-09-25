package com.semhas.app.ui.health

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.semhas.app.ui.components.AppCard
import com.semhas.app.ui.components.SectionHeader
import com.semhas.app.ui.theme.Dimensions
import com.semhas.app.ui.theme.StatusActive
import com.semhas.app.ui.theme.StatusError
import com.semhas.app.ui.theme.StatusWarning
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HealthScreen(
    viewModel: HealthViewModel
) {
    val state by viewModel.uiState.collectAsState()

    // Expandable section collapse states (collapsed by default as required)
    var hardwareExpanded by remember { mutableStateOf(false) }
    var connectivityExpanded by remember { mutableStateOf(false) }
    var diagnosticsExpanded by remember { mutableStateOf(false) }

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
        // 1. Header
        item {
            Spacer(modifier = Modifier.height(Dimensions.spaceSmall))
            SectionHeader(
                title = "System Health",
                subtitle = "Live controller telemetry & hardware command status"
            )

            // 2. Top Overall Health Card
            AppCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.surface,
                borderColor = MaterialTheme.colorScheme.outline
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "OVERALL STATUS",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = when {
                                    state.isHealthy -> "System Healthy"
                                    state.attentionRequired -> "Attention Required"
                                    else -> "System Offline"
                                },
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    state.isHealthy -> MaterialTheme.colorScheme.primary
                                    state.attentionRequired -> StatusWarning
                                    else -> StatusError
                                }
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = when {
                                    state.isHealthy -> "${state.reportingSensorsCount}/5 sensors streaming fresh telemetry (<30s)"
                                    state.attentionRequired -> "${state.channels.size - state.reportingSensorsCount} sensor(s) stale or not reporting (>30s)"
                                    else -> "No live telemetry stream • Device offline"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Health Score Pill
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(Dimensions.radiusMedium))
                                .background(
                                    (if (state.isHealthy) MaterialTheme.colorScheme.primary else if (state.attentionRequired) StatusWarning else StatusError)
                                        .copy(alpha = 0.15f)
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${state.overallHealthScore}%",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (state.isHealthy) MaterialTheme.colorScheme.primary else if (state.attentionRequired) StatusWarning else StatusError
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(Dimensions.spaceMedium))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(Dimensions.spaceSmall))

                    // Mini status pills for ESP32, Wi-Fi, Cloud
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CompactStatusPill(
                            label = "ESP32",
                            value = if (state.isDeviceHeartbeatActive) "Online" else if (state.isStreamingLive) "Streaming" else "Offline",
                            isOk = state.isDeviceHeartbeatActive || state.isStreamingLive
                        )
                        CompactStatusPill(
                            label = "Wi-Fi",
                            value = if (state.device.wifiRssi != null || state.isDeviceHeartbeatActive) "Connected" else "Offline",
                            isOk = state.device.wifiRssi != null || state.isDeviceHeartbeatActive
                        )
                        CompactStatusPill(
                            label = "Cloud",
                            value = if (state.isCloudConnected) "Active" else "Offline",
                            isOk = state.isCloudConnected
                        )
                    }
                }
            }
        }

        // 3. Compact 2x2 Summary Grid
        item {
            Column(verticalArrangement = Arrangement.spacedBy(Dimensions.spaceSmall)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Dimensions.spaceSmall)
                ) {
                    SummaryGridCard(
                        title = "ESP32",
                        statusText = if (state.isDeviceHeartbeatActive) "ESP32 Online" else "ESP32 Offline / Stale",
                        isOk = state.isDeviceHeartbeatActive,
                        metric = state.device.deviceCode,
                        subtext = if (state.isDeviceHeartbeatActive) "Heartbeat fresh (<30s)" else "Heartbeat stale (>30s)",
                        modifier = Modifier.weight(1f)
                    )
                    SummaryGridCard(
                        title = "Wi-Fi",
                        statusText = if (state.device.wifiRssi != null || state.isDeviceHeartbeatActive) "Connected" else "Offline",
                        isOk = state.device.wifiRssi != null || state.isDeviceHeartbeatActive,
                        metric = state.device.wifiSsid ?: "WLAN Active",
                        subtext = if (state.device.wifiRssi != null) "RSSI: ${formatRssi(state.device.wifiRssi)}" else "RSSI: Not available",
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Dimensions.spaceSmall)
                ) {
                    SummaryGridCard(
                        title = "Sensors",
                        statusText = "${state.reportingSensorsCount}/5 Reporting",
                        isOk = state.reportingSensorsCount > 0,
                        metric = "${state.healthySensorsCount}/5 Online",
                        subtext = "Freshness: <30s threshold",
                        modifier = Modifier.weight(1f)
                    )
                    SummaryGridCard(
                        title = "Relay Commands",
                        statusText = "${state.commandedRelaysOnCount}/5 Set ON",
                        isOk = true,
                        metric = "Commanded State",
                        subtext = "Feedback: Not available",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 4. Expandable Sections (COLLAPSED BY DEFAULT)

        // Section A: Hardware Health
        item {
            ExpandableCardHeader(
                title = "Hardware Health",
                subtitle = "INA219 telemetry, commanded relay state & I2C buses",
                badgeText = "${state.reportingSensorsCount}/5 Reporting",
                isExpanded = hardwareExpanded,
                onToggle = { hardwareExpanded = !hardwareExpanded }
            ) {
                Column(
                    modifier = Modifier.padding(top = Dimensions.spaceSmall),
                    verticalArrangement = Arrangement.spacedBy(Dimensions.spaceSmall)
                ) {
                    Text(
                        text = "INA219 SENSORS (CH1–CH5)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    state.channels.forEach { ch ->
                        val isFresh = ch.lastReadingMillis > 0L &&
                                (state.currentClockMillis - ch.lastReadingMillis) <= HealthViewModel.SENSOR_STALE_THRESHOLD_MS

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "CH${ch.channelNumber} • ${ch.applianceName}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (isFresh) {
                                        "${ch.voltage}V • ${ch.current}A • ${ch.power}W"
                                    } else if (ch.lastReadingMillis > 0L) {
                                        "Not Reporting • Stale (>30s)"
                                    } else {
                                        "Not Reporting • No readings"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isFresh) MaterialTheme.colorScheme.onSurfaceVariant else StatusWarning
                                )
                            }
                            LiveDotStatus(
                                isOk = isFresh && ch.isHealthy,
                                label = if (isFresh && ch.isHealthy) "Reporting" else if (!isFresh) "Not Reporting" else "Fault"
                            )
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = Dimensions.spaceExtraSmall),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )

                    Column {
                        Text(
                            text = "RELAYS (CH1–CH5 COMMAND STATE)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Commanded database state. Physical relay feedback is currently not available from ESP32.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }

                    state.channels.forEach { ch ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Relay CH${ch.channelNumber} (${ch.applianceName})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Physical Feedback: Not available",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                            Text(
                                text = if (ch.relayState) "Command: ON" else "Command: OFF",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (ch.relayState) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    DiagnosticRow("Physical Relay Feedback", "Not available")

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = Dimensions.spaceExtraSmall),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )

                    Text(
                        text = "I2C BUS INTERFACES",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    DiagnosticRow("I2C Bus 1 (CH1–CH4 Sensors)", "Not available")
                    DiagnosticRow("I2C Bus 2 (CH5 Sensor)", "Not available")
                }
            }
        }

        // Section B: Connectivity
        item {
            ExpandableCardHeader(
                title = "Connectivity",
                subtitle = "Wi-Fi link, Supabase REST & Realtime WebSocket",
                badgeText = if (state.isStreamingLive || state.device.isOnline) "Connected" else "Offline",
                isExpanded = connectivityExpanded,
                onToggle = { connectivityExpanded = !connectivityExpanded }
            ) {
                Column(
                    modifier = Modifier.padding(top = Dimensions.spaceSmall),
                    verticalArrangement = Arrangement.spacedBy(Dimensions.spaceSmall)
                ) {
                    DiagnosticRow("Wi-Fi Status", if (state.device.wifiRssi != null || state.isDeviceHeartbeatActive) "Connected" else "Offline")
                    DiagnosticRow("Wi-Fi SSID", state.device.wifiSsid ?: "Not available")
                    DiagnosticRow("Wi-Fi RSSI", formatRssi(state.device.wifiRssi))
                    DiagnosticRow("IP Address", state.device.ipAddress ?: "Not available")
                    DiagnosticRow("Supabase API", if (state.isCloudConnected) "Connected (REST / HTTPS)" else "Offline")
                    DiagnosticRow("Realtime WebSocket", if (state.isRealtimeConnected) "Active stream" else "Disconnected")
                    DiagnosticRow("Live Telemetry Stream", if (state.isStreamingLive) "Active (<30s)" else "Inactive (>30s)")
                    DiagnosticRow("Last DB Heartbeat", formatRelativeIso(state.device.lastSeen, state.currentClockMillis))
                    DiagnosticRow("Health Telemetry Updated", formatRelativeTime(state.device.healthUpdatedAt, state.currentClockMillis))
                }
            }
        }

        // Section C: ESP32 Diagnostics
        item {
            ExpandableCardHeader(
                title = "ESP32 Diagnostics",
                subtitle = "Microcontroller firmware, heap memory & reset history",
                badgeText = state.device.firmwareVersion,
                isExpanded = diagnosticsExpanded,
                onToggle = { diagnosticsExpanded = !diagnosticsExpanded }
            ) {
                Column(
                    modifier = Modifier.padding(top = Dimensions.spaceSmall),
                    verticalArrangement = Arrangement.spacedBy(Dimensions.spaceSmall)
                ) {
                    DiagnosticRow("Firmware Version", state.device.firmwareVersion)
                    DiagnosticRow("Device Code", state.device.deviceCode)
                    DiagnosticRow("Uptime", formatUptime(state.device.uptimeSeconds))
                    DiagnosticRow("Free Heap Memory", formatBytesToKb(state.device.freeHeapBytes))
                    DiagnosticRow("Minimum Free Heap", formatBytesToKb(state.device.minFreeHeapBytes))
                    DiagnosticRow("Reset Reason", state.device.resetReason ?: "Not available")
                    DiagnosticRow("Boot Time", formatInstantLocal(state.device.bootTime))
                }
            }
        }

        // 5. Compact Live-Data Indicator at Bottom
        item {
            AppCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.surface,
                borderColor = MaterialTheme.colorScheme.outline
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (state.isStreamingLive) MaterialTheme.colorScheme.primary else StatusWarning)
                        )
                        Spacer(modifier = Modifier.width(Dimensions.spaceSmall))
                        Text(
                            text = if (state.isStreamingLive) "LIVE TELEMETRY (<30s)" else "TELEMETRY INACTIVE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (state.isStreamingLive) MaterialTheme.colorScheme.primary else StatusWarning
                        )
                    }

                    Text(
                        text = if (state.lastUpdatedMillis > 0L) {
                            "Last reading: " + SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(state.lastUpdatedMillis))
                        } else {
                            "Awaiting telemetry"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(Dimensions.spaceLarge))
        }
    }
}

@Composable
private fun CompactStatusPill(
    label: String,
    value: String,
    isOk: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(if (isOk) StatusActive else StatusError)
        )
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (isOk) MaterialTheme.colorScheme.onSurface else StatusError
        )
    }
}

@Composable
private fun SummaryGridCard(
    title: String,
    statusText: String,
    isOk: Boolean,
    metric: String,
    subtext: String,
    modifier: Modifier = Modifier
) {
    AppCard(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        borderColor = MaterialTheme.colorScheme.outline
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(if (isOk) StatusActive else StatusError)
                )
            }

            Spacer(modifier = Modifier.height(Dimensions.spaceSmall))

            Text(
                text = statusText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (isOk) MaterialTheme.colorScheme.primary else StatusError
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = metric,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = subtext,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ExpandableCardHeader(
    title: String,
    subtitle: String,
    badgeText: String,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        containerColor = MaterialTheme.colorScheme.surface,
        borderColor = MaterialTheme.colorScheme.outline
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(Dimensions.spaceSmall))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = badgeText,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(24.dp)
                        .rotate(if (isExpanded) 180f else 0f)
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(Dimensions.spaceSmall))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    content()
                }
            }
        }
    }
}

@Composable
private fun DiagnosticRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (value == "Not available") FontWeight.Normal else FontWeight.Medium,
            color = if (value == "Not available") MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun LiveDotStatus(
    isOk: Boolean,
    label: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(if (isOk) StatusActive else StatusError)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (isOk) StatusActive else StatusError
        )
    }
}

private fun formatUptime(seconds: Long?): String {
    if (seconds == null || seconds < 0) return "Not available"
    val days = seconds / 86400
    val hours = (seconds % 86400) / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return when {
        days > 0 -> "${days}d ${hours}h ${minutes}m ${String.format(Locale.US, "%02ds", secs)}"
        hours > 0 -> "${hours}h ${minutes}m ${String.format(Locale.US, "%02ds", secs)}"
        minutes > 0 -> "${minutes}m ${String.format(Locale.US, "%02ds", secs)}"
        else -> "${secs}s"
    }
}

private fun formatBytesToKb(bytes: Long?): String {
    if (bytes == null || bytes <= 0) return "Not available"
    return "${bytes / 1024} KB"
}

private fun formatRssi(rssi: Int?): String {
    if (rssi == null) return "Not available"
    val quality = when {
        rssi >= -60 -> "Good"
        rssi >= -75 -> "Fair"
        else -> "Weak"
    }
    return "$rssi dBm • $quality"
}

private fun formatInstantLocal(instant: java.time.Instant?): String {
    if (instant == null) return "Not available"
    return try {
        java.time.format.DateTimeFormatter
            .ofPattern("dd MMM yyyy, HH:mm:ss", Locale.getDefault())
            .withZone(java.time.ZoneId.systemDefault())
            .format(instant)
    } catch (e: Exception) {
        "Not available"
    }
}

private fun formatRelativeTime(instant: java.time.Instant?, currentClockMillis: Long): String {
    if (instant == null) return "Not available"
    val elapsedSec = maxOf(0L, (currentClockMillis - instant.toEpochMilli()) / 1000L)
    return when {
        elapsedSec < 60 -> "${elapsedSec}s ago"
        elapsedSec < 3600 -> "${elapsedSec / 60}m ago"
        else -> "${elapsedSec / 3600}h ago"
    }
}

private fun formatRelativeIso(iso: String?, currentClockMillis: Long): String {
    if (iso.isNullOrBlank()) return "Not available"
    val ms = try {
        java.time.Instant.parse(iso).toEpochMilli()
    } catch (e: Exception) {
        0L
    }
    if (ms <= 0L) return iso
    val elapsedSec = maxOf(0L, (currentClockMillis - ms) / 1000L)
    return when {
        elapsedSec < 60 -> "${elapsedSec}s ago"
        elapsedSec < 3600 -> "${elapsedSec / 60}m ago"
        else -> "${elapsedSec / 3600}h ago"
    }
}



