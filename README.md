# ESP32 Flashing App

Android application for editing, building, flashing and monitoring ESP32 firmware.

**ESP-IDF builds. Android controls the hardware.**

Reference platform:
- Android 16 / arm64-v8a
- ESP-IDF 5.5
- Seeed Studio XIAO ESP32-S3

Product workflow:

`Install APK -> select ESP target -> automatic environment preparation -> Edit -> Build -> Flash -> Reset -> Serial Monitor`

The app embeds its Linux execution boundary and provisions a Debian 12 ARM64/glibc build environment in app-private storage. ESP-IDF/toolchains are versioned and provisioned on demand; no external terminal app is required.

See `PROJECT_GUIDELINES.md` for the current architecture and validated facts.
