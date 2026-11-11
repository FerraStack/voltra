package dev.voltra

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Uses a separate database name, not the user's history. */
@RunWith(AndroidJUnit4::class)
class HistoryMigrationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Before fun setup() { context.deleteDatabase("migration-test.db") }
    @After fun cleanup() { context.deleteDatabase("migration-test.db") }
    @Test fun storesTransitionsPrunesOldSamplesAndIgnoresLateReaders() {
        val now = System.currentTimeMillis()
        fun reading(time: Long, level: Int, screenOn: Boolean) = Reading(
            level = level, charging = false, full = false, plugged = false, screenOn = screenOn,
            temperature = 30f, voltage = 4000, current = null, averageCurrent = null,
            energyNwh = null, counter = 1_000_000, status = "ok", source = "battery",
            chargeMinutes = null, cycles = null, time = time)
        HistoryStore(context, "migration-test.db").use { history ->
            history.record(reading(now - 31L * 24 * 60 * 60_000, 90, true))
            history.record(reading(now, 80, true))
            history.record(reading(now + 1000, 80, false))
            history.record(reading(now + 500, 79, true))
            val points = history.observations()
            assertEquals(2, points.size)
            assertEquals(listOf(true, false), points.map { it.screenOn })
            assertEquals(listOf(4000, 4000), points.map { it.voltageMv })
            assertEquals(listOf(80, 80), points.map { it.level })
            history.clear()
            assertTrue(history.observations().isEmpty())
        }
    }
    @Test fun substantialTemperatureChangesAreRecordedBetweenPeriodicSamples() {
        val now = System.currentTimeMillis()
        fun reading(time: Long, temperature: Float) = Reading(level = 80, charging = false, full = false, plugged = false, screenOn = true,
            temperature = temperature, voltage = null, current = null, averageCurrent = null, energyNwh = null, counter = null,
            status = "", source = "", chargeMinutes = null, cycles = null, time = time)
        HistoryStore(context, "migration-test.db").use { history ->
            history.record(reading(now, 30f))
            history.record(reading(now + 1000, 31f))
            history.record(reading(now + 2000, 32f))
            assertEquals(listOf(30f, 32f), history.observations().map { it.temperature })
        }
    }
    @Test fun historyIsBoundedBySampleCount() {
        val now = System.currentTimeMillis()
        HistoryStore(context, "migration-test.db").use { history ->
            val db = history.writableDatabase
            db.beginTransaction()
            try {
                val statement = db.compileStatement("INSERT INTO samples(time,level,plugged,charging,screen_on) VALUES(?,80,0,0,1)")
                for (i in 0 until 20_005) {
                    statement.bindLong(1, now - (20_005 - i) * 1000L)
                    statement.executeInsert()
                }
                statement.close()
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
            val reading = Reading(level = 79, charging = false, full = false, plugged = false, screenOn = true,
                temperature = null, voltage = null, current = null, averageCurrent = null, energyNwh = null, counter = null,
                status = "", source = "", chargeMinutes = null, cycles = null, time = now)
            history.record(reading)
            val count = db.rawQuery("SELECT COUNT(*) FROM samples", null).use { cursor -> cursor.moveToFirst(); cursor.getInt(0) }
            assertEquals(20_000, count)
            assertEquals(20_000, history.observations().size)
        }
    }
    @Test fun sampleCountCapUsesInsertionOrderAfterClockRollback() {
        val now = System.currentTimeMillis()
        HistoryStore(context, "migration-test.db").use { history ->
            val db = history.writableDatabase
            db.beginTransaction()
            try {
                val statement = db.compileStatement("INSERT INTO samples(time,level,plugged,charging,screen_on) VALUES(?,80,0,0,1)")
                for (i in 0 until 20_010) {
                    val time = if (i < 20_000) now - (20_000 - i) * 1000L else now - 24 * 60 * 60_000L + (i - 20_000) * 1000L
                    statement.bindLong(1, time)
                    statement.executeInsert()
                }
                statement.close()
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
            history.record(Reading(level = 79, charging = false, full = false, plugged = false, screenOn = true,
                temperature = null, voltage = null, current = null, averageCurrent = null, energyNwh = null, counter = null,
                status = "", source = "", chargeMinutes = null, cycles = null, time = now - 24 * 60 * 60_000L + 10_000L))
            val points = history.observations()
            assertEquals(20_000, points.size)
            assertEquals(now - (20_000 - 11) * 1000L, points.first().time)
            assertEquals(79, points.last().level)
        }
    }
    @Test fun largeClockRollbackPreservesExistingSamplesAndSplitsSessions() {
        val now = System.currentTimeMillis()
        fun reading(time: Long, level: Int) = Reading(level = level, charging = false, full = false, plugged = false, screenOn = true,
            temperature = null, voltage = null, current = null, averageCurrent = null, energyNwh = null, counter = null,
            status = "", source = "", chargeMinutes = null, cycles = null, time = time)
        HistoryStore(context, "migration-test.db").use { history ->
            history.record(reading(now, 80))
            history.record(reading(now - 5 * 60_000, 79))
            val points = history.observations()
            assertEquals(listOf(80, 79), points.map { it.level })
            assertEquals(2, Sessions.group(points).size)
        }
    }
    @Test fun retentionSettingPrunesTransactionallyAndPersists() {
        val now = System.currentTimeMillis()
        val day = 24 * 60 * 60_000L
        HistoryStore(context, "migration-test.db").use { history ->
            val db = history.writableDatabase
            for ((time, level) in listOf(now - 60 * day to 60, now - 20 * day to 70, now - 2 * day to 80)) {
                db.execSQL("INSERT INTO samples(time,level,plugged,charging) VALUES (?, ?, 0, 0)", arrayOf<Any>(time, level))
            }
            assertEquals(30, history.retentionDays())
            history.setRetentionDays(90, now)
            assertEquals(listOf(60, 70, 80), history.observations().map { it.level })
            history.setRetentionDays(7, now)
            assertEquals(listOf(80), history.observations().map { it.level })
            assertThrows(IllegalArgumentException::class.java) { history.setRetentionDays(1, now) }
        }
        HistoryStore(context, "migration-test.db").use { history -> assertEquals(7, history.retentionDays()) }
    }
    @Test fun retentionChangesAreSeenByIndependentBackgroundStore() {
        val now = System.currentTimeMillis()
        val day = 24 * 60 * 60_000L
        HistoryStore(context, "migration-test.db").use { settings ->
            settings.setRetentionDays(90, now)
            settings.writableDatabase.execSQL("INSERT INTO samples(time,level,plugged,charging) VALUES (?, 80, 0, 0)", arrayOf<Any>(now - 60 * day))
            HistoryStore(context, "migration-test.db").use { worker ->
                worker.record(Reading(level = 79, charging = false, full = false, plugged = false, screenOn = true,
                    temperature = null, voltage = null, current = null, averageCurrent = null, energyNwh = null, counter = null,
                    status = "", source = "", chargeMinutes = null, cycles = null, time = now))
                assertEquals(listOf(80, 79), worker.observations().map { it.level })
                settings.setRetentionDays(7, now)
                assertEquals(7, worker.retentionDays())
                assertEquals(listOf(79), worker.observations().map { it.level })
            }
        }
    }
    @Test fun upgradeFromVersionFiveDefaultsToThirtyDays() {
        val path = context.getDatabasePath("migration-test.db")
        path.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
            db.execSQL("CREATE TABLE samples (id INTEGER PRIMARY KEY AUTOINCREMENT, time INTEGER NOT NULL, level INTEGER NOT NULL, plugged INTEGER NOT NULL, charging INTEGER NOT NULL, counter INTEGER, temperature REAL, screen_on INTEGER, plug_type INTEGER, current_ua INTEGER, voltage_mv INTEGER)")
            db.execSQL("INSERT INTO samples(time,level,plugged,charging) VALUES (1000,42,1,1)")
            db.version = 5
        }
        HistoryStore(context, "migration-test.db").use { history ->
            assertEquals(42, history.observations().single().level)
            assertEquals(30, history.retentionDays())
            assertEquals(6, history.readableDatabase.version)
        }
    }
    @Test fun upgradeFromVersionFourLeavesNewMeasurementsUnknown() {
        val path = context.getDatabasePath("migration-test.db")
        path.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
            db.execSQL("CREATE TABLE samples (id INTEGER PRIMARY KEY AUTOINCREMENT, time INTEGER NOT NULL, level INTEGER NOT NULL, plugged INTEGER NOT NULL, charging INTEGER NOT NULL, counter INTEGER, temperature REAL, screen_on INTEGER, plug_type INTEGER)")
            db.execSQL("INSERT INTO samples(time,level,plugged,charging,screen_on,plug_type) VALUES (1000,42,1,1,1,2)")
            db.version = 4
        }
        HistoryStore(context, "migration-test.db").use { history ->
            val point = history.observations().single()
            assertEquals(2, point.plugType)
            assertNull(point.currentMicroamps)
            assertNull(point.voltageMv)
            assertEquals(6, history.readableDatabase.version)
        }
    }
    @Test fun upgradeFromVersionThreePreservesData() {
        val path = context.getDatabasePath("migration-test.db")
        path.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
            db.execSQL("CREATE TABLE samples (id INTEGER PRIMARY KEY AUTOINCREMENT, time INTEGER NOT NULL, level INTEGER NOT NULL, plugged INTEGER NOT NULL, charging INTEGER NOT NULL, counter INTEGER, temperature REAL, screen_on INTEGER)")
            db.execSQL("INSERT INTO samples(time,level,plugged,charging,screen_on) VALUES (1000,42,1,1,1)")
            db.version = 3
        }
        HistoryStore(context, "migration-test.db").use { history ->
            val point = history.observations().single()
            assertEquals(42, point.level)
            assertNull(point.plugType)
            assertEquals(6, history.readableDatabase.version)
        }
    }
    @Test fun upgradeFromVersionTwoPreservesScreenState() {
        val path = context.getDatabasePath("migration-test.db")
        path.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
            db.execSQL("CREATE TABLE samples (time INTEGER PRIMARY KEY, level INTEGER NOT NULL, plugged INTEGER NOT NULL, charging INTEGER NOT NULL, counter INTEGER, temperature REAL, screen_on INTEGER)")
            db.execSQL("INSERT INTO samples VALUES (1000, 42, 0, 0, 1200000, 27.5, 1)")
            db.version = 2
        }
        HistoryStore(context, "migration-test.db").use { history ->
            assertEquals(true, history.observations().single().screenOn)
            assertEquals(6, history.readableDatabase.version)
        }
    }
    @Test fun upgradeFromVersionOnePreservesSamplesAndUnknownScreenState() {
        val path = context.getDatabasePath("migration-test.db")
        path.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
            db.execSQL("CREATE TABLE samples (time INTEGER PRIMARY KEY, level INTEGER NOT NULL, plugged INTEGER NOT NULL, charging INTEGER NOT NULL, counter INTEGER, temperature REAL)")
            db.execSQL("INSERT INTO samples VALUES (1000, 42, 0, 0, 1200000, 27.5)")
            db.version = 1
        }
        HistoryStore(context, "migration-test.db").use { history ->
            val point = history.observations().single()
            assertEquals(42, point.level)
            assertEquals(1_200_000, point.counter)
            assertNull(point.screenOn)
            assertEquals(6, history.readableDatabase.version)
        }
    }
}
