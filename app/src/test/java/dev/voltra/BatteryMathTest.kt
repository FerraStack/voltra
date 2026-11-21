package dev.voltra

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BatteryMathTest {
    @Test
    fun currentCalibrationHandlesMissingAndOverflow() {
        assertEquals(900_000, BatteryMath.scaleCurrent(900, 1000))
        assertEquals(-500_000, BatteryMath.scaleCurrent(-500, 1000))
        assertNull(BatteryMath.scaleCurrent(null, 1))
        assertNull(BatteryMath.scaleCurrent(3_000_000, 1000))
        assertNull(BatteryMath.scaleCurrent(1, 3))
    }

    @Test
    fun dischargeRequiresEnoughData() {
        assertNull(BatteryMath.remaining(Sample(80, null, 0), Sample(79, null, 600_000)))
        assertNull(BatteryMath.remaining(Sample(80, null, 0), Sample(78, null, 300_000)))
        assertEquals(390, BatteryMath.remaining(Sample(80, null, 0), Sample(78, null, 600_000)))
    }

    @Test
    fun capacityNeedsChargeCounterAndTenPercent() {
        assertNull(BatteryMath.capacity(Sample(20, null, 0), Sample(30, 1_200_000, 600_000)))
        assertNull(BatteryMath.capacity(Sample(20, 1_000_000, 0), Sample(29, 1_360_000, 600_000)))
        assertNull(BatteryMath.capacity(Sample(20, 1_000_000, 0), Sample(30, 1_400_000, 60_000)))
        assertEquals(4000, BatteryMath.capacity(Sample(20, 1_000_000, 0), Sample(30, 1_400_000, 600_000)))
        assertEquals(80, BatteryMath.health(4000, 5000))
        assertNull(BatteryMath.health(4000, 0))
    }

    @Test
    fun temperatureUnitConversionsAreAccurate() {
        assertEquals(25.0f, TemperatureUnit.CELSIUS.convertFromCelsius(25.0f), 0.001f)
        assertEquals(77.0f, TemperatureUnit.FAHRENHEIT.convertFromCelsius(25.0f), 0.001f)
        assertEquals(104, TemperatureUnit.FAHRENHEIT.convertFromCelsiusInt(40))
        assertEquals(40, TemperatureUnit.CELSIUS.convertFromCelsiusInt(40))
    }
}
