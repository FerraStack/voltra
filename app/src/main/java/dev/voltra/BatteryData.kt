package dev.voltra

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import kotlin.math.roundToInt

enum class BatteryHealthStatus {
    GOOD,
    OVERHEAT,
    COLD,
    DEAD,
    OVER_VOLTAGE,
    UNSPECIFIED_FAILURE,
    UNKNOWN;

    companion object {
        fun fromBatteryManager(health: Int): BatteryHealthStatus = when (health) {
            BatteryManager.BATTERY_HEALTH_GOOD -> GOOD
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> OVERHEAT
            BatteryManager.BATTERY_HEALTH_COLD -> COLD
            BatteryManager.BATTERY_HEALTH_DEAD -> DEAD
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> OVER_VOLTAGE
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> UNSPECIFIED_FAILURE
            else -> UNKNOWN
        }
    }
}

enum class PowerSource {
    AC,
    USB,
    WIRELESS,
    DOCK,
    OTHER_PLUGGED,
    BATTERY;

    companion object {
        const val PLUG_AC = BatteryManager.BATTERY_PLUGGED_AC
        const val PLUG_USB = BatteryManager.BATTERY_PLUGGED_USB
        const val PLUG_WIRELESS = BatteryManager.BATTERY_PLUGGED_WIRELESS
        const val PLUG_DOCK = 8

        fun fromPlugType(plugged: Int): PowerSource = when (plugged) {
            PLUG_AC -> AC
            PLUG_USB -> USB
            PLUG_WIRELESS -> WIRELESS
            PLUG_DOCK -> DOCK
            else -> if (plugged != 0) OTHER_PLUGGED else BATTERY
        }
    }
}

data class Reading(
    val level: Int,
    val charging: Boolean,
    val full: Boolean,
    val plugged: Boolean,
    val screenOn: Boolean,
    val temperature: Float?,
    val voltage: Int?,
    val current: Int?,
    val averageCurrent: Int?,
    val energyNwh: Long?,
    val counter: Int?,
    val status: BatteryHealthStatus,
    val source: PowerSource,
    val chargeMinutes: Int?,
    val cycles: Int?,
    val technology: String? = null,
    val plugType: Int = 0,
    val time: Long = System.currentTimeMillis(),
)

data class Sample(val level: Int, val counter: Int?, val time: Long)

data class Estimates(
    val remainingMinutes: Int? = null,
    val capacityMah: Int? = null,
    val healthPercent: Int? = null,
    val screenOnMinutes: Int? = null,
    val screenOffMinutes: Int? = null,
    val chargeSpeed: Double? = null,
    val dischargeSpeed: Double? = null,
    val chargeRemaining: Int? = null,
    val sourceSpeeds: Map<Int, Double> = emptyMap(),
    val observedScreenOn: Int = 0,
    val observedScreenOff: Int = 0,
)

data class BatterySnapshot(
    val reading: Reading,
    val estimates: Estimates,
    val observations: List<Observation>,
    val sessions: List<BatterySession>,
    val retentionDays: Int,
)

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

    fun health(capacity: Int?, design: Int): Int? =
        if (capacity != null && design in 500..50000) (capacity * 100.0 / design).roundToInt() else null
}

class BatteryRepository internal constructor(
    context: Context,
    private val history: HistoryStore,
    private val ownsStore: Boolean,
) {
    constructor(context: Context) : this(context, HistoryStore.getInstance(context), ownsStore = false)

    private val app = context.applicationContext
    private val manager = app.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    private val prefs = app.getSharedPreferences("battery", Context.MODE_PRIVATE)

    @Volatile
    private var capacityCacheValid: Boolean = prefs.getBoolean(KEY_CAPACITY_CACHE_VALID, false)

    @Volatile
    private var cachedCapacityMah: Int? =
        if (capacityCacheValid) prefs.getInt(KEY_CACHED_CAPACITY_MAH, -1).takeIf { it > 0 } else null

    @Volatile
    private var lastObservedCharging: Boolean = prefs.getBoolean(KEY_LAST_CHARGING, false)

    @Synchronized
    fun observations(): List<Observation> = history.observations()

    val designCapacity: Int
        get() = prefs.getInt(KEY_DESIGN, 0)

    val currentMultiplier: Int
        get() = prefs.getInt(KEY_CURRENT_MULTIPLIER, 1).takeIf { it in MULTIPLIERS } ?: 1

    fun setCurrentMultiplier(value: Int) {
        if (value in MULTIPLIERS) {
            prefs.edit().putInt(KEY_CURRENT_MULTIPLIER, value).apply()
        }
    }

    @Synchronized
    fun setRetentionDaysAndSnapshot(value: Int): BatterySnapshot {
        history.setRetentionDays(value)
        invalidateCapacityCache()
        return snapshot()
    }

    @Synchronized
    fun setDesign(value: Int) {
        prefs.edit().putInt(KEY_DESIGN, value).apply()
        invalidateCapacityCache()
    }

    @Synchronized
    fun clear() {
        history.clear()
        prefs.edit()
            .remove(KEY_DESIGN)
            .remove("capacity")
            .remove("rate_milli")
            .remove(KEY_CACHED_CAPACITY_MAH)
            .remove(KEY_CAPACITY_CACHE_VALID)
            .remove(KEY_LAST_CHARGING)
            .apply()
        cachedCapacityMah = null
        capacityCacheValid = false
        lastObservedCharging = false
    }

    @Synchronized
    fun close() {
        // Never close the process-wide singleton HistoryStore; only close isolated test stores.
        if (ownsStore) {
            history.close()
        }
    }

    fun pause() = Unit

    @Synchronized
    internal fun invalidateCapacityCache() {
        capacityCacheValid = false
        cachedCapacityMah = null
        prefs.edit()
            .putBoolean(KEY_CAPACITY_CACHE_VALID, false)
            .remove(KEY_CACHED_CAPACITY_MAH)
            .apply()
    }

    @Synchronized
    private fun updateCapacityCache(capacityMah: Int?) {
        cachedCapacityMah = capacityMah
        capacityCacheValid = true
        prefs.edit().apply {
            putBoolean(KEY_CAPACITY_CACHE_VALID, true)
            if (capacityMah != null) putInt(KEY_CACHED_CAPACITY_MAH, capacityMah) else remove(KEY_CACHED_CAPACITY_MAH)
        }.apply()
    }

    @Synchronized
    fun cachedHealthPercent(): Int? {
        val design = designCapacity
        if (design !in 500..50000) return null
        if (!capacityCacheValid) {
            val computed = Sessions.capacity(Sessions.group(history.observations()))
            updateCapacityCache(computed)
        }
        return BatteryMath.health(cachedCapacityMah, design)
    }

    @Synchronized
    fun sample(): Reading {
        val reading = sampleInternal()
        BatteryWidget.update(app, reading) { cachedHealthPercent() }
        return reading
    }

    @Synchronized
    private fun sampleInternal(): Reading {
        val intent = app.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        fun property(id: Int): Int? = manager.getIntProperty(id).takeUnless { it == Int.MIN_VALUE }
        val temp = intent
            ?.takeIf { it.hasExtra(BatteryManager.EXTRA_TEMPERATURE) }
            ?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
            ?.div(10f)
        val voltage = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)?.takeIf { it > 0 }
        val healthStatus = BatteryHealthStatus.fromBatteryManager(
            intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1,
        )
        val powerSource = PowerSource.fromPlugType(plugged)
        val isCharging = plugged != 0 && status == BatteryManager.BATTERY_STATUS_CHARGING
        val isFull = status == BatteryManager.BATTERY_STATUS_FULL
        val powerManager = app.getSystemService(Context.POWER_SERVICE) as PowerManager
        val reading = Reading(
            level = if (level >= 0 && scale > 0 && level <= scale) level * 100 / scale else -1,
            charging = isCharging,
            full = isFull,
            plugged = plugged != 0,
            screenOn = powerManager.isInteractive,
            temperature = temp,
            voltage = voltage,
            current = BatteryMath.scaleCurrent(property(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW), currentMultiplier),
            averageCurrent = BatteryMath.scaleCurrent(property(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE), currentMultiplier),
            energyNwh = manager.getLongProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER)
                .takeIf { it > 0 && it != Long.MIN_VALUE },
            counter = property(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)?.takeIf { it > 0 },
            status = healthStatus,
            source = powerSource,
            chargeMinutes = if (Build.VERSION.SDK_INT >= 28 && plugged != 0) {
                manager.computeChargeTimeRemaining().takeIf { it >= 0 }?.div(60_000)?.coerceAtMost(10080)?.toInt()
            } else {
                null
            },
            cycles = if (Build.VERSION.SDK_INT >= 34) {
                intent?.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1)?.takeIf { it >= 0 }
            } else {
                null
            },
            technology = intent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)?.takeIf { it.isNotBlank() },
            plugType = plugged,
        )

        val chargingEnded = lastObservedCharging && !reading.charging
        if (lastObservedCharging != reading.charging) {
            lastObservedCharging = reading.charging
            prefs.edit().putBoolean(KEY_LAST_CHARGING, reading.charging).apply()
        }

        history.record(reading)

        if (chargingEnded) {
            invalidateCapacityCache()
        }

        // Re-arm the charging-constraint background job when unplugged.
        if (!reading.plugged && BackgroundSampling.enabled(app)) {
            BackgroundSampling.ensureChargingJobScheduled(app)
        }

        ChargeAlert.onReading(app, reading)
        TemperatureAlert.onReading(app, reading)
        LowBatteryAlert.onReading(app, reading)
        return reading
    }

    @Synchronized
    fun snapshot(): BatterySnapshot {
        val reading = sampleInternal()
        val observations = history.observations()
        val sessions = Sessions.group(observations)
        val capacity = Sessions.capacity(sessions)
        updateCapacityCache(capacity)
        val health = BatteryMath.health(capacity, designCapacity)
        BatteryWidget.update(app, reading) { health }
        return BatterySnapshot(
            reading = reading,
            estimates = Estimates(
                remainingMinutes = Sessions.remaining(sessions, reading.level, reading.plugged),
                capacityMah = capacity,
                healthPercent = health,
                screenOnMinutes = Sessions.screenRemaining(observations, reading.level, reading.plugged, true),
                screenOffMinutes = Sessions.screenRemaining(observations, reading.level, reading.plugged, false),
                chargeSpeed = Sessions.averageSpeed(sessions, true),
                dischargeSpeed = Sessions.averageSpeed(sessions, false),
                chargeRemaining = Sessions.chargeRemaining(sessions, reading),
                sourceSpeeds = listOf(1, 2, 4, 8)
                    .mapNotNull { type -> Sessions.averageSpeed(sessions, true, type)?.let { type to it } }
                    .toMap(),
                observedScreenOn = Sessions.observedScreenMinutes(observations, reading.time, true),
                observedScreenOff = Sessions.observedScreenMinutes(observations, reading.time, false),
            ),
            observations = observations,
            sessions = sessions,
            retentionDays = history.retentionDays(),
        )
    }

    companion object {
        private const val KEY_DESIGN = "design"
        private const val KEY_CURRENT_MULTIPLIER = "current_multiplier"
        private const val KEY_CAPACITY_CACHE_VALID = "cached_capacity_valid"
        private const val KEY_CACHED_CAPACITY_MAH = "cached_capacity_mah"
        private const val KEY_LAST_CHARGING = "last_observed_charging"
        val MULTIPLIERS = listOf(1, 10, 100, 1000)

        @android.annotation.SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: BatteryRepository? = null

        fun getInstance(context: Context): BatteryRepository =
            instance ?: synchronized(this) {
                instance ?: BatteryRepository(context.applicationContext).also { instance = it }
            }
    }
}
