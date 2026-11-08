package dev.voltra

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import kotlin.math.roundToInt

data class Reading(val level: Int, val charging: Boolean, val full: Boolean, val plugged: Boolean, val screenOn: Boolean, val temperature: Float?, val voltage: Int?, val current: Int?, val averageCurrent: Int?, val energyNwh: Long?, val counter: Int?, val status: String, val source: String, val chargeMinutes: Int?, val cycles: Int?, val technology: String? = null, val plugType: Int = 0, val time: Long = System.currentTimeMillis())
data class Sample(val level: Int, val counter: Int?, val time: Long)
data class Estimates(val remainingMinutes: Int? = null, val capacityMah: Int? = null, val healthPercent: Int? = null, val screenOnMinutes: Int? = null, val screenOffMinutes: Int? = null, val chargeSpeed: Double? = null, val dischargeSpeed: Double? = null, val chargeRemaining: Int? = null, val sourceSpeeds: Map<Int, Double> = emptyMap(), val observedScreenOn: Int = 0, val observedScreenOff: Int = 0)
data class BatterySnapshot(val reading: Reading, val estimates: Estimates, val observations: List<Observation>, val sessions: List<BatterySession>)

object BatteryMath {
    fun scaleCurrent(raw: Int?, multiplier: Int): Int? {
        if (raw == null || multiplier !in listOf(1, 10, 100, 1000)) return null
        val scaled = raw.toLong() * multiplier
        return scaled.takeIf { it in (Int.MIN_VALUE + 1L)..Int.MAX_VALUE.toLong() }?.toInt()
    }
    fun remaining(start: Sample, end: Sample): Int? {
        val minutes = (end.time - start.time) / 60_000.0
        val drop = start.level - end.level
        if (minutes < 10 || drop < 2 || end.level <= 0) return null
        return (minutes * end.level / drop).roundToInt().coerceAtMost(7 * 24 * 60)
    }
    fun capacity(start: Sample, end: Sample): Int? {
        val delta = end.level - start.level
        val charge = (end.counter ?: return null).toLong() - (start.counter ?: return null).toLong()
        if (delta < 10 || charge <= 0 || end.time - start.time !in 5 * 60_000L..72 * 60 * 60_000L) return null
        return (charge / 1000.0 * 100 / delta).roundToInt().takeIf { it in 500..50000 }
    }
    fun health(capacity: Int?, design: Int): Int? = if (capacity != null && design in 500..50000) (capacity * 100.0 / design).roundToInt() else null
}

class BatteryRepository(context: Context) {
    private val app = context.applicationContext
    private val manager = app.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    private val prefs = app.getSharedPreferences("battery", Context.MODE_PRIVATE)
    private val history = HistoryStore(app)
    @Synchronized fun observations(): List<Observation> = history.observations()
    val designCapacity get() = prefs.getInt("design", 0)
    val currentMultiplier get() = prefs.getInt("current_multiplier", 1).takeIf { it in listOf(1, 10, 100, 1000) } ?: 1
    fun setCurrentMultiplier(value: Int) { if (value in listOf(1, 10, 100, 1000)) prefs.edit().putInt("current_multiplier", value).apply() }
    @Synchronized fun setDesign(value: Int) { prefs.edit().putInt("design", value).apply() }
    @Synchronized fun clear() { history.clear(); prefs.edit().remove("design").remove("capacity").remove("rate_milli").apply() }
    @Synchronized fun close() = history.close()
    fun pause() = Unit
    @Synchronized fun sample(): Reading {
        val intent = app.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        fun property(id: Int): Int? = manager.getIntProperty(id).takeUnless { it == Int.MIN_VALUE }
        val temp = intent?.takeIf { it.hasExtra(BatteryManager.EXTRA_TEMPERATURE) }?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)?.div(10f)
        val voltage = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)?.takeIf { it > 0 }
        val state = when (intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Хорошее"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Перегрев"
            BatteryManager.BATTERY_HEALTH_COLD -> "Переохлаждение"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Неисправна"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Повышенное напряжение"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Неисправность"
            else -> "Нет данных"
        }
        val reading = Reading(if (level >= 0 && scale > 0 && level <= scale) level * 100 / scale else -1,
            plugged != 0 && status == BatteryManager.BATTERY_STATUS_CHARGING, status == BatteryManager.BATTERY_STATUS_FULL, plugged != 0, (app.getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive, temp, voltage,
            BatteryMath.scaleCurrent(property(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW), currentMultiplier), BatteryMath.scaleCurrent(property(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE), currentMultiplier),
            manager.getLongProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER).takeIf { it > 0 && it != Long.MIN_VALUE },
            property(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)?.takeIf { it > 0 }, state,
            when (plugged) { BatteryManager.BATTERY_PLUGGED_AC -> "Сеть"; BatteryManager.BATTERY_PLUGGED_USB -> "USB"; BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Беспроводная"; 8 -> "Док-станция"; else -> if (plugged != 0) "Подключено" else "От батареи" },
            if (Build.VERSION.SDK_INT >= 28 && plugged != 0) manager.computeChargeTimeRemaining().takeIf { it >= 0 }?.div(60_000)?.coerceAtMost(10080)?.toInt() else null,
            if (Build.VERSION.SDK_INT >= 34) intent?.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1)?.takeIf { it >= 0 } else null,
            intent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)?.takeIf { it.isNotBlank() }, plugged)
        history.record(reading)
        ChargeAlert.onReading(app, reading)
        TemperatureAlert.onReading(app, reading)
        LowBatteryAlert.onReading(app, reading)
        BatteryWidget.update(app, reading)
        return reading
    }
    @Synchronized fun snapshot(): BatterySnapshot {
        val reading = sample()
        val observations = observations()
        val sessions = Sessions.group(observations)
        val capacity = Sessions.capacity(sessions)
        return BatterySnapshot(reading, Estimates(Sessions.remaining(sessions, reading.level, reading.plugged), capacity, BatteryMath.health(capacity, designCapacity),
            Sessions.screenRemaining(observations, reading.level, reading.plugged, true), Sessions.screenRemaining(observations, reading.level, reading.plugged, false),
            Sessions.averageSpeed(sessions, true), Sessions.averageSpeed(sessions, false), Sessions.chargeRemaining(sessions, reading),
            listOf(1, 2, 4, 8).mapNotNull { type -> Sessions.averageSpeed(sessions, true, type)?.let { type to it } }.toMap(),
            Sessions.observedScreenMinutes(observations, reading.time, true), Sessions.observedScreenMinutes(observations, reading.time, false)), observations, sessions)
    }
}
