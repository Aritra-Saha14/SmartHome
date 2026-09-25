package com.semhas.app.data.model

import com.semhas.app.data.mock.MockSemhasRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApplianceIntensityTest {

    private fun createChannel(id: Int, name: String) = Channel(
        channelId = id,
        channelNumber = id,
        applianceName = name,
        relayState = false,
        voltage = 230.0,
        current = 0.5,
        power = 50.0,
        energy = 0.2,
        runtime = 600L,
        estimatedCost = 1.6,
        intensity = 100
    )

    @Test
    fun testChannelBasedIntensityCapability() {
        // CH1, CH2, CH3: Intensity/Speed = DISABLED
        assertFalse("CH1 must have intensity disabled", createChannel(1, "Appliance 1").isIntensitySupported)
        assertFalse("CH2 must have intensity disabled", createChannel(2, "Appliance 2").isIntensitySupported)
        assertFalse("CH3 must have intensity disabled", createChannel(3, "Appliance 3").isIntensitySupported)

        // CH4, CH5: Intensity/Speed = ENABLED
        assertTrue("CH4 must have intensity enabled", createChannel(4, "Appliance 4").isIntensitySupported)
        assertTrue("CH5 must have intensity enabled", createChannel(5, "Appliance 5").isIntensitySupported)
    }

    @Test
    fun testRenamingDoesNotAffectIntensityCapability() {
        // Renaming CH1, CH2, CH3 to "Fan" or "Light" must NEVER enable intensity
        val ch1RenamedFan = createChannel(1, "Bedroom Fan")
        assertFalse(ch1RenamedFan.isIntensitySupported)

        val ch2RenamedLight = createChannel(2, "Living Room Light")
        assertFalse(ch2RenamedLight.isIntensitySupported)

        val ch3RenamedFan = createChannel(3, "Ceiling Fan")
        assertFalse(ch3RenamedFan.isIntensitySupported)

        // Renaming CH4 or CH5 to anything (Fan, Light, TV, AC, Kitchen, etc.) must NEVER disable intensity
        val ch4Names = listOf("Fan", "Light", "TV", "AC", "Kitchen", "Custom Device", "Channel 4")
        for (name in ch4Names) {
            val ch4 = createChannel(4, name)
            assertTrue("CH4 renamed to '$name' must still have intensity enabled", ch4.isIntensitySupported)
        }

        val ch5Names = listOf("Fan", "Light", "TV", "AC", "Heater", "Custom Device", "Channel 5")
        for (name in ch5Names) {
            val ch5 = createChannel(5, name)
            assertTrue("CH5 renamed to '$name' must still have intensity enabled", ch5.isIntensitySupported)
        }
    }

    @Test
    fun testIntensityDisplayLabelContextual() {
        val ch4Fan = createChannel(4, "Exhaust Fan")
        assertEquals("Fan Speed", ch4Fan.intensityDisplayLabel)
        assertTrue(ch4Fan.isIntensitySupported)

        val ch4Light = createChannel(4, "Balcony Light")
        assertEquals("Light Intensity", ch4Light.intensityDisplayLabel)
        assertTrue(ch4Light.isIntensitySupported)

        val ch4Generic = createChannel(4, "Kitchen Device")
        assertEquals("Speed / Intensity", ch4Generic.intensityDisplayLabel)
        assertTrue(ch4Generic.isIntensitySupported)

        val ch5Generic = createChannel(5, "Air Conditioner")
        assertEquals("Speed / Intensity", ch5Generic.intensityDisplayLabel)
        assertTrue(ch5Generic.isIntensitySupported)
    }

    @Test
    fun testRepositorySetChannelIntensityOnCh4AndCh5() = runBlocking {
        val repository = MockSemhasRepository()

        // Test CH4
        repository.setChannelIntensity(4, 25)
        assertEquals(25, repository.channels.value.find { it.channelId == 4 }?.intensity)

        repository.setChannelIntensity(4, 50)
        assertEquals(50, repository.channels.value.find { it.channelId == 4 }?.intensity)

        repository.setChannelIntensity(4, 75)
        assertEquals(75, repository.channels.value.find { it.channelId == 4 }?.intensity)

        repository.setChannelIntensity(4, 100)
        assertEquals(100, repository.channels.value.find { it.channelId == 4 }?.intensity)

        // Test CH5
        repository.setChannelIntensity(5, 25)
        assertEquals(25, repository.channels.value.find { it.channelId == 5 }?.intensity)

        repository.setChannelIntensity(5, 50)
        assertEquals(50, repository.channels.value.find { it.channelId == 5 }?.intensity)

        repository.setChannelIntensity(5, 75)
        assertEquals(75, repository.channels.value.find { it.channelId == 5 }?.intensity)

        repository.setChannelIntensity(5, 100)
        assertEquals(100, repository.channels.value.find { it.channelId == 5 }?.intensity)

        // Verify CH1, CH2, CH3 remain unaffected at their initial intensity
        assertEquals(100, repository.channels.value.find { it.channelId == 1 }?.intensity)
        assertEquals(100, repository.channels.value.find { it.channelId == 2 }?.intensity)
        assertEquals(100, repository.channels.value.find { it.channelId == 3 }?.intensity)
    }
}

