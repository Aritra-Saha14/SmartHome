package com.semhas.app.data.model

data class Notification(
    val id: String,
    val type: String, // "CONSUMPTION", "CONNECTIVITY", "SYSTEM", "ALERT"
    val title: String,
    val message: String,
    val timestamp: Long,
    val isRead: Boolean = false,
    val severity: String = "INFO" // "INFO", "WARNING", "ERROR", "SUCCESS"
)
