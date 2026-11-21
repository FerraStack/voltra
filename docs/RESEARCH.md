# Battery Monitoring Research & Measurement Caveats

Research date: 2026-11-08. Product descriptions are vendor claims, not independent tests.

## Existing Apps

- [AccuBattery on Google Play](https://play.google.com/store/apps/details?id=com.digibites.accubattery&hl=en): battery capacity from the charge controller, charge sessions, charge alarm, charge and discharge speed, screen-on/off estimates, app usage, and an ongoing notification. Its app-use attribution combines measured device drain with foreground app data; it is not a direct per-app power meter. The developer explicitly calls out why sessions may be excluded from health calculation.
- [Battery Guru on Google Play](https://play.google.com/store/apps/details?id=com.paget96.batteryguru&hl=en): capacity and health estimates with confidence labels and charge history; charging power, temperatures, charge alarm, screen-on/off drain, system cycle count where available, exports, and widgets. It describes the charge alarm as a reminder, not a charge limiter. App usage needs separate usage access. The store listing notes gaps caused by background restrictions and paused charging.
- AccuBattery's linked methodology page returned HTTP 403 during this research. We have not independently checked its equations and do not reproduce its wear model.

## Open Implementations

- [Battery Monitor: current multiplier detection](https://github.com/tswistak/Battery-Monitor/blob/master/app/src/main/kotlin/codes/swistak/batterymonitor/monitoring/BatteryCurrentMultiplierDetector.kt) illustrates a practical complication: some devices report current using unexpected magnitude or sign. Voltra does not automatically infer a multiplier from heuristics; it offers explicit manual calibration defaulting to Android's documented microampere units. Incorrect calibration also changes computed power.
- [Battery Monitor: predictor](https://github.com/tswistak/Battery-Monitor/blob/master/app/src/main/kotlin/codes/swistak/batterymonitor/monitoring/PredictorCore.kt) maintains separate estimates for discharge and different power sources and combines recent with older measurements. Voltra uses a time-weighted estimate from recent measured discharge sessions, separates charging speeds by reported source, and uses Android's charge-time estimate when available with a clearly labeled linear fallback; it does not use manufacturer profiles or unmeasured default predictions.
- [Battery Monitor: target alerts](https://github.com/tswistak/Battery-Monitor/blob/master/app/src/main/kotlin/codes/swistak/batterymonitor/monitoring/TargetAlarmEvaluator.kt) models alert arming and transitions explicitly. Voltra uses separate thresholds and rearming hysteresis rules. Sampling gaps still affect delivery timing.

These projects are GPL-licensed; these notes describe observed behavior, and no source code was incorporated into Voltra.

## Platform Constraints

- [BatteryManager](https://developer.android.com/reference/android/os/BatteryManager) provides a sticky battery broadcast, optional current in µA, charge counter in µAh, and, on supported Android versions and devices, a system estimate of charge time and cycle count. Missing properties remain unknown. Android's battery health status is a hardware condition flag, not a percentage of original capacity. The charge counter represents remaining charge, not total energy delivered by a wall adapter.
- [Battery monitoring](https://developer.android.com/training/monitoring-device-state/battery-monitoring): battery broadcasts provide level, voltage, temperature, and status. Sampling too aggressively consumes power itself.
- [Periodic background work](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work) is inexact and can be delayed by Doze and OEM power management. Its minimum periodic interval is 15 minutes; pairing it with a charging-constraint `JobInfo` (`setRequiresCharging(true)`) improves session boundary capture on Android 8+ where implicit `ACTION_POWER_CONNECTED` manifest broadcasts are restricted, though delivery remains best-effort.
- Voltra also offers an explicit foreground service (`specialUse`) with a persistent notification, immediate power/screen broadcast listeners, no wake lock, and a visible battery cost. Android can still delay or stop it. The `specialUse` subtype requires Play Store review if distributed on Google Play ([service types](https://developer.android.com/develop/background-work/services/fgs/service-types)).

## Design Principles & Measurement Caveats

1. **Distinguish observed facts from estimates**: Keep a bounded local history and treat gaps over 75 minutes, clock changes, charging transitions, plug-type switches, and large reversals of the reported counter as session boundaries. The counter-reversal threshold is a conservative heuristic, not a device-independent guarantee.
2. **Honest background behavior**: Make background sampling opt-in, infrequent, and explicitly labeled as best-effort; display when the last sample was taken. Never silently request battery-optimization exemptions.
3. **Conservative capacity estimation**: Calculate estimated capacity only from sufficiently wide, internally consistent charging sessions (≥ 10% gain over ≥ 5 minutes with an increasing charge counter), display the qualifying session count, and explain why excluded sessions were skipped. Device-specific current units, dual-cell batteries, charging pauses, charge limits, and OEM sensors can all affect accuracy.
4. **No unvalidated wear scores**: Voltra intentionally avoids presenting a fabricated wear-per-charge score or battery-aging model that cannot be independently measured from Android's battery APIs.
