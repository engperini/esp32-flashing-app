package com.engperini.esp32flashingapp.build

enum class BuildOperation { HEALTH_CHECK, PREPARE_ENVIRONMENT, BUILD, CLEAN }
enum class EnvironmentComponent { STORAGE, TOOLCHAIN, ESP_IDF, PYTHON, CMAKE, NINJA }

data class BuildRequest(
    val id: String,
    val operation: BuildOperation,
    val projectPath: String? = null,
    val target: String = "esp32s3"
)

data class EnvironmentCheck(
    val component: EnvironmentComponent,
    val ready: Boolean,
    val version: String? = null,
    val detail: String? = null
)

data class BuildResult(
    val id: String,
    val success: Boolean,
    val exitCode: Int,
    val stdout: String = "",
    val stderr: String = "",
    val artifacts: List<String> = emptyList(),
    val checks: List<EnvironmentCheck> = emptyList()
)
