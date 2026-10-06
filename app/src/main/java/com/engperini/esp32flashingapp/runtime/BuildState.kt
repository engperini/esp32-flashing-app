package com.engperini.esp32flashingapp.runtime

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class BuildUiState(
    val visible: Boolean = false,
    val title: String = "ESP-IDF Build",
    val status: String = "Waiting",
    val log: String = "",
    val error: String? = null,
    val completed: Boolean = false
)

object BuildState {
    private val mutable = MutableStateFlow(BuildUiState())
    val state: StateFlow<BuildUiState> = mutable

    fun open(title: String = "ESP-IDF Build", status: String = "Starting ESP-IDF build…") { mutable.value = BuildUiState(visible = true, title = title, status = status) }
    fun output(log: String, status: String? = null) { mutable.value = mutable.value.copy(visible = true, status = status ?: mutable.value.status, log = log.takeLast(8000)) }
    fun cancelling() { mutable.value = mutable.value.copy(status = "Cancelling Build…") }
    fun cancelled() { mutable.value = mutable.value.copy(status = "Build cancelled", completed = true, error = null) }
    fun success(log: String, status: String = "Build completed") { mutable.value = mutable.value.copy(visible = true, status = status, log = log.takeLast(8000), completed = true) }
    fun error(message: String) { mutable.value = mutable.value.copy(visible = true, status = "Build failed", error = message) }
    fun close() { mutable.value = mutable.value.copy(visible = false) }
}
