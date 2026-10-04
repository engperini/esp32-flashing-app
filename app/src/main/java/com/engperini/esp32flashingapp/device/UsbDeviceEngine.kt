package com.engperini.esp32flashingapp.device
import android.content.Context
import android.hardware.usb.UsbManager
import com.engperini.esp32flashingapp.core.AppState
import com.engperini.esp32flashingapp.core.OperationState
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.delay
import java.io.IOException
class UsbDeviceEngine(context:Context,private val config:DeviceConfig=DeviceConfig()){
 private val usbManager=context.applicationContext.getSystemService(Context.USB_SERVICE) as UsbManager
 private val prober=UsbSerialProber.getDefaultProber()
 private var port:UsbSerialPort?=null
 private var connection:android.hardware.usb.UsbDeviceConnection?=null
 fun hasDevice()=prober.findAllDrivers(usbManager).isNotEmpty()\n fun isConnected()=port!=null
 fun connect():Boolean{
  val drv=prober.findAllDrivers(usbManager).firstOrNull()?:run{AppState.operation(OperationState.WAITING_DEVICE,"No supported ESP32 USB serial device");return false}
  if(!usbManager.hasPermission(drv.device)){AppState.operation(OperationState.USB_PERMISSION_ERROR,"USB permission required");return false}
  return try{val c=usbManager.openDevice(drv.device)?:return false;val p=drv.ports.firstOrNull()?:run{c.close();return false};p.open(c);p.setParameters(config.baudRate,8,UsbSerialPort.STOPBITS_1,UsbSerialPort.PARITY_NONE);connection=c;port=p;setBootReset(false,false);AppState.device(drv.javaClass.simpleName+" / "+drv.device.deviceName);AppState.operation(OperationState.IDLE,"USB connected");true}catch(e:Exception){disconnect();AppState.operation(OperationState.DEVICE_DISCONNECTED,"USB open failed: "+e.message);false}
 }
 fun disconnect(){runCatching{port?.close()};runCatching{connection?.close()};port=null;connection=null;AppState.device("No ESP32 connected")}
 suspend fun enterBootloader(){requirePort();AppState.operation(OperationState.ENTERING_BOOTLOADER,"Asserting BOOT + RESET");setBootReset(true,true);delay(config.resetPulseMs);setBootReset(true,false);delay(config.bootloaderHoldMs);setBootReset(false,false);AppState.operation(OperationState.BOOTLOADER_READY,"Bootloader ready")}
 suspend fun resetToApplication(){requirePort();AppState.operation(OperationState.RESETTING,"Resetting ESP32");setBootReset(false,true);delay(config.resetPulseMs);setBootReset(false,false);AppState.operation(OperationState.WAITING_APPLICATION,"Waiting for application serial")}
 fun write(data:ByteArray,timeoutMs:Int=1000):Int{requirePort().write(data,timeoutMs);return data.size}
 fun read(buffer:ByteArray,timeoutMs:Int=250)=requirePort().read(buffer,timeoutMs)
 private fun requirePort():UsbSerialPort=port?:throw IOException("USB device is not connected")
 private fun setBootReset(bootActive:Boolean,resetActive:Boolean){val p=requirePort();val lines=BootResetLineMapper.map(bootActive,resetActive,config);p.setDTR(lines.dtr);p.setRTS(lines.rts)}
}
