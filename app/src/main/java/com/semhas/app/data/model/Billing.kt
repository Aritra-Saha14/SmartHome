package com.semhas.app.data.model

data class Billing(
    val billingPeriodStart: String = "",
    val billingPeriodEnd: String = "",
    val ratePerWh: Double = 0.008,
    val consumedEnergyWh: Double = 0.0,
    val estimatedCost: Double = consumedEnergyWh * ratePerWh
) {
    // Backward-compatibility getters
    val ratePerKwh: Double get() = ratePerWh * 1000.0
    val consumedEnergyKwh: Double get() = consumedEnergyWh / 1000.0

    companion object {
        fun fromLegacyKwh(
            billingPeriodStart: String = "",
            billingPeriodEnd: String = "",
            ratePerKwh: Double = 8.0,
            consumedEnergyKwh: Double = 0.0,
            estimatedCost: Double = 0.0
        ): Billing = Billing(
            billingPeriodStart = billingPeriodStart,
            billingPeriodEnd = billingPeriodEnd,
            ratePerWh = if (ratePerKwh > 1.0) ratePerKwh / 1000.0 else ratePerKwh,
            consumedEnergyWh = consumedEnergyKwh * 1000.0,
            estimatedCost = estimatedCost
        )
    }
}
