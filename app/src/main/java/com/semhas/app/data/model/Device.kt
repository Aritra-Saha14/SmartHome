package com.semhas.app.data.model

import java.time.Instant

data class Device(
    val id: String = "SEMHAS-001",
    val name: String = "SEMHAS Central Hub",
    val deviceCode: String = "SEMHAS-001",
    val firmwareVersion: String = "v1.0.4-esp32",
    val wifiSsid: String? = null,
    val isOnline: Boolean = false,
    val lastSeen: String? = null,
    val wifiRssi: Int? = null,
    val ipAddress: String? = null,
    val uptimeSeconds: Long? = null,
    val freeHeapBytes: Long? = null,
    val minFreeHeapBytes: Long? = null,
    val resetReason: String? = null,
    val bootTime: Instant? = null,
    val healthUpdatedAt: Instant? = null,
    // Compatibility accessors for existing UI and voice components
    val isActive: Boolean = isOnline,
    val connectivityStatus: String = if (isOnline) "Connected" else "Offline"
)
