package com.engperini.esp32flashingapp.runtime

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

data class SetupUiState(
    val visible: Boolean = false,
    val current: SetupProgress = SetupProgress(SetupStep.RUNTIME, "Waiting"),
    val history: List<SetupProgress> = emptyList(),
    val error: String? = null
)

object SetupState {
    private val mutable = MutableStateFlow(SetupUiState())
    val state: StateFlow<SetupUiState> = mutable

    fun open() = mutable.update { it.copy(visible = true, error = null) }
    fun close() = mutable.update { it.copy(visible = false) }
    fun progress(value: SetupProgress) = mutable.update { old ->
        val history = if (value.completed) (old.history + value).takeLast(20) else old.history
        old.copy(visible = true, current = value, history = history, error = null)
    }
    fun success() = mutable.update { it.copy(error = null) }
    fun error(message: String) = mutable.update { it.copy(visible = true, error = message) }
}
