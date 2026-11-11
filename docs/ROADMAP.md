# Roadmap

The [research](RESEARCH.md) describes the source material and important limitations. This is a prototype, not a calibrated diagnostic tool.

## Available

- Current device-provided battery values, approximate battery-side power, system charge-time and cycle count where available.
- Bounded local sample history, charge/discharge sessions, chart, CSV export and explanations for excluded capacity estimates.
- Estimated remaining capacity and health based on a charge-span-weighted median of qualifying charge sessions, design capacity entered by the user; estimated discharge time and approximate screen-state subsets.
- Opt-in periodic sampling and opt-in foreground monitoring, charge and temperature notifications, home-screen widget with temperature and estimated health when available.
- No network permissions, account, telemetry or ads.

## Next

- Validate readings and estimates on real devices from multiple vendors, including dual-cell batteries, fast charging, charging limits, sensor dropout, reboots and battery optimization. Compare with external instruments; record known limitations per device.
- Extend the existing instrumented database migration and sampling tests to cover background/foreground lifecycle, notifications and CSV export. Extend coverage of large forward clock changes and timezone changes. Clock rollback already preserves previous samples and splits sessions.
- Introduce session quality/confidence based on tested device behavior and multiple independent measurements. Explore counter drift and discontinuities; avoid inventing precision or a scientifically unvalidated wear score.
- Improve screen-on/off coverage without claiming transitions were observed when they weren't; test screen events during a user-enabled foreground session and measure missed events.
- Consider a separate opt-in usage-access flow for app foreground activity. Any per-app power attribution must be labeled as an estimate and should not imply access to measured per-app electrical consumption.
- Add accessible chart descriptions, larger font and small-screen testing, translations, and a richer tablet layout.
- Consider safe backup/import and richer export, configurable history retention, and more notification thresholds.
- Evaluate foreground service eligibility and Play policy before a Play release. The `specialUse` subtype is reviewed by Google and monitoring behavior needs a clear disclosure.
