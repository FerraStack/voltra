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
            assertEquals(5, history.readableDatabase.version)
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
            assertEquals(5, history.readableDatabase.version)
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
            assertEquals(5, history.readableDatabase.version)
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
            assertEquals(5, history.readableDatabase.version)
        }
    }
}
