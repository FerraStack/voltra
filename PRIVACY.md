# Privacy Policy

**Effective date:** 2026-11-21

Voltra is designed to be completely private and offline.

## Data Collection & Storage

- **No personal data is collected or transmitted.** Voltra does not declare the `INTERNET` permission and cannot send any data off your device.
- **Local storage only.** Battery telemetry samples (charge level, temperature, voltage, current, charge counter, plug state, and screen interactivity) and your preferences (design capacity, alert thresholds, theme, and units) are stored exclusively in local app storage (`history.db` and `SharedPreferences`) on your device.
- **Backups disabled.** Both cloud backup (`allowBackup="false"`) and device-to-device extraction are disabled in the app manifest.
- **No accounts, analytics, crash reporters, or ads.**

## User Control

- **Export:** You can export your local sample history as a CSV file to a location you choose via Android's Storage Access Framework.
- **Retention & deletion:** You can configure history retention (7, 30, or 90 days) or permanently delete all recorded history and saved design capacity at any time in **Settings → History & Data → Clear all data**, or by uninstalling the app.

## Permissions

- **Notifications (`POST_NOTIFICATIONS`):** Requested only if you enable charge, temperature, low-battery reminders, or continuous monitoring.
- **Foreground service (`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`):** Used only when you explicitly turn on continuous monitoring in Settings.
- **Run at startup (`RECEIVE_BOOT_COMPLETED`):** Used solely by Android's `JobScheduler` to restore opt-in background sampling after a device reboot.

## Contact

Questions or privacy concerns can be raised via [GitHub Issues](https://github.com/FerraStack/voltra/issues).
