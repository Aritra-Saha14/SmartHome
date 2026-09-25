package com.semhas.app.voice

import com.semhas.app.data.model.Channel
import java.util.Locale

enum class TelemetryMetric {
    POWER,
    VOLTAGE,
    CURRENT,
    RUNTIME
}

sealed class ParsedVoiceResult {
    data class AllDevices(
        val targetState: Boolean,
        val rawTranscript: String
    ) : ParsedVoiceResult()

    data class SingleDevice(
        val channelId: Int,
        val channelName: String,
        val targetState: Boolean,
        val rawTranscript: String,
        val feedbackMessage: String,
        val spokenResponse: String
    ) : ParsedVoiceResult()

    data class AmbiguousAppliance(
        val candidateNames: List<String>,
        val rawTranscript: String
    ) : ParsedVoiceResult()

    data class QueryCurrentPower(
        val rawTranscript: String
    ) : ParsedVoiceResult()

    data class QueryVoltage(
        val rawTranscript: String
    ) : ParsedVoiceResult()

    data class QueryCurrent(
        val rawTranscript: String
    ) : ParsedVoiceResult()

    data class QueryApplianceTelemetry(
        val channelId: Int,
        val channelName: String,
        val metric: TelemetryMetric,
        val rawTranscript: String
    ) : ParsedVoiceResult()

    data class QueryTodayEnergy(
        val rawTranscript: String
    ) : ParsedVoiceResult()

    data class QueryApplianceEnergy(
        val channelId: Int,
        val channelName: String,
        val rawTranscript: String
    ) : ParsedVoiceResult()

    data class QueryMonthlyCost(
        val rawTranscript: String
    ) : ParsedVoiceResult()

    data class QueryTodayCost(
        val rawTranscript: String
    ) : ParsedVoiceResult()

    data class QueryElectricityRate(
        val rawTranscript: String
    ) : ParsedVoiceResult()

    data class QueryApplianceCost(
        val channelId: Int,
        val channelName: String,
        val rawTranscript: String
    ) : ParsedVoiceResult()

    data class QueryApplianceCostBreakdown(
        val rawTranscript: String
    ) : ParsedVoiceResult()

    data class QueryHighestPowerDevice(
        val rawTranscript: String
    ) : ParsedVoiceResult()

    data class QueryDeviceState(
        val channelId: Int,
        val channelName: String,
        val rawTranscript: String
    ) : ParsedVoiceResult()

    data class QuerySystemHealth(
        val rawTranscript: String
    ) : ParsedVoiceResult()

    data class NavigateTo(
        val route: String,
        val destinationName: String,
        val rawTranscript: String
    ) : ParsedVoiceResult()

    data class QueryHelp(
        val rawTranscript: String
    ) : ParsedVoiceResult()

    data class WakeWordOnly(
        val rawTranscript: String
    ) : ParsedVoiceResult()

    data class Unsupported(
        val reason: String,
        val spokenResponse: String,
        val rawTranscript: String
    ) : ParsedVoiceResult()

    data class Ignored(
        val rawTranscript: String
    ) : ParsedVoiceResult()
}

object VoiceCommandParser {

    const val DEFAULT_UNSUPPORTED_RESPONSE = "Sorry, I couldn't understand that SEMHAS command."
    const val AMBIGUOUS_RESPONSE = "I found more than one possible appliance. Which one do you mean?"

    /**
     * Formats appliance name appropriately for spoken output.
     * Preserves acronyms like TV and AC while lowercasing natural names.
     */
    fun formatApplianceNameForSpeech(name: String): String {
        val trimmed = name.trim()
        return when {
            trimmed.equals("TV", ignoreCase = true) -> "TV"
            trimmed.equals("AC", ignoreCase = true) -> "AC"
            else -> trimmed.lowercase()
        }
    }

    fun hasWakePhrase(transcript: String): Boolean {
        val clean = normalize(transcript)
        val regexSem = Regex("""\bhey\s+sem\b""", RegexOption.IGNORE_CASE)
        return regexSem.containsMatchIn(clean)
    }

    /**
     * Comprehensive deterministic parsing covering:
     * - Appliance Control (Individual & All Devices)
     * - Power, Voltage, Current Monitoring
     * - Per-appliance Telemetry (Power, Voltage, Current, Runtime)
     * - Energy & Cost Queries (Today, Monthly, Rates, Per-Appliance)
     * - Highest Consumer Query
     * - System Health Query
     * - In-App Navigation Routes
     * - Help / Capabilities Query
     * - Multi-appliance Ambiguity Detection
     */
    fun parse(
        transcript: String,
        channels: List<Channel> = emptyList(),
        requireWakePhrase: Boolean = false
    ): ParsedVoiceResult {
        val rawClean = transcript.trim()
        val normalized = normalize(rawClean)

        if (requireWakePhrase && !hasWakePhrase(normalized)) {
            return ParsedVoiceResult.Ignored(rawClean)
        }

        val commandPart = cleanUtterance(normalized)

        if (commandPart.isBlank()) {
            return ParsedVoiceResult.WakeWordOnly(rawClean)
        }

        // ==========================================
        // 1. HELP / CAPABILITIES
        // ==========================================
        if (isHelpQuery(commandPart)) {
            return ParsedVoiceResult.QueryHelp(rawClean)
        }

        // ==========================================
        // 2. IN-APP NAVIGATION COMMANDS
        // ==========================================
        val navMatch = matchNavigation(commandPart)
        if (navMatch != null) {
            return ParsedVoiceResult.NavigateTo(
                route = navMatch.first,
                destinationName = navMatch.second,
                rawTranscript = rawClean
            )
        }

        // ==========================================
        // 3. ALL-DEVICE CONTROL COMMANDS ("Turn everything off", "All on", etc.)
        // ==========================================
        val isAllDeviceCommand = isAllDevicesIntent(commandPart)
        val targetAllState = determineState(commandPart)
        if (isAllDeviceCommand && targetAllState != null) {
            return ParsedVoiceResult.AllDevices(
                targetState = targetAllState,
                rawTranscript = rawClean
            )
        }

        // ==========================================
        // 4. SINGLE APPLIANCE ON/OFF CONTROL
        // ==========================================
        val singleState = determineState(commandPart)
        val isStateQuery = isDeviceStateQuery(commandPart)
        val applianceMatchResult = matchAppliance(commandPart, channels)

        if (applianceMatchResult is ApplianceMatch.Ambiguous && singleState != null) {
            return ParsedVoiceResult.AmbiguousAppliance(
                candidateNames = applianceMatchResult.candidateNames,
                rawTranscript = rawClean
            )
        }

        val channelMatch = (applianceMatchResult as? ApplianceMatch.Single)?.channel

        if (singleState != null && channelMatch != null && !isStateQuery) {
            val stateLabel = if (singleState) "ON" else "OFF"
            val stateSpoken = if (singleState) "on" else "off"
            val applianceSpoken = formatApplianceNameForSpeech(channelMatch.applianceName)
            val spokenResponse = "Your $applianceSpoken is now $stateSpoken."

            return ParsedVoiceResult.SingleDevice(
                channelId = channelMatch.channelId,
                channelName = channelMatch.applianceName,
                targetState = singleState,
                rawTranscript = rawClean,
                feedbackMessage = "${channelMatch.applianceName} turned $stateLabel",
                spokenResponse = spokenResponse
            )
        }

        // ==========================================
        // 5. PER-APPLIANCE TELEMETRY & ENERGY/COST QUERIES
        // ==========================================
        if (channelMatch != null) {
            // A. Per-appliance Power ("How much power is the TV using?", "What is the power of the fan?")
            if (commandPart.contains("power") || commandPart.contains("watt")) {
                return ParsedVoiceResult.QueryApplianceTelemetry(
                    channelId = channelMatch.channelId,
                    channelName = channelMatch.applianceName,
                    metric = TelemetryMetric.POWER,
                    rawTranscript = rawClean
                )
            }
            // B. Per-appliance Voltage ("What is the voltage of the AC?")
            if (commandPart.contains("voltage") || commandPart.contains("volts")) {
                return ParsedVoiceResult.QueryApplianceTelemetry(
                    channelId = channelMatch.channelId,
                    channelName = channelMatch.applianceName,
                    metric = TelemetryMetric.VOLTAGE,
                    rawTranscript = rawClean
                )
            }
            // C. Per-appliance Current / Amperage ("How much current is the light drawing?")
            if (commandPart.contains("current") || commandPart.contains("amperage") || commandPart.contains("amps") || commandPart.contains("drawing")) {
                return ParsedVoiceResult.QueryApplianceTelemetry(
                    channelId = channelMatch.channelId,
                    channelName = channelMatch.applianceName,
                    metric = TelemetryMetric.CURRENT,
                    rawTranscript = rawClean
                )
            }
            // D. Per-appliance Runtime ("How long has the fan been running?")
            if (commandPart.contains("how long") || commandPart.contains("running") || commandPart.contains("runtime") || commandPart.contains("operating time")) {
                return ParsedVoiceResult.QueryApplianceTelemetry(
                    channelId = channelMatch.channelId,
                    channelName = channelMatch.applianceName,
                    metric = TelemetryMetric.RUNTIME,
                    rawTranscript = rawClean
                )
            }
            // E. Per-appliance Energy ("How much energy did the fan use?")
            if (commandPart.contains("energy") || commandPart.contains("kwh") || commandPart.contains("kilowatt")) {
                return ParsedVoiceResult.QueryApplianceEnergy(
                    channelId = channelMatch.channelId,
                    channelName = channelMatch.applianceName,
                    rawTranscript = rawClean
                )
            }
            // F. Per-appliance Cost ("How much did the fan cost?")
            if (commandPart.contains("cost") || commandPart.contains("bill") || commandPart.contains("spend") || commandPart.contains("rupees")) {
                return ParsedVoiceResult.QueryApplianceCost(
                    channelId = channelMatch.channelId,
                    channelName = channelMatch.applianceName,
                    rawTranscript = rawClean
                )
            }
            // G. Device State Query ("is the bedroom fan on?")
            if (isStateQuery) {
                return ParsedVoiceResult.QueryDeviceState(
                    channelId = channelMatch.channelId,
                    channelName = channelMatch.applianceName,
                    rawTranscript = rawClean
                )
            }
        }

        // ==========================================
        // 6. GLOBAL TELEMETRY QUERIES
        // ==========================================

        // A. Highest Consumer ("Which appliance uses the most power?")
        if (isHighestPowerQuery(commandPart)) {
            return ParsedVoiceResult.QueryHighestPowerDevice(rawClean)
        }

        // B. Electricity Rate ("What is the current electricity rate?", "What is my rate per unit?")
        if (isElectricityRateQuery(commandPart)) {
            return ParsedVoiceResult.QueryElectricityRate(rawClean)
        }

        // C. Appliance Cost Breakdown ("How much did each appliance cost?")
        if (isCostBreakdownQuery(commandPart)) {
            return ParsedVoiceResult.QueryApplianceCostBreakdown(rawClean)
        }

        // D. Monthly Bill / Cost ("What is my current bill?", "How much have I spent this month?")
        if (isMonthlyCostQuery(commandPart)) {
            return ParsedVoiceResult.QueryMonthlyCost(rawClean)
        }

        // E. Today's Cost ("How much have I spent today?", "What is today's bill?")
        if (isTodayCostQuery(commandPart)) {
            return ParsedVoiceResult.QueryTodayCost(rawClean)
        }

        // F. Today's / Total Energy ("How much energy did I use today?", "How much energy has the house used?")
        if (isTodayEnergyQuery(commandPart)) {
            return ParsedVoiceResult.QueryTodayEnergy(rawClean)
        }

        // G. Total Power ("What is the current power?", "Power right now")
        if (isCurrentPowerQuery(commandPart)) {
            return ParsedVoiceResult.QueryCurrentPower(rawClean)
        }

        // H. System Voltage ("What is the current voltage?", "What voltage is the system running at?")
        if (isVoltageQuery(commandPart)) {
            return ParsedVoiceResult.QueryVoltage(rawClean)
        }

        // I. Total Current / Amperage ("What is the current?", "How much current am I using?")
        if (isCurrentDrawQuery(commandPart)) {
            return ParsedVoiceResult.QueryCurrent(rawClean)
        }

        // J. System Health ("Is SEMHAS online?", "Is the system healthy?", "Is ESP32 online?")
        if (isSystemHealthQuery(commandPart)) {
            return ParsedVoiceResult.QuerySystemHealth(rawClean)
        }

        // If an appliance was identified but state was missing or ambiguous
        if (channelMatch != null && singleState == null && !isStateQuery) {
            return ParsedVoiceResult.Unsupported(
                reason = "Please specify whether to turn ${channelMatch.applianceName} ON or OFF",
                spokenResponse = "Please specify whether to turn your ${formatApplianceNameForSpeech(channelMatch.applianceName)} on or off.",
                rawTranscript = rawClean
            )
        }

        // If ON/OFF was specified but appliance was not identified
        if (singleState != null && channelMatch == null && !isAllDeviceCommand) {
            return ParsedVoiceResult.Unsupported(
                reason = "Unknown appliance specified",
                spokenResponse = "I couldn't find that appliance.",
                rawTranscript = rawClean
            )
        }

        // Unsupported / Out-of-scope
        return ParsedVoiceResult.Unsupported(
            reason = "Unsupported query",
            spokenResponse = DEFAULT_UNSUPPORTED_RESPONSE,
            rawTranscript = rawClean
        )
    }

    private fun cleanUtterance(text: String): String {
        return text
            .replace(Regex("""^\s*(hey|ok)\s+sem\b[,.\s]*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""^[,.\-?!;:]+"""), "")
            .replace(Regex("""^(please|can you|could you|would you|will you|kindly|i want you to|i want to|tell me|check|show me)\s+""", RegexOption.IGNORE_CASE), "")
            .trim()
    }

    private fun determineState(text: String): Boolean? {
        val t = " $text "

        val isOn = t.contains(" turn on ") ||
                t.contains(" switch on ") ||
                t.contains(" power on ") ||
                t.contains(" start ") ||
                t.contains(" activate ") ||
                t.contains(" enable ") ||
                t.contains(" turn the ") && t.contains(" on ") ||
                t.contains(" switch the ") && t.contains(" on ") ||
                t.contains(" power the ") && t.contains(" on ") ||
                Regex("""\bturn\s+(?:the\s+|my\s+|our\s+)?(.+?)\s+on\b""").containsMatchIn(t) ||
                Regex("""\bswitch\s+(?:the\s+|my\s+|our\s+)?(.+?)\s+on\b""").containsMatchIn(t) ||
                Regex("""\bpower\s+(?:the\s+|my\s+|our\s+)?(.+?)\s+on\b""").containsMatchIn(t) ||
                t.endsWith(" on ") ||
                t.startsWith(" on ")

        val isOff = t.contains(" turn off ") ||
                t.contains(" switch off ") ||
                t.contains(" power off ") ||
                t.contains(" stop ") ||
                t.contains(" shut off ") ||
                t.contains(" shut down ") ||
                t.contains(" shut ") && t.contains(" down ") ||
                t.contains(" deactivate ") ||
                t.contains(" disable ") ||
                t.contains(" cut off ") ||
                t.contains(" turn the ") && t.contains(" off ") ||
                t.contains(" switch the ") && t.contains(" off ") ||
                t.contains(" power the ") && t.contains(" off ") ||
                Regex("""\bturn\s+(?:the\s+|my\s+|our\s+)?(.+?)\s+off\b""").containsMatchIn(t) ||
                Regex("""\bswitch\s+(?:the\s+|my\s+|our\s+)?(.+?)\s+off\b""").containsMatchIn(t) ||
                Regex("""\bpower\s+(?:the\s+|my\s+|our\s+)?(.+?)\s+off\b""").containsMatchIn(t) ||
                t.endsWith(" off ") ||
                t.startsWith(" off ")

        return when {
            isOn && !isOff -> true
            isOff && !isOn -> false
            else -> null
        }
    }

    private fun isAllDevicesIntent(text: String): Boolean {
        val t = " $text "
        return t.contains(" everything ") ||
                t.contains(" all devices ") ||
                t.contains(" all appliances ") ||
                t.contains(" every device ") ||
                t.contains(" every appliance ") ||
                t.contains(" all lights ") ||
                t.contains(" all off ") ||
                t.contains(" all on ") ||
                t.contains(" turn off all ") ||
                t.contains(" turn on all ") ||
                t.contains(" shut everything down ") ||
                t.contains(" switch off everything ") ||
                t.contains(" switch on everything ") ||
                text.equals("all off", ignoreCase = true) ||
                text.equals("all on", ignoreCase = true) ||
                text.equals("turn all off", ignoreCase = true) ||
                text.equals("turn all on", ignoreCase = true)
    }

    private sealed class ApplianceMatch {
        data class Single(val channel: Channel) : ApplianceMatch()
        data class Ambiguous(val candidateNames: List<String>) : ApplianceMatch()
        object None : ApplianceMatch()
    }

    private fun matchAppliance(text: String, channels: List<Channel>): ApplianceMatch {
        val cleanText = " $text "
        val effectiveChannels = if (channels.isNotEmpty()) channels else listOf(
            Channel(1, 1, "Living Room Light", false, 0.0, 0.0, 0.0, 0.0, 0, 0.0),
            Channel(2, 2, "Bedroom Fan", false, 0.0, 0.0, 0.0, 0.0, 0, 0.0),
            Channel(3, 3, "TV", false, 0.0, 0.0, 0.0, 0.0, 0, 0.0),
            Channel(4, 4, "Kitchen", false, 0.0, 0.0, 0.0, 0.0, 0, 0.0),
            Channel(5, 5, "AC", false, 0.0, 0.0, 0.0, 0.0, 0, 0.0)
        )

        // 1. Exact Name Matching (longest first) with word boundaries
        val sortedChannels = effectiveChannels.sortedByDescending { it.applianceName.length }
        for (channel in sortedChannels) {
            val normName = normalize(channel.applianceName)
            if (normName.isNotBlank() && cleanText.contains(" $normName ")) {
                return ApplianceMatch.Single(channel)
            }
        }

        // 2. Safe Alias Mapping with word boundaries
        val matchedChannels = mutableSetOf<Channel>()
        for (channel in sortedChannels) {
            val aliases = getAliasesForChannel(channel)
            for (alias in aliases) {
                val normAlias = normalize(alias)
                if (normAlias.isNotBlank() && cleanText.contains(" $normAlias ")) {
                    matchedChannels.add(channel)
                    break
                }
            }
        }

        return when {
            matchedChannels.size == 1 -> ApplianceMatch.Single(matchedChannels.first())
            matchedChannels.size > 1 -> ApplianceMatch.Ambiguous(matchedChannels.map { it.applianceName })
            else -> ApplianceMatch.None
        }
    }

    private fun getAliasesForChannel(channel: Channel): List<String> {
        val aliases = mutableListOf<String>()
        val norm = normalize(channel.applianceName)
        if (norm.isNotBlank()) {
            aliases.add(norm)
        }

        if (norm.contains("fan")) {
            aliases.add("fan")
            aliases.add("fans")
        }
        if (norm.contains("light")) {
            aliases.add("light")
            aliases.add("lights")
        }
        if (norm.contains("tv") || norm.contains("television")) {
            aliases.addAll(listOf("tv", "television", "tele"))
        }
        if (norm.contains("ac") || norm.contains("air conditioner") || norm.contains("cooler")) {
            aliases.addAll(listOf("ac", "air conditioner", "air conditioning", "cooler"))
        }
        if (norm.contains("kitchen")) {
            aliases.add("kitchen")
        }

        // Add multi-letter words (>= 3 chars) from name excluding common stop words
        norm.split(" ").forEach { word ->
            if (word.length >= 3 && !aliases.contains(word) && word !in listOf("the", "room", "and", "our", "my")) {
                aliases.add(word)
            }
        }
        return aliases.distinct()
    }

    private fun matchNavigation(text: String): Pair<String, String>? {
        val t = " $text "
        if (!t.contains(" open ") && !t.contains(" go to ") && !t.contains(" navigate to ") && !t.contains(" show me ")) {
            return null
        }

        return when {
            t.contains(" home ") -> Pair("home", "Home")
            t.contains(" voice ") -> Pair("voice", "Voice Control")
            t.contains(" monitor ") || t.contains(" monitoring ") -> Pair("monitor", "Monitor")
            t.contains(" control ") -> Pair("control", "Control")
            t.contains(" billing ") || t.contains(" bill ") -> Pair("billing", "Billing")
            t.contains(" analytics ") || t.contains(" trends ") -> Pair("analytics", "Analytics")
            t.contains(" history ") || t.contains(" logs ") -> Pair("history", "History")
            t.contains(" notifications ") || t.contains(" alerts ") -> Pair("notifications", "Notifications")
            t.contains(" system health ") || t.contains(" health ") -> Pair("health", "System Health")
            t.contains(" settings ") -> Pair("settings", "Settings")
            else -> null
        }
    }

    private fun isHelpQuery(text: String): Boolean {
        return text == "help" ||
                text.contains("what can you do") ||
                text.contains("what commands can i use") ||
                text.contains("what can semhas do") ||
                text.contains("how do i use") ||
                text.contains("command list") ||
                text.contains("what can i say")
    }

    private fun isVoltageQuery(text: String): Boolean {
        return (text.contains("voltage") || text.contains("volts")) && !text.contains("fan") && !text.contains("tv") && !text.contains("ac") && !text.contains("light") && !text.contains("kitchen")
    }

    private fun isCurrentDrawQuery(text: String): Boolean {
        return (text.contains("what is the current") || text.contains("how much current") || text.contains("amperage") || text.contains("current draw") || text.contains("total current") || text == "current" || text == "what is current") &&
                !text.contains("power") && !text.contains("rate") && !text.contains("fan") && !text.contains("tv") && !text.contains("ac")
    }

    private fun isElectricityRateQuery(text: String): Boolean {
        return text.contains("rate") || text.contains("per unit") || text.contains("per kwh") || text.contains("unit rate")
    }

    private fun isCostBreakdownQuery(text: String): Boolean {
        return text.contains("each appliance") ||
                text.contains("cost breakdown") ||
                text.contains("appliance cost breakdown") ||
                text.contains("how much each appliance cost")
    }

    private fun isDeviceStateQuery(text: String): Boolean {
        return text.startsWith("is ") ||
                text.startsWith("are ") ||
                text.contains(" status") ||
                text.startsWith("status ") ||
                text.contains("currently on") ||
                text.contains("currently off") ||
                text.contains("is on") ||
                text.contains("is off")
    }

    private fun isHighestPowerQuery(text: String): Boolean {
        return text.contains("most power") ||
                text.contains("highest power") ||
                text.contains("maximum power") ||
                text.contains("max power") ||
                text.contains("uses the most") ||
                text.contains("using the most") ||
                text.contains("consumes the most") ||
                text.contains("consuming the most") ||
                text.contains("biggest consumer") ||
                text.contains("highest consumption")
    }

    private fun isCurrentPowerQuery(text: String): Boolean {
        return text.contains("current power") ||
                text.contains("power right now") ||
                text.contains("power usage") ||
                text.contains("power am i using") ||
                text.contains("power now") ||
                text.contains("how many watts") ||
                text.contains("current watts") ||
                text.contains("total power") ||
                text.contains("what is the power") ||
                text.contains("what is current power") ||
                text.contains("power consumption")
    }

    private fun isMonthlyCostQuery(text: String): Boolean {
        return (text.contains("bill") || text.contains("month") || text.contains("monthly") || text.contains("how much have i spent") || text.contains("how much did i spend") || text.contains("estimated bill")) &&
                !text.contains("today") && !text.contains("each appliance") && !text.contains("breakdown") && !text.contains("rate")
    }

    private fun isTodayEnergyQuery(text: String): Boolean {
        return (text.contains("energy") || text.contains("kilowatt") || text.contains("kwh") || text.contains("energy consumption")) &&
                !text.contains("cost") && !text.contains("bill") && !text.contains("rate") && !text.contains("power")
    }

    private fun isTodayCostQuery(text: String): Boolean {
        return text.contains("today") && (text.contains("bill") || text.contains("cost") || text.contains("spent") || text.contains("rupees"))
    }

    private fun isSystemHealthQuery(text: String): Boolean {
        val t = " $text "
        return t.contains(" health") ||
                t.contains(" healthy") ||
                t.contains(" status") ||
                (t.contains(" online") && (t.contains("esp") || t.contains("semhas") || t.contains("system") || t.contains("device"))) ||
                (t.contains(" connected") && (t.contains("device") || t.contains("system") || t.contains("supabase") || t.contains("controller") || t.contains("esp"))) ||
                t.contains("wi fi") ||
                t.contains("wifi")
    }

    private fun normalize(input: String): String {
        return input.lowercase(Locale.US)
            .replace(Regex("""[^\w\s]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }
}
