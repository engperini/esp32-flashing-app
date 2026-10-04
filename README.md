# ESP32 Flashing App

Android-first ESP32 development, build, flashing and serial-monitor application.

**ESP-IDF builds. Android controls the hardware.**

Initial target: Seeed Studio XIAO ESP32-S3 with ESP-IDF 5.5.

This project evolves the proven `engperini/esp-idf-android-bridge` prototype into an integrated workflow:

`Edit -> Build -> Flash -> Reset -> Serial Monitor`

Termux/Linux/ESP-IDF are backend infrastructure and must not be part of the normal user workflow.

See `PROJECT_GUIDELINES.md` for architecture, invariants, engineering decisions and acceptance criteria.
