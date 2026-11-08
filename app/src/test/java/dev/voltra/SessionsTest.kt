package dev.voltra

import org.junit.Assert.*
import org.junit.Test

class SessionsTest {
    private fun point(minute: Long, level: Int, plugged: Boolean = false, counter: Int? = null) = Observation(minute * 60_000, level, plugged, plugged, counter, 30f)
    @Test fun boundariesDoNotBridgeGapsOrChargerChanges() {
        val sessions = Sessions.group(listOf(point(0, 80), point(15, 78), point(100, 75), point(115, 76, true), point(130, 80, true)))
        assertEquals(3, sessions.size)
        assertEquals(2, sessions[0].sampleCount)
        assertEquals(1, sessions[1].sampleCount)
        assertTrue(sessions[2].charging)
        assertEquals(0, Sessions.group(emptyList()).size)
    }
    @Test fun estimateUsesDistinctChargeSessionsAndMedian() {
        val sessions = listOf(
            BatterySession(point(0, 20, true, 1_000_000), point(20, 30, true, 1_400_000), true, 2),
            BatterySession(point(30, 20, true, 1_000_000), point(50, 30, true, 1_600_000), true, 2),
            BatterySession(point(60, 20, true, 1_000_000), point(80, 30, true, 1_420_000), true, 2))
        assertEquals(4200, Sessions.capacity(sessions))
    }
    @Test fun capacityWeightsWiderChargeRangesMoreHeavily() {
        val short = BatterySession(point(0, 20, true, 1_000_000), point(20, 30, true, 1_600_000), true, 2)
        val wide = BatterySession(point(30, 20, true, 1_000_000), point(90, 50, true, 2_200_000), true, 2)
        assertEquals(4000, Sessions.capacity(listOf(short, wide)))
    }
    @Test fun remainingRequiresObservedDischargeAndIsHiddenWhilePlugged() {
        val session = BatterySession(point(0, 80), point(20, 76), false, 2)
        assertEquals(380, Sessions.remaining(listOf(session), 76, false))
        assertNull(Sessions.remaining(listOf(session), 76, true))
        assertNull(Sessions.remaining(emptyList(), 76, false))
    }
    @Test fun remainingWeightsRecentSessionsByTheirDuration() {
        val sessions = listOf(BatterySession(point(0, 80), point(10, 78), false, 2), BatterySession(point(20, 78), point(120, 68), false, 2))
        assertEquals(623, Sessions.remaining(sessions, 68, false))
    }
    @Test fun chargingForecastRequiresAnActiveObservedSession() {
        val session = BatterySession(point(0, 70, true), point(20, 80, true), true, 2)
        val reading = Reading(level = 80, charging = true, full = false, plugged = true, screenOn = true,
            temperature = null, voltage = null, current = null, averageCurrent = null, energyNwh = null,
            counter = null, status = "", source = "", chargeMinutes = null, cycles = null, time = 20 * 60_000L)
        assertEquals(40, Sessions.chargeRemaining(listOf(session), reading))
        assertNull(Sessions.chargeRemaining(listOf(session), reading.copy(charging = false)))
        assertNull(Sessions.chargeRemaining(listOf(session), reading.copy(time = 100 * 60_000L)))
        assertNull(Sessions.chargeRemaining(emptyList(), reading))
    }
    @Test fun chargingSpeedCanBeSeparatedByPowerSource() {
        val ac = BatterySession(point(0, 20, true).copy(plugType = 1), point(20, 30, true).copy(plugType = 1), true, 2)
        val usb = BatterySession(point(30, 20, true).copy(plugType = 2), point(50, 24, true).copy(plugType = 2), true, 2)
        assertEquals(30.0, Sessions.averageSpeed(listOf(ac, usb), true, 1)!!, 0.001)
        assertEquals(12.0, Sessions.averageSpeed(listOf(ac, usb), true, 2)!!, 0.001)
        assertNull(Sessions.averageSpeed(listOf(ac, usb), true, 4))
    }
    @Test fun speedWeightsSessionsByObservedDuration() {
        val sessions = listOf(BatterySession(point(0, 80), point(20, 76), false, 2), BatterySession(point(30, 76), point(70, 72), false, 2))
        assertEquals(8.0, Sessions.averageSpeed(sessions, false)!!, 0.001)
        assertNull(Sessions.averageSpeed(sessions, true))
    }
    @Test fun observedScreenTimeSkipsTransitionsAndLargeGaps() {
        val points = listOf(point(0, 80).copy(screenOn = true), point(20, 79).copy(screenOn = true),
            point(30, 78).copy(screenOn = false), point(50, 77).copy(screenOn = false), point(150, 70).copy(screenOn = false))
        assertEquals(20, Sessions.observedScreenMinutes(points, 150 * 60_000L, true))
        assertEquals(20, Sessions.observedScreenMinutes(points, 150 * 60_000L, false))
    }
    @Test fun screenEstimatesOnlyUseMatchingEndpointsAndRejectGaps() {
        val points = listOf(
            point(0, 80).copy(screenOn = true), point(20, 76).copy(screenOn = true),
            point(30, 74).copy(screenOn = false), point(50, 72).copy(screenOn = false),
            point(150, 60).copy(screenOn = false))
        assertEquals(360, Sessions.screenRemaining(points, 72, false, true))
        assertEquals(720, Sessions.screenRemaining(points, 72, false, false))
        assertNull(Sessions.screenRemaining(points, 72, true, true))
        assertNull(Sessions.screenRemaining(points.map { it.copy(screenOn = null) }, 72, false, true))
    }
    @Test fun switchingPowerSourceSplitsChargingSessions() {
        val ac = point(0, 40, true).copy(plugType = 1)
        val usb = point(10, 45, true).copy(plugType = 2)
        assertEquals(2, Sessions.group(listOf(ac, usb)).size)
        assertEquals(1, Sessions.group(listOf(ac.copy(plugType = null), usb)).size)
    }
    @Test fun largeCounterReversalsSplitSessions() {
        assertEquals(2, Sessions.group(listOf(point(0, 70, true, 2_000_000), point(10, 71, true, 1_800_000))).size)
        assertEquals(2, Sessions.group(listOf(point(0, 70, false, 2_000_000), point(10, 69, false, 2_200_000))).size)
        assertEquals(1, Sessions.group(listOf(point(0, 70, true, 2_000_000), point(10, 71, true, 1_950_000))).size)
    }
    @Test fun inconsistentLevelSplitsSession() {
        assertEquals(2, Sessions.group(listOf(point(0, 70), point(10, 75))).size)
        assertEquals(2, Sessions.group(listOf(point(0, 70, true), point(10, 65, true))).size)
    }
}
