# Voltra

Voltra is a private, on-device Android battery companion built with Kotlin and Jetpack Compose. The app is an early prototype; see [research and limitations](docs/RESEARCH.md).

## Features

- Live charge, system battery condition, temperature, voltage and current where exposed by the device. Optional manual current-unit calibration affects current and calculated power. Android's charge-time and cycle-count estimates are shown when available.
- Local history with 24-hour and seven-day charge, temperature, current and voltage graphs, charging/discharging sessions, source-specific average charging speeds where observed, and CSV export. History is bounded to 30 days and 20,000 samples; older samples are pruned when a new sample is saved. Snapshots are stored at most every five minutes unless the level, power connection, charging state or screen state changes, or the temperature differs by at least 2 °C from the last stored point; monitoring and alert checks may run more often.
- Optional user-started continuous monitoring with an ongoing notification and samples approximately every 30 seconds. While running it also samples screen on/off events and rate-limited battery-change broadcasts. It uses no wake lock, so Android can delay it while asleep or stop it. It consumes additional power and can be stopped from its notification.
- Optional best-effort background sampling, approximately every 15 minutes and on power connection changes. Android may delay or skip jobs; gaps longer than 75 minutes split sessions. Turn it on in settings. This mode does not need a persistent service or exemption from battery optimization.
- Charge time from Android where available, or a rough linear estimate from the current charging session; it cannot predict charging taper or charge limits.
- A time-weighted discharge estimate based on up to five observed sessions with at least 10 minutes and a 2% drop. Separate screen-on/off estimates and observed-time summaries use Android device interactivity at interval endpoints; interactivity can differ from the actual display state and transitions between samples can be missed.
- An estimated capacity based on a charge-span-weighted median of up to ten charging sessions with a 10% rise over at least five minutes and a supported charge counter. Enter the manufacturer's design capacity to display a health percentage. This is an estimate, not a diagnostic measurement; phone sensors and multi-cell designs can affect accuracy. Android's battery health status is a separate system status.
- Home-screen widget showing the last battery level and observation time. Android schedules widget updates roughly every 30 minutes; adding it creates periodic observations even when background sampling is off.
- Optional low-battery reminder with a configurable threshold, subject to sampling delays.
- Optional high-temperature reminder with a configurable 35–50 °C threshold and 2 °C reset hysteresis, subject to sampling delays.
- Optional charge-level reminder from 60% to 100%; it can be delayed between samples and does not stop charging. Notification permission is requested only when enabled.
- Data and design capacity can be cleared in settings. No account, network access, analytics or advertising.

The first measurements can take time. Charge counter, current, system charge time and cycle count are not available on every device. Background sampling is inexact and cannot reliably support timely alarms.

## Build

Install Android SDK 36.1 and JDK 17 or newer, then run `./gradlew :app:assembleDebug :app:testDebugUnitTest`. The debug APK is in `app/build/outputs/apk/debug/`. With a device or emulator connected, run `./gradlew :app:connectedDebugAndroidTest` for database migration and retention tests. See [CONTRIBUTING.md](CONTRIBUTING.md) for development notes.

CSV timestamps are Unix milliseconds. `charge_counter_uAh` is the device-reported charge counter, `temperature_c` is degrees Celsius, `current_ua` is calibrated current in microamperes as stored at the time of measurement, `voltage_mv` is millivolts, `device_interactive` is Android device interactivity, and `plug_type` is Android’s raw power-source value (0 unplugged, 1 AC, 2 USB, 4 wireless, 8 dock). Empty fields mean unavailable or not recorded by an older version.
