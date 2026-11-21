package dev.voltra

import android.content.Context
import java.util.Locale
import kotlin.math.roundToInt

enum class ThemeMode(val key: String) {
    SYSTEM("system"),
    DARK("dark"),
    LIGHT("light");

    companion object {
        fun fromKey(key: String?): ThemeMode = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

enum class TemperatureUnit(val key: String) {
    CELSIUS("c"),
    FAHRENHEIT("f");

    fun convertFromCelsius(celsius: Float): Float = when (this) {
        CELSIUS -> celsius
        FAHRENHEIT -> celsius * 9f / 5f + 32f
    }

    fun convertFromCelsiusInt(celsius: Int): Int = when (this) {
        CELSIUS -> celsius
        FAHRENHEIT -> (celsius * 9.0 / 5.0 + 32.0).roundToInt()
    }

    fun format(context: Context, celsius: Float): String {
        val converted = convertFromCelsius(celsius)
        val resId = if (this == CELSIUS) R.string.format_temp_c else R.string.format_temp_f
        return String.format(Locale.getDefault(), context.getString(resId), converted)
    }

    fun formatIntThreshold(context: Context, celsius: Int): String {
        val converted = convertFromCelsiusInt(celsius)
        val resId = if (this == CELSIUS) R.string.format_temp_int_c else R.string.format_temp_int_f
        return context.getString(resId, converted)
    }

    fun symbol(context: Context): String =
        context.getString(if (this == CELSIUS) R.string.unit_celsius else R.string.unit_fahrenheit)

    companion object {
        fun fromKey(key: String?): TemperatureUnit = entries.firstOrNull { it.key == key } ?: CELSIUS
    }
}

object AppPreferences {
    private const val PREFS_NAME = "battery"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_TEMP_UNIT = "temp_unit"
    private const val KEY_SETUP_DISMISSED = "setup_dismissed"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun themeMode(context: Context): ThemeMode =
        ThemeMode.fromKey(prefs(context).getString(KEY_THEME_MODE, null))

    fun setThemeMode(context: Context, mode: ThemeMode) {
        prefs(context).edit().putString(KEY_THEME_MODE, mode.key).apply()
    }

    fun temperatureUnit(context: Context): TemperatureUnit =
        TemperatureUnit.fromKey(prefs(context).getString(KEY_TEMP_UNIT, null))

    fun setTemperatureUnit(context: Context, unit: TemperatureUnit) {
        prefs(context).edit().putString(KEY_TEMP_UNIT, unit.key).apply()
    }

    fun isSetupDismissed(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SETUP_DISMISSED, false)

    fun setSetupDismissed(context: Context, dismissed: Boolean) {
        prefs(context).edit().putBoolean(KEY_SETUP_DISMISSED, dismissed).apply()
    }
}
