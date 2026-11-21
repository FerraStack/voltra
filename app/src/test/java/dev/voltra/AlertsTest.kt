package dev.voltra

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class AlertsTest {
    private lateinit var context: Application
    private lateinit var notificationManager: NotificationManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("battery", Context.MODE_PRIVATE).edit().clear().commit()
        notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        shadowOf(notificationManager).setNotificationsEnabled(true)
    }

    private fun sampleReading(
        level: Int = 50,
        plugged: Boolean = false,
        charging: Boolean = false,
        temperature: Float? = 30f,
    ) = Reading(
        level = level,
        charging = charging,
        full = false,
        plugged = plugged,
        screenOn = true,
        temperature = temperature,
        voltage = 4000,
        current = null,
        averageCurrent = null,
        energyNwh = null,
        counter = null,
        status = BatteryHealthStatus.GOOD,
        source = if (plugged) PowerSource.AC else PowerSource.BATTERY,
        chargeMinutes = null,
        cycles = null,
    )

    @Test
    fun chargeAlertFiresOnceWhilePluggedAndResetsOnUnplug() {
        ChargeAlert.setThreshold(context, 80)
        ChargeAlert.setEnabled(context, true)

        assertFalse(ChargeAlert.onReading(context, sampleReading(level = 79, plugged = true, charging = true)))
        assertTrue(ChargeAlert.onReading(context, sampleReading(level = 80, plugged = true, charging = true)))
        // Suppressed on subsequent ticks while still plugged
        assertFalse(ChargeAlert.onReading(context, sampleReading(level = 85, plugged = true, charging = true)))
        assertTrue(ChargeAlert.wasSent(context))

        // Unplug resets alert state
        assertFalse(ChargeAlert.onReading(context, sampleReading(level = 85, plugged = false)))
        assertFalse(ChargeAlert.wasSent(context))

        // Re-plugging above threshold triggers again
        assertTrue(ChargeAlert.onReading(context, sampleReading(level = 85, plugged = true, charging = true)))
    }

    @Test
    fun chargeAlertRespectsDisabledStateAndClampsThreshold() {
        ChargeAlert.setThreshold(context, 10)
        assertEquals(60, ChargeAlert.threshold(context))
        ChargeAlert.setThreshold(context, 120)
        assertEquals(100, ChargeAlert.threshold(context))

        ChargeAlert.setEnabled(context, false)
        assertFalse(ChargeAlert.onReading(context, sampleReading(level = 100, plugged = true, charging = true)))
    }

    @Test
    fun temperatureAlertUsesTwoDegreeResetHysteresis() {
        TemperatureAlert.setThreshold(context, 40)
        TemperatureAlert.setEnabled(context, true)

        assertFalse(TemperatureAlert.onReading(context, sampleReading(temperature = null)))
        assertFalse(TemperatureAlert.onReading(context, sampleReading(temperature = 39.9f)))
        assertTrue(TemperatureAlert.onReading(context, sampleReading(temperature = 40.0f)))
        // Duplicate suppressed
        assertFalse(TemperatureAlert.onReading(context, sampleReading(temperature = 41.0f)))
        // Cooling to 38.5 °C is still within hysteresis band (> 38 °C), so NOT reset yet
        assertFalse(TemperatureAlert.onReading(context, sampleReading(temperature = 38.5f)))
        assertTrue(TemperatureAlert.wasSent(context))
        assertFalse(TemperatureAlert.onReading(context, sampleReading(temperature = 40.5f)))
        // Cooling to <= 38.0 °C resets hysteresis
        assertFalse(TemperatureAlert.onReading(context, sampleReading(temperature = 38.0f)))
        assertFalse(TemperatureAlert.wasSent(context))
        // Reaching threshold again triggers again
        assertTrue(TemperatureAlert.onReading(context, sampleReading(temperature = 40.0f)))
    }

    @Test
    fun lowBatteryAlertUsesThreePercentHysteresisAndResetsOnPlugIn() {
        LowBatteryAlert.setThreshold(context, 20)
        LowBatteryAlert.setEnabled(context, true)

        // Unknown level (-1) ignored
        assertFalse(LowBatteryAlert.onReading(context, sampleReading(level = -1, plugged = false)))
        assertFalse(LowBatteryAlert.onReading(context, sampleReading(level = 21, plugged = false)))
        assertTrue(LowBatteryAlert.onReading(context, sampleReading(level = 20, plugged = false)))
        // Duplicate suppressed
        assertFalse(LowBatteryAlert.onReading(context, sampleReading(level = 19, plugged = false)))
        // Small bounce within +3% does not reset
        assertFalse(LowBatteryAlert.onReading(context, sampleReading(level = 23, plugged = false)))
        assertTrue(LowBatteryAlert.wasSent(context))
        // Exceeding threshold + 3% resets
        assertFalse(LowBatteryAlert.onReading(context, sampleReading(level = 24, plugged = false)))
        assertFalse(LowBatteryAlert.wasSent(context))
        // Triggers again when dropping back
        assertTrue(LowBatteryAlert.onReading(context, sampleReading(level = 18, plugged = false)))
        // Plugging in resets immediately
        assertFalse(LowBatteryAlert.onReading(context, sampleReading(level = 18, plugged = true, charging = true)))
        assertFalse(LowBatteryAlert.wasSent(context))
    }

    @Test
    fun alertsDoNotFireWhenNotificationsDisabled() {
        shadowOf(notificationManager).setNotificationsEnabled(false)
        ChargeAlert.setEnabled(context, true)
        TemperatureAlert.setEnabled(context, true)
        LowBatteryAlert.setEnabled(context, true)

        assertFalse(ChargeAlert.onReading(context, sampleReading(level = 95, plugged = true, charging = true)))
        assertFalse(TemperatureAlert.onReading(context, sampleReading(temperature = 45f)))
        assertFalse(LowBatteryAlert.onReading(context, sampleReading(level = 10, plugged = false)))
    }
}
