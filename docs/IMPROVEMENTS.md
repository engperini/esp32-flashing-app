# Improvements backlog

Only current, actionable improvements are kept here. Remove an item when it is implemented and validated. Validated architecture and product decisions belong in `PROJECT_GUIDELINES.md`.

## ESP-IDF operations

- Add safe cancellation to **Configure ESP-IDF**. Cancellation must stop the active provisioning/download/install session, preserve already completed stages, and allow Configure to resume safely.
- Reduce Build startup latency caused by running the full ESP-IDF `export.sh` discovery on every build. Provision the environment once and reuse the required `IDF_PATH`, `IDF_TOOLS_PATH`, PATH and Python environment safely.
- Keep the Build cancellation path observable: cancelling must stop the active PRoot/Ninja process tree, preserve partial build output, and allow the next Build to resume incrementally.

## Project workflow

- Complete real multi-project management: create, select, rename and delete projects while keeping each project's target and files isolated.
- Add rename and delete operations to the Project file explorer.
- Add project import/export using a portable ESP-IDF project format.

## Build & Flash

- Make **Build & Flash** a true chained operation: Build must complete successfully, promote the new last-good artifact set, then start native Flash automatically. A failed or cancelled Build must never start Flash.
- Keep target-specific native Flash/reset behavior isolated. ESP32-S3 is the validated baseline; ESP32 classic must be physically validated before being marked supported for Flash.

## Flash artifact integrity

- Make replacement of the last-good flash artifact set atomic so an interrupted promotion cannot leave a partially replaced set.
- Extend flash preflight diagnostics with specific missing/corrupt artifact messages where the current validation still falls back to a generic failure.

## Application state

- Separate Serial Monitor state from the general operation state so Build/Setup status cannot make an active monitor appear stopped in the UI.
