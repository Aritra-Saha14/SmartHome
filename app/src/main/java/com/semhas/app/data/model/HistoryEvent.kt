package com.semhas.app.data.model

data class HistoryEvent(
    val id: String,
    val timestamp: Long,
    val channelId: Int? = null,
    val eventType: String, // "CONTROL", "ALERT", "ENERGY", "SYSTEM"
    val description: String
)
