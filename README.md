# Voltra

[![Android CI](https://github.com/FerraStack/voltra/actions/workflows/android.yml/badge.svg)](https://github.com/FerraStack/voltra/actions/workflows/android.yml)
[![Release](https://github.com/FerraStack/voltra/actions/workflows/release.yml/badge.svg)](https://github.com/FerraStack/voltra/actions/workflows/release.yml)

Voltra is a private, 100% offline Android battery companion built with Kotlin and Jetpack Compose. It records local battery telemetry, separates charging and discharging sessions, estimates remaining battery capacity from observed charge-counter spans, and stays honest about what Android sensors can and cannot measure.

## Download

Download the latest signed APK and `SHA256SUMS` from [GitHub Releases](https://github.com/FerraStack/voltra/releases).

## Features

- **Live battery telemetry**: Charge level, Android system battery condition, temperature (°C or °F), voltage, instantaneous and average current, remaining energy (Wh), battery-side power estimate, plus system charge-time and cycle-count estimates where exposed by the device. Includes optional manual current-unit calibration (`×1`–`×1000`).
- **Interactive local history**: 24-hour, 7-day, and 30-day charts for charge, temperature, current, and voltage with touch/drag scrubbing, Y/X axis labels, charging/discharging session breakdowns, per-source average charging speeds, and dated CSV export (`voltra-history-YYYY-MM-DD.csv`).
- **Bounded SQLite storage**: WAL-mode local database keeping up to 20,000 samples across a configurable 7, 30 (default), or 90 days. Shortening retention requires confirmation and prunes immediately; forward clock jumps over 365 days do not wipe historical samples.
- **Estimated capacity & health**: Charge-span-weighted median across up to 10 qualifying charging sessions (≥ 10% gain over ≥ 5 minutes with an increasing charge counter). Enter your phone's rated design capacity to display an estimated health percentage, with clear reasons when a session is excluded.
- **Opt-in background sampling**: Periodic `JobScheduler` sampling approximately every 15 minutes plus a charging-constraint job (`setRequiresCharging(true)`) so Android 8+ triggers sampling when charging starts without relying on blocked implicit broadcasts. Android may delay or batch jobs; gaps over 75 minutes start a new session.
- **Opt-in continuous monitoring**: Explicit foreground service (`specialUse`) with an ongoing notification sampling roughly every 30 seconds plus immediate power-connection and screen-state events, with no wake lock and a one-tap Stop action.
- **Configurable alerts**: Charge-level reminder (60–100%), high-temperature alert (35–50 °C with 2 °C reset hysteresis), and low-battery alert (5–30% with 3% reset hysteresis). Voltra never controls charging hardware.
- **Home-screen widget**: Resizable widget showing battery level, state, observation time, temperature (°C/°F), and cached health estimate when available.
- **Bilingual & adaptive UI**: Full English and Russian localization (including Android 13+ per-app language support), System/Dark/Light Material 3 themes, edge-to-edge layout, and adaptive phone/tablet navigation (`NavigationBar` / `NavigationRail`).

## Permissions

Voltra requests no network permission (`INTERNET` is not declared) and collects zero analytics or telemetry. See [PRIVACY.md](PRIVACY.md).

| Permission | Purpose | Opt-in / Behavior |
| --- | --- | --- |
| `POST_NOTIFICATIONS` | Charge, temperature, and low-battery reminders, plus ongoing continuous monitoring notification | Requested only when you enable an alert or continuous monitoring (Android 13+) |
| `FOREGROUND_SERVICE` & `FOREGROUND_SERVICE_SPECIAL_USE` | Optional user-started continuous monitoring with a visible notification | Off by default; stopped anytime from Settings or the notification |
| `RECEIVE_BOOT_COMPLETED` | Persisting scheduled `JobScheduler` background sampling across reboots | Active only when background sampling is enabled in Settings |

## CSV Export Format

Exported files are named `voltra-history-YYYY-MM-DD.csv` with the following columns:

| Column | Description |
| --- | --- |
| `timestamp_ms` | Sample timestamp in Unix milliseconds |
| `level_percent` | Battery level (`0`–`100`) |
| `plugged` | `true` if connected to external power |
| `charging` | `true` if actively charging |
| `charge_counter_uAh` | Device-reported charge counter in microampere-hours (empty if unavailable) |
| `temperature_c` | Battery temperature in degrees Celsius (always °C regardless of display unit) |
| `device_interactive` | Android `PowerManager.isInteractive` state at sample time |
| `plug_type` | Android raw plug type (`0` battery, `1` AC, `2` USB, `4` wireless, `8` dock) |
| `current_ua` | Calibrated current in microamperes as stored at measurement time |
| `voltage_mv` | Battery voltage in millivolts |

## Build

Requires Android SDK 36.1 and JDK 17+.

```sh
# Run unit tests, lint, and build debug + AndroidTest APKs
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest

# Build an R8-minified release APK locally using the debug signing key
./gradlew :app:assembleRelease -Pvoltra.signReleaseWithDebugKey=true

# Run instrumented SQLite migration tests on a connected device or emulator
./gradlew :app:connectedDebugAndroidTest
```

See [CONTRIBUTING.md](CONTRIBUTING.md), [docs/RESEARCH.md](docs/RESEARCH.md), [docs/ROADMAP.md](docs/ROADMAP.md), and [docs/RELEASING.md](docs/RELEASING.md).
