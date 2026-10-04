package com.engperini.esp32flashingapp.flash

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class FlashUiState(
    val visible:Boolean=false,
    val status:String="",
    val log:String="",
    val completed:Boolean=false,
    val error:String?=null
)
object FlashState {
    private val mutable=MutableStateFlow(FlashUiState())
    val state=mutable.asStateFlow()
    fun open(){ mutable.value=FlashUiState(true,"Preparing flash transport…") }
    fun status(value:String){ mutable.value=mutable.value.copy(status=value,log=(mutable.value.log+"\n"+value).trim()) }
    fun success(value:String){ mutable.value=mutable.value.copy(status=value,log=(mutable.value.log+"\n"+value).trim(),completed=true) }
    fun error(value:String){ mutable.value=mutable.value.copy(status="Flash stopped",log=(mutable.value.log+"\n"+value).trim(),error=value) }
    fun close(){ mutable.value=FlashUiState() }
}
