package com.engperini.esp32flashingapp.flash
import com.engperini.esp32flashingapp.core.AppState
import com.engperini.esp32flashingapp.core.OperationState
import com.engperini.esp32flashingapp.device.UsbDeviceEngine
class FlashCoordinator(private val device:UsbDeviceEngine){
 suspend fun prepareForFlash(){device.enterBootloader();AppState.operation(OperationState.BOOTLOADER_READY,"Ready for esptool transport")}
 suspend fun finishSuccessfulFlash(){AppState.operation(OperationState.VERIFYING,"Flash completed; handing hardware back to Android");device.resetToApplication()}
}
