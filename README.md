# ESP32 Flashing App

An open-source Android app for editing, building, flashing and monitoring ESP32 firmware directly from a phone.

> **ESP-IDF builds the firmware. Android controls the hardware.**

## Goal

The intended user experience is simple:

```text
Install APK
   ↓
Select ESP target
   ↓
Configure ESP-IDF once
   ↓
Edit firmware
   ↓
Build / Flash / Build & Flash
   ↓
Serial Monitor
```

The user does **not** need Termux, Ubuntu, a terminal, Python, CMake, Ninja, esptool, or a separately installed ESP-IDF environment. The app manages its own build environment in Android app-private storage.

## Current status

Reference platform:

- Android 16 / arm64-v8a
- ESP-IDF 5.5
- Seeed Studio XIAO ESP32-S3

Validated on a physical Android phone:

- app-managed Debian ARM64/glibc runtime;
- ESP-IDF 5.5 and ESP32-S3 toolchain provisioning;
- ESP-IDF Doctor;
- complete ESP32-S3 firmware build;
- native Android USB serial monitor;
- automatic ESP32-S3 ROM bootloader entry.

Native Android flashing is currently under active development and physical validation. Do not treat the project as a finished production flasher yet.

## Requirements

- **Android:** ARM64 (arm64-v8a); Android 16 is the currently validated reference platform. Broader Android-version compatibility will be declared only after physical validation.
- **Storage:** several GB of free internal storage are required for the managed Debian runtime, ESP-IDF 5.5, target toolchain and project builds. A precise minimum will be published after final packaging measurements; the app will gain a free-space preflight before setup.
- **Network:** internet access is required for the first ESP-IDF/target setup. Once provisioned, normal builds can run offline.
- **USB:** Android USB Host/OTG support plus a data-capable USB cable is required for Flash and Serial Monitor. Build itself does not require the board to be connected.
- **Target:** ESP32-S3 is the current validated target; Seeed Studio XIAO ESP32-S3 is the physical reference board.

## Download the Android app

### Final/public distribution

The finished MVP will be published as a versioned **GitHub Release with the APK attached directly to the release**. Normal users will not need to browse GitHub Actions. The Releases page will be the canonical open-source download location.

### Current development builds

Until the first public Release, development APKs are produced automatically by GitHub Actions.

1. Open the repository **Actions** tab.
2. Open the latest successful CI run on the `main` branch.
3. In **Artifacts**, download `esp32-flashing-app-public-debug-apk`.
4. Extract the downloaded archive and install the APK on an ARM64 Android device.
5. Android may ask you to allow installation from the browser/file manager used to open the APK.

Repository Actions:

https://github.com/engperini/esp32-flashing-app/actions

Updates from this development channel are signed consistently, so a newer APK can normally be installed **over the existing app**. Do not uninstall between updates unless explicitly required: uninstalling also removes the app-managed ESP-IDF environment and build files.

A proper GitHub Release APK is planned; the Actions artifact is the current development distribution method.

## First setup

The first ESP-IDF configuration requires an internet connection and significantly more time and storage than later runs.

1. Open the app.
2. Select the ESP target. The current reference target is **ESP32-S3**.
3. Tap **Configure ESP-IDF**.
4. Keep the setup screen open while the current development build prepares its private Linux environment, ESP-IDF 5.5 and the target toolchain. Background-safe execution is a required MVP item and is not complete yet.
5. Follow the live setup status and log until the environment reports ready.
6. Optionally run **Doctor** to validate the installed toolchain.

The environment is cached in app-private storage. Normal later builds do not download ESP-IDF again and can work offline once all required components are installed.

## Using the app

Connect the ESP32 by USB and grant Android USB permission when requested.

- **Build** — compile the current project and generate the firmware binaries. USB is not required.
- **Flash** — flash an already completed and validated build without compiling it again.
- **Build & Flash** — build the current source first and flash only after a successful build.
- **Doctor** — validate the installed ESP-IDF 5.5 environment and ESP32-S3 compiler.
- **Configure ESP-IDF** — install or prepare the managed build environment.

The Serial Monitor uses Android's native USB connection. Build execution and USB transport are intentionally separate.

## Architecture

```text
Android UI / Project
        │
        ├── Build ──> app-managed Debian ARM64 ──> ESP-IDF 5.5
        │
        └── USB ───> Android native USB ─────────> ESP32
```

The embedded Linux environment exists only to run the official Linux ESP-IDF toolchain. USB ownership remains native to Android; the app does not expose the ESP32 as a Linux `/dev/tty*` device.

No external terminal application is part of the product architecture.

## Development

The current engineering baseline and validated architecture are documented in [PROJECT_GUIDELINES.md](PROJECT_GUIDELINES.md).

Current actionable improvements are tracked in [docs/IMPROVEMENTS.md](docs/IMPROVEMENTS.md).

Contributions should preserve the product contract: a normal user should never have to manually configure Linux, a shell, Python, ESP-IDF, CMake, Ninja, or USB forwarding in order to build and flash an ESP32 from Android.
