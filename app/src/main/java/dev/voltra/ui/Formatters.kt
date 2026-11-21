package dev.voltra.ui

import android.content.Context
import dev.voltra.BatteryHealthStatus
import dev.voltra.CapacityExclusionReason
import dev.voltra.PowerSource
import dev.voltra.R
import dev.voltra.Reading
import dev.voltra.TemperatureUnit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

object Formatters {
    fun healthStatus(context: Context, status: BatteryHealthStatus): String = context.getString(
        when (status) {
            BatteryHealthStatus.GOOD -> R.string.health_good
            BatteryHealthStatus.OVERHEAT -> R.string.health_overheat
            BatteryHealthStatus.COLD -> R.string.health_cold
            BatteryHealthStatus.DEAD -> R.string.health_dead
            BatteryHealthStatus.OVER_VOLTAGE -> R.string.health_over_voltage
            BatteryHealthStatus.UNSPECIFIED_FAILURE -> R.string.health_unspecified_failure
            BatteryHealthStatus.UNKNOWN -> R.string.health_unknown
        },
    )

    fun powerSource(context: Context, source: PowerSource): String = context.getString(
        when (source) {
            PowerSource.AC -> R.string.source_ac
            PowerSource.USB -> R.string.source_usb
            PowerSource.WIRELESS -> R.string.source_wireless
            PowerSource.DOCK -> R.string.source_dock
            PowerSource.OTHER_PLUGGED -> R.string.source_other_plugged
            PowerSource.BATTERY -> R.string.source_battery
        },
    )

    fun plugType(context: Context, plugType: Int): String = context.getString(
        when (plugType) {
            PowerSource.PLUG_AC -> R.string.source_ac
            PowerSource.PLUG_USB -> R.string.source_usb
            PowerSource.PLUG_WIRELESS -> R.string.source_wireless
            PowerSource.PLUG_DOCK -> R.string.source_dock
            else -> R.string.source_unknown
        },
    )

    fun exclusionReason(context: Context, reason: CapacityExclusionReason): String = context.getString(
        when (reason) {
            CapacityExclusionReason.NO_CHARGE_COUNTER -> R.string.exclusion_no_counter
            CapacityExclusionReason.INSUFFICIENT_CHARGE_SPAN -> R.string.exclusion_insufficient_span
            CapacityExclusionReason.SESSION_TOO_SHORT -> R.string.exclusion_too_short
            CapacityExclusionReason.SESSION_TOO_LONG -> R.string.exclusion_too_long
            CapacityExclusionReason.COUNTER_DID_NOT_INCREASE -> R.string.exclusion_counter_not_increased
            CapacityExclusionReason.OUT_OF_RANGE -> R.string.exclusion_out_of_range
        },
    )

    fun chargeState(context: Context, reading: Reading): String = context.getString(
        when {
            reading.full && reading.plugged -> R.string.state_charged
            reading.charging -> R.string.state_charging
            reading.plugged -> R.string.state_plugged
            else -> R.string.state_on_battery
        },
    )

    fun sessionType(context: Context, charging: Boolean, plugged: Boolean): String = context.getString(
        when {
            charging -> R.string.state_charging
            plugged -> R.string.state_plugged
            else -> R.string.state_discharging
        },
    )

    fun temperature(context: Context, celsius: Float, unit: TemperatureUnit): String =
        unit.format(context, celsius)

    fun voltage(context: Context, mv: Int): String =
        String.format(Locale.getDefault(), context.getString(R.string.format_voltage), mv / 1000f)

    fun powerWatts(context: Context, currentUa: Int, voltageMv: Int): String {
        val watts = abs(currentUa / 1_000_000f * voltageMv / 1000f)
        return String.format(Locale.getDefault(), context.getString(R.string.format_power), watts)
    }

    fun energyWh(context: Context, energyNwh: Long): String =
        String.format(Locale.getDefault(), context.getString(R.string.format_energy), energyNwh / 1_000_000_000.0)

    fun current(context: Context, microamps: Int): String {
        if (microamps == 0) return context.getString(R.string.format_current_zero)
        if (abs(microamps.toLong()) < 10_000L) {
            return String.format(Locale.getDefault(), context.getString(R.string.format_current_small), microamps / 1000.0)
        }
        val ma = microamps / 1000
        return context.getString(R.string.format_current_large, if (ma > 0) "+" else "", ma)
    }

    fun speed(context: Context, percentPerHour: Double): String =
        String.format(Locale.getDefault(), context.getString(R.string.format_speed), percentPerHour)

    fun estimate(context: Context, minutes: Int): String =
        if (minutes >= 7 * 24 * 60) {
            context.getString(R.string.format_estimate_over_week)
        } else {
            context.getString(R.string.format_duration_hm, minutes / 60, minutes % 60)
        }

    fun duration(context: Context, ms: Long): String {
        val minutes = (ms / 60_000L).coerceAtLeast(0L).toInt()
        return context.getString(R.string.format_duration_hm, minutes / 60, minutes % 60)
    }

    fun dateTime(time: Long): String =
        SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(time))

    fun axisTime(time: Long, days: Int): String {
        val pattern = if (days <= 1) "HH:mm" else "dd MMM"
        return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(time))
    }
}
