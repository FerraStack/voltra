# Roadmap

See [RESEARCH.md](RESEARCH.md) for source material and measurement caveats. Voltra is an honest, privacy-first battery companion—not a calibrated laboratory diagnostic instrument.

## Shipped in v1.0.0

- Live device-reported battery metrics (charge, temperature in °C/°F, voltage, current, average current, remaining energy, battery-side power estimate, system charge-time, and cycle count where exposed).
- Bounded WAL-mode SQLite history (up to 20,000 samples across 7, 30, or 90 days) with clock rollback preservation, > 365-day forward-jump pruning guard, and schema v1–v6 migrations.
- Interactive 24h / 7d / 30d history charts with Y/X axis labels and touch/drag scrubbing, session history with incremental loading, per-source average charging speeds, and dated CSV export (`voltra-history-YYYY-MM-DD.csv`).
- Capacity & health estimation via charge-span-weighted median across up to 10 qualifying charge sessions, cached for fast sampling and widget refreshes, with typed exclusion explanations.
- Opt-in periodic + charging-constraint `JobScheduler` background sampling and hardened `specialUse` foreground monitoring service with safe broadcast Stop action.
- Charge-level, high-temperature (2 °C hysteresis), and low-battery (3% hysteresis) alerts with custom monochrome notification icons.
- Resizable home-screen widget with Android 12+ rounded corners, preview layout, and localized formatting.
- Full English and Russian localization (`locales_config.xml`), System/Dark/Light Material 3 themes, edge-to-edge insets, and adaptive phone/tablet navigation (`NavigationBar` / `NavigationRail`).
- R8 shrinking and automated signed release builds with APK/AAB verification and `SHA256SUMS`.

## Next (Post-1.0)

- **Multi-vendor hardware validation**: Validate charge-counter behavior, dual-cell batteries, fast-charging taper, and OEM power restrictions across a wider matrix of physical devices and document device-specific quirks.
- **Session confidence indicators**: Explore empirical confidence ranges based on sample density and variance across multiple independent charging sessions without inventing unverified wear scores.
- **Local backup & import**: Support explicit user-initiated encrypted or plain SQLite/CSV import so users can migrate history when switching devices without cloud backup.
- **Optional per-app usage attribution**: Evaluate a strictly opt-in `PACKAGE_USAGE_STATS` flow that clearly labels foreground time vs. estimated drain without implying direct per-app electrical measurement.
- **Additional translations & Play Store readiness**: Expand localization beyond EN/RU and prepare formal `specialUse` foreground service declarations if distributed via Google Play.
