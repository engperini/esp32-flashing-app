package com.engperini.esp32flashingapp.runtime

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class BuildUiState(
    val visible: Boolean = false,
    val status: String = "Waiting",
    val log: String = "",
    val error: String? = null,
    val completed: Boolean = false
)

object BuildState {
    private val mutable = MutableStateFlow(BuildUiState())
    val state: StateFlow<BuildUiState> = mutable

    fun open() { mutable.value = BuildUiState(visible = true, status = "Starting ESP-IDF build…") }
    fun output(log: String) { mutable.value = mutable.value.copy(visible = true, status = "Compiling firmware…", log = log.takeLast(8000)) }
    fun cancelling() { mutable.value = mutable.value.copy(status = "Cancelling Build…") }
    fun cancelled() { mutable.value = mutable.value.copy(status = "Build cancelled", completed = true, error = null) }
    fun success(log: String) { mutable.value = mutable.value.copy(visible = true, status = "Build completed", log = log.takeLast(8000), completed = true) }
    fun error(message: String) { mutable.value = mutable.value.copy(visible = true, status = "Build failed", error = message) }
    fun close() { mutable.value = mutable.value.copy(visible = false) }
}
