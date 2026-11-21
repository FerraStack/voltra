# Changelog

All notable changes to Voltra are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2026-11-21

### Added
- Full English (default) and Russian localization with Android 13+ per-app locale configuration (`locales_config.xml`).
- Dedicated grouped Settings screen (Battery & Calibration, Monitoring, Alerts, Appearance & Units, History & Data, About).
- Cohesive Dark, Light, and System Material 3 themes with dynamic edge-to-edge system bar styling.
- Celsius (°C) and Fahrenheit (°F) temperature display unit setting applied across screens, charts, notifications, and widget.
- Interactive `HistoryChart` with Y/X axis labels, touch/drag scrubbing readout, stable snapshot timestamp, and 24h / 7d / 30d range selector.
- Adaptive layout switching between `NavigationBar` (phones) and `NavigationRail` + 4-column metric grid (tablets/landscape).
- Pull-to-refresh on Overview and a dismissible first-run setup card.
- Adaptive 108dp launcher icon with monochrome themed variant and custom monochrome vector notification status icons.
- Charging-constraint `JobInfo` (`setRequiresCharging(true)`) alongside periodic 15-minute background sampling for Android 8+ reliability.
- Dated CSV export filenames (`voltra-history-YYYY-MM-DD.csv`) with empty-history guard and snackbar feedback.
- R8 code shrinking, resource shrinking, reproducible signing configuration, and automated GitHub Release workflow publishing signed APK, AAB, and `SHA256SUMS`.
- Expanded JVM unit test suite covering alert hysteresis, SQLite migrations v1–v6, clock forward-jump protection, and CSV export formatting.

### Changed
- Converted `HistoryStore` and `BatteryRepository` into process-wide singletons with SQLite WAL mode, `PRAGMA busy_timeout = 5000`, cached capacity/health estimation, and single-read snapshots.
- Refactored monolithic `MainActivity` into modular `ui`, `theme`, `components`, `screens`, and `VoltraViewModel` preserving state across configuration changes and running storage operations on `Dispatchers.IO`.
- Replaced hardcoded Russian domain strings with typed enums (`BatteryHealthStatus`, `PowerSource`, `CapacityExclusionReason`).
- Upgraded `BatteryWidget` with Android 12+ rounded corners, target cell sizing, preview layout, and localized formatting.

### Fixed
- Guarded history pruning against forward RTC clock jumps exceeding 365 days so historical samples are not wiped.
- Hardened `MonitorService` with `ServiceCompat.startForeground(..., FOREGROUND_SERVICE_TYPE_SPECIAL_USE)`, reactive `StateFlow<Boolean> isRunning`, immediate `ACTION_POWER_CONNECTED` / `ACTION_POWER_DISCONNECTED` handling, guarded receiver cleanup, and a dedicated `MonitorStopReceiver` so stale notifications after process death cannot crash.
- Set `android:exported="false"` on `BatterySampleJob` and added `fullBackupContent` rules matching `dataExtractionRules`.
