package com.semhas.app.voice

import com.semhas.app.data.model.Channel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCommandParserTest {

    private val channels = listOf(
        createChannel(1, "Bedroom Fan"),
        createChannel(2, "Living Room Light"),
        createChannel(3, "TV"),
        createChannel(4, "Kitchen"),
        createChannel(5, "AC")
    )

    private fun createChannel(id: Int, name: String, state: Boolean = false) = Channel(
        channelId = id,
        channelNumber = id,
        applianceName = name,
        relayState = state,
        voltage = 12.0,
        current = 0.5,
        power = 6.0,
        energy = 0.1,
        runtime = 1200L,
        estimatedCost = 0.8
    )

    // ==========================================
    // 1. ALL DEVICES OFF / ON TESTS
    // ==========================================

    @Test
    fun testAllDevicesOffVariants() {
        val offCommands = listOf(
            "Turn everything off",
            "Turn all devices off",
            "Turn all appliances off",
            "Switch everything off",
            "Switch all devices off",
            "Switch off everything",
            "All off",
            "Turn off all",
            "Shut everything down",
            "Hey SEM, please turn everything off",
            "Can you switch off all devices"
        )

        for (cmd in offCommands) {
            val result = VoiceCommandParser.parse(cmd, channels)
            assertTrue("Expected AllDevices(false) for '$cmd' but got $result", result is ParsedVoiceResult.AllDevices)
            assertEquals("Expected targetState=false for '$cmd'", false, (result as ParsedVoiceResult.AllDevices).targetState)
        }
    }

    @Test
    fun testAllDevicesOnVariants() {
        val onCommands = listOf(
            "Turn everything on",
            "Turn all devices on",
            "Turn all appliances on",
            "Switch everything on",
            "Switch on everything",
            "All on",
            "Turn on all",
            "Hey SEM, turn everything on"
        )

        for (cmd in onCommands) {
            val result = VoiceCommandParser.parse(cmd, channels)
            assertTrue("Expected AllDevices(true) for '$cmd' but got $result", result is ParsedVoiceResult.AllDevices)
            assertEquals("Expected targetState=true for '$cmd'", true, (result as ParsedVoiceResult.AllDevices).targetState)
        }
    }

    // ==========================================
    // 2. INDIVIDUAL APPLIANCE CONTROL
    // ==========================================

    @Test
    fun testIndividualApplianceOnVariants() {
        val commands = listOf(
            "Turn on bedroom fan",
            "Turn the bedroom fan on",
            "Switch on bedroom fan",
            "Start bedroom fan",
            "Bedroom fan on",
            "Please turn on the bedroom fan"
        )

        for (cmd in commands) {
            val result = VoiceCommandParser.parse(cmd, channels)
            assertTrue("Expected SingleDevice(1, true) for '$cmd' but got $result", result is ParsedVoiceResult.SingleDevice)
            val switchResult = result as ParsedVoiceResult.SingleDevice
            assertEquals(1, switchResult.channelId)
            assertEquals(true, switchResult.targetState)
        }
    }

    @Test
    fun testIndividualApplianceOffVariants() {
        val commands = listOf(
            "Turn off bedroom fan",
            "Turn the bedroom fan off",
            "Switch off bedroom fan",
            "Stop bedroom fan",
            "Bedroom fan off",
            "Hey SEM, please switch off the bedroom fan"
        )

        for (cmd in commands) {
            val result = VoiceCommandParser.parse(cmd, channels)
            assertTrue("Expected SingleDevice(1, false) for '$cmd' but got $result", result is ParsedVoiceResult.SingleDevice)
            val switchResult = result as ParsedVoiceResult.SingleDevice
            assertEquals(1, switchResult.channelId)
            assertEquals(false, switchResult.targetState)
        }
    }

    // ==========================================
    // 3. POWER & LIVE MONITORING QUERIES
    // ==========================================

    @Test
    fun testCurrentPowerQueries() {
        val commands = listOf(
            "What is the current power?",
            "What's the current power?",
            "How much power am I using?",
            "Power right now?",
            "Current power usage?"
        )

        for (cmd in commands) {
            val result = VoiceCommandParser.parse(cmd, channels)
            assertTrue("Expected QueryCurrentPower for '$cmd' but got $result", result is ParsedVoiceResult.QueryCurrentPower)
        }
    }

    @Test
    fun testVoltageQueries() {
        val commands = listOf(
            "What is the current voltage?",
            "What's the voltage?",
            "Show me the voltage",
            "What voltage is the system running at?"
        )

        for (cmd in commands) {
            val result = VoiceCommandParser.parse(cmd, channels)
            assertTrue("Expected QueryVoltage for '$cmd' but got $result", result is ParsedVoiceResult.QueryVoltage)
        }
    }

    @Test
    fun testCurrentAmperageQueries() {
        val commands = listOf(
            "What is the current?",
            "How much current am I using?",
            "What's the amperage?"
        )

        for (cmd in commands) {
            val result = VoiceCommandParser.parse(cmd, channels)
            assertTrue("Expected QueryCurrent for '$cmd' but got $result", result is ParsedVoiceResult.QueryCurrent)
        }
    }

    @Test
    fun testPerApplianceTelemetryQueries() {
        val fanPower = VoiceCommandParser.parse("What is the power of the bedroom fan?", channels)
        assertTrue(fanPower is ParsedVoiceResult.QueryApplianceTelemetry)
        assertEquals(1, (fanPower as ParsedVoiceResult.QueryApplianceTelemetry).channelId)
        assertEquals(TelemetryMetric.POWER, fanPower.metric)

        val acVoltage = VoiceCommandParser.parse("What is the voltage of the AC?", channels)
        assertTrue(acVoltage is ParsedVoiceResult.QueryApplianceTelemetry)
        assertEquals(5, (acVoltage as ParsedVoiceResult.QueryApplianceTelemetry).channelId)
        assertEquals(TelemetryMetric.VOLTAGE, acVoltage.metric)

        val lightCurrent = VoiceCommandParser.parse("How much current is the living room light drawing?", channels)
        assertTrue(lightCurrent is ParsedVoiceResult.QueryApplianceTelemetry)
        assertEquals(2, (lightCurrent as ParsedVoiceResult.QueryApplianceTelemetry).channelId)
        assertEquals(TelemetryMetric.CURRENT, lightCurrent.metric)

        val fanRuntime = VoiceCommandParser.parse("How long has the fan been running?", channels)
        assertTrue(fanRuntime is ParsedVoiceResult.QueryApplianceTelemetry)
        assertEquals(1, (fanRuntime as ParsedVoiceResult.QueryApplianceTelemetry).channelId)
        assertEquals(TelemetryMetric.RUNTIME, fanRuntime.metric)
    }

    // ==========================================
    // 4. ENERGY QUERIES
    // ==========================================

    @Test
    fun testTodayEnergyQueries() {
        val commands = listOf(
            "How much energy did I use today?",
            "What's today's energy usage?",
            "How much energy has the house used?",
            "What's my current energy consumption?"
        )

        for (cmd in commands) {
            val result = VoiceCommandParser.parse(cmd, channels)
            assertTrue("Expected QueryTodayEnergy for '$cmd' but got $result", result is ParsedVoiceResult.QueryTodayEnergy)
        }
    }

    @Test
    fun testPerApplianceEnergyQueries() {
        val fanEnergy = VoiceCommandParser.parse("How much energy did the fan use?", channels)
        assertTrue(fanEnergy is ParsedVoiceResult.QueryApplianceEnergy)
        assertEquals(1, (fanEnergy as ParsedVoiceResult.QueryApplianceEnergy).channelId)

        val tvEnergy = VoiceCommandParser.parse("How much energy did the TV consume?", channels)
        assertTrue(tvEnergy is ParsedVoiceResult.QueryApplianceEnergy)
        assertEquals(3, (tvEnergy as ParsedVoiceResult.QueryApplianceEnergy).channelId)
    }

    // ==========================================
    // 5. BILLING & COST QUERIES
    // ==========================================

    @Test
    fun testBillingQueries() {
        val commands = listOf(
            "What is my current bill?",
            "What's my electricity bill?",
            "How much is my bill?",
            "What's my estimated bill?",
            "How much have I spent this month?"
        )

        for (cmd in commands) {
            val result = VoiceCommandParser.parse(cmd, channels)
            assertTrue("Expected QueryMonthlyCost for '$cmd' but got $result", result is ParsedVoiceResult.QueryMonthlyCost)
        }
    }

    @Test
    fun testElectricityRateQuery() {
        val commands = listOf(
            "What is the current electricity rate?",
            "What is my rate per unit?"
        )

        for (cmd in commands) {
            val result = VoiceCommandParser.parse(cmd, channels)
            assertTrue("Expected QueryElectricityRate for '$cmd' but got $result", result is ParsedVoiceResult.QueryElectricityRate)
        }
    }

    @Test
    fun testPerApplianceCostQueries() {
        val fanCost = VoiceCommandParser.parse("How much did the fan cost?", channels)
        assertTrue(fanCost is ParsedVoiceResult.QueryApplianceCost)
        assertEquals(1, (fanCost as ParsedVoiceResult.QueryApplianceCost).channelId)

        val acCost = VoiceCommandParser.parse("How much did the AC cost?", channels)
        assertTrue(acCost is ParsedVoiceResult.QueryApplianceCost)
        assertEquals(5, (acCost as ParsedVoiceResult.QueryApplianceCost).channelId)

        val breakdown = VoiceCommandParser.parse("How much did each appliance cost?", channels)
        assertTrue(breakdown is ParsedVoiceResult.QueryApplianceCostBreakdown)
    }

    // ==========================================
    // 6. HIGHEST CONSUMER QUERY
    // ==========================================

    @Test
    fun testHighestConsumerQueries() {
        val commands = listOf(
            "Which appliance uses the most power?",
            "What is using the most power?",
            "Which appliance consumes the most?",
            "What's the highest power appliance?",
            "Which device is the biggest consumer?"
        )

        for (cmd in commands) {
            val result = VoiceCommandParser.parse(cmd, channels)
            assertTrue("Expected QueryHighestPowerDevice for '$cmd' but got $result", result is ParsedVoiceResult.QueryHighestPowerDevice)
        }
    }

    // ==========================================
    // 7. SYSTEM HEALTH QUERIES
    // ==========================================

    @Test
    fun testSystemHealthQueries() {
        val commands = listOf(
            "Is SEMHAS online?",
            "Is the system healthy?",
            "Is the ESP32 online?",
            "Is the device connected?",
            "What's the system status?",
            "What is the Wi-Fi status?",
            "Is Supabase connected?"
        )

        for (cmd in commands) {
            val result = VoiceCommandParser.parse(cmd, channels)
            assertTrue("Expected QuerySystemHealth for '$cmd' but got $result", result is ParsedVoiceResult.QuerySystemHealth)
        }
    }

    // ==========================================
    // 8. NAVIGATION QUERIES
    // ==========================================

    @Test
    fun testNavigationQueries() {
        val navTests = listOf(
            "Open Home" to "home",
            "Open Monitor" to "monitor",
            "Open Control" to "control",
            "Open Billing" to "billing",
            "Open Analytics" to "analytics",
            "Open History" to "history",
            "Open Notifications" to "notifications",
            "Open System Health" to "health",
            "Open Settings" to "settings",
            "Open Voice Control" to "voice"
        )

        for ((cmd, expectedRoute) in navTests) {
            val result = VoiceCommandParser.parse(cmd, channels)
            assertTrue("Expected NavigateTo for '$cmd' but got $result", result is ParsedVoiceResult.NavigateTo)
            assertEquals(expectedRoute, (result as ParsedVoiceResult.NavigateTo).route)
        }
    }

    // ==========================================
    // 9. HELP QUERIES
    // ==========================================

    @Test
    fun testHelpQueries() {
        val commands = listOf(
            "What can you do?",
            "What commands can I use?",
            "Help",
            "What can SEMHAS do?"
        )

        for (cmd in commands) {
            val result = VoiceCommandParser.parse(cmd, channels)
            assertTrue("Expected QueryHelp for '$cmd' but got $result", result is ParsedVoiceResult.QueryHelp)
        }
    }

    // ==========================================
    // 10. UNKNOWN / AMBIGUOUS APPLIANCE
    // ==========================================

    @Test
    fun testUnknownAppliance() {
        val result = VoiceCommandParser.parse("Turn on washing machine", channels)
        assertTrue(result is ParsedVoiceResult.Unsupported)
        assertEquals("I couldn't find that appliance.", (result as ParsedVoiceResult.Unsupported).spokenResponse)
    }

    // ==========================================
    // 11. WAKE PHRASE DETECTION ("Hey SEM", case-insensitive)
    // ==========================================

    @Test
    fun testWakePhraseDetectionCaseInsensitive() {
        val validWakePhrases = listOf(
            "Hey SEM",
            "hey sem",
            "HEY SEM",
            "Hey Sem",
            "hey   sem",
            "Hey SEM, turn on the light",
            "hey sem please switch off all devices",
            "HEY SEM status"
        )

        for (phrase in validWakePhrases) {
            assertTrue("Expected wake phrase detected for '$phrase'", VoiceCommandParser.hasWakePhrase(phrase))
        }

        val invalidWakePhrases = listOf(
            "Hey Jarvis",
            "hey google",
            "alexa turn on light",
            "hello",
            "turn on fan"
        )

        for (phrase in invalidWakePhrases) {
            assertFalse("Expected wake phrase NOT detected for '$phrase'", VoiceCommandParser.hasWakePhrase(phrase))
        }
    }

    @Test
    fun testParseWithRequireWakePhrase() {
        // When requireWakePhrase is true, valid wake phrases should parse
        val validCmd = "Hey SEM, turn everything off"
        val validResult = VoiceCommandParser.parse(validCmd, channels, requireWakePhrase = true)
        assertTrue("Expected AllDevices when wake word present", validResult is ParsedVoiceResult.AllDevices)

        // When requireWakePhrase is true, commands without "Hey SEM" must be ignored
        val invalidCmd = "Turn everything off"
        val ignoredResult = VoiceCommandParser.parse(invalidCmd, channels, requireWakePhrase = true)
        assertTrue("Expected Ignored when wake word absent", ignoredResult is ParsedVoiceResult.Ignored)

        // Old wake word must be ignored when requireWakePhrase is true
        val oldWakeWordCmd = "Hey Jarvis, turn everything off"
        val oldResult = VoiceCommandParser.parse(oldWakeWordCmd, channels, requireWakePhrase = true)
        assertTrue("Expected Ignored for old wake word", oldResult is ParsedVoiceResult.Ignored)
    }
}
