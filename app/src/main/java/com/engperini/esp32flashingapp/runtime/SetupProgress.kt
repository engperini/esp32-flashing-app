package com.engperini.esp32flashingapp.runtime

enum class SetupStep(val label: String) {
    RUNTIME("Debian ARM64 runtime"),
    DEPENDENCIES("Build dependencies"),
    ESP_IDF("ESP-IDF 5.5"),
    TOOLCHAIN("ESP32-S3 toolchain"),
    READY("Ready")
}

data class SetupProgress(
    val step: SetupStep,
    val status: String,
    val log: String = "",
    val fraction: Float? = null,
    val completed: Boolean = false
)
