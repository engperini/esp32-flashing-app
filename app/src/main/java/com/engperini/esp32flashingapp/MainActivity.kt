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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.engperini.esp32flashingapp.build.TermuxBuildBackend
import com.engperini.esp32flashingapp.core.AppState
import com.engperini.esp32flashingapp.core.OperationState
import com.engperini.esp32flashingapp.device.UsbDeviceEngine
import com.engperini.esp32flashingapp.project.ProjectManager
import kotlinx.coroutines.launch

class MainActivity:ComponentActivity(){
 private lateinit var device:UsbDeviceEngine
 private lateinit var projects:ProjectManager
 private lateinit var buildBackend:TermuxBuildBackend
 private val usbPermissionReceiver=object:BroadcastReceiver(){
  override fun onReceive(context:Context,intent:Intent){
   if(intent.action==UsbDeviceEngine.ACTION_USB_PERMISSION) device.onPermissionResult(intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED,false))
  }
 }
 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState)
  device=UsbDeviceEngine(applicationContext)
  projects=ProjectManager(applicationContext);projects.ensureExampleProject()
  buildBackend=TermuxBuildBackend(applicationContext)
  ContextCompat.registerReceiver(this,usbPermissionReceiver,IntentFilter(UsbDeviceEngine.ACTION_USB_PERMISSION),ContextCompat.RECEIVER_NOT_EXPORTED)
  setContent{MaterialTheme{
   val state by AppState.state.collectAsStateWithLifecycle()
   var source by remember { mutableStateOf(projects.loadMain()) }
   Scaffold{padding->Column(Modifier.fillMaxSize().padding(padding).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
    Text("ESP32 Flashing App",style=MaterialTheme.typography.headlineMedium)
    Text(state.deviceLabel);Text("ESP-IDF 5.5 • Target: esp32s3");Text("State: "+state.operation.name);Text(state.detail)
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick={device.connect()}){Text("Connect USB")};OutlinedButton(onClick={device.disconnect()}){Text("Disconnect")}}
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
     Button(enabled=device.isConnected(),onClick={lifecycleScope.launch{runCatching{device.enterBootloader()}.onFailure{AppState.operation(OperationState.BOOTLOADER_ERROR,it.message?:"Bootloader failed")}}}){Text("Bootloader")}
     Button(enabled=device.isConnected(),onClick={lifecycleScope.launch{runCatching{device.resetToApplication()}.onFailure{AppState.operation(OperationState.RESET_ERROR,it.message?:"Reset failed")}}}){Text("Reset")}
    }
    HorizontalDivider();Text("Editor • main/main.c",style=MaterialTheme.typography.titleMedium);OutlinedTextField(value=source,onValueChange={source=it},modifier=Modifier.fillMaxWidth().height(220.dp),textStyle=LocalTextStyle.current.copy(fontFamily=FontFamily.Monospace),label={Text("ESP-IDF source")});Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick={projects.saveMain(source);AppState.operation(OperationState.IDLE,"main.c saved")}){Text("Save")};Button(onClick={startBuild(source)}){Text("Build")};Button(onClick={AppState.operation(OperationState.PREPARING_BUILD,"Build first; automatic flash will follow after successful artifact validation");startBuild(source)}){Text("Build & Flash")}};HorizontalDivider();Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("Serial Monitor • 115200 • RX ${state.rxBytes} bytes");TextButton(onClick={AppState.clearSerial()}){Text("Clear")}};Surface(Modifier.fillMaxWidth().height(280.dp),tonalElevation=2.dp){Text(if(state.serialText.isEmpty())"Waiting for serial data…" else state.serialText,Modifier.padding(10.dp).verticalScroll(rememberScrollState()),style=MaterialTheme.typography.bodySmall)}
   }}
  }}
 }

 private fun startBuild(source:String){
  projects.saveMain(source)
  if(ContextCompat.checkSelfPermission(this,"com.termux.permission.RUN_COMMAND")!=PackageManager.PERMISSION_GRANTED){
   AppState.operation(OperationState.BUILD_ERROR,"Grant 'Run commands in Termux environment' in App info > Permissions > Additional permissions, then return and tap Build.")
   runCatching{startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+packageName)))}
   return
  }
  buildBackend.build(source)
 }
 override fun onDestroy(){runCatching{unregisterReceiver(usbPermissionReceiver)};device.disconnect();super.onDestroy()}
}