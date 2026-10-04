# Android runtime strategy

## Product contract

A clean compatible Android device must not require the user to manually install or configure Termux, Ubuntu/proot, Python, CMake, Ninja, ESP-IDF or esptool.

Normal first run is:

1. Install APK.
2. Grant Android permissions requested by the app.
3. App checks the environment.
4. App automatically downloads/prepares missing build components when required.
5. Connect ESP32.
6. Edit -> Build -> Flash -> Monitor.

The development phone's existing Termux/proot installation is a reference implementation and compatibility path, not a product prerequisite.

## Runtime boundary

The app talks to a Build Backend through structured requests/results. UI code must not construct shell commands.

The backend contract includes health check, environment preparation, build and clean operations. Results include exit code, stdout/stderr, artifacts and component health.

This boundary allows us to test three implementations without changing UX:

- CI/native Linux reference backend.
- Existing Termux/proot compatibility backend.
- Preferred app-managed Android runtime/toolchain.

## Provisioning constraints

ESP-IDF is too large to assume it belongs inside the base APK. Prefer a small APK plus first-run, versioned provisioning into app-controlled storage. Provisioned files must be integrity checked and the environment must be resumable/recoverable after interruption.

No silent fallback to asking the user to type terminal commands.

## Validation ladder

1. Pure JVM protocol/planning tests.
2. Android APK compilation/unit tests.
3. Official ESP-IDF 5.5 container builds.
4. Android/emulator provisioning tests where practical.
5. Termux/proot compatibility test.
6. Physical Android USB + XIAO ESP32-S3 hardware-in-the-loop.

The physical device is the final integration test, not the first debugging environment.
