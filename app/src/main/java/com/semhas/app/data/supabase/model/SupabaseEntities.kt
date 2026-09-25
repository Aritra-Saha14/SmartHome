package com.semhas.app.data.supabase.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DeviceDto(
    @SerialName("id") val id: String,
    @SerialName("device_name") val deviceName: String = "SEMHAS Central Hub",
    @SerialName("device_code") val deviceCode: String = "SEMHAS-001",
    @SerialName("firmware_version") val firmwareVersion: String = "v1.0.4-esp32",
    @SerialName("wifi_ssid") val wifiSsid: String? = null,
    @SerialName("is_online") val isOnline: Boolean = false,
    @SerialName("last_seen") val lastSeen: String? = null,
    @SerialName("wifi_rssi") val wifiRssi: Int? = null,
    @SerialName("ip_address") val ipAddress: String? = null,
    @SerialName("uptime_seconds") val uptimeSeconds: Long? = null,
    @SerialName("free_heap_bytes") val freeHeapBytes: Long? = null,
    @SerialName("min_free_heap_bytes") val minFreeHeapBytes: Long? = null,
    @SerialName("reset_reason") val resetReason: String? = null,
    @SerialName("boot_time") val bootTime: String? = null,
    @SerialName("health_updated_at") val healthUpdatedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class ApplianceDto(
    @SerialName("id") val id: String,
    @SerialName("device_id") val deviceId: String,
    @SerialName("channel_number") val channelNumber: Int,
    @SerialName("appliance_name") val applianceName: String,
    @SerialName("appliance_type") val applianceType: String? = null,
    @SerialName("relay_state") val relayState: Boolean = false,
    @SerialName("intensity") val intensity: Int? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class LiveReadingDto(
    @SerialName("id") val id: Long? = null,
    @SerialName("appliance_id") val applianceId: String,
    @SerialName("voltage") val voltage: Double = 0.0,
    @SerialName("current") val current: Double = 0.0,
    @SerialName("power") val power: Double = 0.0,
    @SerialName("energy") val energy: Double = 0.0,
    @SerialName("runtime_seconds") val runtimeSeconds: Long = 0L,
    @SerialName("estimated_cost") val estimatedCost: Double = 0.0,
    @SerialName("recorded_at") val recordedAt: String? = null
)

@Serializable
data class EnergyReadingDto(
    @SerialName("id") val id: String? = null,
    @SerialName("appliance_id") val applianceId: String,
    @SerialName("voltage") val voltage: Double = 0.0,
    @SerialName("current") val current: Double = 0.0,
    @SerialName("power") val power: Double = 0.0,
    @SerialName("energy_delta_kwh") val energyDeltaKwh: Double = 0.0,
    @SerialName("runtime_delta_seconds") val runtimeDeltaSeconds: Double = 0.0,
    @SerialName("estimated_cost") val estimatedCost: Double = 0.0,
    @SerialName("recorded_at") val recordedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null
) {
    val energyDeltaWh: Double get() = energyDeltaKwh * 1000.0
}

@Serializable
data class DailyConsumptionDto(
    @SerialName("id") val id: String? = null,
    @SerialName("appliance_id") val applianceId: String,
    @SerialName("consumption_date") val consumptionDate: String,
    @SerialName("energy_kwh") val energyKwh: Double = 0.0,
    @SerialName("runtime_seconds") val runtimeSeconds: Double = 0.0,
    @SerialName("estimated_cost") val estimatedCost: Double = 0.0,
    @SerialName("updated_at") val updatedAt: String? = null
) {
    val energyWh: Double get() = energyKwh * 1000.0
}

