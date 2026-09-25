package com.semhas.app.data.repository

import android.util.Log
import com.semhas.app.data.model.ApplianceHistoricalUsage
import com.semhas.app.data.model.Billing
import com.semhas.app.data.model.Channel
import com.semhas.app.data.model.Device
import com.semhas.app.data.model.EnergyUsage
import com.semhas.app.data.model.HistoricalBillingSummary
import com.semhas.app.data.model.HistoryEvent
import com.semhas.app.data.model.Notification
import com.semhas.app.data.model.PeriodAnalyticsData
import com.semhas.app.data.supabase.SupabaseClientProvider
import com.semhas.app.data.supabase.SupabaseConfig
import com.semhas.app.data.supabase.model.ApplianceDto
import com.semhas.app.data.supabase.model.DailyConsumptionDto
import com.semhas.app.data.supabase.model.DeviceDto
import com.semhas.app.data.supabase.model.EnergyReadingDto
import com.semhas.app.data.supabase.model.LiveReadingDto
import com.semhas.app.utils.Constants
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import android.content.Context
import com.semhas.app.data.preferences.BillingRateStore
import com.semhas.app.data.preferences.DataStoreBillingPreferences
import com.semhas.app.data.preferences.InMemoryBillingRateStore
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class SupabaseSemhasRepository(
    context: Context? = null,
    rateStore: BillingRateStore? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : SemhasRepository {

    private val rateStore: BillingRateStore =
        rateStore ?: context?.let { DataStoreBillingPreferences(it) }
        ?: InMemoryBillingRateStore()

    companion object {
        private const val TAG = "SupabaseRepository"

        private fun parseIsoToMillis(isoString: String?): Long {
            if (isoString.isNullOrBlank()) return 0L
            return try {
                java.time.Instant.parse(isoString).toEpochMilli()
            } catch (e: Exception) {
                try {
                    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
                    sdf.timeZone = TimeZone.getTimeZone("UTC")
                    val cleanStr = if (isoString.length >= 19) isoString.substring(0, 19) else isoString
                    sdf.parse(cleanStr)?.time ?: 0L
                } catch (e2: Exception) {
                    0L
                }
            }
        }

        private fun parseIsoToInstant(isoString: String?): Instant? {
            if (isoString.isNullOrBlank()) return null
            return try {
                Instant.parse(isoString)
            } catch (e: Exception) {
                try {
                    val ms = parseIsoToMillis(isoString)
                    if (ms > 0L) Instant.ofEpochMilli(ms) else null
                } catch (e2: Exception) {
                    null
                }
            }
        }

        private fun DeviceDto.toDomain(): Device {
            val bootInstant = parseIsoToInstant(bootTime)
            val healthUpdatedInstant = parseIsoToInstant(healthUpdatedAt)
            return Device(
                id = id,
                name = deviceName,
                deviceCode = deviceCode,
                firmwareVersion = firmwareVersion,
                wifiSsid = wifiSsid,
                isOnline = isOnline,
                lastSeen = lastSeen,
                wifiRssi = wifiRssi,
                ipAddress = ipAddress,
                uptimeSeconds = uptimeSeconds,
                freeHeapBytes = freeHeapBytes,
                minFreeHeapBytes = minFreeHeapBytes,
                resetReason = resetReason,
                bootTime = bootInstant,
                healthUpdatedAt = healthUpdatedInstant,
                isActive = isOnline,
                connectivityStatus = if (isOnline) "Connected" else "Offline"
            )
        }
    }

    private val client by lazy { SupabaseClientProvider.client }

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    // Domain StateFlows exposed to ViewModels
    private val _device = MutableStateFlow(
        Device(
            id = SupabaseConfig.DEVICE_CODE,
            name = "SEMHAS Central Hub",
            deviceCode = SupabaseConfig.DEVICE_CODE,
            firmwareVersion = "v1.0.4-esp32",
            wifiSsid = null,
            isOnline = false,
            lastSeen = null,
            isActive = false,
            connectivityStatus = "Connecting..."
        )
    )
    override val device: StateFlow<Device> = _device.asStateFlow()

    private val _channels = MutableStateFlow<List<Channel>>(emptyList())
    override val channels: StateFlow<List<Channel>> = _channels.asStateFlow()

    private var currentRate: Double = Constants.DEFAULT_ELECTRICITY_RATE_PER_WH

    private val _billing = MutableStateFlow(
        Billing(
            billingPeriodStart = "01 Sep 2026",
            billingPeriodEnd = "30 Sep 2026",
            ratePerWh = currentRate,
            consumedEnergyWh = 0.0,
            estimatedCost = 0.0
        )
    )
    override val billing: StateFlow<Billing> = _billing.asStateFlow()

    private val _billingSummary = MutableStateFlow(HistoricalBillingSummary())
    override val billingSummary: StateFlow<HistoricalBillingSummary> = _billingSummary.asStateFlow()

    private val _energyUsage = MutableStateFlow(
        EnergyUsage(
            period = "Today",
            totalEnergy = 0.0,
            estimatedCost = 0.0,
            ratePerWh = currentRate
        )
    )
    override val energyUsage: StateFlow<EnergyUsage> = _energyUsage.asStateFlow()

    private val _notifications = MutableStateFlow<List<Notification>>(emptyList())
    override val notifications: StateFlow<List<Notification>> = _notifications.asStateFlow()

    private val _historyEvents = MutableStateFlow<List<HistoryEvent>>(emptyList())
    override val historyEvents: StateFlow<List<HistoryEvent>> = _historyEvents.asStateFlow()

    private val _monthlyBillLimit = MutableStateFlow(Constants.DEFAULT_MONTHLY_BILL_LIMIT)
    override val monthlyBillLimit: StateFlow<Double> = _monthlyBillLimit.asStateFlow()

    private val applianceIdToChannelNumber = ConcurrentHashMap<String, Int>()
    private val applianceIdMap = ConcurrentHashMap<Int, ApplianceDto>()
    private val latestReadingsMap = ConcurrentHashMap<Int, LiveReadingDto>()
    private val dailyConsumptionMap = ConcurrentHashMap<String, DailyConsumptionDto>()
    private val todayEnergyReadings = CopyOnWriteArrayList<EnergyReadingDto>()

    // Forensic write tracking for relay downlink verification
    private val lastWriteTimestamp = ConcurrentHashMap<Int, Long>()
    private val lastWriteState = ConcurrentHashMap<Int, Boolean>()
    private val lastWriteSource = ConcurrentHashMap<Int, String>()

    private var realtimeChannel: RealtimeChannel? = null
    private var realtimeJob: Job? = null

    init {
        Log.d(TAG, "Initializing SupabaseSemhasRepository. isConfigured=${SupabaseConfig.isConfigured}")
        startInitialLoadAndSubscriptions()
    }

    private fun startInitialLoadAndSubscriptions() {
        scope.launch {
            try {
                currentRate = rateStore.getSavedRate()
                Log.d(TAG, "Loaded persisted electricity rate: ₹$currentRate/Wh")
            } catch (e: Exception) {
                currentRate = Constants.DEFAULT_ELECTRICITY_RATE_PER_WH
            }
            try {
                _monthlyBillLimit.value = rateStore.getSavedMonthlyLimit()
                Log.d(TAG, "Loaded persisted monthly bill limit: ₹${_monthlyBillLimit.value}")
            } catch (e: Exception) {
                _monthlyBillLimit.value = Constants.DEFAULT_MONTHLY_BILL_LIMIT
            }
            _billing.value = _billing.value.copy(ratePerWh = currentRate)
            _energyUsage.value = _energyUsage.value.copy(ratePerWh = currentRate)

            if (!SupabaseConfig.isConfigured) {
                Log.w(TAG, "Supabase credentials not configured in local.properties. Awaiting configuration...")
                _device.value = _device.value.copy(
                    connectivityStatus = "Config Required",
                    isActive = false
                )
                initializeDefaultChannels()
                return@launch
            }

            fetchDailyConsumptionFromRemote()
            loadInitialData()
            fetchTodayEnergyReadingsFromRemote()
            setupRealtimeSubscriptions()
        }
    }

    private fun initializeDefaultChannels() {
        if (_channels.value.isNotEmpty()) return

        val defaultList = (1..Constants.CHANNEL_COUNT).map { chNum ->
            val name = when (chNum) {
                1 -> Constants.CHANNEL_1_DEFAULT_NAME
                2 -> Constants.CHANNEL_2_DEFAULT_NAME
                3 -> Constants.CHANNEL_3_DEFAULT_NAME
                4 -> Constants.CHANNEL_4_DEFAULT_NAME
                5 -> Constants.CHANNEL_5_DEFAULT_NAME
                else -> "Channel $chNum"
            }
            Channel(
                channelId = chNum,
                channelNumber = chNum,
                applianceName = name,
                relayState = false,
                voltage = 0.0,
                current = 0.0,
                power = 0.0,
                energy = 0.0,
                runtime = 0L,
                estimatedCost = 0.0,
                isHealthy = true
            )
        }
        _channels.value = defaultList
    }

    private suspend fun loadInitialData() {
        try {
            Log.d(TAG, "Fetching device: ${SupabaseConfig.DEVICE_CODE}")

            // STEP 1: Fetch device
            val deviceDto = client.from("devices")
                .select {
                    filter {
                        eq("device_code", SupabaseConfig.DEVICE_CODE)
                    }
                }.decodeSingleOrNull<DeviceDto>()

            if (deviceDto == null) {
                Log.e(TAG, "Device '${SupabaseConfig.DEVICE_CODE}' not found in public.devices!")
                _device.value = _device.value.copy(
                    connectivityStatus = "Device Not Found",
                    isActive = false
                )
                initializeDefaultChannels()
                return
            }

            Log.d(TAG, "Device fetched: ${deviceDto.deviceName} (ID: ${deviceDto.id}), online=${deviceDto.isOnline}")
            _device.value = deviceDto.toDomain()

            // STEP 2: Fetch all appliances for this device
            Log.d(TAG, "Fetching appliances for device_id: ${deviceDto.id}")
            val appliances = client.from("appliances")
                .select {
                    filter {
                        eq("device_id", deviceDto.id)
                    }
                    order("channel_number", Order.ASCENDING)
                }.decodeList<ApplianceDto>()

            Log.d(TAG, "Fetched ${appliances.size} appliances from public.appliances")

            appliances.forEach { app ->
                applianceIdToChannelNumber[app.id] = app.channelNumber
                applianceIdMap[app.channelNumber] = app
                Log.i(
                    TAG,
                    "CH${app.channelNumber}_MAPPING:\n" +
                        "deviceId=${deviceDto.id}\n" +
                        "applianceId=${app.id}\n" +
                        "channelNumber=${app.channelNumber}"
                )
            }

            // STEP 3: For each appliance, fetch its latest reading
            for (appliance in appliances) {
                try {
                    val latestReading = client.from("live_readings")
                        .select {
                            filter {
                                eq("appliance_id", appliance.id)
                            }
                            order("recorded_at", Order.DESCENDING)
                            limit(1)
                        }.decodeSingleOrNull<LiveReadingDto>()

                    if (latestReading != null) {
                        Log.d(
                            TAG,
                            "CH${appliance.channelNumber} (${appliance.applianceName}) latest reading: " +
                                "${latestReading.voltage}V, ${latestReading.power}W, ${latestReading.energy}kWh"
                        )
                        latestReadingsMap[appliance.channelNumber] = latestReading
                    } else {
                        Log.d(TAG, "CH${appliance.channelNumber} (${appliance.applianceName}) has no live_readings yet")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error fetching latest reading for appliance ${appliance.id}: ${e.message}")
                }
            }

            // STEP 4: Build Channel domain list
            rebuildChannelsAndEmit()

            addHistoryEvent(
                eventType = "SYSTEM",
                description = "Synchronized ${appliances.size} channels with Supabase",
                channelId = null
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error during initial Supabase load: ${e.message}", e)
            _device.value = _device.value.copy(
                connectivityStatus = "Connection Error",
                isActive = false
            )
            initializeDefaultChannels()
        }
    }

    private suspend fun fetchDailyConsumptionFromRemote() {
        try {
            val records = client.from("daily_consumption")
                .select()
                .decodeList<DailyConsumptionDto>()
            Log.d(TAG, "Fetched ${records.size} records from daily_consumption")
            dailyConsumptionMap.clear()
            records.forEach { rec ->
                dailyConsumptionMap["${rec.applianceId}_${rec.consumptionDate}"] = rec
            }
            updateHistoricalBillingAndAnalytics()
            rebuildChannelsAndEmit()
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching daily_consumption: ${e.message}")
        }
    }

    private suspend fun fetchTodayEnergyReadingsFromRemote() {
        try {
            val todayStr = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            val startOfDay = "${todayStr}T00:00:00Z"
            val readings = client.from("energy_readings")
                .select {
                    filter {
                        gte("created_at", startOfDay)
                    }
                    order("created_at", Order.ASCENDING)
                }.decodeList<EnergyReadingDto>()
            todayEnergyReadings.clear()
            todayEnergyReadings.addAll(readings)
            Log.d(TAG, "Fetched ${readings.size} energy readings for today from remote")
        } catch (e: Exception) {
            Log.w(TAG, "Could not fetch today's energy readings from remote: ${e.message}")
        }
    }

    private fun getApplianceHistoricalEnergy(applianceId: String): Double {
        return dailyConsumptionMap.values
            .filter { it.applianceId == applianceId }
            .sumOf { it.energyWh }
    }

    private fun getApplianceHistoricalRuntime(applianceId: String): Long {
        return dailyConsumptionMap.values
            .filter { it.applianceId == applianceId }
            .sumOf { it.runtimeSeconds }
            .toLong()
    }

    private fun updateHistoricalBillingAndAnalytics() {
        val today = LocalDate.now()
        val todayStr = today.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val sunday = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        val monStr = monday.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val sunStr = sunday.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val monthPrefix = String.format(Locale.US, "%04d-%02d", today.year, today.monthValue)

        val allRecords = dailyConsumptionMap.values.toList()

        val todayEnergyWh = allRecords.filter { it.consumptionDate == todayStr }.sumOf { it.energyWh }
        val todayCost = todayEnergyWh * currentRate

        val weeklyEnergyWh = allRecords.filter { it.consumptionDate >= monStr && it.consumptionDate <= sunStr }.sumOf { it.energyWh }
        val weeklyCost = weeklyEnergyWh * currentRate

        val monthlyEnergyWh = allRecords.filter { it.consumptionDate.startsWith(monthPrefix) }.sumOf { it.energyWh }
        val monthlyCost = monthlyEnergyWh * currentRate

        val allTimeEnergyWh = allRecords.sumOf { it.energyWh }
        val allTimeCost = allTimeEnergyWh * currentRate

        _billingSummary.value = HistoricalBillingSummary(
            todayEnergyWh = todayEnergyWh,
            todayCost = Math.round(todayCost * 100.0) / 100.0,
            weeklyEnergyWh = weeklyEnergyWh,
            weeklyCost = Math.round(weeklyCost * 100.0) / 100.0,
            monthlyEnergyWh = monthlyEnergyWh,
            monthlyCost = Math.round(monthlyCost * 100.0) / 100.0,
            allTimeEnergyWh = allTimeEnergyWh,
            allTimeCost = Math.round(allTimeCost * 100.0) / 100.0
        )

        // Update EnergyUsage flow for "Today" in Wh
        _energyUsage.value = EnergyUsage(
            period = "Today",
            totalEnergy = todayEnergyWh,
            estimatedCost = Math.round(todayCost * 100.0) / 100.0,
            ratePerWh = currentRate
        )

        // Update Billing flow for current monthly cycle in Wh
        val curBilling = _billing.value
        _billing.value = curBilling.copy(
            consumedEnergyWh = monthlyEnergyWh,
            estimatedCost = Math.round(monthlyCost * 100.0) / 100.0,
            ratePerWh = currentRate
        )
    }

    private fun rebuildChannelsAndEmit() {
        val channelsList = mutableListOf<Channel>()

        for (chNum in 1..Constants.CHANNEL_COUNT) {
            val appliance = applianceIdMap[chNum]
            val reading = latestReadingsMap[chNum]

            val defaultName = when (chNum) {
                1 -> Constants.CHANNEL_1_DEFAULT_NAME
                2 -> Constants.CHANNEL_2_DEFAULT_NAME
                3 -> Constants.CHANNEL_3_DEFAULT_NAME
                4 -> Constants.CHANNEL_4_DEFAULT_NAME
                5 -> Constants.CHANNEL_5_DEFAULT_NAME
                else -> "Channel $chNum"
            }

            val appName = appliance?.applianceName?.ifBlank { defaultName } ?: defaultName
            val relayState = appliance?.relayState ?: false

            // Current electrical power is strictly 0 when relay is turned OFF
            val voltage = if (relayState) (reading?.voltage ?: 0.0) else 0.0
            val current = if (relayState) (reading?.current ?: 0.0) else 0.0
            val power = if (relayState) (reading?.power ?: 0.0) else 0.0

            // Energy and runtime are strictly persistent historical values from daily_consumption.
            // live_readings MUST NEVER be used as a fallback for historical energy or runtime.
            val histEnergy = appliance?.let { getApplianceHistoricalEnergy(it.id) } ?: 0.0
            val histRuntime = appliance?.let { getApplianceHistoricalRuntime(it.id) } ?: 0L
            val estimatedCost = Math.round(histEnergy * currentRate * 100.0) / 100.0

            val readingTimestamp = parseIsoToMillis(reading?.recordedAt)
            val intensity = appliance?.intensity ?: 100

            channelsList.add(
                Channel(
                    channelId = chNum,
                    channelNumber = chNum,
                    applianceName = appName,
                    relayState = relayState,
                    voltage = Math.round(voltage * 10.0) / 10.0,
                    current = Math.round(current * 100.0) / 100.0,
                    power = Math.round(power * 10.0) / 10.0,
                    energy = histEnergy,
                    runtime = histRuntime,
                    estimatedCost = estimatedCost,
                    isHealthy = true,
                    lastReadingMillis = readingTimestamp,
                    intensity = intensity
                )
            )
        }

        _channels.value = channelsList
        updateAggregatedBillingAndEnergy(channelsList)
    }

    private fun updateAggregatedBillingAndEnergy(channelList: List<Channel>) {
        if (dailyConsumptionMap.isNotEmpty()) {
            updateHistoricalBillingAndAnalytics()
        } else {
            // Strictly zero when persistent historical database is empty.
            // DO NOT fall back to channelList.sumOf { it.energy } or live_readings.
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

            val curBilling = _billing.value
            _billing.value = curBilling.copy(
                consumedEnergyWh = 0.0,
                estimatedCost = 0.0,
                ratePerWh = currentRate
            )

            val curUsage = _energyUsage.value
            _energyUsage.value = curUsage.copy(
                totalEnergy = 0.0,
                estimatedCost = 0.0,
                ratePerWh = currentRate
            )
        }
    }

    private fun setupRealtimeSubscriptions() {
        realtimeJob?.cancel()
        realtimeJob = scope.launch {
            try {
                Log.d(TAG, "Setting up Supabase Realtime channel...")
                val channel = client.channel("semhas-realtime-channel")
                realtimeChannel = channel

                val readingChangeFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = "live_readings"
                }

                val energyReadingChangeFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = "energy_readings"
                }

                val dailyConsumptionChangeFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = "daily_consumption"
                }

                val applianceChangeFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = "appliances"
                }

                // Launch listener for live_readings (ESP32 telemetry inserts)
                launch {
                    readingChangeFlow.collect { action ->
                        try {
                            if (action is PostgresAction.Insert) {
                                val reading = json.decodeFromJsonElement<LiveReadingDto>(action.record)
                                onLiveReadingReceived(reading)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing live_readings Realtime event: ${e.message}")
                        }
                    }
                }

                // Launch listener for energy_readings (interval delta telemetry)
                launch {
                    energyReadingChangeFlow.collect { action ->
                        try {
                            when (action) {
                                is PostgresAction.Insert -> {
                                    val reading = json.decodeFromJsonElement<EnergyReadingDto>(action.record)
                                    onEnergyReadingReceived(reading)
                                }
                                is PostgresAction.Delete -> {
                                    Log.d(TAG, "REALTIME DELETE on energy_readings, refreshing daily consumption")
                                    fetchDailyConsumptionFromRemote()
                                }
                                else -> Unit
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing energy_readings Realtime event: ${e.message}")
                        }
                    }
                }

                // Launch listener for daily_consumption (persistent daily aggregations)
                launch {
                    dailyConsumptionChangeFlow.collect { action ->
                        try {
                            when (action) {
                                is PostgresAction.Insert -> {
                                    val dto = json.decodeFromJsonElement<DailyConsumptionDto>(action.record)
                                    onDailyConsumptionUpdated(dto)
                                }
                                is PostgresAction.Update -> {
                                    val dto = json.decodeFromJsonElement<DailyConsumptionDto>(action.record)
                                    onDailyConsumptionUpdated(dto)
                                }
                                is PostgresAction.Delete -> {
                                    Log.d(TAG, "REALTIME DELETE on daily_consumption, refreshing from remote")
                                    fetchDailyConsumptionFromRemote()
                                }
                                else -> Unit
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing daily_consumption Realtime event: ${e.message}")
                        }
                    }
                }

                // Launch listener for appliances (relay_state / appliance_name updates)
                launch {
                    applianceChangeFlow.collect { action ->
                        try {
                            when (action) {
                                is PostgresAction.Update -> {
                                    val appliance = json.decodeFromJsonElement<ApplianceDto>(action.record)
                                    onApplianceUpdated(appliance)
                                }
                                is PostgresAction.Insert -> {
                                    val appliance = json.decodeFromJsonElement<ApplianceDto>(action.record)
                                    onApplianceUpdated(appliance)
                                }
                                else -> Unit
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing appliances Realtime event: ${e.message}")
                        }
                    }
                }

                val deviceChangeFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = "devices"
                }

                // Launch listener for devices table (online status / last_seen / wifi_ssid)
                launch {
                    deviceChangeFlow.collect { action ->
                        try {
                            when (action) {
                                is PostgresAction.Update -> {
                                    val updatedDev = json.decodeFromJsonElement<DeviceDto>(action.record)
                                    if (updatedDev.deviceCode == SupabaseConfig.DEVICE_CODE) {
                                        _device.value = updatedDev.toDomain()
                                    }
                                }
                                else -> Unit
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing devices Realtime event: ${e.message}")
                        }
                    }
                }

                channel.subscribe()
                Log.d(TAG, "Supabase Realtime channel successfully subscribed!")

            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize Realtime subscriptions: ${e.message}", e)
                // Retry with backoff if network issue
                delay(5000L)
                if (isActive && SupabaseConfig.isConfigured) {
                    setupRealtimeSubscriptions()
                }
            }
        }
    }

    private fun onLiveReadingReceived(reading: LiveReadingDto) {
        val chNum = applianceIdToChannelNumber[reading.applianceId]
        if (chNum == null) {
            Log.d(TAG, "Received reading for unknown appliance_id: ${reading.applianceId}")
            return
        }

        Log.d(
            TAG,
            "REALTIME TELEMETRY CH$chNum: ${reading.voltage}V, ${reading.power}W, ${reading.current}A, ${reading.energy}kWh"
        )
        latestReadingsMap[chNum] = reading
        val readingTimestamp = parseIsoToMillis(reading.recordedAt).let {
            if (it > 0L) it else System.currentTimeMillis()
        }

        // Update Channel list reactively while preserving persistent historical energy
        val currentChannels = _channels.value
        val updated = currentChannels.map { channel ->
            if (channel.channelNumber == chNum) {
                val isRelayOn = channel.relayState
                val livePower = if (isRelayOn) reading.power else 0.0
                val liveCurrent = if (isRelayOn) reading.current else 0.0
                val liveVoltage = if (isRelayOn) reading.voltage else 0.0

                val histEnergy = getApplianceHistoricalEnergy(reading.applianceId)
                val histRuntime = getApplianceHistoricalRuntime(reading.applianceId)
                val newCost = Math.round(histEnergy * currentRate * 100.0) / 100.0

                channel.copy(
                    voltage = Math.round(liveVoltage * 10.0) / 10.0,
                    current = Math.round(liveCurrent * 100.0) / 100.0,
                    power = Math.round(livePower * 10.0) / 10.0,
                    energy = histEnergy,
                    runtime = histRuntime,
                    estimatedCost = newCost,
                    lastReadingMillis = readingTimestamp
                )
            } else {
                channel
            }
        }

        _channels.value = updated
        updateAggregatedBillingAndEnergy(updated)
    }

    private fun onEnergyReadingReceived(reading: EnergyReadingDto) {
        val chNum = applianceIdToChannelNumber[reading.applianceId] ?: return
        Log.d(TAG, "REALTIME ENERGY_READING CH$chNum: power=${reading.power}W delta=${reading.energyDeltaKwh}kWh")
        latestReadingsMap[chNum] = LiveReadingDto(
            applianceId = reading.applianceId,
            voltage = reading.voltage,
            current = reading.current,
            power = reading.power,
            energy = 0.0,
            runtimeSeconds = 0L,
            estimatedCost = 0.0,
            recordedAt = reading.recordedAt
        )
        todayEnergyReadings.add(reading)

        // Incrementally update dailyConsumptionMap for today without heavy remote fetch
        val todayStr = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val key = "${reading.applianceId}_$todayStr"
        val existing = dailyConsumptionMap[key]
        if (existing != null) {
            dailyConsumptionMap[key] = existing.copy(
                energyKwh = existing.energyKwh + reading.energyDeltaKwh,
                runtimeSeconds = existing.runtimeSeconds + reading.runtimeDeltaSeconds,
                estimatedCost = (existing.energyKwh + reading.energyDeltaKwh) * 1000.0 * currentRate
            )
        } else {
            dailyConsumptionMap[key] = DailyConsumptionDto(
                applianceId = reading.applianceId,
                consumptionDate = todayStr,
                energyKwh = reading.energyDeltaKwh,
                runtimeSeconds = reading.runtimeDeltaSeconds,
                estimatedCost = reading.energyDeltaKwh * 1000.0 * currentRate
            )
        }

        updateHistoricalBillingAndAnalytics()
        rebuildChannelsAndEmit()
    }

    private fun onDailyConsumptionUpdated(dto: DailyConsumptionDto) {
        Log.d(TAG, "REALTIME DAILY_CONSUMPTION UPDATE: appliance=${dto.applianceId} date=${dto.consumptionDate} energy=${dto.energyKwh}")
        dailyConsumptionMap["${dto.applianceId}_${dto.consumptionDate}"] = dto
        updateHistoricalBillingAndAnalytics()
        rebuildChannelsAndEmit()
    }

    private fun onApplianceUpdated(appliance: ApplianceDto) {
        val chNum = appliance.channelNumber
        Log.d(TAG, "REALTIME APPLIANCE UPDATE CH$chNum: name='${appliance.applianceName}', relay=${appliance.relayState}, intensity=${appliance.intensity}")

        val startMs = commandLatencyStartTimes[chNum]
        val targetState = commandLatencyTargetStates[chNum]
        if (startMs != null && targetState != null && appliance.relayState == targetState) {
            val rtElapsedMs = System.currentTimeMillis() - startMs
            Log.i(TAG, "RELAY_LATENCY_REALTIME_CONFIRMED channel=$chNum elapsed_ms=$rtElapsedMs")
            commandLatencyStartTimes.remove(chNum)
            commandLatencyTargetStates.remove(chNum)
        }

        val prevChannel = _channels.value.find { it.channelNumber == chNum }
        if (appliance.intensity != null && (prevChannel == null || appliance.intensity != prevChannel.intensity)) {
            Log.i(TAG, "INTENSITY_REALTIME_UPDATE: channel=$chNum intensity=${appliance.intensity}")
        }

        applianceIdToChannelNumber[appliance.id] = chNum
        applianceIdMap[chNum] = appliance

        val currentChannels = _channels.value
        val updated = currentChannels.map { channel ->
            if (channel.channelNumber == chNum) {
                val isRelayOn = appliance.relayState
                val reading = latestReadingsMap[chNum]
                val livePower = if (isRelayOn) (reading?.power ?: 0.0) else 0.0
                val liveCurrent = if (isRelayOn) (reading?.current ?: 0.0) else 0.0
                val liveVoltage = if (isRelayOn) (reading?.voltage ?: 0.0) else 0.0

                channel.copy(
                    applianceName = appliance.applianceName,
                    relayState = appliance.relayState,
                    intensity = appliance.intensity ?: channel.intensity,
                    power = Math.round(livePower * 10.0) / 10.0,
                    current = Math.round(liveCurrent * 100.0) / 100.0,
                    voltage = Math.round(liveVoltage * 10.0) / 10.0
                )
            } else {
                channel
            }
        }

        _channels.value = updated
        updateAggregatedBillingAndEnergy(updated)

        addHistoryEvent(
            eventType = "CONTROL",
            description = "${appliance.applianceName} relay state: ${if (appliance.relayState) "ON" else "OFF"}",
            channelId = chNum
        )
    }

    private fun addHistoryEvent(eventType: String, description: String, channelId: Int?) {
        val event = HistoryEvent(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            channelId = channelId,
            eventType = eventType,
            description = description
        )
        _historyEvents.value = listOf(event) + _historyEvents.value
    }

    // ==========================================
    // SemhasRepository Methods
    // ==========================================

    private val channelWriteJobs = ConcurrentHashMap<Int, Job>()
    private val commandLatencyStartTimes = ConcurrentHashMap<Int, Long>()
    private val commandLatencyTargetStates = ConcurrentHashMap<Int, Boolean>()

    override suspend fun toggleChannel(channelId: Int, state: Boolean) {
        toggleChannel(channelId, state, "UI")
    }

    override suspend fun toggleChannel(channelId: Int, state: Boolean, source: String) {
        val startTime = System.currentTimeMillis()
        commandLatencyStartTimes[channelId] = startTime
        commandLatencyTargetStates[channelId] = state
        Log.i(TAG, "RELAY_LATENCY_START channel=$channelId target=$state")

        val currentChannel = _channels.value.find { it.channelId == channelId }
        val previousState = currentChannel?.relayState ?: false
        if (currentChannel != null && previousState == state) {
            // Already in requested state; avoid redundant network calls
            return
        }

        // 1. Optimistic UI update: immediately update local StateFlow
        _channels.value = _channels.value.map { ch ->
            if (ch.channelId == channelId) {
                val livePower = if (state) ch.power else 0.0
                val liveCurrent = if (state) ch.current else 0.0
                val liveVoltage = if (state) ch.voltage else 0.0
                ch.copy(
                    relayState = state,
                    power = livePower,
                    current = liveCurrent,
                    voltage = liveVoltage
                )
            } else {
                ch
            }
        }

        // 2. Safely cancel / coalesce obsolete pending writes for this channel
        channelWriteJobs[channelId]?.cancel()

        // 3. Launch asynchronous Supabase write
        val job = scope.launch {
            val success = updateChannelInternal(channelId, state, source)
            if (success) {
                Log.i(TAG, "CH${channelId}_UPDATE_SUCCESS: Channel $channelId set to relay_state=$state (source=$source)")
            } else {
                Log.e(TAG, "CH${channelId}_UPDATE_FAILURE: Channel $channelId failed to set to relay_state=$state. Reverting optimistic update.")
                // Rollback optimistic state if update fails
                _channels.value = _channels.value.map { ch ->
                    if (ch.channelId == channelId) ch.copy(relayState = previousState) else ch
                }
            }
        }
        channelWriteJobs[channelId] = job
    }

    private suspend fun updateChannelInternal(channelId: Int, state: Boolean, source: String = "OTHER"): Boolean {
        Log.i(TAG, "ANDROID_RELAY_WRITE_SOURCE:\nchannel=$channelId\nsource=$source\ntargetState=$state")

        // Forensic detection of rapid reversals (< 10 seconds)
        val prevTimestamp = lastWriteTimestamp[channelId]
        val prevState = lastWriteState[channelId]
        val prevSource = lastWriteSource[channelId]
        if (prevTimestamp != null && prevState != null && prevState != state) {
            val elapsedMs = System.currentTimeMillis() - prevTimestamp
            if (elapsedMs < 10000L) {
                Log.w(
                    TAG,
                    "CRITICAL_STATE_REVERSAL_DETECTED:\n" +
                        "channel=$channelId\n" +
                        "previousState=$prevState (source=$prevSource)\n" +
                        "newState=$state (source=$source)\n" +
                        "elapsedMs=$elapsedMs"
                )
            }
        }
        lastWriteTimestamp[channelId] = System.currentTimeMillis()
        lastWriteState[channelId] = state
        lastWriteSource[channelId] = source

        if (!SupabaseConfig.isConfigured) {
            Log.w(TAG, "Supabase credentials not configured in local.properties. Cannot update CH$channelId in database.")
            return false
        }

        var appliance = applianceIdMap[channelId]
        if (appliance == null) {
            Log.w(TAG, "Appliance for CH$channelId not yet loaded in cache. Loading initial data from Supabase...")
            loadInitialData()
            appliance = applianceIdMap[channelId]
        }

        if (appliance == null) {
            Log.e(TAG, "toggleChannel ERROR: Could not find appliance record for CH$channelId in public.appliances")
            return false
        }

        val applianceUuid = appliance.id
        val devId = _device.value.id
        Log.i(
            TAG,
            "CH${channelId}_MAPPING:\n" +
                "deviceId=$devId\n" +
                "applianceId=$applianceUuid\n" +
                "channelNumber=$channelId"
        )
        Log.i(
            TAG,
            "ANDROID_RELAY_WRITE_STARTED:\n" +
                "channel=$channelId\n" +
                "targetState=$state\n" +
                "applianceId=$applianceUuid"
        )

        try {
            Log.i(TAG, "RELAY_LATENCY_DB_WRITE_START channel=$channelId")

            // 1. Send UPDATE with select() to verify rows affected
            val updateResult = client.from("appliances")
                .update(mapOf("relay_state" to state)) {
                    select()
                    filter {
                        eq("id", applianceUuid)
                    }
                }.decodeList<ApplianceDto>()

            val rowsAffected = updateResult.size
            Log.i(
                TAG,
                "ANDROID_RELAY_WRITE_RESPONSE:\n" +
                    "channel=$channelId\n" +
                    "rowsAffected=$rowsAffected"
            )

            if (rowsAffected == 0) {
                Log.e(
                    TAG,
                    "CRITICAL: Supabase returned 0 updated rows for CH$channelId (UUID: $applianceUuid, requested relay_state=$state)! " +
                    "This occurs because public.appliances does NOT have a development UPDATE RLS policy enabled. " +
                    "Execute this SQL in Supabase SQL Editor:\n" +
                    "CREATE POLICY \"Development update appliances\" ON public.appliances FOR UPDATE USING (true) WITH CHECK (true);"
                )
                return false
            }

            val startMs = commandLatencyStartTimes[channelId] ?: System.currentTimeMillis()
            val dbElapsedMs = System.currentTimeMillis() - startMs
            Log.i(TAG, "RELAY_LATENCY_DB_CONFIRMED channel=$channelId elapsed_ms=$dbElapsedMs")

            // 2. Immediately verify returned row or query verification
            val verified = updateResult.firstOrNull() ?: client.from("appliances")
                .select {
                    filter {
                        eq("id", applianceUuid)
                    }
                }.decodeSingleOrNull<ApplianceDto>()

            val storedRelayState = verified?.relayState
            Log.i(
                TAG,
                "ANDROID_RELAY_WRITE_VERIFY:\n" +
                    "channel=$channelId\n" +
                    "storedState=$storedRelayState"
            )

            if (verified != null && storedRelayState == state) {
                Log.i(
                    TAG,
                    "ANDROID_RELAY_WRITE_COMPLETE:\n" +
                        "channel=$channelId\n" +
                        "targetState=$state\n" +
                        "verifiedState=$storedRelayState"
                )

                // Update internal appliance cache
                applianceIdMap[channelId] = verified

                // 3. ONLY update local StateFlow after Supabase confirms the database update
                _channels.value = _channels.value.map { ch ->
                    if (ch.channelId == channelId) ch.copy(relayState = state) else ch
                }

                addHistoryEvent(
                    eventType = "CONTROL",
                    description = "${verified.applianceName} turned ${if (state) "ON" else "OFF"}",
                    channelId = channelId
                )
                return true
            } else {
                Log.e(
                    TAG,
                    "VERIFICATION MISMATCH: CH$channelId (UUID: $applianceUuid) expected relay_state=$state, " +
                    "but Supabase holds relay_state=$storedRelayState. Local StateFlow was NOT modified."
                )
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Supabase UPDATE failed for CH$channelId (UUID: $applianceUuid): ${e.message}", e)
            return false
        }
    }

    override suspend fun renameChannel(channelId: Int, newName: String) {
        val trimmedName = newName.trim()
        if (trimmedName.isBlank()) return
        Log.d(TAG, "renameChannel called: CH$channelId -> '$trimmedName'")

        var appliance = applianceIdMap[channelId]
        if (appliance == null) {
            loadInitialData()
            appliance = applianceIdMap[channelId]
        }
        if (appliance == null) return

        try {
            val verified = client.from("appliances")
                .update(mapOf("appliance_name" to trimmedName)) {
                    select()
                    filter {
                        eq("id", appliance.id)
                    }
                }.decodeSingleOrNull<ApplianceDto>()

            if (verified != null) {
                applianceIdMap[channelId] = verified
                _channels.value = _channels.value.map { ch ->
                    if (ch.channelId == channelId) ch.copy(applianceName = verified.applianceName) else ch
                }
                Log.d(TAG, "CONFIRMED: CH$channelId renamed to '${verified.applianceName}' in Supabase")
                addHistoryEvent(
                    eventType = "CONFIG",
                    description = "Channel $channelId renamed to '${verified.applianceName}'",
                    channelId = channelId
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Supabase UPDATE failed for rename CH$channelId: ${e.message}", e)
        }
    }

    override suspend fun setElectricityRate(newRate: Double) {
        if (newRate <= 0.0) return
        Log.d(TAG, "setElectricityRate: ₹$newRate/Wh")
        currentRate = newRate
        try {
            rateStore.saveRate(newRate)
        } catch (e: Exception) {
            Log.e(TAG, "Error persisting electricity rate: ${e.message}")
        }
        val updatedChannels = _channels.value.map { channel ->
            channel.copy(
                estimatedCost = Math.round(channel.energy * currentRate * 100.0) / 100.0
            )
        }
        _channels.value = updatedChannels
        updateHistoricalBillingAndAnalytics()
        updateAggregatedBillingAndEnergy(updatedChannels)
    }

    override suspend fun setMonthlyBillLimit(limit: Double) {
        if (limit <= 0.0) return
        Log.d(TAG, "setMonthlyBillLimit: ₹$limit")
        _monthlyBillLimit.value = limit
        try {
            rateStore.saveMonthlyLimit(limit)
        } catch (e: Exception) {
            Log.e(TAG, "Error persisting monthly bill limit: ${e.message}")
        }
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

        if (!SupabaseConfig.isConfigured) {
            Log.e(TAG, "ALL_CHANNELS_ACTION_FAILURE: Supabase credentials not configured.")
            throw IllegalStateException("Supabase not configured")
        }

        // Ensure all appliances are populated in cache before executing batch
        if (applianceIdMap.size < Constants.CHANNEL_COUNT) {
            loadInitialData()
        }

        val failedChannels = mutableListOf<Int>()

        // Update all 5 channels sequentially and log each result
        for (i in 1..Constants.CHANNEL_COUNT) {
            val success = updateChannelInternal(i, state, "ALL_DEVICES")
            if (success) {
                Log.i(TAG, "CH${i}_UPDATE_SUCCESS: Channel $i successfully set to relay_state=$state")
            } else {
                failedChannels.add(i)
                Log.e(TAG, "CH${i}_UPDATE_FAILURE: Channel $i failed to update to relay_state=$state")
            }
        }

        if (failedChannels.isEmpty()) {
            Log.i(TAG, "ALL_CHANNELS_ACTION_SUCCESS: All ${Constants.CHANNEL_COUNT} channels successfully updated to relay_state=$state")
        } else {
            Log.e(TAG, "ALL_CHANNELS_ACTION_FAILURE: Failed channels: $failedChannels")
            throw IllegalStateException("Failed to update channels: $failedChannels")
        }
    }

    override suspend fun setChannelIntensity(channelId: Int, intensity: Int) {
        Log.i(TAG, "[SEMHAS][INTENSITY] CH$channelId -> $intensity%")
        Log.i(TAG, "INTENSITY_ACTION_STARTED: channel=$channelId requestedIntensity=$intensity")

        if (!SupabaseConfig.isConfigured) {
            Log.w(TAG, "Supabase credentials not configured in local.properties. Cannot update CH$channelId intensity in database.")
            _channels.value = _channels.value.map { ch ->
                if (ch.channelId == channelId) ch.copy(intensity = intensity) else ch
            }
            Log.i(TAG, "INTENSITY_DB_UPDATE_SUCCESS: channel=$channelId intensity=$intensity")
            return
        }

        var appliance = applianceIdMap[channelId]
        if (appliance == null) {
            Log.w(TAG, "Appliance for CH$channelId not yet loaded in cache. Loading initial data from Supabase...")
            loadInitialData()
            appliance = applianceIdMap[channelId]
        }

        if (appliance == null) {
            Log.e(TAG, "INTENSITY_DB_UPDATE_FAILURE: Could not find appliance record for CH$channelId in public.appliances")
            return
        }

        val applianceUuid = appliance.id
        Log.d(TAG, "Executing Supabase UPDATE: table=public.appliances, column=intensity, value=$intensity, WHERE id='$applianceUuid' (channel_number=$channelId)")

        try {
            val updateResult = client.from("appliances")
                .update(mapOf("intensity" to intensity)) {
                    select()
                    filter {
                        eq("id", applianceUuid)
                    }
                }.decodeList<ApplianceDto>()

            val rowsAffected = updateResult.size
            if (rowsAffected == 0) {
                Log.e(TAG, "INTENSITY_DB_UPDATE_FAILURE: Supabase returned 0 updated rows for CH$channelId (UUID: $applianceUuid, requested intensity=$intensity)")
                return
            }

            val verified = updateResult.firstOrNull() ?: client.from("appliances")
                .select {
                    filter {
                        eq("id", applianceUuid)
                    }
                }.decodeSingleOrNull<ApplianceDto>()

            val storedIntensity = verified?.intensity
            if (verified != null && storedIntensity == intensity) {
                Log.i(TAG, "INTENSITY_DB_UPDATE_SUCCESS: channel=$channelId intensity=$intensity")
                applianceIdMap[channelId] = verified
                _channels.value = _channels.value.map { ch ->
                    if (ch.channelId == channelId) ch.copy(intensity = intensity) else ch
                }
                addHistoryEvent(
                    eventType = "CONTROL",
                    description = "${verified.applianceName} set to $intensity%",
                    channelId = channelId
                )
            } else {
                Log.e(TAG, "INTENSITY_DB_UPDATE_FAILURE: Verification mismatch for CH$channelId. Expected intensity=$intensity, but Supabase holds intensity=$storedIntensity")
            }
        } catch (e: Exception) {
            Log.e(TAG, "INTENSITY_DB_UPDATE_FAILURE: Exception updating CH$channelId intensity in Supabase: ${e.message}", e)
        }
    }

    override suspend fun refreshHistoricalAnalytics() {
        fetchDailyConsumptionFromRemote()
    }

    override suspend fun getDailyAnalytics(date: LocalDate): PeriodAnalyticsData {
        val dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val formattedLabel = date.format(DateTimeFormatter.ofPattern("EEEE, d MMM yyyy", Locale.ENGLISH))

        val dayRecords = dailyConsumptionMap.values.filter { it.consumptionDate == dateStr }
        val totalEnergyWh = dayRecords.sumOf { it.energyWh }

        // Build appliance breakdown for CH1..CH5 in Wh
        val breakdown = (1..Constants.CHANNEL_COUNT).map { chNum ->
            val app = applianceIdMap[chNum]
            val appName = app?.applianceName ?: "Channel $chNum"
            val appEnergyWh = if (app != null) {
                dayRecords.filter { it.applianceId == app.id }.sumOf { it.energyWh }
            } else 0.0
            val appCost = appEnergyWh * currentRate
            val pct = if (totalEnergyWh > 0.0) (appEnergyWh / totalEnergyWh) * 100.0 else 0.0
            ApplianceHistoricalUsage(
                channelNumber = chNum,
                applianceName = appName,
                energyWh = appEnergyWh,
                cost = appCost,
                percentage = pct
            )
        }

        val isToday = (date == LocalDate.now())
        // Fixed 24 hourly buckets: 00..23
        val trends = (0..23).map { h ->
            val hourLabel = String.format(Locale.US, "%02d", h)
            val hourWh = if (isToday) {
                val readingsInHour = todayEnergyReadings.filter { reading ->
                    val ts = reading.recordedAt ?: reading.createdAt
                    val instant = parseIsoToInstant(ts)
                    if (instant != null) {
                        val localTime = instant.atZone(ZoneId.systemDefault())
                        localTime.toLocalDate() == date && localTime.hour == h
                    } else false
                }
                readingsInHour.sumOf { it.energyDeltaWh }
            } else {
                0.0
            }
            hourLabel to hourWh
        }

        // If today has recorded totalEnergyWh from daily_consumption but individual hourly records in todayEnergyReadings
        // are not yet populated (e.g. initial load before telemetry), ensure current hour displays today's consumption
        val adjustedTrends = if (isToday && totalEnergyWh > 0.0 && trends.all { it.second == 0.0 }) {
            val curHour = java.time.LocalTime.now().hour
            trends.map { (label, value) ->
                if (label == String.format(Locale.US, "%02d", curHour)) label to totalEnergyWh else label to value
            }
        } else {
            trends
        }

        val hourlySum = adjustedTrends.sumOf { it.second }
        val effectiveTotalWh = if (isToday && hourlySum > totalEnergyWh) hourlySum else totalEnergyWh
        val totalCost = effectiveTotalWh * currentRate

        val highest = breakdown.maxByOrNull { it.energyWh }

        return PeriodAnalyticsData(
            periodName = "DAILY",
            periodLabel = formattedLabel,
            totalEnergyWh = effectiveTotalWh,
            totalCost = totalCost,
            trends = adjustedTrends,
            applianceBreakdown = breakdown,
            highestApplianceName = highest?.applianceName ?: "N/A",
            highestApplianceEnergy = highest?.energyWh ?: 0.0,
            hasData = effectiveTotalWh > 0.0
        )
    }

    override suspend fun getWeeklyAnalytics(weekStartDate: LocalDate): PeriodAnalyticsData {
        val monday = weekStartDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val sunday = monday.plusDays(6)
        val monStr = monday.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val sunStr = sunday.format(DateTimeFormatter.ISO_LOCAL_DATE)

        val label = "${monday.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))} - ${sunday.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH))}"

        val weekRecords = dailyConsumptionMap.values.filter {
            it.consumptionDate >= monStr && it.consumptionDate <= sunStr
        }

        // Build 7-day trend (Mon, Tue, Wed, Thu, Fri, Sat, Sun) in Wh
        val dayLabels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        val trends = (0..6).map { dayOffset ->
            val dayDate = monday.plusDays(dayOffset.toLong())
            val dayStr = dayDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
            val dayEWh = weekRecords.filter { it.consumptionDate == dayStr }.sumOf { it.energyWh }
            dayLabels[dayOffset] to dayEWh
        }

        // Weekly total must strictly equal sum(Mon + Tue + Wed + Thu + Fri + Sat + Sun)
        val totalEnergyWh = trends.sumOf { it.second }
        val totalCost = totalEnergyWh * currentRate

        // Build appliance breakdown for CH1..CH5 in Wh
        val breakdown = (1..Constants.CHANNEL_COUNT).map { chNum ->
            val app = applianceIdMap[chNum]
            val appName = app?.applianceName ?: "Channel $chNum"
            val appEnergyWh = if (app != null) {
                weekRecords.filter { it.applianceId == app.id }.sumOf { it.energyWh }
            } else 0.0
            val appCost = appEnergyWh * currentRate
            val pct = if (totalEnergyWh > 0.0) (appEnergyWh / totalEnergyWh) * 100.0 else 0.0
            ApplianceHistoricalUsage(
                channelNumber = chNum,
                applianceName = appName,
                energyWh = appEnergyWh,
                cost = appCost,
                percentage = pct
            )
        }

        val highest = breakdown.maxByOrNull { it.energyWh }

        return PeriodAnalyticsData(
            periodName = "WEEKLY",
            periodLabel = label,
            totalEnergyWh = totalEnergyWh,
            totalCost = totalCost,
            trends = trends,
            applianceBreakdown = breakdown,
            highestApplianceName = highest?.applianceName ?: "N/A",
            highestApplianceEnergy = highest?.energyWh ?: 0.0,
            hasData = totalEnergyWh > 0.0
        )
    }

    override suspend fun getMonthlyAnalytics(year: Int, month: Int): PeriodAnalyticsData {
        val monthPrefix = String.format(Locale.US, "%04d-%02d", year, month)
        val monthDate = LocalDate.of(year, month, 1)
        val label = monthDate.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH))

        val monthRecords = dailyConsumptionMap.values.filter {
            it.consumptionDate.startsWith(monthPrefix)
        }

        // Group into exactly 4 weekly buckets across the month in Wh
        // Week 1 = days 1–7
        // Week 2 = days 8–14
        // Week 3 = days 15–21
        // Week 4 = days 22–last day of selected month (NEVER Week 5)
        val week1 = monthRecords.filter {
            val day = it.consumptionDate.substringAfterLast("-").toIntOrNull() ?: 0
            day in 1..7
        }.sumOf { it.energyWh }

        val week2 = monthRecords.filter {
            val day = it.consumptionDate.substringAfterLast("-").toIntOrNull() ?: 0
            day in 8..14
        }.sumOf { it.energyWh }

        val week3 = monthRecords.filter {
            val day = it.consumptionDate.substringAfterLast("-").toIntOrNull() ?: 0
            day in 15..21
        }.sumOf { it.energyWh }

        val week4 = monthRecords.filter {
            val day = it.consumptionDate.substringAfterLast("-").toIntOrNull() ?: 0
            day >= 22
        }.sumOf { it.energyWh }

        val trends = listOf(
            "Week 1" to week1,
            "Week 2" to week2,
            "Week 3" to week3,
            "Week 4" to week4
        )

        // Monthly total equals sum of the 4 buckets
        val totalEnergyWh = trends.sumOf { it.second }
        val totalCost = totalEnergyWh * currentRate

        // Build appliance breakdown for CH1..CH5 in Wh
        val breakdown = (1..Constants.CHANNEL_COUNT).map { chNum ->
            val app = applianceIdMap[chNum]
            val appName = app?.applianceName ?: "Channel $chNum"
            val appEnergyWh = if (app != null) {
                monthRecords.filter { it.applianceId == app.id }.sumOf { it.energyWh }
            } else 0.0
            val appCost = appEnergyWh * currentRate
            val pct = if (totalEnergyWh > 0.0) (appEnergyWh / totalEnergyWh) * 100.0 else 0.0
            ApplianceHistoricalUsage(
                channelNumber = chNum,
                applianceName = appName,
                energyWh = appEnergyWh,
                cost = appCost,
                percentage = pct
            )
        }

        val highest = breakdown.maxByOrNull { it.energyWh }

        return PeriodAnalyticsData(
            periodName = "MONTHLY",
            periodLabel = label,
            totalEnergyWh = totalEnergyWh,
            totalCost = totalCost,
            trends = trends,
            applianceBreakdown = breakdown,
            highestApplianceName = highest?.applianceName ?: "N/A",
            highestApplianceEnergy = highest?.energyWh ?: 0.0,
            hasData = totalEnergyWh > 0.0
        )
    }

    override fun getAnalyticsTrends(period: String): List<Pair<String, Double>> {
        val today = LocalDate.now()
        return when (period.uppercase()) {
            "DAILY" -> {
                (0..23).map { h ->
                    val hourLabel = String.format(Locale.US, "%02d", h)
                    val hourWh = todayEnergyReadings.filter { reading ->
                        val ts = reading.recordedAt ?: reading.createdAt
                        val instant = parseIsoToInstant(ts)
                        if (instant != null) {
                            val localTime = instant.atZone(ZoneId.systemDefault())
                            localTime.toLocalDate() == today && localTime.hour == h
                        } else false
                    }.sumOf { it.energyDeltaWh }
                    hourLabel to hourWh
                }
            }
            "WEEKLY" -> {
                val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val dayLabels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                (0..6).map { dayOffset ->
                    val dayDate = monday.plusDays(dayOffset.toLong())
                    val dayStr = dayDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
                    val dayEWh = dailyConsumptionMap.values.filter { it.consumptionDate == dayStr }.sumOf { it.energyWh }
                    dayLabels[dayOffset] to dayEWh
                }
            }
            "MONTHLY" -> {
                val monthPrefix = String.format(Locale.US, "%04d-%02d", today.year, today.monthValue)
                val monthRecords = dailyConsumptionMap.values.filter { it.consumptionDate.startsWith(monthPrefix) }
                val w1 = monthRecords.filter { (it.consumptionDate.substringAfterLast("-").toIntOrNull() ?: 0) in 1..7 }.sumOf { it.energyWh }
                val w2 = monthRecords.filter { (it.consumptionDate.substringAfterLast("-").toIntOrNull() ?: 0) in 8..14 }.sumOf { it.energyWh }
                val w3 = monthRecords.filter { (it.consumptionDate.substringAfterLast("-").toIntOrNull() ?: 0) in 15..21 }.sumOf { it.energyWh }
                val w4 = monthRecords.filter { (it.consumptionDate.substringAfterLast("-").toIntOrNull() ?: 0) >= 22 }.sumOf { it.energyWh }
                listOf(
                    "Week 1" to w1,
                    "Week 2" to w2,
                    "Week 3" to w3,
                    "Week 4" to w4
                )
            }
            else -> emptyList()
        }
    }
}
