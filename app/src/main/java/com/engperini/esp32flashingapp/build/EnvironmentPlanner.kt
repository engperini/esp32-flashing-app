package com.engperini.esp32flashingapp.build

/**
 * Pure decision layer for first-run provisioning. It deliberately knows nothing
 * about Termux: a backend implementation may change without changing product UX.
 */
object EnvironmentPlanner {
    val required = listOf(
        EnvironmentComponent.STORAGE,
        EnvironmentComponent.TOOLCHAIN,
        EnvironmentComponent.ESP_IDF,
        EnvironmentComponent.PYTHON,
        EnvironmentComponent.CMAKE,
        EnvironmentComponent.NINJA
    )

    fun missing(checks: List<EnvironmentCheck>): List<EnvironmentComponent> {
        val ready = checks.filter { it.ready }.map { it.component }.toSet()
        return required.filterNot { it in ready }
    }

    fun isReady(checks: List<EnvironmentCheck>): Boolean = missing(checks).isEmpty()
}
