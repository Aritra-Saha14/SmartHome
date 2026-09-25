package com.semhas.app.data.model

data class EnergyUsage(
    val period: String, // e.g. "Today", "This Week", "This Month"
    val totalEnergy: Double, // canonical in Wh
    val estimatedCost: Double, // in INR
    val ratePerWh: Double // in INR per Wh
) {
    // Backward-compatibility constructor
    constructor(
        period: String,
        totalEnergyKwh: Double,
        estimatedCost: Double,
        ratePerKwh: Double,
        isLegacy: Boolean = true
    ) : this(
        period = period,
        totalEnergy = totalEnergyKwh * 1000.0,
        estimatedCost = estimatedCost,
        ratePerWh = if (ratePerKwh > 1.0) ratePerKwh / 1000.0 else ratePerKwh
    )

    // Backward-compatibility getters
    val totalEnergyWh: Double get() = totalEnergy
    val totalEnergyKwh: Double get() = totalEnergy / 1000.0
    val ratePerKwh: Double get() = ratePerWh * 1000.0
}
