package com.engperini.esp32flashingapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.engperini.esp32flashingapp.core.AppState
import com.engperini.esp32flashingapp.core.OperationState
import com.engperini.esp32flashingapp.device.UsbDeviceEngine
import kotlinx.coroutines.launch

class MainActivity:ComponentActivity(){
 private lateinit var device:UsbDeviceEngine
 private val usbPermissionReceiver=object:BroadcastReceiver(){
  override fun onReceive(context:Context,intent:Intent){
   if(intent.action==UsbDeviceEngine.ACTION_USB_PERMISSION) device.onPermissionResult(intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED,false))
  }
 }
 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState)
  device=UsbDeviceEngine(applicationContext)
  ContextCompat.registerReceiver(this,usbPermissionReceiver,IntentFilter(UsbDeviceEngine.ACTION_USB_PERMISSION),ContextCompat.RECEIVER_NOT_EXPORTED)
  setContent{MaterialTheme{
   val state by AppState.state.collectAsStateWithLifecycle()
   Scaffold{padding->Column(Modifier.fillMaxSize().padding(padding).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
    Text("ESP32 Flashing App",style=MaterialTheme.typography.headlineMedium)
    Text(state.deviceLabel);Text("ESP-IDF 5.5 • Target: esp32s3");Text("State: "+state.operation.name);Text(state.detail)
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick={device.connect()}){Text("Connect USB")};OutlinedButton(onClick={device.disconnect()}){Text("Disconnect")}}
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
     Button(enabled=device.isConnected(),onClick={lifecycleScope.launch{runCatching{device.enterBootloader()}.onFailure{AppState.operation(OperationState.BOOTLOADER_ERROR,it.message?:"Bootloader failed")}}}){Text("Bootloader")}
     Button(enabled=device.isConnected(),onClick={lifecycleScope.launch{runCatching{device.resetToApplication()}.onFailure{AppState.operation(OperationState.RESET_ERROR,it.message?:"Reset failed")}}}){Text("Reset")}
    }
    HorizontalDivider();Text("Hardware diagnostic: USB permission/connect, BOOT and RESET.")
   }}
  }}
 }
 override fun onDestroy(){runCatching{unregisterReceiver(usbPermissionReceiver)};device.disconnect();super.onDestroy()}
}