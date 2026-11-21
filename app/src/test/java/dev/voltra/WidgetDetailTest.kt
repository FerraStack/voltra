package dev.voltra

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class WidgetDetailTest {
    @Test
    fun detailJoinsOnlyPresentSegments() {
        assertEquals(
            "Discharging · 21:04",
            BatteryWidget.detail(state = "Discharging", timeLabel = "21:04"),
        )
        assertEquals(
            "Charging · 21:04 · Temp 36.5 °C · Health 80%",
            BatteryWidget.detail(
                state = "Charging",
                timeLabel = "21:04",
                temperatureLabel = "Temp 36.5 °C",
                healthLabel = "Health 80%",
            ),
        )
        assertEquals(
            "Plugged in · 09:15 · Temp 30.0 °C",
            BatteryWidget.detail(
                state = "Plugged in",
                timeLabel = "09:15",
                temperatureLabel = "Temp 30.0 °C",
                healthLabel = null,
            ),
        )
        assertEquals(
            "Charged · 00:01 · Health 100%",
            BatteryWidget.detail(
                state = "Charged",
                timeLabel = "00:01",
                temperatureLabel = null,
                healthLabel = "Health 100%",
            ),
        )
        val shown = BatteryWidget.detail("Discharging", "21:04", "Temp 36.5 °C", "Health 80%")
        assertFalse(shown.contains("Wear"))
    }
}
