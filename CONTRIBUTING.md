# Contributing

Voltra is an Android app written in Kotlin and Jetpack Compose. Install Android SDK 36.1 and JDK 17 or later. Run:

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest
```

With an emulator or device connected, run `./gradlew :app:connectedDebugAndroidTest`. Instrumented history tests use a separate temporary database and must not touch user history. CI builds the debug app and both test APKs; it does not currently run an emulator.

Battery measurements vary by device. Explain the source and units of new values, represent unavailable sensors as unknown, and label estimates and gaps. Keep monitoring opt-in where it runs in the background, avoid wake locks unless there is a documented need, and test changes to background work on real devices as well as an emulator. Do not add accounts, telemetry or network access without a product decision. Record significant battery API and estimation findings in `docs/RESEARCH.md`.
