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
import com.engperini.esp32flashingapp.flash.EspRomTransport
import com.engperini.esp32flashingapp.flash.FlashState
import com.engperini.esp32flashingapp.flash.FlashPlanLoader
import com.engperini.esp32flashingapp.runtime.IdfBuildExecutor
import com.engperini.esp32flashingapp.runtime.IdfOperationService
import com.engperini.esp32flashingapp.runtime.SetupState
import com.engperini.esp32flashingapp.runtime.BuildState
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
   val flash by FlashState.state.collectAsStateWithLifecycle()
   if(setup.visible){ AlertDialog(onDismissRequest={},confirmButton={if(setup.current.completed||setup.error!=null) TextButton(onClick={SetupState.close()}){Text("Close")}},title={Text("ESP-IDF Setup")},text={Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(10.dp)){Text(setup.current.step.label,style=MaterialTheme.typography.titleMedium);Text(setup.current.status);if(setup.current.fraction!=null) LinearProgressIndicator(progress={setup.current.fraction!!},modifier=Modifier.fillMaxWidth()) else LinearProgressIndicator(modifier=Modifier.fillMaxWidth());setup.error?.let{Text("Error: $it")};Surface(Modifier.fillMaxWidth().height(260.dp),tonalElevation=2.dp){Text(if(setup.current.log.isBlank())"Waiting for installer output…" else setup.current.log,Modifier.padding(8.dp).verticalScroll(rememberScrollState()),fontFamily=FontFamily.Monospace,style=MaterialTheme.typography.bodySmall)}}}) }
   if(build.visible){ AlertDialog(onDismissRequest={},confirmButton={if(build.completed||build.error!=null) TextButton(onClick={BuildState.close()}){Text("Close")}},title={Text("ESP-IDF Build")},text={Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(10.dp)){Text(build.status,style=MaterialTheme.typography.titleMedium);if(!build.completed&&build.error==null) LinearProgressIndicator(modifier=Modifier.fillMaxWidth());build.error?.let{Text("Error: $it")};Surface(Modifier.fillMaxWidth().height(300.dp),tonalElevation=2.dp){Text(if(build.log.isBlank())"Waiting for compiler output…" else build.log,Modifier.padding(8.dp).verticalScroll(rememberScrollState()),fontFamily=FontFamily.Monospace,style=MaterialTheme.typography.bodySmall)}}}) }
   if(flash.visible){ AlertDialog(onDismissRequest={},confirmButton={if(flash.completed||flash.error!=null) TextButton(onClick={FlashState.close()}){Text("Close")}},title={Text("ESP32 Flash")},text={Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(10.dp)){Text(flash.status,style=MaterialTheme.typography.titleMedium);if(!flash.completed&&flash.error==null) LinearProgressIndicator(modifier=Modifier.fillMaxWidth());flash.error?.let{Text("Error: $it")};Surface(Modifier.fillMaxWidth().height(240.dp),tonalElevation=2.dp){Text(if(flash.log.isBlank())"Waiting…" else flash.log,Modifier.padding(8.dp).verticalScroll(rememberScrollState()),fontFamily=FontFamily.Monospace,style=MaterialTheme.typography.bodySmall)}}}) }
   var source by remember { mutableStateOf(projects.loadMain()) }
   var serialAutoScroll by remember { mutableStateOf(true) }
   val serialScroll = rememberScrollState()
   LaunchedEffect(state.serialText, serialAutoScroll) { if(serialAutoScroll) serialScroll.animateScrollTo(serialScroll.maxValue) }
   Scaffold{padding->Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
    Text("ESP32 Flashing App",style=MaterialTheme.typography.headlineMedium)
    Text(state.deviceLabel);Text("ESP-IDF 5.5 • Target: esp32s3");Text("State: "+state.operation.name);Text(state.detail)
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick={device.connect()}){Text("Connect USB")};OutlinedButton(onClick={device.disconnect()}){Text("Disconnect")}}
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
     Button(enabled=device.isConnected(),onClick={lifecycleScope.launch{runCatching{device.enterBootloader()}.onFailure{AppState.operation(OperationState.BOOTLOADER_ERROR,it.message?:"Bootloader failed")}}}){Text("Bootloader")}
     Button(enabled=device.isConnected(),onClick={lifecycleScope.launch{runCatching{device.resetToApplication()}.onFailure{AppState.operation(OperationState.RESET_ERROR,it.message?:"Reset failed")}}}){Text("Reset")}
    }
    HorizontalDivider();Text("Editor • main/main.c",style=MaterialTheme.typography.titleMedium);OutlinedTextField(value=source,onValueChange={source=it},modifier=Modifier.fillMaxWidth().height(220.dp),textStyle=LocalTextStyle.current.copy(fontFamily=FontFamily.Monospace),label={Text("ESP-IDF source")});Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(modifier=Modifier.weight(1f),onClick={projects.saveMain(source);AppState.operation(OperationState.IDLE,"main.c saved")}){Text("Save")};Button(modifier=Modifier.weight(2f),onClick={configureIdf()}){Text("Configure ESP-IDF")}};Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton(modifier=Modifier.weight(1f),onClick={runDoctor()}){Text("Doctor")};OutlinedButton(modifier=Modifier.weight(1f),onClick={runFullClean()}){Text("Full Clean")}}}
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(modifier=Modifier.weight(1f),onClick={startBuild(source)}){Text("Build")};Button(modifier=Modifier.weight(1f),enabled=device.isConnected(),onClick={startFlash()}){Text("Flash")}};Button(modifier=Modifier.fillMaxWidth(),enabled=device.isConnected(),onClick={startBuildAndFlash(source)}){Text("Build & Flash")}};HorizontalDivider()
    Text("Serial Monitor",style=MaterialTheme.typography.titleMedium)
    Text("115200 baud • RX ${state.rxBytes} bytes",style=MaterialTheme.typography.bodyMedium)
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
     Button(modifier=Modifier.weight(1f),enabled=device.isConnected(),onClick={device.startSerialMonitor()}){Text("Start Monitor")}
     OutlinedButton(modifier=Modifier.weight(1f),enabled=device.isConnected(),onClick={device.stopSerialMonitor()}){Text("Stop Monitor")}
    }
    Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){
     Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){Checkbox(checked=serialAutoScroll,onCheckedChange={serialAutoScroll=it});Text("Auto-scroll")}
     TextButton(onClick={AppState.clearSerial()}){Text("Clear")}
    }
    Surface(Modifier.fillMaxWidth().height(320.dp),tonalElevation=2.dp){Text(if(state.serialText.isEmpty())"Waiting for serial data…" else state.serialText,Modifier.padding(10.dp).verticalScroll(serialScroll),fontFamily=FontFamily.Monospace,style=MaterialTheme.typography.bodySmall)}
   }}
  }}
 }

 private fun configureIdf(){ IdfOperationService.setup(applicationContext) }

 private fun runDoctor(){ IdfOperationService.doctor(applicationContext) }

 private fun runFullClean(){ IdfOperationService.fullClean(applicationContext) }

 private fun startBuild(source:String){
  projects.saveMain(source)
  lifecycleScope.launch {
   BuildState.open()
   AppState.operation(OperationState.BUILDING,"Building ESP32-S3 firmware…")
   runCatching {
    IdfBuildExecutor(applicationContext).buildPrepared(projects.projectDir,"esp32s3"){BuildState.output(it)}
   }.onSuccess { output ->
    runCatching { FlashPlanLoader.promoteLastGood(projects.projectDir) }
    BuildState.success(output)
    AppState.operation(OperationState.BUILD_SUCCESS,"Firmware built successfully")
   }.onFailure {
    val message=it.message?:"Build failed"
    BuildState.error(message)
    AppState.operation(OperationState.BUILD_ERROR,"Build failed — see Build details")
   }
  }
 }
 private fun startFlash(){
  lifecycleScope.launch {
   FlashState.open()
   AppState.operation(OperationState.PREPARING_BUILD,"Validating existing firmware artifacts…")
   runCatching {
    val plan=FlashPlanLoader.load(projects.projectDir)
    FlashState.status("Using existing build: ${plan.images.size} validated flash images at ${plan.baudRate} baud.")
    FlashState.status("Taking exclusive USB ownership…")
    device.acquireTransport()
    try {
     FlashState.status("Entering ESP32-S3 ROM bootloader…")
     device.enterBootloader()
     FlashState.status("Synchronizing at 115200 baud…")
     AppState.operation(OperationState.BOOTLOADER_READY,"Synchronizing with ESP32-S3 ROM…")
     val transport=EspRomTransport(device)
     check(transport.sync()){"ESP32-S3 ROM did not answer SYNC"}
     FlashState.status("ESP32-S3 ROM SYNC successful.")
     transport.flash(plan){ FlashState.status(it) }
     FlashState.success("Flash completed and verified — resetting ESP32-S3…")
     device.resetToApplication()
     AppState.operation(OperationState.WAITING_APPLICATION,"Firmware flashed and verified; waiting for application serial")
    } finally {
     device.releaseTransport()
    }
   }.onFailure {
    val message=it.message?:"Build & Flash preparation failed"
    FlashState.error(message)
    AppState.operation(OperationState.BUILD_ERROR,"Build & Flash stopped — see details")
   }
  }
 }
 private fun startBuildAndFlash(source:String){
  projects.saveMain(source)
  IdfOperationService.buildAndFlash(applicationContext)
 }
 override fun onDestroy(){runCatching{unregisterReceiver(usbPermissionReceiver)};device.disconnect();super.onDestroy()}
}