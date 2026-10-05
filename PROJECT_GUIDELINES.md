# ESP32 Flashing App — Engineering Baseline

This file records the current product architecture and facts that have been validated. Keep it concise; obsolete experiments and superseded approaches do not belong here.

## Product contract

Normal user workflow:

**Install APK -> select ESP target -> automatic environment preparation -> Edit -> Build -> Flash -> Reset -> Serial Monitor**

The user does not install or configure a terminal, Linux distribution, Python, CMake, Ninja, ESP-IDF or toolchain manually.

Reference platform: Android arm64-v8a, ESP-IDF 5.5, Seeed Studio XIAO ESP32-S3.

## Architecture

**ESP-IDF builds; Android controls the hardware.**

- UI / Project Engine: editor, project files and operation status.
- Build Engine: app-managed Linux runtime, ESP-IDF preparation, target toolchain, build and artifacts.
- Device Engine: Android USB Host, permissions, serial, DTR/RTS, boot/reset and reconnect.
- Flash Engine: consumes build artifacts, coordinates bootloader, flashing, verification and reset.
- Serial Monitor: Android-owned serial RX/TX, default 115200 baud.

Build and physical USB ownership remain separate.

## Validated runtime

Validated physically on the reference Samsung Android 16/aarch64 phone:

- Embedded PRoot 5.4.0-pr executes from the APK without root or an external terminal app.
- App-managed OCI provisioning works in app-private storage.
- Debian GNU/Linux 12 (bookworm) ARM64 rootfs provisions and executes successfully.
- Guest architecture reports aarch64 and uses glibc.
- Provisioned rootfs persists across app restarts.

Production build guest alias: `idf-base`.

ESP-IDF version: `v5.5`.

ESP-IDF and tools are stored in versioned paths under `/opt/esp` in the guest. Common dependencies are prepared once. Target-specific tools are installed only when that target is selected and are cached for reuse.

Provisioning stages are persistent and resumable. A stage is marked complete only after its success marker is observed.

## Validated device behavior

- Android USB serial connection to XIAO ESP32-S3 works.
- Native serial monitoring works.
- Android DTR/RTS bootloader sequence successfully enters ESP32-S3 ROM download mode.
- Deterministic reset back to the application still requires final validation.

The Device Engine owns reset/boot electrical control. Build Engine does not own USB.

## Operation states

`IDLE -> PREPARING_BUILD -> BUILDING -> BUILD_SUCCESS -> WAITING_DEVICE -> ENTERING_BOOTLOADER -> BOOTLOADER_READY -> FLASHING -> VERIFYING -> RESETTING -> WAITING_APPLICATION -> MONITORING`

Errors remain categorized as build, USB permission/device loss, bootloader, flash, reset and monitor errors.

## Security

- UI does not expose a generic shell executor.
- Backend operations are structured and restricted to known provisioning/build operations.
- Target names and project paths are treated as untrusted input.
- Request minimal Android permissions.
- Provisioned components must be versioned and integrity-verifiable.

## Current implementation direction

1. Complete app-managed ESP-IDF 5.5 common provisioning inside Debian ARM64/glibc.
2. Provision ESP32-S3 tools on target selection.
3. Build the bundled reference firmware and collect official ESP-IDF flash artifacts.
4. Implement exclusive Android flash transport handoff from Serial Monitor.
5. Validate deterministic post-flash reset and automatic monitor recovery.
6. Move provisioning, Build and Flash execution out of Activity lifecycle into durable foreground execution with visible Android progress notification; UI observes operation state.
7. Harden interruption/reconnect behavior.

## MVP acceptance

On a clean compatible Android device:

**install APK -> select ESP32-S3 -> automatic preparation -> edit main.c -> Build & Flash -> firmware starts -> serial output appears**

No external terminal or manually configured build environment.

Provisioning, Build and Flash must continue safely when the app UI goes to the background. They must not depend on an Activity `lifecycleScope`; long-running operations require foreground execution with a user-visible Android notification.
