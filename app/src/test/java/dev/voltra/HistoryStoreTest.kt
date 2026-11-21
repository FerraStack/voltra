package dev.voltra

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HistoryStoreTest {
    private lateinit var context: Application
    private val dbName = "history-unit-test.db"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    private fun reading(
        time: Long,
        level: Int,
        screenOn: Boolean = true,
        temperature: Float? = 30f,
    ) = Reading(
        level = level,
        charging = false,
        full = false,
        plugged = false,
        screenOn = screenOn,
        temperature = temperature,
        voltage = 4000,
        current = null,
        averageCurrent = null,
        energyNwh = null,
        counter = 1_000_000,
        status = BatteryHealthStatus.GOOD,
        source = PowerSource.BATTERY,
        chargeMinutes = null,
        cycles = null,
        time = time,
    )

    @Test
    fun forwardClockJumpOver365DaysDoesNotWipeHistoricalSamples() {
        val baseTime = 1_700_000_000_000L
        val day = HistoryStore.DAY_MS
        HistoryStore(context, dbName).use { store ->
            store.record(reading(baseTime, 90))
            store.record(reading(baseTime + 10 * 60_000L, 88))
            // Simulate a rogue RTC forward jump (+400 days)
            store.record(reading(baseTime + 400L * day, 85))

            val points = store.observations()
            assertEquals(listOf(90, 88, 85), points.map { it.level })
        }
    }

    @Test
    fun normalForwardProgressionPrunesBeyondRetention() {
        val baseTime = 1_700_000_000_000L
        val day = HistoryStore.DAY_MS
        HistoryStore(context, dbName).use { store ->
            store.record(reading(baseTime, 90))
            // Normal 35-day progression (> 30d default retention, < 365d guard) prunes old sample
            store.record(reading(baseTime + 35L * day, 80))

            val points = store.observations()
            assertEquals(listOf(80), points.map { it.level })
        }
    }

    @Test
    fun clockRollbackPreservesExistingSamplesAndSplitsSessions() {
        val now = 1_700_000_000_000L
        HistoryStore(context, dbName).use { store ->
            store.record(reading(now, 80))
            store.record(reading(now - 5 * 60_000L, 79))

            val points = store.observations()
            assertEquals(listOf(80, 79), points.map { it.level })
            assertEquals(2, Sessions.group(points).size)
        }
    }

    @Test
    fun retentionSettingPrunesTransactionallyAndPersists() {
        val now = 1_700_000_000_000L
        val day = HistoryStore.DAY_MS
        HistoryStore(context, dbName).use { store ->
            val db = store.writableDatabase
            for ((time, level) in listOf(now - 60 * day to 60, now - 20 * day to 70, now - 2 * day to 80)) {
                db.execSQL("INSERT INTO samples(time,level,plugged,charging) VALUES (?, ?, 0, 0)", arrayOf<Any>(time, level))
            }
            assertEquals(30, store.retentionDays())
            store.setRetentionDays(90, now)
            assertEquals(listOf(60, 70, 80), store.observations().map { it.level })
            store.setRetentionDays(7, now)
            assertEquals(listOf(80), store.observations().map { it.level })
            assertThrows(IllegalArgumentException::class.java) { store.setRetentionDays(1, now) }
        }
        HistoryStore(context, dbName).use { store ->
            assertEquals(7, store.retentionDays())
        }
    }

    @Test
    fun walModeAndBusyTimeoutAreConfigured() {
        HistoryStore(context, dbName).use { store ->
            val db = store.writableDatabase
            assertTrue(db.isWriteAheadLoggingEnabled)
            db.beginTransaction()
            try {
                val timeout = db.rawQuery("PRAGMA busy_timeout", null).use { c ->
                    c.moveToFirst()
                    c.getInt(0)
                }
                assertEquals(HistoryStore.BUSY_TIMEOUT_MS, timeout)
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    @Test
    fun upgradesFromSchemaV1ThroughV5ToV6() {
        // V1 -> V6
        val path = context.getDatabasePath(dbName)
        path.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
            db.execSQL("CREATE TABLE samples (time INTEGER PRIMARY KEY, level INTEGER NOT NULL, plugged INTEGER NOT NULL, charging INTEGER NOT NULL, counter INTEGER, temperature REAL)")
            db.execSQL("INSERT INTO samples VALUES (1000, 42, 0, 0, 1200000, 27.5)")
            db.version = 1
        }
        HistoryStore(context, dbName).use { store ->
            val point = store.observations().single()
            assertEquals(42, point.level)
            assertEquals(1_200_000, point.counter)
            assertNull(point.screenOn)
            assertEquals(6, store.readableDatabase.version)
            assertEquals(30, store.retentionDays())
        }

        // V4 -> V6
        context.deleteDatabase(dbName)
        SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
            db.execSQL("CREATE TABLE samples (id INTEGER PRIMARY KEY AUTOINCREMENT, time INTEGER NOT NULL, level INTEGER NOT NULL, plugged INTEGER NOT NULL, charging INTEGER NOT NULL, counter INTEGER, temperature REAL, screen_on INTEGER, plug_type INTEGER)")
            db.execSQL("INSERT INTO samples(time,level,plugged,charging,screen_on,plug_type) VALUES (1000,55,1,1,1,2)")
            db.version = 4
        }
        HistoryStore(context, dbName).use { store ->
            val point = store.observations().single()
            assertEquals(55, point.level)
            assertEquals(2, point.plugType)
            assertNull(point.currentMicroamps)
            assertNull(point.voltageMv)
            assertEquals(6, store.readableDatabase.version)
        }

        // V5 -> V6
        context.deleteDatabase(dbName)
        SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
            db.execSQL("CREATE TABLE samples (id INTEGER PRIMARY KEY AUTOINCREMENT, time INTEGER NOT NULL, level INTEGER NOT NULL, plugged INTEGER NOT NULL, charging INTEGER NOT NULL, counter INTEGER, temperature REAL, screen_on INTEGER, plug_type INTEGER, current_ua INTEGER, voltage_mv INTEGER)")
            db.execSQL("INSERT INTO samples(time,level,plugged,charging,current_ua,voltage_mv) VALUES (1000,66,1,1,1500000,4100)")
            db.version = 5
        }
        HistoryStore(context, dbName).use { store ->
            val point = store.observations().single()
            assertEquals(66, point.level)
            assertEquals(1_500_000, point.currentMicroamps)
            assertEquals(4100, point.voltageMv)
            assertEquals(30, store.retentionDays())
            assertEquals(6, store.readableDatabase.version)
        }
    }
}
