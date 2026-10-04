# Implementation Plan

All implementation commits go directly to `main`.

## Phase 1 — foundation
- Android/Kotlin project skeleton.
- Explicit operation state machine.
- Device Engine separated from UI, TCP and backend.
- Preserve proven XIAO ESP32-S3 DTR/RTS mapping and timing.
- Flash Coordinator owns bootloader-to-reset hardware handoff.
- Structured flash artifact model/parser.

## Phase 2 — hardware pipeline
1. USB permission/attach/detach lifecycle.
2. Binary transport based on the proven bridge.
3. Keep 6667/6668 compatibility for diagnostics.
4. Direct esptool with `--before no_reset --after no_reset`.
5. Deterministic post-flash reset and automatic serial-monitor transition.
6. Repeat application -> bootloader -> flash -> reset -> application -> serial.

## Phase 3 — Build Engine
Backend health check, ESP-IDF 5.5, project sync, idf.py build, artifact collection, flash metadata parsing and structured protocol. No terminal interaction in normal UX.

## Phase 4 — product UI
Project tree/editor, Build/Flash/Build & Flash/Monitor, unified console, device/toolchain status and actionable errors.

## Phase 5 — hardening
Cable removal, denied USB, broken build, large firmware, bootloader pre-state, background/suspend, backend restart and USB reconnect.
