# ESP32 Flashing App — Project Guidelines

This is the living engineering record for the project. Do not silently erase previous decisions. If a decision changes, mark it SUPERSEDED and add a new decision with the reason.

## Product goal

Deliver a professional Android application where the normal ESP32 workflow is:

**Edit code -> Build -> Flash -> Reset -> Serial Monitor**

The user must not need to open Termux/Linux, type shell commands, understand idf.py/esptool/CMake/Ninja/Python/TCP ports, or manually manipulate BOOT/RESET/DTR/RTS.

Initial reference platform:
- Android / Kotlin
- ESP-IDF 5.5
- Seeed Studio XIAO ESP32-S3
- Extensible to additional ESP32 boards later

## Core architecture

Android App
- UI / editor
- Project Engine
- Build Engine -> hidden Termux/Linux/ESP-IDF backend
- Device Engine -> Android USB Host
- Flash Engine
- Serial Monitor

Fundamental rule: **ESP-IDF builds; Android controls the hardware.**

### Build Engine

Responsible for environment preparation, project synchronization, target configuration, idf.py build, artifact collection, flash metadata parsing and structured build results.

It does not own the physical USB connection or electrical reset/boot control.

### Device Engine

Responsible for USB attach/detach, permission, VID/PID/driver detection, connection lifecycle, DTR/RTS, bootloader/reset sequences, reconnect and device-loss handling.

### Flash Engine

Consumes official ESP-IDF build artifacts and flash metadata. It coordinates Device Engine bootloader entry, flashing, verification, deterministic post-flash reset and handoff to Serial Monitor.

The validated prototype showed that Linux-side automatic serial reset is not reliable through a PTY/TCP bridge. Direct esptool operations therefore use no-reset semantics when Android owns reset/boot:
`python -m esptool ... --before no_reset --after no_reset write_flash @flash_args`

Do not use `idf.py flash` as the preferred flashing path through a virtual PTY when it expects real UART ioctl/reset behavior.

### Serial Monitor

Default 115200 baud. Support RX/TX, pause/resume, clear, autoscroll and later timestamps/export. It must survive the flash -> reset -> application transition.

## Proven prototype knowledge

Baseline repository: `engperini/esp-idf-android-bridge`.

Proven components that should be reused or evolved rather than rediscovered:
- Android owns USB serial.
- usb-serial-for-android support for CDC ACM, CH340, CP210x and FTDI.
- Binary-safe localhost transport on TCP 6667.
- Separate control service on TCP 6668.
- Control commands include DTR, RTS, RESET, BOOTLOADER and STATUS.
- Foreground service for long-running device work.
- Configurable DTR/RTS inversion and swap.
- Prototype defaults include swap DTR/RTS=true, bootloader timing 250 ms and reset pulse 120 ms.
- Build works in Ubuntu/proot/ESP-IDF.
- Serial monitoring works.
- Bootloader entry works.
- Direct esptool communication works.

The old socat + pseudo-TTY layer is a compatibility mechanism, not a product requirement. Prefer removing unnecessary PTY dependence from the final architecture where technically practical.

## State machine

IDLE
-> PREPARING_BUILD
-> BUILDING
-> BUILD_SUCCESS
-> WAITING_DEVICE
-> ENTERING_BOOTLOADER
-> BOOTLOADER_READY
-> FLASHING
-> VERIFYING
-> RESETTING
-> WAITING_APPLICATION
-> MONITORING

Error states:
BUILD_ERROR
USB_PERMISSION_ERROR
DEVICE_DISCONNECTED
BOOTLOADER_ERROR
FLASH_ERROR
RESET_ERROR
MONITOR_ERROR

State transitions must be explicit and observable. Do not infer critical hardware state only from UI state.

## Reliability target

The primary engineering target is a repeatable cycle:

**application -> bootloader -> flash -> reset -> application -> serial**

Test normal repetition plus cable removal during flash, denied USB permission, broken builds, large firmware, board already in bootloader, app backgrounding, screen suspend, backend death/restart and USB reconnect.

Build/flash operations must not depend on Activity lifetime.

## App/backend protocol

Prefer structured messages over parsing human terminal text. Include at least:
- command/request ID
- operation
- state
- progress
- stdout/stderr
- exit code
- artifact paths
- error category

JSON is acceptable.

## UX

Main experience should expose project/file tree, editor, consolidated console, device status, Build, Flash, Build & Flash and Monitor.

Example status:
- XIAO ESP32-S3 connected
- ESP-IDF 5.5
- Target: esp32s3
- Building
- Flashing %
- Resetting
- Serial connected 115200

Editor MVP: C/C++, headers, CMake, JSON/YAML/text, line numbers, syntax highlighting, search/replace, undo/redo, tabs, modified marker and clickable compiler errors.

Do not build a complete IDE before the hardware/build/flash/monitor cycle is reliable.

## Security

Treat project paths and filenames as untrusted. Avoid unsafe shell concatenation. Restrict backend command surface. Do not log secrets. Request minimal Android permissions.

## MVP acceptance

Install/prepare app -> connect XIAO ESP32-S3 -> open project -> edit main.c/main.cpp -> tap Build & Flash -> firmware starts -> serial output appears, without manual Termux or terminal interaction.

## Decision log

### D-001 — ACTIVE — 2026-10-03
Android owns the physical USB connection.
Reason: Android USB Host is the reliable hardware boundary.

### D-002 — SUPERSEDED by D-014 — 2026-10-03
Termux/Linux was initially treated as hidden backend infrastructure.
Reason for supersession: a clean Android install must not require Termux; it remains only a compatibility/reference backend.

### D-003 — ACTIVE — 2026-10-03
Use official ESP-IDF for builds.
Reason: preserve compatibility with normal ESP-IDF projects and artifacts.

### D-004 — ACTIVE — 2026-10-03
Build and flash are separate engines/responsibilities.
Reason: compilation does not require hardware and USB control must remain Android-owned.

### D-005 — ACTIVE — 2026-10-03
Preserve the validated 6667 binary bridge and 6668 control channel as compatibility/debug infrastructure while the integrated architecture evolves.
Reason: both paths are proven useful and provide a fallback during migration.

### D-006 — ACTIVE — 2026-10-03
XIAO ESP32-S3 is the first supported board.
Reason: current hardware target and validated prototype.

### D-007 — ACTIVE — 2026-10-03
Post-flash reset belongs to Device/Flash Engine.
Reason: Linux/esptool cannot safely assume ownership of reset through the virtual transport.

### D-008 — ACTIVE — 2026-10-03
Use an explicit operation state machine.
Reason: build, USB, bootloader, flash, reset and monitor failures must be distinguishable and recoverable.

### D-009 — ACTIVE — 2026-10-03
This document is mandatory living project memory.
Reason: future implementation sessions must recover architecture and decisions from the repository itself.

### D-010 — ACTIVE — 2026-10-03
ESP-IDF 5.5 is the reference toolchain.
Reason: this is the validated target version for the project.

### D-011 — ACTIVE — 2026-10-03
Official repository is `engperini/esp32-flashing-app`, default branch `main`.

### D-012 — ACTIVE — 2026-10-04
`engperini/esp-idf-android-bridge` is the code-level baseline for proven USB/bridge/reset behavior.
Reason: repository access was restored and the implementation was inspected directly. Reuse proven behavior but improve architecture rather than copying limitations.

### D-013 — ACTIVE — 2026-10-04
socat/PTY is compatibility infrastructure, not the desired final hardware abstraction.
Reason: it exists to adapt Linux serial assumptions to TCP and causes ioctl/reset limitations. The new design should minimize this boundary while retaining a fallback until the replacement is proven.


### D-014 — ACTIVE — 2026-10-04
A clean compatible Android device must not require a preinstalled or manually configured Termux/proot/toolchain.
Reason: the product contract is APK -> permissions -> automatic environment preparation -> ESP32 workflow. Existing Termux/proot remains a development/reference compatibility path, not an end-user prerequisite.

### D-015 — ACTIVE — 2026-10-04
UI communicates with the Build Backend through a structured contract and must not construct shell commands.
Reason: this lets the current Termux/proot implementation and a preferred app-managed runtime be tested/replaced without changing product UX.

### D-016 — ACTIVE — 2026-10-04
Prefer versioned first-run provisioning of the large ESP-IDF/toolchain into app-controlled storage instead of embedding the full toolchain in the base APK.
Reason: keep installation practical while preserving automatic setup, integrity checking, resumability and eventual offline builds.


### D-017 — ACTIVE — 2026-10-04
Downloaded executables in app-private writable storage are not a valid Android runtime strategy.
Reason: Android 10+ blocks direct execve of executable code from the writable app home. Runtime launcher/interpreter code must be shipped as APK native code; downloaded ESP-IDF/toolchain/rootfs content is treated as data behind that packaged execution boundary.

### D-018 — ACTIVE — 2026-10-04
The app-managed build-runtime proof must target arm64-v8a first and validate on Android 16 before replacing the compatibility backend.
Reason: the XIAO test phone is Android 16/aarch64, while Espressif publishes an official Linux ARM64 toolchain. The proof must establish the Linux/glibc compatibility boundary and must not assume Linux binaries execute directly under Android/bionic.
