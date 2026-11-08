# Battery monitoring research

Research date: 2026-11-08. Product descriptions are vendor claims, not independent tests.

## Existing apps

- [AccuBattery on Google Play](https://play.google.com/store/apps/details?id=com.digibites.accubattery&hl=en): battery capacity from the charge controller, charge sessions, charge alarm, charge and discharge speed, screen-on/off estimates, app usage and an ongoing notification. Its app-use attribution combines measured device drain with foreground app data; it is not a direct per-app power meter. The developer explicitly calls out why sessions may be excluded from health calculation.
- [Battery Guru on Google Play](https://play.google.com/store/apps/details?id=com.paget96.batteryguru&hl=en): capacity and health estimates with confidence labels and charge history; charging power, temperatures, charge alarm, screen-on/off drain, system cycle count where available, exports and widgets. It describes the charge alarm as a reminder, not a charge limiter. App usage needs separate usage access. The store listing notes gaps caused by background restrictions and paused charging.
- AccuBattery's linked methodology page returned HTTP 403 during this research. We have not independently checked its equations and do not reproduce its wear model.

## Open implementations

- [Battery Monitor: current multiplier detection](https://github.com/tswistak/Battery-Monitor/blob/master/app/src/main/kotlin/codes/swistak/batterymonitor/monitoring/BatteryCurrentMultiplierDetector.kt) illustrates a practical complication: some devices report current using unexpected magnitude or sign. The project has a detector based on typical-current heuristics. Voltra does not automatically infer a multiplier from those assumptions; it offers explicit manual calibration, defaulting to Android's documented units. Incorrect calibration also changes the computed power.
- [Battery Monitor: predictor](https://github.com/tswistak/Battery-Monitor/blob/master/app/src/main/kotlin/codes/swistak/batterymonitor/monitoring/PredictorCore.kt) maintains separate estimates for discharge and different power sources and combines recent with older measurements. Voltra uses a time-weighted estimate from recent measured discharge sessions, separates charging speeds by reported source, and uses Android's charge-time estimate when available with a labeled linear fallback; it does not use manufacturer profiles or unmeasured default predictions.
- [Battery Monitor: target alerts](https://github.com/tswistak/Battery-Monitor/blob/master/app/src/main/kotlin/codes/swistak/batterymonitor/monitoring/TargetAlarmEvaluator.kt) models alert arming and transitions explicitly. Voltra uses separate thresholds and rearming rules. Sampling gaps still affect delivery.

These projects are GPL-licensed; these notes describe observed behavior, and no source code was incorporated into Voltra.

## Platform constraints

- [BatteryManager](https://developer.android.com/reference/android/os/BatteryManager) provides a sticky battery broadcast, optional current in µA, charge counter in µAh, and, on supported Android versions/devices, a system estimate of charge time and cycle count. Missing properties must remain unknown. Battery health status is not a percentage of original capacity. Charge counter is remaining charge, not energy delivered by a charger.
- [Battery monitoring](https://developer.android.com/training/monitoring-device-state/battery-monitoring): battery broadcasts provide level, voltage, temperature and status. Sampling too aggressively consumes power itself.
- [Periodic background work](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work) is inexact and can be delayed by power management. Its minimum interval is 15 minutes; it cannot guarantee alarms, continuous current integration, or full sessions. Voltra also offers an explicit foreground service with a persistent notification, no wake lock and a battery cost. Android can still delay or stop it. The specialUse type and its use case require review for Play distribution ([service types](https://developer.android.com/develop/background-work/services/fgs/service-types)). Never pretend missing periods were observed.

## Voltra priorities

1. Keep a local, bounded history and distinguish observed data from estimates. Treat large gaps, clock changes, charging transitions, and large reversals of the reported counter as session boundaries. The counter-reversal threshold is a conservative heuristic, not a device-independent guarantee.
2. Make background sampling opt-in, infrequent and explicitly best-effort; show when the last observation occurred. No silent exemption from battery optimization.
3. Calculate charge capacity only from sufficiently wide, internally consistent samples; show the source and number of sessions rather than an invented precision. Validate on real phones, especially multi-cell devices, before claiming accuracy.
4. Later: charge and temperature reminders with explicit notification permission and reliable user-visible monitoring, richer session charts, screen-on/off statistics, export, optional usage access and widgets. Avoid per-app drain claims without explaining the attribution method.

## Current scope

Voltra is a prototype. Device-specific units, counter behavior, charging pauses, reboots, process death and manufacturer restrictions require device testing. A score or exact wear per charge would imply a battery-aging model not established by the sources above, so it is intentionally not presented as fact.
