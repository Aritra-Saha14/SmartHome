package com.semhas.app.data.mock

import com.semhas.app.data.model.ApplianceHistoricalUsage
import com.semhas.app.data.model.Billing
import com.semhas.app.data.model.Channel
import com.semhas.app.data.model.Device
import com.semhas.app.data.model.EnergyUsage
import com.semhas.app.data.model.HistoricalBillingSummary
import com.semhas.app.data.model.HistoryEvent
import com.semhas.app.data.model.Notification
import com.semhas.app.data.model.PeriodAnalyticsData
import com.semhas.app.data.repository.SemhasRepository
import com.semhas.app.utils.Constants
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import android.util.Log
import com.semhas.app.data.preferences.BillingRateStore
import com.semhas.app.data.preferences.InMemoryBillingRateStore
import java.util.Locale
import java.util.UUID
import kotlin.random.Random

private const val TAG = "MockSemhasRepo"

class MockSemhasRepository(
    val rateStore: BillingRateStore = InMemoryBillingRateStore(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) : SemhasRepository {

    private var isHistoricalDataCleared = false

    private val _device = MutableStateFlow(MockData.getInitialDevice())
    override val device: StateFlow<Device> = _device.asStateFlow()

    private val _channels = MutableStateFlow(MockData.getInitialChannels())
    override val channels: StateFlow<List<Channel>> = _channels.asStateFlow()

    private val _billing = MutableStateFlow(MockData.getInitialBilling())
    override val billing: StateFlow<Billing> = _billing.asStateFlow()

    private val _billingSummary = MutableStateFlow(
        HistoricalBillingSummary(
            todayEnergyWh = 2450.0,
            todayCost = 19.60,
            weeklyEnergyWh = 16800.0,
            weeklyCost = 134.40,
            monthlyEnergyWh = 68500.0,
            monthlyCost = 548.00,
            allTimeEnergyWh = 245800.0,
            allTimeCost = 1966.40
        )
    )
    override val billingSummary: StateFlow<HistoricalBillingSummary> = _billingSummary.asStateFlow()

    private val _energyUsage = MutableStateFlow(MockData.getInitialEnergyUsage())
    override val energyUsage: StateFlow<EnergyUsage> = _energyUsage.asStateFlow()

    private val _notifications = MutableStateFlow(MockData.getInitialNotifications())
    override val notifications: StateFlow<List<Notification>> = _notifications.asStateFlow()

    private val _historyEvents = MutableStateFlow(MockData.getInitialHistoryEvents())
    override val historyEvents: StateFlow<List<HistoryEvent>> = _historyEvents.asStateFlow()

    private val _monthlyBillLimit = MutableStateFlow(Constants.DEFAULT_MONTHLY_BILL_LIMIT)
    override val monthlyBillLimit: StateFlow<Double> = _monthlyBillLimit.asStateFlow()

    init {
        scope.launch {
            _monthlyBillLimit.value = rateStore.getSavedMonthlyLimit()
            rateStore.monthlyLimitFlow.collect { limit ->
                _monthlyBillLimit.value = limit
            }
        }
        startTelemetrySimulation()
    }

    private fun startTelemetrySimulation() {
        scope.launch {
            while (isActive) {
                delay(2000L)
                simulateTick()
            }
        }
    }

    @Synchronized
    private fun simulateTick() {
        val currentRate = _billing.value.ratePerWh
        var additionalEnergyWh = 0.0

        val updatedChannels = _channels.value.map { channel ->
            if (channel.relayState) {
                // Subtle realistic fluctuation
                val voltageJitter = Random.nextDouble(-0.8, 0.8)
                val newVoltage = (230.0 + voltageJitter).coerceIn(220.0, 240.0)

                val nominalPower = when (channel.channelId) {
                    1 -> 40.0 // Living Room Light
                    2 -> 75.0 // Bedroom Fan
                    3 -> 120.0 // TV
                    4 -> 1200.0 // Kitchen
                    5 -> 1480.0 // AC
                    else -> 50.0
                }

                val powerJitter = nominalPower * Random.nextDouble(-0.03, 0.03)
                val newPower = (nominalPower + powerJitter).coerceAtLeast(5.0)
                val newCurrent = newPower / newVoltage

                // 2 seconds worth of Wh: (power * 2) / 3600
                val deltaWh = (newPower * 2.0) / 3600.0
                val newEnergy = channel.energy + deltaWh
                additionalEnergyWh += deltaWh

                val newRuntime = channel.runtime + 2L
                val newEstimatedCost = newEnergy * currentRate

                channel.copy(
                    voltage = Math.round(newVoltage * 10.0) / 10.0,
                    current = Math.round(newCurrent * 100.0) / 100.0,
                    power = Math.round(newPower * 10.0) / 10.0,
                    energy = newEnergy,
                    runtime = newRuntime,
                    estimatedCost = newEstimatedCost
                )
            } else {
                channel.copy(
                    voltage = 230.0,
                    current = 0.0,
                    power = 0.0
                )
            }
        }

        _channels.value = updatedChannels

        if (additionalEnergyWh > 0.0) {
            // Update Running Billing
            val currentBilling = _billing.value
            val newTotalEnergy = currentBilling.consumedEnergyWh + additionalEnergyWh
            val newEstimatedCost = newTotalEnergy * currentRate
            _billing.value = currentBilling.copy(
                consumedEnergyWh = newTotalEnergy,
                estimatedCost = newEstimatedCost
            )

            // Update Today's Energy Usage
            val currentUsage = _energyUsage.value
            val newUsageEnergy = currentUsage.totalEnergy + additionalEnergyWh
            _energyUsage.value = currentUsage.copy(
                totalEnergy = newUsageEnergy,
                estimatedCost = newUsageEnergy * currentRate
            )
        }
    }

    override suspend fun toggleChannel(channelId: Int, state: Boolean) {
        val currentRate = _billing.value.ratePerWh
        var affectedApplianceName = "Channel $channelId"

        _channels.value = _channels.value.map { channel ->
            if (channel.channelId == channelId) {
                affectedApplianceName = channel.applianceName
                val defaultPower = when (channelId) {
                    1 -> 40.0
                    2 -> 75.0
                    3 -> 120.0
                    4 -> 1200.0
                    5 -> 1480.0
                    else -> 50.0
                }
                val newPower = if (state) defaultPower else 0.0
                val newCurrent = if (state) defaultPower / 230.0 else 0.0
                channel.copy(
                    relayState = state,
                    power = newPower,
                    current = Math.round(newCurrent * 100.0) / 100.0,
                    estimatedCost = channel.energy * currentRate
                )
            } else {
                channel
            }
        }

        val event = HistoryEvent(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            channelId = channelId,
            eventType = "CONTROL",
            description = "$affectedApplianceName turned ${if (state) "ON" else "OFF"}"
        )
        _historyEvents.value = listOf(event) + _historyEvents.value
    }

    override suspend fun renameChannel(channelId: Int, newName: String) {
        if (newName.isBlank()) return

        var oldName = ""
        _channels.value = _channels.value.map { channel ->
            if (channel.channelId == channelId) {
                oldName = channel.applianceName
                channel.copy(applianceName = newName.trim())
            } else {
                channel
            }
        }

        val event = HistoryEvent(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            channelId = channelId,
            eventType = "SYSTEM",
            description = "Channel $channelId renamed from '$oldName' to '$newName'"
        )
        _historyEvents.value = listOf(event) + _historyEvents.value
    }

    override suspend fun setElectricityRate(newRate: Double) {
        if (newRate <= 0.0) return
        rateStore.saveRate(newRate)

        // Recalculate Billing
        val currentBilling = _billing.value
        _billing.value = currentBilling.copy(
            ratePerWh = newRate,
            estimatedCost = currentBilling.consumedEnergyWh * newRate
        )

        // Recalculate EnergyUsage
        val currentUsage = _energyUsage.value
        _energyUsage.value = currentUsage.copy(
            ratePerWh = newRate,
            estimatedCost = currentUsage.totalEnergy * newRate
        )

        // Recalculate per-channel estimated costs without changing Wh
        _channels.value = _channels.value.map { channel ->
            channel.copy(estimatedCost = channel.energy * newRate)
        }

        // Recalculate billing summary costs without changing Wh
        val curSummary = _billingSummary.value
        _billingSummary.value = curSummary.copy(
            todayCost = curSummary.todayEnergyWh * newRate,
            weeklyCost = curSummary.weeklyEnergyWh * newRate,
            monthlyCost = curSummary.monthlyEnergyWh * newRate,
            allTimeCost = curSummary.allTimeEnergyWh * newRate
        )

        val event = HistoryEvent(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            channelId = null,
            eventType = "SYSTEM",
            description = "Electricity rate updated to ₹${String.format(Locale.US, "%.4f", newRate)}/Wh"
        )
        _historyEvents.value = listOf(event) + _historyEvents.value
    }

    override suspend fun setMonthlyBillLimit(limit: Double) {
        if (limit <= 0.0) return
        _monthlyBillLimit.value = limit
        rateStore.saveMonthlyLimit(limit)
    }

    fun clearHistoricalData() {
        isHistoricalDataCleared = true
        _channels.value = _channels.value.map { channel ->
            channel.copy(
                energy = 0.0,
                runtime = 0L,
                estimatedCost = 0.0
            )
        }
        _billingSummary.value = HistoricalBillingSummary(
            todayEnergyWh = 0.0,
            todayCost = 0.0,
            weeklyEnergyWh = 0.0,
            weeklyCost = 0.0,
            monthlyEnergyWh = 0.0,
            monthlyCost = 0.0,
            allTimeEnergyWh = 0.0,
            allTimeCost = 0.0
        )
        _energyUsage.value = _energyUsage.value.copy(
            totalEnergy = 0.0,
            estimatedCost = 0.0
        )
        _billing.value = _billing.value.copy(
            consumedEnergyWh = 0.0,
            estimatedCost = 0.0
        )
    }

    override suspend fun markNotificationAsRead(id: String) {
        _notifications.value = _notifications.value.map { notif ->
            if (notif.id == id) notif.copy(isRead = true) else notif
        }
    }

    override suspend fun clearAllNotifications() {
        _notifications.value = _notifications.value.map { it.copy(isRead = true) }
    }

    override suspend fun setAllChannels(state: Boolean) {
        Log.i(TAG, "ALL_CHANNELS_ACTION_STARTED: requested relay_state=$state for all channels")
        val currentRate = _billing.value.ratePerKwh
        _channels.value = _channels.value.map { channel ->
            val defaultPower = when (channel.channelId) {
                1 -> 40.0
                2 -> 75.0
                3 -> 120.0
                4 -> 1200.0
                5 -> 1480.0
                else -> 50.0
            }
            val newPower = if (state) defaultPower else 0.0
            val newCurrent = if (state) defaultPower / 230.0 else 0.0
            Log.i(TAG, "CH${channel.channelId}_UPDATE_SUCCESS: Channel ${channel.channelId} updated to relay_state=$state")
            channel.copy(
                relayState = state,
                power = newPower,
                current = Math.round(newCurrent * 100.0) / 100.0,
                estimatedCost = channel.energy * currentRate
            )
        }
        Log.i(TAG, "ALL_CHANNELS_ACTION_SUCCESS: All 5 channels successfully updated to relay_state=$state")

        val event = HistoryEvent(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            channelId = null,
            eventType = "CONTROL",
            description = "All 5 channels turned ${if (state) "ON" else "OFF"}"
        )
        _historyEvents.value = listOf(event) + _historyEvents.value
    }

    override suspend fun setChannelIntensity(channelId: Int, intensity: Int) {
        Log.i(TAG, "[SEMHAS][INTENSITY] CH$channelId -> $intensity%")
        Log.i(TAG, "INTENSITY_ACTION_STARTED: channel=$channelId requestedIntensity=$intensity")
        _channels.value = _channels.value.map { ch ->
            if (ch.channelId == channelId) ch.copy(intensity = intensity) else ch
        }
        Log.i(TAG, "INTENSITY_DB_UPDATE_SUCCESS: channel=$channelId intensity=$intensity")
        val event = HistoryEvent(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            channelId = channelId,
            eventType = "CONTROL",
            description = "Channel $channelId intensity set to $intensity%"
        )
        _historyEvents.value = listOf(event) + _historyEvents.value
    }

    override fun getAnalyticsTrends(period: String): List<Pair<String, Double>> {
        return MockData.getAnalyticsTrends(period)
    }

    override suspend fun refreshHistoricalAnalytics() {
        // No-op in mock
    }

    override suspend fun getDailyAnalytics(date: LocalDate): PeriodAnalyticsData {
        val currentRate = _billing.value.ratePerWh
        val dateStr = date.format(DateTimeFormatter.ofPattern("EEEE, d MMM yyyy"))
        if (isHistoricalDataCleared) {
            return PeriodAnalyticsData(
                periodName = "DAILY",
                periodLabel = dateStr,
                totalEnergyWh = 0.0,
                totalCost = 0.0,
                trends = emptyList(),
                applianceBreakdown = emptyList(),
                highestApplianceName = "N/A",
                highestApplianceEnergy = 0.0,
                hasData = false
            )
        }
        val totalE = _channels.value.sumOf { it.energy }
        val breakdown = _channels.value.map { ch ->
            val pct = if (totalE > 0) Math.round((ch.energy / totalE) * 100.0).toDouble() else 0.0
            ApplianceHistoricalUsage(
                channelNumber = ch.channelNumber,
                applianceName = ch.applianceName,
                energyWh = ch.energy,
                cost = ch.estimatedCost,
                percentage = pct
            )
        }
        val highest = breakdown.maxByOrNull { it.energyWh }
        return PeriodAnalyticsData(
            periodName = "DAILY",
            periodLabel = dateStr,
            totalEnergyWh = totalE,
            totalCost = totalE * currentRate,
            trends = MockData.getAnalyticsTrends("DAILY"),
            applianceBreakdown = breakdown,
            highestApplianceName = highest?.applianceName ?: "N/A",
            highestApplianceEnergy = highest?.energyWh ?: 0.0,
            hasData = totalE > 0.0
        )
    }

    override suspend fun getWeeklyAnalytics(weekStartDate: LocalDate): PeriodAnalyticsData {
        val currentRate = _billing.value.ratePerWh
        val monday = weekStartDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val sunday = monday.plusDays(6)
        val label = "${monday.format(DateTimeFormatter.ofPattern("d MMM"))} - ${sunday.format(DateTimeFormatter.ofPattern("d MMM yyyy"))}"
        if (isHistoricalDataCleared) {
            return PeriodAnalyticsData(
                periodName = "WEEKLY",
                periodLabel = label,
                totalEnergyWh = 0.0,
                totalCost = 0.0,
                trends = emptyList(),
                applianceBreakdown = emptyList(),
                highestApplianceName = "N/A",
                highestApplianceEnergy = 0.0,
                hasData = false
            )
        }
        val trends = MockData.getAnalyticsTrends("WEEKLY")
        val totalE = trends.sumOf { it.second }
        val breakdown = _channels.value.map { ch ->
            ApplianceHistoricalUsage(
                channelNumber = ch.channelNumber,
                applianceName = ch.applianceName,
                energyWh = ch.energy * 7,
                cost = ch.estimatedCost * 7,
                percentage = 20.0
            )
        }
        val highest = breakdown.maxByOrNull { it.energyWh }
        return PeriodAnalyticsData(
            periodName = "WEEKLY",
            periodLabel = label,
            totalEnergyWh = totalE,
            totalCost = totalE * currentRate,
            trends = trends,
            applianceBreakdown = breakdown,
            highestApplianceName = highest?.applianceName ?: "AC",
            highestApplianceEnergy = 35000.0,
            hasData = true
        )
    }

    override suspend fun getMonthlyAnalytics(year: Int, month: Int): PeriodAnalyticsData {
        val currentRate = _billing.value.ratePerWh
        val monthDate = LocalDate.of(year, month, 1)
        val now = LocalDate.now()
        val label = monthDate.format(DateTimeFormatter.ofPattern("MMMM yyyy"))

        if (isHistoricalDataCleared || year > now.year || (year == now.year && month > now.monthValue) || year < 2024) {
            return PeriodAnalyticsData(
                periodName = "MONTHLY",
                periodLabel = label,
                totalEnergyWh = 0.0,
                totalCost = 0.0,
                trends = emptyList(),
                applianceBreakdown = emptyList(),
                highestApplianceName = "N/A",
                highestApplianceEnergy = 0.0,
                hasData = false
            )
        }

        val trends = MockData.getAnalyticsTrends("MONTHLY")
        val totalE = trends.sumOf { it.second }
        val breakdown = _channels.value.map { ch ->
            ApplianceHistoricalUsage(
                channelNumber = ch.channelNumber,
                applianceName = ch.applianceName,
                energyWh = ch.energy * 30,
                cost = ch.estimatedCost * 30,
                percentage = 20.0
            )
        }
        val highest = breakdown.maxByOrNull { it.energyWh }
        return PeriodAnalyticsData(
            periodName = "MONTHLY",
            periodLabel = label,
            totalEnergyWh = totalE,
            totalCost = totalE * currentRate,
            trends = trends,
            applianceBreakdown = breakdown,
            highestApplianceName = highest?.applianceName ?: "AC",
            highestApplianceEnergy = 160000.0,
            hasData = true
        )
    }
}



