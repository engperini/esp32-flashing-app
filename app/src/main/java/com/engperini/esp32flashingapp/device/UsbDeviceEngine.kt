package com.engperini.esp32flashingapp.device

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.engperini.esp32flashingapp.core.AppState
import com.engperini.esp32flashingapp.core.OperationState
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.*
import java.io.IOException

class UsbDeviceEngine(context:Context,private val config:DeviceConfig=DeviceConfig()){
 companion object { const val ACTION_USB_PERMISSION="com.engperini.esp32flashingapp.USB_PERMISSION" }
 private val appContext=context.applicationContext
 private val usbManager=appContext.getSystemService(Context.USB_SERVICE) as UsbManager
 private val prober=UsbSerialProber.getDefaultProber()
 private var port:UsbSerialPort?=null
 private var connection:android.hardware.usb.UsbDeviceConnection?=null
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
 private var monitorJob:Job?=null
 @Volatile private var transportOwned=false
 private var rxBytes=0L
 private var txBytes=0L
 fun hasDevice()=prober.findAllDrivers(usbManager).isNotEmpty()
 fun isConnected()=port!=null
 fun connect():Boolean{
  if(port!=null)return true
  val drv=prober.findAllDrivers(usbManager).firstOrNull()?:run{AppState.operation(OperationState.WAITING_DEVICE,"No supported ESP32 USB serial device");return false}
  if(!usbManager.hasPermission(drv.device)){requestUsbPermission(drv.device);AppState.operation(OperationState.WAITING_DEVICE,"USB permission requested");return false}
  return try{val c=usbManager.openDevice(drv.device)?:throw IOException("openDevice returned null");val p=drv.ports.firstOrNull()?:run{c.close();throw IOException("USB serial driver has no ports")};p.open(c);p.setParameters(config.baudRate,8,UsbSerialPort.STOPBITS_1,UsbSerialPort.PARITY_NONE);connection=c;port=p;setBootReset(false,false);AppState.device(drv.javaClass.simpleName+" / "+drv.device.deviceName);AppState.operation(OperationState.IDLE,"USB connected");startMonitor();true}catch(e:Exception){disconnect();AppState.operation(OperationState.DEVICE_DISCONNECTED,"USB open failed: "+e.message);false}
 }
 fun onPermissionResult(granted:Boolean){if(granted){AppState.operation(OperationState.IDLE,"USB permission granted");connect()}else AppState.operation(OperationState.USB_PERMISSION_ERROR,"USB permission denied")}
 fun disconnect(){monitorJob?.cancel();monitorJob=null;runCatching{port?.close()};runCatching{connection?.close()};port=null;connection=null;AppState.device("No ESP32 connected")}
 suspend fun enterBootloader(){requirePort();AppState.operation(OperationState.ENTERING_BOOTLOADER,"Asserting BOOT + RESET");setBootReset(true,true);delay(config.resetPulseMs);setBootReset(true,false);delay(config.bootloaderHoldMs);setBootReset(false,false);AppState.operation(OperationState.BOOTLOADER_READY,"Bootloader ready")}
 suspend fun resetToApplication(){requirePort();AppState.operation(OperationState.RESETTING,"Resetting ESP32");setBootReset(false,true);delay(config.resetPulseMs);setBootReset(false,false);AppState.operation(OperationState.WAITING_APPLICATION,"Waiting for application serial")}
 fun acquireTransport(){monitorJob?.cancel();monitorJob=null;transportOwned=true}
 fun releaseTransport(){transportOwned=false;startMonitor()}
 fun write(data:ByteArray,timeoutMs:Int=1000):Int{requirePort().write(data,timeoutMs);txBytes+=data.size;AppState.counters(rxBytes,txBytes);return data.size}
 private fun startMonitor(){monitorJob?.cancel();monitorJob=scope.launch{val buffer=ByteArray(4096);while(isActive&&port!=null&&!transportOwned){try{val n=read(buffer,250);if(n>0){rxBytes+=n;AppState.counters(rxBytes,txBytes);AppState.serial(buffer.copyOf(n).toString(Charsets.UTF_8));AppState.operation(OperationState.MONITORING,"Serial active")}}catch(e:IOException){if(isActive){AppState.operation(OperationState.MONITOR_ERROR,"Serial read failed: "+e.message)};break}}}}
 fun read(buffer:ByteArray,timeoutMs:Int=250)=requirePort().read(buffer,timeoutMs)
 private fun requestUsbPermission(device:UsbDevice){val pi=PendingIntent.getBroadcast(appContext,device.deviceId,Intent(ACTION_USB_PERMISSION).setPackage(appContext.packageName),PendingIntent.FLAG_IMMUTABLE);usbManager.requestPermission(device,pi)}
 private fun requirePort():UsbSerialPort=port?:throw IOException("USB device is not connected")
 private fun setBootReset(bootActive:Boolean,resetActive:Boolean){val p=requirePort();val lines=BootResetLineMapper.map(bootActive,resetActive,config);p.setDTR(lines.dtr);p.setRTS(lines.rts)}
}