package com.semhas.app.utils

object Constants {
    const val CHANNEL_COUNT = 5

    const val CHANNEL_1_DEFAULT_NAME = "Living Room Light"
    const val CHANNEL_2_DEFAULT_NAME = "Bedroom Fan"
    const val CHANNEL_3_DEFAULT_NAME = "TV"
    const val CHANNEL_4_DEFAULT_NAME = "Kitchen"
    const val CHANNEL_5_DEFAULT_NAME = "AC"

    const val DEFAULT_ELECTRICITY_RATE_PER_WH = 0.008 // INR per Wh (equivalent to ₹8.00/kWh)
    const val DEFAULT_ELECTRICITY_RATE = DEFAULT_ELECTRICITY_RATE_PER_WH
    const val CURRENCY_SYMBOL = "₹"
    const val DEFAULT_MONTHLY_BILL_LIMIT = 1500.0 // Default monthly electricity bill limit in INR (₹)

    const val DEVICE_ID = "SEMHAS-ESP32-01"
    const val DEVICE_NAME = "SEMHAS Central Hub"
    const val FIRMWARE_VERSION = "v1.0.4-esp32"
}
