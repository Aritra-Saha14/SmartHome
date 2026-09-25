package com.semhas.app.data.model

data class PowerReading(
    val channelId: Int,
    val timestamp: Long,
    val voltage: Double,
    val current: Double,
    val power: Double,
    val energy: Double
)
