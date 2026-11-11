package dev.voltra

import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class WidgetDetailTest {
    @Test fun detailAddsTemperatureAndHealthOnlyWhenKnown() {
        val locale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            assertEquals("Разряд · 21:04", BatteryWidget.detail("Разряд", null, null, "21:04"))
            assertEquals("Зарядка · 21:04 · Температура 36.5 °C · Здоровье 80%", BatteryWidget.detail("Зарядка", 36.5f, 80, "21:04"))
            assertEquals("Подключено · 09:15 · Температура 30.0 °C", BatteryWidget.detail("Подключено", 30f, null, "09:15"))
            assertEquals("Заряжено · 00:01 · Здоровье 100%", BatteryWidget.detail("Заряжено", null, 100, "00:01"))
            val shown = BatteryWidget.detail("Разряд", 36.5f, 80, "21:04")
            assertFalse(shown.contains("Износ"))
            Locale.setDefault(Locale.forLanguageTag("ru-RU"))
            assertEquals("Разряд · 21:04 · Температура 36,5 °C · Здоровье 80%", BatteryWidget.detail("Разряд", 36.5f, 80, "21:04"))
        } finally { Locale.setDefault(locale) }
    }
}
