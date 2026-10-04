package com.engperini.esp32flashingapp

import android.os.Bundle\nimport android.content.BroadcastReceiver\nimport android.content.Context\nimport android.content.Intent\nimport android.content.IntentFilter\nimport android.hardware.usb.UsbManager\nimport androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.engperini.esp32flashingapp.core.AppState
import com.engperini.esp32flashingapp.device.UsbDeviceEngine
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
 private lateinit var device: UsbDeviceEngine
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  device=UsbDeviceEngine(applicationContext)\n  ContextCompat.registerReceiver(this,usbPermissionReceiver,IntentFilter(UsbDeviceEngine.ACTION_USB_PERMISSION),ContextCompat.RECEIVER_NOT_EXPORTED)
  setContent {
   MaterialTheme {
    val state by AppState.state.collectAsStateWithLifecycle()
    Scaffold { padding ->
     Column(Modifier.fillMaxSize().padding(padding).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
      Text("ESP32 Flashing App",style=MaterialTheme.typography.headlineMedium)
      Text(state.deviceLabel)
      Text("ESP-IDF 5.5 • Target: esp32s3")
      Text("State: "+state.operation.name)
      Text(state.detail)
      Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
       Button(onClick={ if(device.connect()) Unit else Unit }){Text("Connect USB")}
       OutlinedButton(onClick={device.disconnect()}){Text("Disconnect")}
      }
      Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
       Button(enabled=device.isConnected(),onClick={lifecycleScope.launch{runCatching{device.enterBootloader()}.onFailure{AppState.operation(com.engperini.esp32flashingapp.core.OperationState.BOOTLOADER_ERROR,it.message?:"Bootloader failed")}}}){Text("Bootloader")}
       Button(enabled=device.isConnected(),onClick={lifecycleScope.launch{runCatching{device.resetToApplication()}.onFailure{AppState.operation(com.engperini.esp32flashingapp.core.OperationState.RESET_ERROR,it.message?:"Reset failed")}}}){Text("Reset")}
      }
      HorizontalDivider()
      Text("Hardware diagnostic build: USB permission/connect, BOOT and RESET are active. Build/Flash remain disabled until the transport is validated.")
     }
    }
   }
  }
 }
}