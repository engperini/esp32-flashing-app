package com.engperini.esp32flashingapp.core
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
enum class OperationState { IDLE, PREPARING_BUILD, BUILDING, BUILD_SUCCESS, WAITING_DEVICE, ENTERING_BOOTLOADER, BOOTLOADER_READY, FLASHING, VERIFYING, RESETTING, WAITING_APPLICATION, MONITORING, BUILD_ERROR, USB_PERMISSION_ERROR, DEVICE_DISCONNECTED, BOOTLOADER_ERROR, FLASH_ERROR, RESET_ERROR, MONITOR_ERROR }
data class AppUiState(val operation:OperationState=OperationState.IDLE,val deviceLabel:String="No ESP32 connected",val detail:String="Ready",val rxBytes:Long=0,val txBytes:Long=0,val serialText:String="")
object AppState {
 private const val MAX_SERIAL_CHARS=20000
 private val mutable=MutableStateFlow(AppUiState());val state:StateFlow<AppUiState> = mutable
 fun operation(value:OperationState,detail:String)=mutable.update{it.copy(operation=value,detail=detail)}
 fun device(label:String)=mutable.update{it.copy(deviceLabel=label)}
 fun counters(rx:Long,tx:Long)=mutable.update{it.copy(rxBytes=rx,txBytes=tx)}
 fun serial(text:String)=mutable.update{val combined=it.serialText+text;it.copy(serialText=if(combined.length>MAX_SERIAL_CHARS)combined.takeLast(MAX_SERIAL_CHARS) else combined)}
 fun clearSerial()=mutable.update{it.copy(serialText="")}
}