package com.engperini.esp32flashingapp.project

data class ProjectConfig(val name: String, val target: String)

object SupportedTargets {
    val values = listOf("esp32s3", "esp32")
    fun label(target: String) = when (target) {
        "esp32s3" -> "ESP32-S3"
        "esp32" -> "ESP32"
        else -> target.uppercase()
    }
}
