# Improvements backlog

Only current, actionable improvements are kept here. Validated architecture and product decisions remain in `PROJECT_GUIDELINES.md`.

## Background execution — MVP requirement

- Provisioning, Build and Flash must survive the app UI being sent to the background.
- Do not run long operations in an Activity `lifecycleScope`.
- Move long-running work to a foreground service/durable application-scoped operation engine with a user-visible Android progress notification.
- Reopening the app must reattach the UI to the active operation and its current log/progress rather than starting it again.
- Define safe cancellation and recovery semantics for interrupted operations.

## Build and ESP-IDF runtime

- Reduce Build startup latency caused by running the full ESP-IDF `export.sh` discovery on every build. Provision the environment once and reuse the required `IDF_PATH`, `IDF_TOOLS_PATH`, PATH and Python environment safely.
- Keep Build independent from USB connection state.
- Preserve the last known-good firmware artifacts when a later build fails. A failed/incomplete build must not destroy the only flashable artifact set.

## Flash artifact integrity

- Treat the ESP-IDF build output as one consistent artifact set.
- Use the official ESP-IDF 5.5 `build/flasher_args.json` as the structured flash manifest.
- Before taking USB ownership, validate the target and every binary referenced by `flash_files`.
- If an artifact is missing, report a specific preflight message such as `Build incomplete: bootloader.bin missing — run Build` instead of a generic Flash failure.
- Keep the actions distinct: `Build`, `Flash`, and `Build & Flash`.

## USB lifecycle

- Detect physical USB removal and transition to `DISCONNECTED` instead of leaving `MONITOR_ERROR` as the persistent application state.
- Stop the serial reader cleanly on USB removal.
- Disable Bootloader, Reset and Flash actions when no device is connected.
- USB disconnect/reconnect must not affect an ESP-IDF Build already running.

## Serial Monitor

- Replace the current RX summary-only area at the bottom of the main screen with a real receive monitor.
- Display incoming serial RX text in a scrollable read-only panel.
- Provide a user-selectable `Auto-scroll` option, enabled by default.
- Keep the RX byte counter and Clear action.
- No TX/input field yet; this first monitor iteration is RX-only.
- Preserve received output while the monitor is connected, with a bounded in-memory buffer to avoid unbounded growth.
- Pause USB monitoring only while the flash transport has exclusive ownership, then resume it after flashing.
