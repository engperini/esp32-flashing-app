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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.engperini.esp32flashingapp.core.AppState
import com.engperini.esp32flashingapp.core.OperationState
import com.engperini.esp32flashingapp.device.UsbDeviceEngine
import com.engperini.esp32flashingapp.project.ProjectManager
import com.engperini.esp32flashingapp.runtime.IdfBuildExecutor
import com.engperini.esp32flashingapp.runtime.SetupState
import com.engperini.esp32flashingapp.runtime.BuildState
import com.engperini.esp32flashingapp.runtime.IdfDoctor
import kotlinx.coroutines.launch

class MainActivity:ComponentActivity(){
 private lateinit var device:UsbDeviceEngine
 private lateinit var projects:ProjectManager
 private val usbPermissionReceiver=object:BroadcastReceiver(){
  override fun onReceive(context:Context,intent:Intent){
   if(intent.action==UsbDeviceEngine.ACTION_USB_PERMISSION) device.onPermissionResult(intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED,false))
  }
 }
 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState)
  device=UsbDeviceEngine(applicationContext)
  projects=ProjectManager(applicationContext);projects.ensureExampleProject()
  ContextCompat.registerReceiver(this,usbPermissionReceiver,IntentFilter(UsbDeviceEngine.ACTION_USB_PERMISSION),ContextCompat.RECEIVER_NOT_EXPORTED)
  setContent{MaterialTheme{
   val state by AppState.state.collectAsStateWithLifecycle()
   val setup by SetupState.state.collectAsStateWithLifecycle()
   val build by BuildState.state.collectAsStateWithLifecycle()
   if(setup.visible){ AlertDialog(onDismissRequest={},confirmButton={if(setup.current.completed||setup.error!=null) TextButton(onClick={SetupState.close()}){Text("Close")}},title={Text("ESP-IDF Setup")},text={Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(10.dp)){Text(setup.current.step.label,style=MaterialTheme.typography.titleMedium);Text(setup.current.status);if(setup.current.fraction!=null) LinearProgressIndicator(progress={setup.current.fraction!!},modifier=Modifier.fillMaxWidth()) else LinearProgressIndicator(modifier=Modifier.fillMaxWidth());setup.error?.let{Text("Error: $it")};Surface(Modifier.fillMaxWidth().height(260.dp),tonalElevation=2.dp){Text(if(setup.current.log.isBlank())"Waiting for installer output…" else setup.current.log,Modifier.padding(8.dp).verticalScroll(rememberScrollState()),fontFamily=FontFamily.Monospace,style=MaterialTheme.typography.bodySmall)}}}) }
   if(build.visible){ AlertDialog(onDismissRequest={},confirmButton={if(build.completed||build.error!=null) TextButton(onClick={BuildState.close()}){Text("Close")}},title={Text("ESP-IDF Build")},text={Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(10.dp)){Text(build.status,style=MaterialTheme.typography.titleMedium);if(!build.completed&&build.error==null) LinearProgressIndicator(modifier=Modifier.fillMaxWidth());build.error?.let{Text("Error: $it")};Surface(Modifier.fillMaxWidth().height(300.dp),tonalElevation=2.dp){Text(if(build.log.isBlank())"Waiting for compiler output…" else build.log,Modifier.padding(8.dp).verticalScroll(rememberScrollState()),fontFamily=FontFamily.Monospace,style=MaterialTheme.typography.bodySmall)}}}) }
   var source by remember { mutableStateOf(projects.loadMain()) }
   Scaffold{padding->Column(Modifier.fillMaxSize().padding(padding).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
    Text("ESP32 Flashing App",style=MaterialTheme.typography.headlineMedium)
    Text(state.deviceLabel);Text("ESP-IDF 5.5 • Target: esp32s3");Text("State: "+state.operation.name);Text(state.detail)
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick={device.connect()}){Text("Connect USB")};OutlinedButton(onClick={device.disconnect()}){Text("Disconnect")}}
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
     Button(enabled=device.isConnected(),onClick={lifecycleScope.launch{runCatching{device.enterBootloader()}.onFailure{AppState.operation(OperationState.BOOTLOADER_ERROR,it.message?:"Bootloader failed")}}}){Text("Bootloader")}
     Button(enabled=device.isConnected(),onClick={lifecycleScope.launch{runCatching{device.resetToApplication()}.onFailure{AppState.operation(OperationState.RESET_ERROR,it.message?:"Reset failed")}}}){Text("Reset")}
    }
    HorizontalDivider();Text("Editor • main/main.c",style=MaterialTheme.typography.titleMedium);OutlinedTextField(value=source,onValueChange={source=it},modifier=Modifier.fillMaxWidth().height(220.dp),textStyle=LocalTextStyle.current.copy(fontFamily=FontFamily.Monospace),label={Text("ESP-IDF source")});Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick={projects.saveMain(source);AppState.operation(OperationState.IDLE,"main.c saved")}){Text("Save")};Button(onClick={configureIdf()}){Text("Configure ESP-IDF")};OutlinedButton(onClick={AppState.operation(OperationState.IDLE,IdfDoctor.inspect(applicationContext,"esp32s3").text())}){Text("Doctor")}}
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick={startBuild(source)}){Text("Build")};Button(onClick={AppState.operation(OperationState.PREPARING_BUILD,"Build first; automatic flash will follow after successful artifact validation");startBuild(source)}){Text("Build & Flash")}};HorizontalDivider();Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("Serial Monitor • 115200 • RX ${state.rxBytes} bytes");TextButton(onClick={AppState.clearSerial()}){Text("Clear")}};Surface(Modifier.fillMaxWidth().height(280.dp),tonalElevation=2.dp){Text(if(state.serialText.isEmpty())"Waiting for serial data…" else state.serialText,Modifier.padding(10.dp).verticalScroll(rememberScrollState()),style=MaterialTheme.typography.bodySmall)}
   }}
  }}
 }

 private fun configureIdf(){
  lifecycleScope.launch {
   AppState.operation(OperationState.PREPARING_BUILD,"Configuring ESP-IDF 5.5 for ESP32-S3…")
   SetupState.open()
   runCatching {
    IdfBuildExecutor(applicationContext).prepare("esp32s3"){SetupState.progress(it)}
   }.onSuccess {
    AppState.operation(OperationState.IDLE,"ESP-IDF 5.5 / esp32s3 ready")
   }.onFailure {
    val message=it.message?:"ESP-IDF setup failed"
    SetupState.error(message)
    AppState.operation(OperationState.BUILD_ERROR,message)
   }
  }
 }

 private fun startBuild(source:String){
  projects.saveMain(source)
  lifecycleScope.launch {
   BuildState.open()
   AppState.operation(OperationState.BUILDING,"Building ESP32-S3 firmware…")
   runCatching {
    IdfBuildExecutor(applicationContext).buildPrepared(projects.projectDir,"esp32s3"){BuildState.output(it)}
   }.onSuccess { output ->
    BuildState.success(output)
    AppState.operation(OperationState.BUILD_SUCCESS,output.takeLast(3500))
   }.onFailure {
    val message=it.message?:"Build failed"
    BuildState.error(message)
    AppState.operation(OperationState.BUILD_ERROR,message)
   }
  }
 }
 override fun onDestroy(){runCatching{unregisterReceiver(usbPermissionReceiver)};device.disconnect();super.onDestroy()}
}