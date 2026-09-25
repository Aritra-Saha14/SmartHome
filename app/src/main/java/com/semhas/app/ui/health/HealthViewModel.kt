package com.semhas.app.ui.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.semhas.app.data.model.Channel
import com.semhas.app.data.model.Device
import com.semhas.app.data.repository.SemhasRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

data class HealthUiState(
    val device: Device = Device(),
    val channels: List<Channel> = emptyList(),
    val overallHealthScore: Int = 100,
    val healthySensorsCount: Int = 0,
    val reportingSensorsCount: Int = 0,
    val commandedRelaysOnCount: Int = 0,
    val lastUpdatedMillis: Long = System.currentTimeMillis(),
    val currentClockMillis: Long = System.currentTimeMillis(),
    val isRealtimeConnected: Boolean = true,
    val isCloudConnected: Boolean = true,
    val staleThresholdSeconds: Long = HealthViewModel.SENSOR_STALE_THRESHOLD_SECONDS,
    val isDeviceHeartbeatActive: Boolean = false,
    val isLoading: Boolean = false
) {
    // True if at least one sensor is actively streaming fresh telemetry within threshold
    val isStreamingLive: Boolean
        get() = reportingSensorsCount > 0

    // Offline if no fresh telemetry received and device is not marked online in Supabase with a fresh heartbeat
    val isOffline: Boolean
        get() = !isDeviceHeartbeatActive && !isStreamingLive

    val attentionRequired: Boolean
        get() = !isOffline && (reportingSensorsCount < channels.size || overallHealthScore < 100)

    val isHealthy: Boolean
        get() = !isOffline && !attentionRequired && channels.isNotEmpty() && reportingSensorsCount == channels.size
}

class HealthViewModel(
    private val repository: SemhasRepository
) : ViewModel() {

    companion object {
        const val SENSOR_STALE_THRESHOLD_SECONDS = 30L
        const val SENSOR_STALE_THRESHOLD_MS = SENSOR_STALE_THRESHOLD_SECONDS * 1000L
        const val HEARTBEAT_STALE_THRESHOLD_SECONDS = 30L
        const val HEARTBEAT_STALE_THRESHOLD_MS = HEARTBEAT_STALE_THRESHOLD_SECONDS * 1000L

        private fun parseIsoToMillis(isoString: String?): Long {
            if (isoString.isNullOrBlank()) return 0L
            return try {
                java.time.Instant.parse(isoString).toEpochMilli()
            } catch (e: Exception) {
                try {
                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
                    sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                    val cleanStr = if (isoString.length >= 19) isoString.substring(0, 19) else isoString
                    sdf.parse(cleanStr)?.time ?: 0L
                } catch (e2: Exception) {
                    0L
                }
            }
        }
    }

    // Ticker flow emitting every 5 seconds to actively re-evaluate reading freshness and heartbeat
    private val tickerFlow = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(5000L)
        }
    }

    val uiState: StateFlow<HealthUiState> = combine(
        repository.device,
        repository.channels,
        tickerFlow
    ) { device, channels, currentClock ->
        // Freshness-based sensor reporting:
        // A sensor is reporting ONLY if its last reading timestamp is within 30 seconds
        val reportingSensors = channels.count { ch ->
            ch.lastReadingMillis > 0L && (currentClock - ch.lastReadingMillis) <= SENSOR_STALE_THRESHOLD_MS
        }
        val healthySensors = channels.count { ch ->
            val isFresh = ch.lastReadingMillis > 0L && (currentClock - ch.lastReadingMillis) <= SENSOR_STALE_THRESHOLD_MS
            ch.isHealthy && isFresh
        }

        // Real heartbeat check: is_online == true AND last_seen is within 30 seconds
        val lastSeenMs = parseIsoToMillis(device.lastSeen)
        val isHeartbeatActive = device.isOnline && lastSeenMs > 0L && (currentClock - lastSeenMs) <= HEARTBEAT_STALE_THRESHOLD_MS

        val commandedRelaysOn = channels.count { it.relayState }
        val isStreaming = reportingSensors > 0
        val isOffline = !isHeartbeatActive && !isStreaming
        val score = if (isOffline) 0 else if (channels.isNotEmpty()) (healthySensors * 100) / channels.size else 100

        val latestReadingTimestamp = channels.maxOfOrNull { it.lastReadingMillis }?.takeIf { it > 0L }
            ?: System.currentTimeMillis()

        HealthUiState(
            device = device,
            channels = channels,
            overallHealthScore = score,
            healthySensorsCount = healthySensors,
            reportingSensorsCount = reportingSensors,
            commandedRelaysOnCount = commandedRelaysOn,
            lastUpdatedMillis = latestReadingTimestamp,
            currentClockMillis = currentClock,
            isRealtimeConnected = true,
            isCloudConnected = true,
            staleThresholdSeconds = SENSOR_STALE_THRESHOLD_SECONDS,
            isDeviceHeartbeatActive = isHeartbeatActive,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HealthUiState(isLoading = true)
    )
}
