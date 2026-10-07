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
- Classic ESP32 (physical hardware validation)

Validated on a physical Android phone:

- app-managed Debian ARM64/glibc runtime;
- ESP-IDF 5.5 and ESP32-S3 toolchain provisioning;
- ESP-IDF Doctor;
- complete and incremental ESP32-S3 firmware build, including background-safe foreground execution;
- native Android USB serial monitor at 115200 baud;
- native Android ESP32-S3 flash write and MD5 verification;
- reference firmware runs physically after flash: GPIO21 built-in LED heartbeat and one serial status line per second;
- automatic watchdog reset exits ESP32-S3 USB download mode after flash;
- automatic USB detection/reconnection continues after Android permission is granted;
- automatic ESP32-S3 ROM bootloader entry;
- classic ESP32 build, ROM bootloader entry, native flash write, MD5 verification and automatic RTS/EN hard reset;
- flash transport runs off the Android UI thread so progress remains responsive during USB writes;
- physically validated classic ESP32 firmware execution and Serial Monitor after automatic post-flash reset.

The end-to-end build, native flash, automatic post-flash boot, USB reconnection, firmware execution and serial monitor paths are physically validated on the reference hardware.

## Requirements

- **Android:** ARM64 (arm64-v8a); Android 16 is the currently validated reference platform. Broader Android-version compatibility will be declared only after physical validation.
- **Storage:** several GB of free internal storage are required for the managed Debian runtime, ESP-IDF 5.5, target toolchain and project builds. A precise minimum will be published after final packaging measurements; the app will gain a free-space preflight before setup.
- **Network:** internet access is required for the first ESP-IDF/target setup. Once provisioned, normal builds can run offline.
- **USB:** Android USB Host/OTG support plus a data-capable USB cable is required for Flash and Serial Monitor. Build itself does not require the board to be connected.
- **Targets:** ESP32-S3 and classic ESP32 are physically validated. Seeed Studio XIAO ESP32-S3 remains the S3 reference board.

## Download the Android app

### User: install the app

Download the APK from the **latest GitHub Release**:

https://github.com/engperini/esp32-flashing-app/releases/latest

Open the release, download `esp32-flashing-app.apk`, and install it on an ARM64 Android device. Android may ask you to allow installation from the browser or file manager used to open the APK.

Updates use a consistent public development signature, so a newer APK can normally be installed over the existing app. Do not uninstall between updates unless explicitly required: uninstalling also removes the app-managed ESP-IDF environment and project/build files.

GitHub Actions artifacts are CI outputs for developers and are **not** the normal application download channel.

### Developer: clone and reproduce

Clone the repository and build from source:

```bash
git clone https://github.com/engperini/esp32-flashing-app.git
cd esp32-flashing-app
```

The canonical CI definition is `.github/workflows/ci.yml`. It uses Java 17, Android SDK platform/build-tools 34, Gradle 8.11.1, builds the maintained `oonid/pr` Android PRoot engine, runs the Android unit tests, builds the APK, and verifies the reproducible public AOSP test-key signature. The same workflow also validates the ESP-IDF 5.5 reference firmware/toolchain path and the ARM64 runtime payload.

For an exact reproducible reference, use the versions and commands pinned in `.github/workflows/ci.yml` rather than relying on workstation-global Android/Gradle configuration.

Releases are produced by `.github/workflows/release.yml`. That workflow **does not rebuild the APK**: it downloads the APK artifact from a specified successful CI run and publishes that exact file as a GitHub Release asset.

## First setup

The first ESP-IDF configuration requires an internet connection and significantly more time and storage than later runs.

1. Open the app.
2. Select the ESP target: **ESP32-S3** or **ESP32**.
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
- **Doctor** — validate the installed ESP-IDF 5.5 environment and selected target compiler.
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
