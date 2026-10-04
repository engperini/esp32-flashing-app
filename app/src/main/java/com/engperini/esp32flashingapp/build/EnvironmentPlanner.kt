package com.engperini.esp32flashingapp.build

/**
 * Pure provisioning policy. Common build infrastructure is prepared once;
 * architecture toolchains are resolved lazily from the selected ESP target.
 */
object EnvironmentPlanner {
    val commonRequired = listOf(
        EnvironmentComponent.STORAGE,
        EnvironmentComponent.ESP_IDF,
        EnvironmentComponent.PYTHON,
        EnvironmentComponent.CMAKE,
        EnvironmentComponent.NINJA
    )

    fun requiredFor(target: String): List<EnvironmentComponent> = when (target.lowercase()) {
        "esp32s3" -> commonRequired + EnvironmentComponent.TOOLCHAIN
        else -> commonRequired + EnvironmentComponent.TOOLCHAIN
    }

    fun missing(checks: List<EnvironmentCheck>, target: String = "esp32s3"): List<EnvironmentComponent> {
        val ready = checks.filter { it.ready }.map { it.component }.toSet()
        return requiredFor(target).filterNot { it in ready }
    }

    fun isReady(checks: List<EnvironmentCheck>, target: String = "esp32s3"): Boolean =
        missing(checks, target).isEmpty()
}
