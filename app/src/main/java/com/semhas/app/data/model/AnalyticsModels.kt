package com.semhas.app.data.model

/**
 * Persistent billing summary representing accumulated energy and costs
 * across multiple time horizons. Canonical energy is stored in Wh.
 */
data class HistoricalBillingSummary(
    val todayEnergyWh: Double = 0.0,
    val todayCost: Double = 0.0,
    val weeklyEnergyWh: Double = 0.0,
    val weeklyCost: Double = 0.0,
    val monthlyEnergyWh: Double = 0.0,
    val monthlyCost: Double = 0.0,
    val allTimeEnergyWh: Double = 0.0,
    val allTimeCost: Double = 0.0
) {
    // Backward-compatibility getters
    val todayEnergyKwh: Double get() = todayEnergyWh / 1000.0
    val weeklyEnergyKwh: Double get() = weeklyEnergyWh / 1000.0
    val monthlyEnergyKwh: Double get() = monthlyEnergyWh / 1000.0
    val allTimeEnergyKwh: Double get() = allTimeEnergyWh / 1000.0
}

/**
 * Per-appliance persistent consumption breakdown for a given time period in Wh.
 */
data class ApplianceHistoricalUsage(
    val channelNumber: Int,
    val applianceName: String,
    val energyWh: Double,
    val cost: Double,
    val percentage: Double
) {
    val energyKwh: Double get() = energyWh / 1000.0
}

/**
 * Aggregated analytics data for a selected view (Daily, Weekly, Monthly) in Wh.
 */
data class PeriodAnalyticsData(
    val periodName: String = "DAILY",
    val periodLabel: String = "",
    val totalEnergyWh: Double = 0.0,
    val totalCost: Double = 0.0,
    val trends: List<Pair<String, Double>> = emptyList(),
    val applianceBreakdown: List<ApplianceHistoricalUsage> = emptyList(),
    val highestApplianceName: String = "N/A",
    val highestApplianceEnergy: Double = 0.0,
    val hasData: Boolean = false
) {
    val totalEnergyKwh: Double get() = totalEnergyWh / 1000.0
}
