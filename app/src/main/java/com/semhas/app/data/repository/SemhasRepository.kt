package com.semhas.app.data.repository

import com.semhas.app.data.model.Billing
import com.semhas.app.data.model.Channel
import com.semhas.app.data.model.Device
import com.semhas.app.data.model.EnergyUsage
import com.semhas.app.data.model.HistoricalBillingSummary
import com.semhas.app.data.model.HistoryEvent
import com.semhas.app.data.model.Notification
import com.semhas.app.data.model.PeriodAnalyticsData
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate

interface SemhasRepository {
    val device: StateFlow<Device>
    val channels: StateFlow<List<Channel>>
    val billing: StateFlow<Billing>
    val billingSummary: StateFlow<HistoricalBillingSummary>
    val energyUsage: StateFlow<EnergyUsage>
    val notifications: StateFlow<List<Notification>>
    val historyEvents: StateFlow<List<HistoryEvent>>
    val monthlyBillLimit: StateFlow<Double>

    suspend fun toggleChannel(channelId: Int, state: Boolean)
    suspend fun toggleChannel(channelId: Int, state: Boolean, source: String = "UI") {
        toggleChannel(channelId, state)
    }
    suspend fun renameChannel(channelId: Int, newName: String)
    suspend fun setElectricityRate(newRate: Double)
    suspend fun setMonthlyBillLimit(limit: Double)
    suspend fun markNotificationAsRead(id: String)
    suspend fun clearAllNotifications()
    suspend fun setAllChannels(state: Boolean)
    suspend fun setChannelIntensity(channelId: Int, intensity: Int)
    fun getAnalyticsTrends(period: String): List<Pair<String, Double>>

    suspend fun getDailyAnalytics(date: LocalDate): PeriodAnalyticsData
    suspend fun getWeeklyAnalytics(weekStartDate: LocalDate): PeriodAnalyticsData
    suspend fun getMonthlyAnalytics(year: Int, month: Int): PeriodAnalyticsData
    suspend fun refreshHistoricalAnalytics()
}

