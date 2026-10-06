package com.engperini.esp32flashingapp

import android.content.*
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
import com.engperini.esp32flashingapp.core.*
import com.engperini.esp32flashingapp.device.UsbDeviceEngine
import com.engperini.esp32flashingapp.flash.*
import com.engperini.esp32flashingapp.project.ProjectManager
import com.engperini.esp32flashingapp.runtime.*
import kotlinx.coroutines.launch

class MainActivity:ComponentActivity(){
 private lateinit var device:UsbDeviceEngine
 private lateinit var projects:ProjectManager
 private val usbReceiver=object:BroadcastReceiver(){
  override fun onReceive(context:Context,intent:Intent){when(intent.action){
   UsbDeviceEngine.ACTION_USB_PERMISSION->device.onPermissionResult(intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED,false))
   UsbManager.ACTION_USB_DEVICE_ATTACHED->device.connect()
   UsbManager.ACTION_USB_DEVICE_DETACHED->device.disconnect()
  }}
 }
 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState);device=UsbDeviceEngine(applicationContext);projects=ProjectManager(applicationContext);projects.ensureExampleProject()
  val usbFilter=IntentFilter().apply{addAction(UsbDeviceEngine.ACTION_USB_PERMISSION);addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)}
  ContextCompat.registerReceiver(this,usbReceiver,usbFilter,ContextCompat.RECEIVER_NOT_EXPORTED);if(device.hasDevice())device.connect()
  setContent{MaterialTheme{
   val state by AppState.state.collectAsStateWithLifecycle();val setup by SetupState.state.collectAsStateWithLifecycle();val build by BuildState.state.collectAsStateWithLifecycle();val flash by FlashState.state.collectAsStateWithLifecycle()
   var section by remember{mutableStateOf(AppSection.HARDWARE)}
   var project by remember{mutableStateOf(projects.config())}
   var files by remember{mutableStateOf(projects.listFiles().map(projects::relativePath))}
   var selectedFile by remember{mutableStateOf("main/main.c")}
   var editorText by remember{mutableStateOf(projects.read(selectedFile))}
   var saveFeedback by remember{mutableStateOf("")}
   OperationDialogs(setup,build,flash)
   MainShell(state,project,section,{section=it},device.isConnected(),files,selectedFile,editorText,
    onTarget={target->if(target!=project.target){projects.setTarget(target);project=projects.config();AppState.operation(OperationState.IDLE,"Target changed to $target • Configure ESP-IDF before building")}},
    onConnect={device.connect()},onDisconnect={device.disconnect()},
    onBootloader={lifecycleScope.launch{runCatching{device.enterBootloader()}.onFailure{AppState.operation(OperationState.BOOTLOADER_ERROR,it.message?:"Bootloader failed")}}},
    onReset={lifecycleScope.launch{runCatching{device.resetToApplication()}.onFailure{AppState.operation(OperationState.RESET_ERROR,it.message?:"Reset failed")}}},
    onBuild={startBuild()},onFlash={startFlash()},onBuildFlash={startBuildAndFlash()},
    onStartMonitor={device.startSerialMonitor()},onStopMonitor={device.stopSerialMonitor()},onClearSerial={AppState.clearSerial()},
    onSelectFile={path->selectedFile=path;editorText=runCatching{projects.read(path)}.getOrElse{"Unable to read file: "+it.message}},
    onEditorText={editorText=it;saveFeedback=""},onSaveFile={projects.save(selectedFile,editorText);saveFeedback="$selectedFile saved";AppState.operation(OperationState.IDLE,"$selectedFile saved")},
    saveFeedback=saveFeedback,
    onCreateFile={path->runCatching{projects.createFile(path)}.onSuccess{files=projects.listFiles().map(projects::relativePath);selectedFile=path;editorText=""}.onFailure{AppState.operation(OperationState.BUILD_ERROR,it.message?:"Unable to create file")}},
    onCreateFolder={path->runCatching{projects.createDirectory(path)}.onSuccess{files=projects.listFiles().map(projects::relativePath)}.onFailure{AppState.operation(OperationState.BUILD_ERROR,it.message?:"Unable to create folder")}},
    onConfigure={configureIdf()},onDoctor={runDoctor()},onFullClean={runFullClean()}
   )
  }}
 }
 private fun target()=projects.config().target
 private fun configureIdf(){IdfOperationService.setup(applicationContext,target())}
 private fun runDoctor(){IdfOperationService.doctor(applicationContext,target())}
 private fun runFullClean(){IdfOperationService.fullClean(applicationContext,target())}
 private fun startBuild(){IdfOperationService.build(applicationContext,target())}
 private fun startBuildAndFlash(){IdfOperationService.buildAndFlash(applicationContext,target())}
 private fun startFlash(){
  if(target()!="esp32s3"){AppState.operation(OperationState.FLASH_ERROR,"Native Flash for "+target()+" is not enabled until its reset path is validated");return}
  lifecycleScope.launch{FlashState.open();AppState.operation(OperationState.PREPARING_BUILD,"Validating existing firmware artifacts…");runCatching{
   val plan=FlashPlanLoader.load(projects.projectDir);FlashState.status("Using existing build: ${plan.images.size} validated flash images at ${plan.baudRate} baud.");FlashState.status("Taking exclusive USB ownership…");device.acquireTransport()
   try{FlashState.status("Entering ESP32-S3 ROM bootloader…");device.enterBootloader();FlashState.status("Synchronizing at 115200 baud…");AppState.operation(OperationState.BOOTLOADER_READY,"Synchronizing with ESP32-S3 ROM…");val transport=EspRomTransport(device);check(transport.sync()){"ESP32-S3 ROM did not answer SYNC"};FlashState.status("ESP32-S3 ROM SYNC successful.");transport.flash(plan){FlashState.status(it)};FlashState.status("Flash completed and verified — leaving USB download mode…");transport.watchdogReset();FlashState.success("Flash completed and verified — reconnecting application USB…");device.reconnectApplication()}finally{device.releaseTransport()}
  }.onFailure{val message=it.message?:"Flash failed";FlashState.error(message);AppState.operation(OperationState.FLASH_ERROR,"Flash stopped — see details")}}
 }
 override fun onDestroy(){runCatching{unregisterReceiver(usbReceiver)};device.disconnect();super.onDestroy()}
}

@Composable private fun OperationDialogs(setup:SetupUiState,build:BuildUiState,flash:FlashUiState){
 if(setup.visible)AlertDialog(onDismissRequest={},confirmButton={if(setup.current.completed||setup.error!=null)TextButton(onClick={SetupState.close()}){Text("Close")}},title={Text("ESP-IDF Setup")},text={Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(10.dp)){Text(setup.current.step.label,style=MaterialTheme.typography.titleMedium);Text(setup.current.status);if(setup.current.fraction!=null)LinearProgressIndicator(progress={setup.current.fraction!!},modifier=Modifier.fillMaxWidth())else LinearProgressIndicator(modifier=Modifier.fillMaxWidth());setup.error?.let{Text("Error: $it")};Surface(Modifier.fillMaxWidth().height(260.dp),tonalElevation=2.dp){Text(if(setup.current.log.isBlank())"Waiting for installer output…"else setup.current.log,Modifier.padding(8.dp).verticalScroll(rememberScrollState()),fontFamily=FontFamily.Monospace,style=MaterialTheme.typography.bodySmall)}}})
 if(build.visible)AlertDialog(onDismissRequest={},confirmButton={if(build.completed||build.error!=null)TextButton(onClick={BuildState.close()}){Text("Close")}},title={Text("ESP-IDF Build")},text={Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(10.dp)){Text(build.status,style=MaterialTheme.typography.titleMedium);if(!build.completed&&build.error==null)LinearProgressIndicator(modifier=Modifier.fillMaxWidth());build.error?.let{Text("Error: $it")};Surface(Modifier.fillMaxWidth().height(300.dp),tonalElevation=2.dp){Text(if(build.log.isBlank())"Waiting for compiler output…"else build.log,Modifier.padding(8.dp).verticalScroll(rememberScrollState()),fontFamily=FontFamily.Monospace,style=MaterialTheme.typography.bodySmall)}}})
 if(flash.visible)AlertDialog(onDismissRequest={},confirmButton={if(flash.completed||flash.error!=null)TextButton(onClick={FlashState.close()}){Text("Close")}},title={Text("ESP32 Flash")},text={Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(10.dp)){Text(flash.status,style=MaterialTheme.typography.titleMedium);if(!flash.completed&&flash.error==null)LinearProgressIndicator(modifier=Modifier.fillMaxWidth());flash.error?.let{Text("Error: $it")};Surface(Modifier.fillMaxWidth().height(240.dp),tonalElevation=2.dp){Text(if(flash.log.isBlank())"Waiting…"else flash.log,Modifier.padding(8.dp).verticalScroll(rememberScrollState()),fontFamily=FontFamily.Monospace,style=MaterialTheme.typography.bodySmall)}}})
}
