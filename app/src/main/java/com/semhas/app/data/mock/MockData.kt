package com.semhas.app.data.mock

import com.semhas.app.data.model.Billing
import com.semhas.app.data.model.Channel
import com.semhas.app.data.model.Device
import com.semhas.app.data.model.EnergyUsage
import com.semhas.app.data.model.HistoryEvent
import com.semhas.app.data.model.Notification
import com.semhas.app.utils.Constants
import java.util.Locale

object MockData {

    fun getInitialDevice(): Device = Device(
        id = Constants.DEVICE_ID,
        name = Constants.DEVICE_NAME,
        isActive = true,
        connectivityStatus = "Connected",
        lastSeen = "Just now",
        firmwareVersion = Constants.FIRMWARE_VERSION
    )

    fun getInitialChannels(): List<Channel> = listOf(
        Channel(
            channelId = 1,
            channelNumber = 1,
            applianceName = Constants.CHANNEL_1_DEFAULT_NAME,
            relayState = true,
            voltage = 230.2,
            current = 0.18,
            power = 41.4,
            energy = 850.0,
            runtime = 18420L, // ~5.1h
            estimatedCost = 850.0 * Constants.DEFAULT_ELECTRICITY_RATE_PER_WH,
            isHealthy = true
        ),
        Channel(
            channelId = 2,
            channelNumber = 2,
            applianceName = Constants.CHANNEL_2_DEFAULT_NAME,
            relayState = true,
            voltage = 229.8,
            current = 0.33,
            power = 75.8,
            energy = 1420.0,
            runtime = 25200L, // 7h
            estimatedCost = 1420.0 * Constants.DEFAULT_ELECTRICITY_RATE_PER_WH,
            isHealthy = true
        ),
        Channel(
            channelId = 3,
            channelNumber = 3,
            applianceName = Constants.CHANNEL_3_DEFAULT_NAME,
            relayState = false,
            voltage = 230.0,
            current = 0.0,
            power = 0.0,
            energy = 2100.0,
            runtime = 10800L, // 3h earlier
            estimatedCost = 2100.0 * Constants.DEFAULT_ELECTRICITY_RATE_PER_WH,
            isHealthy = true
        ),
        Channel(
            channelId = 4,
            channelNumber = 4,
            applianceName = Constants.CHANNEL_4_DEFAULT_NAME,
            relayState = false,
            voltage = 230.1,
            current = 0.0,
            power = 0.0,
            energy = 3650.0,
            runtime = 7200L, // 2h earlier
            estimatedCost = 3650.0 * Constants.DEFAULT_ELECTRICITY_RATE_PER_WH,
            isHealthy = true
        ),
        Channel(
            channelId = 5,
            channelNumber = 5,
            applianceName = Constants.CHANNEL_5_DEFAULT_NAME,
            relayState = true,
            voltage = 228.6,
            current = 6.45,
            power = 1474.4,
            energy = 14800.0,
            runtime = 36000L, // 10h
            estimatedCost = 14800.0 * Constants.DEFAULT_ELECTRICITY_RATE_PER_WH,
            isHealthy = true
        )
    )

    fun getInitialBilling(): Billing {
        val totalWh = 22820.0
        val rate = Constants.DEFAULT_ELECTRICITY_RATE_PER_WH
        return Billing(
            billingPeriodStart = "01 Sep 2026",
            billingPeriodEnd = "30 Sep 2026",
            ratePerWh = rate,
            consumedEnergyWh = totalWh,
            estimatedCost = totalWh * rate
        )
    }

    fun getInitialEnergyUsage(): EnergyUsage {
        val todayWh = 7450.0
        return EnergyUsage(
            period = "Today",
            totalEnergy = todayWh,
            estimatedCost = todayWh * Constants.DEFAULT_ELECTRICITY_RATE_PER_WH,
            ratePerWh = Constants.DEFAULT_ELECTRICITY_RATE_PER_WH
        )
    }

    fun getInitialNotifications(): List<Notification> {
        val now = System.currentTimeMillis()
        return listOf(
            Notification(
                id = "notif-1",
                type = "CONSUMPTION",
                title = "High AC Consumption",
                message = "AC (Channel 5) has been continuously drawing >1.4 kW for 4 hours.",
                timestamp = now - 1800000L, // 30 min ago
                isRead = false,
                severity = "WARNING"
            ),
            Notification(
                id = "notif-2",
                type = "CONNECTIVITY",
                title = "Hub Synced",
                message = "ESP32 Controller connected successfully with 5 INA219 sensors.",
                timestamp = now - 7200000L, // 2 hrs ago
                isRead = false,
                severity = "SUCCESS"
            ),
            Notification(
                id = "notif-3",
                type = "SYSTEM",
                title = "Energy Spike Check",
                message = "Voltage fluctuation detected on Line 1 (235.4 V). Normalized safely.",
                timestamp = now - 21600000L, // 6 hrs ago
                isRead = true,
                severity = "INFO"
            ),
            Notification(
                id = "notif-4",
                type = "ALERT",
                title = "Kitchen Power Off",
                message = "Kitchen (Channel 4) turned OFF via Manual Control.",
                timestamp = now - 43200000L, // 12 hrs ago
                isRead = true,
                severity = "INFO"
            )
        )
    }

    fun getInitialHistoryEvents(): List<HistoryEvent> {
        val now = System.currentTimeMillis()
        return listOf(
            HistoryEvent(
                id = "hist-1",
                timestamp = now - 900000L,
                channelId = 1,
                eventType = "CONTROL",
                description = "Living Room Light turned ON"
            ),
            HistoryEvent(
                id = "hist-2",
                timestamp = now - 3600000L,
                channelId = 5,
                eventType = "ALERT",
                description = "AC reached peak power draw (1,480 W)"
            ),
            HistoryEvent(
                id = "hist-3",
                timestamp = now - 7200000L,
                channelId = 4,
                eventType = "CONTROL",
                description = "Kitchen turned OFF"
            ),
            HistoryEvent(
                id = "hist-4",
                timestamp = now - 14400000L,
                channelId = 2,
                eventType = "CONTROL",
                description = "Bedroom Fan turned ON"
            ),
            HistoryEvent(
                id = "hist-5",
                timestamp = now - 28800000L,
                channelId = null,
                eventType = "SYSTEM",
                description = "System self-test completed: all 5 channels healthy"
            ),
            HistoryEvent(
                id = "hist-6",
                timestamp = now - 86400000L,
                channelId = 3,
                eventType = "CONTROL",
                description = "TV turned OFF"
            )
        )
    }

    fun getAnalyticsTrends(period: String): List<Pair<String, Double>> {
        return when (period.uppercase()) {
            "DAILY" -> (0..23).map { h ->
                val label = String.format(Locale.US, "%02d", h)
                val value = when (h) {
                    in 0..5 -> 0.4
                    in 6..9 -> 1.8
                    in 10..15 -> 3.2
                    in 16..21 -> 4.5
                    else -> 1.2
                }
                label to value
            }
            "WEEKLY" -> listOf(
                "Mon" to 18.4,
                "Tue" to 21.2,
                "Wed" to 19.8,
                "Thu" to 22.5,
                "Fri" to 24.1,
                "Sat" to 26.8,
                "Sun" to 25.2
            )
            "MONTHLY" -> listOf(
                "Week 1" to 142.0,
                "Week 2" to 155.4,
                "Week 3" to 148.2,
                "Week 4" to 160.8
            )
            else -> emptyList()
        }
    }
}

