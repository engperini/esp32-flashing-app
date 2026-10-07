# Improvements backlog

Only current, actionable work belongs here. Remove an item after it is implemented and physically/functionally validated. Each item must describe the current limitation and the expected result; implementation jargon alone is not sufficient.

## ESP-IDF setup and build

- **Cancel ESP-IDF Configure safely.** The Build operation already has a validated Cancel path, but Configure/Setup does not expose equivalent cancellation. Add cancellation for an active provisioning/download/install session without deleting stages that were already completed successfully. Starting Configure again must resume from the first incomplete stage.
- **Reduce Build startup overhead.** Every Build currently enters the ESP-IDF environment by sourcing `/opt/esp/idf/export.sh` before running `idf.py build`. Measure how much time this adds on Android and, only if material, replace it with a cached/precomputed environment that preserves the exact ESP-IDF 5.5 tool paths and Python environment.

## Build & Flash workflow

- **Make the Build & Flash button complete both operations automatically.** Today `ACTION_BUILD_FLASH` performs the Build and validates/saves its flash images, but then stops with “tap Flash to continue safely”; the user must press Flash separately. After a successful Build, the requested combined operation must automatically start the same already-validated native Flash flow. A failed or cancelled Build must never start Flash. Do not change the separately validated ESP32 and ESP32-S3 flash/reset sequences while implementing this.

## Project workflow

- **Support multiple projects instead of the single fixed Example project.** `ProjectManager` currently points permanently to `filesDir/projects/example`. Add create/select/rename/delete project operations, with each project keeping its own files and selected ESP target.
- **Complete file management in the Project explorer.** Creating files and folders is implemented; add rename and delete for project files/folders with the same path-containment protection already used by `safeFile()`.
- **Add project import/export.** Define a portable ESP-IDF project package that preserves source files and app project metadata, while excluding generated Build output and internal cached flash artifacts.

## Flash artifact safety and diagnostics

- **Guarantee that the previous validated firmware remains available if artifact promotion is interrupted.** After every successful Build, `FlashPlanLoader.promoteLastGood()` copies the required ESP-IDF flash files into `.last-good-flash.tmp` and validates the complete temporary set. It then deletes the previous `.last-good-flash` before renaming the temporary directory. If the process/device dies in that small delete→rename window, the previously validated firmware set can be lost. Replace it with a swap/backup strategy that always leaves either the old complete set or the new complete set recoverable. This cache exists so the Flash button uses a complete firmware set from a successful Build rather than partially generated files from a failed/interrupted Build.
- **Make Flash preflight errors identify the bad artifact.** `FlashPlanLoader` already validates `flasher_args.json`, target, image paths and file existence. Improve user-facing errors so a missing/corrupt cached image tells the user which file/set is invalid and whether rebuilding is the recovery action, instead of falling back to a generic Flash failure.

## Application and Serial Monitor state

- **Track Serial Monitor activity independently from the global operation status.** `AppState.operation` is shared by Build, Setup, Flash and Serial Monitor, while `UsbDeviceEngine.startMonitor()` writes `MONITORING` whenever serial data arrives. This allows unrelated operations and incoming serial data to overwrite each other’s displayed state. Keep an independent monitor-running/serial status so the UI can truthfully show “Serial active” while Build/Setup/other operations have their own status.
