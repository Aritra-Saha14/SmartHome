package com.semhas.app.data.model

data class Channel(
    val channelId: Int,
    val channelNumber: Int,
    val applianceName: String,
    val relayState: Boolean,
    val voltage: Double,
    val current: Double,
    val power: Double,
    val energy: Double,
    val runtime: Long, // in seconds
    val estimatedCost: Double,
    val isHealthy: Boolean = true,
    val lastReadingMillis: Long = 0L,
    val intensity: Int = 100
) {
    val energyWh: Double get() = energy
    val energyKwh: Double get() = energy / 1000.0
}

/**
 * Variable speed / intensity capability is strictly hardware-defined:
 * Enabled exclusively for CH4 and CH5.
 * CH1, CH2, and CH3 are unsupported regardless of appliance name or renaming.
 */
val Channel.isIntensitySupported: Boolean
    get() = channelNumber == 4 || channelNumber == 5

/**
 * Display label for variable control:
 * Contextually displays "Fan Speed", "Light Intensity", or generic "Speed / Intensity".
 * Note: Visibility of the control is strictly governed by isIntensitySupported (CH4 & CH5 only).
 */
val Channel.intensityDisplayLabel: String
    get() {
        val name = applianceName.lowercase().trim()
        return when {
            name.contains("fan") -> "Fan Speed"
            name.contains("light") || name.contains("lamp") || name.contains("bulb") -> "Light Intensity"
            else -> "Speed / Intensity"
        }
    }
