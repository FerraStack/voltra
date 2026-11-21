# Contributing

Voltra is an Android app written in Kotlin and Jetpack Compose. Install Android SDK 36.1 and JDK 17 or later. Run:

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
```

To verify the R8-minified release build locally using the debug signing key:

```sh
./gradlew :app:assembleRelease :app:bundleRelease -Pvoltra.signReleaseWithDebugKey=true
```

With an emulator or device connected, run `./gradlew :app:connectedDebugAndroidTest`. Instrumented history tests use a separate temporary database and must not touch user history.

Battery measurements vary by device. Explain the source and units of new values, represent unavailable sensors as unknown, and label estimates and gaps. Keep monitoring opt-in where it runs in the background, avoid wake locks unless there is a documented need, and test changes to background work on real devices as well as an emulator. Do not add accounts, telemetry, or network access without a product decision. Record significant battery API and estimation findings in `docs/RESEARCH.md`.
