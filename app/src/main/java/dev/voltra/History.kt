package dev.voltra

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlin.math.roundToInt

/** Samples are snapshots, not continuous observation. Long gaps end a session. */
data class Observation(val time: Long, val level: Int, val plugged: Boolean, val charging: Boolean, val counter: Int?, val temperature: Float?, val screenOn: Boolean? = null, val plugType: Int? = null, val currentMicroamps: Int? = null, val voltageMv: Int? = null)
data class BatterySession(val start: Observation, val end: Observation, val charging: Boolean, val sampleCount: Int, val peakTemperature: Float? = null) {
    val capacity: Int? get() = if (charging) BatteryMath.capacity(Sample(start.level, start.counter, start.time), Sample(end.level, end.counter, end.time)) else null
    val capacityReason: String? get() {
        if (!charging || capacity != null) return null
        return when {
            start.counter == null || end.counter == null -> "Нет счётчика заряда"
            end.level - start.level < 10 -> "Нужно не менее 10% заряда"
            end.time - start.time < 5 * 60_000L -> "Сессия короче 5 минут"
            end.time - start.time > 72 * 60 * 60_000L -> "Слишком длинная сессия"
            end.counter <= start.counter -> "Счётчик не вырос"
            else -> "Показания вне допустимого диапазона"
        }
    }
    val rate: Double? get() {
        if (charging || start.plugged || end.plugged) return null
        val elapsed = (end.time - start.time) / 60_000.0
        val drop = start.level - end.level
        return if (elapsed >= 10 && drop >= 2) drop / elapsed else null
    }
}
object Sessions {
    private const val MAX_GAP = 75 * 60_000L
    fun group(readings: List<Observation>): List<BatterySession> {
        if (readings.isEmpty()) return emptyList()
        val result = mutableListOf<BatterySession>()
        var start = readings.first()
        var previous = start
        var count = 1
        var peakTemperature = start.temperature
        for (next in readings.drop(1)) {
            val gap = next.time - previous.time
            val counterDelta = if (next.counter != null && previous.counter != null) next.counter.toLong() - previous.counter else null
            val reset = gap !in 1..MAX_GAP || next.plugged != previous.plugged || next.charging != previous.charging ||
                (next.plugged && next.plugType != null && previous.plugType != null && next.plugType != previous.plugType) ||
                (next.charging && next.level < previous.level - 2) || (!next.plugged && next.level > previous.level + 2) ||
                (next.charging && counterDelta != null && counterDelta < -100_000) ||
                (!next.plugged && counterDelta != null && counterDelta > 100_000)
            if (reset) {
                result.add(BatterySession(start, previous, start.charging, count, peakTemperature))
                start = next; count = 1; peakTemperature = next.temperature
            } else {
                count++
                next.temperature?.let { peakTemperature = maxOf(peakTemperature ?: it, it) }
            }
            previous = next
        }
        result.add(BatterySession(start, previous, start.charging, count, peakTemperature))
        return result
    }
    fun chargeRemaining(sessions: List<BatterySession>, reading: Reading): Int? {
        if (!reading.charging || reading.level !in 0..99) return null
        val current = sessions.lastOrNull() ?: return null
        val elapsed = (current.end.time - current.start.time) / 60_000.0
        val gain = current.end.level - current.start.level
        if (!current.charging || reading.time - current.end.time !in 0..MAX_GAP || elapsed < 10 || gain < 2) return null
        return ((100 - reading.level) * elapsed / gain).roundToInt().coerceAtMost(7 * 24 * 60)
    }
    fun averageSpeed(sessions: List<BatterySession>, charging: Boolean, plugType: Int? = null): Double? {
        var minutes = 0.0
        var change = 0
        sessions.forEach { session ->
            val elapsed = (session.end.time - session.start.time) / 60_000.0
            val delta = if (charging) session.end.level - session.start.level else session.start.level - session.end.level
            if (session.charging == charging && (charging || !session.start.plugged) && (plugType == null || session.start.plugType == plugType) && elapsed >= 10 && delta >= 2) {
                minutes += elapsed
                change += delta
            }
        }
        return if (minutes > 0) change * 60.0 / minutes else null
    }
    fun capacity(sessions: List<BatterySession>): Int? {
        val values = sessions.mapNotNull { session -> session.capacity?.let { it to (session.end.level - session.start.level) } }.takeLast(10).sortedBy { it.first }
        if (values.isEmpty()) return null
        val totalWeight = values.sumOf { it.second }
        var weight = 0
        values.forEachIndexed { index, (capacity, chargeSpan) ->
            weight += chargeSpan
            if (weight * 2 > totalWeight) return capacity
            if (weight * 2 == totalWeight && index + 1 < values.size) return ((capacity + values[index + 1].first) / 2.0).roundToInt()
        }
        return values.last().first
    }
    fun observedScreenMinutes(points: List<Observation>, now: Long, screenOn: Boolean): Int {
        val since = now - 24 * 60 * 60_000L
        val duration = points.zipWithNext().sumOf { (a, b) ->
            val gap = b.time - a.time
            if (a.time >= since && b.time <= now && a.screenOn == screenOn && b.screenOn == screenOn && gap in 1..MAX_GAP) gap else 0L
        }
        return (duration / 60_000).coerceAtMost(24 * 60).toInt()
    }
    /** A rough subset of intervals whose end points report the same screen state. */
    fun screenRemaining(points: List<Observation>, level: Int, plugged: Boolean, screenOn: Boolean): Int? {
        if (plugged || level <= 0) return null
        var minutes = 0.0
        var drop = 0
        points.zipWithNext().forEach { (a, b) ->
            val elapsed = b.time - a.time
            val delta = a.level - b.level
            if (!a.plugged && !b.plugged && a.screenOn == screenOn && b.screenOn == screenOn && elapsed in 1..MAX_GAP && delta in 0..10) {
                minutes += elapsed / 60_000.0
                drop += delta
            }
        }
        if (minutes < 10 || drop < 2) return null
        return (level * minutes / drop).roundToInt().coerceAtMost(7 * 24 * 60)
    }
    fun remaining(sessions: List<BatterySession>, level: Int, plugged: Boolean): Int? {
        if (plugged || level <= 0) return null
        val recent = sessions.filter { it.rate != null }.takeLast(5)
        if (recent.isEmpty()) return null
        val totalDrop = recent.sumOf { it.start.level - it.end.level }
        val totalMinutes = recent.sumOf { (it.end.time - it.start.time) / 60_000.0 }
        return (level * totalMinutes / totalDrop).roundToInt().coerceAtMost(7 * 24 * 60)
    }
}

class HistoryStore(context: Context, name: String = "history.db") : SQLiteOpenHelper(context.applicationContext, name, null, 5) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE samples (id INTEGER PRIMARY KEY AUTOINCREMENT, time INTEGER NOT NULL, level INTEGER NOT NULL, plugged INTEGER NOT NULL, charging INTEGER NOT NULL, counter INTEGER, temperature REAL, screen_on INTEGER, plug_type INTEGER, current_ua INTEGER, voltage_mv INTEGER)")
        db.execSQL("CREATE INDEX samples_time ON samples(time)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) db.execSQL("ALTER TABLE samples ADD COLUMN screen_on INTEGER")
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE samples RENAME TO samples_old")
            onCreate(db)
            db.execSQL("INSERT INTO samples(time, level, plugged, charging, counter, temperature, screen_on) SELECT time, level, plugged, charging, counter, temperature, screen_on FROM samples_old ORDER BY time")
            db.execSQL("DROP TABLE samples_old")
        } else {
            if (oldVersion < 4) db.execSQL("ALTER TABLE samples ADD COLUMN plug_type INTEGER")
            if (oldVersion < 5) {
                db.execSQL("ALTER TABLE samples ADD COLUMN current_ua INTEGER")
                db.execSQL("ALTER TABLE samples ADD COLUMN voltage_mv INTEGER")
            }
        }
    }
    fun record(r: Reading) {
        if (r.level !in 0..100) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            val latest = db.rawQuery("SELECT time, level, plugged, charging, screen_on, plug_type, temperature FROM samples ORDER BY id DESC LIMIT 1", null).use { c ->
                if (c.moveToFirst()) listOf(c.getLong(0), c.getInt(1).toLong(), c.getInt(2).toLong(), c.getInt(3).toLong(), if (c.isNull(4)) -1L else c.getInt(4).toLong(), if (c.isNull(5)) -1L else c.getInt(5).toLong(), if (c.isNull(6)) Long.MIN_VALUE else (c.getFloat(6) * 10).roundToInt().toLong()) else null
            }
            // Concurrent readers can finish out of order; do not erase newer samples for a small delay.
            if (latest != null && r.time < latest[0] && latest[0] - r.time <= 120_000) {
                db.setTransactionSuccessful()
                return
            }
            val clockChanged = latest != null && r.time < latest[0]
            val temperatureChanged = r.temperature != null && latest != null && (latest[6] == Long.MIN_VALUE || kotlin.math.abs((r.temperature * 10).roundToInt().toLong() - latest[6]) >= 20)
            val shouldSave = latest == null || clockChanged || temperatureChanged || r.time - latest[0] >= 5 * 60_000 || r.level.toLong() != latest[1] || (if (r.plugged) 1L else 0L) != latest[2] || (if (r.charging) 1L else 0L) != latest[3] || (if (r.screenOn) 1L else 0L) != latest[4] || r.plugType.toLong() != latest[5]
            if (shouldSave) {
                val values = ContentValues().apply {
                    put("time", r.time); put("level", r.level); put("plugged", if (r.plugged) 1 else 0); put("charging", if (r.charging) 1 else 0); put("screen_on", if (r.screenOn) 1 else 0); put("plug_type", r.plugType)
                    r.counter?.let { put("counter", it) }; r.temperature?.let { put("temperature", it) }; r.current?.let { put("current_ua", it) }; r.voltage?.let { put("voltage_mv", it) }
                }
                db.insertWithOnConflict("samples", null, values, SQLiteDatabase.CONFLICT_REPLACE)
                db.delete("samples", "time < ?", arrayOf((r.time - 30L * 24 * 60 * 60_000).toString()))
                db.execSQL("DELETE FROM samples WHERE time < (SELECT time FROM samples ORDER BY id DESC LIMIT 1 OFFSET 19999)")
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    fun observations(): List<Observation> {
        val result = mutableListOf<Observation>()
        readableDatabase.rawQuery("SELECT time,level,plugged,charging,counter,temperature,screen_on,plug_type,current_ua,voltage_mv FROM (SELECT * FROM samples ORDER BY id DESC LIMIT 20000) ORDER BY id", null).use { c ->
            while (c.moveToNext()) result.add(Observation(c.getLong(0), c.getInt(1), c.getInt(2) != 0, c.getInt(3) != 0, if (c.isNull(4)) null else c.getInt(4), if (c.isNull(5)) null else c.getFloat(5), if (c.isNull(6)) null else c.getInt(6) != 0, if (c.isNull(7)) null else c.getInt(7), if (c.isNull(8)) null else c.getInt(8), if (c.isNull(9)) null else c.getInt(9)))
        }
        return result
    }
    fun clear() { writableDatabase.delete("samples", null, null) }
}
