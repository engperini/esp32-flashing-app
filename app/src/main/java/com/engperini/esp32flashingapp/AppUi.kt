package com.engperini.esp32flashingapp

import androidx.compose.material3.ExperimentalMaterial3Api

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.engperini.esp32flashingapp.core.AppUiState
import com.engperini.esp32flashingapp.core.OperationState
import com.engperini.esp32flashingapp.project.ProjectConfig
import com.engperini.esp32flashingapp.project.SupportedTargets

enum class AppSection { HARDWARE, PROJECT, SETTINGS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainShell(
    state: AppUiState,
    project: ProjectConfig,
    projectIds: List<String>,
    selectedProjectId: String,
    onProjectSelect: (String) -> Unit,
    onNewProject: (String, String) -> Unit,
    section: AppSection,
    onSection: (AppSection) -> Unit,
    connected: Boolean,
    files: List<String>,
    selectedFile: String,
    editorText: String,
    onTarget: (String) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onBootloader: () -> Unit,
    onReset: () -> Unit,
    onBuild: () -> Unit,
    onFlash: () -> Unit,
    onBuildFlash: () -> Unit,
    onStartMonitor: () -> Unit,
    onStopMonitor: () -> Unit,
    onClearSerial: () -> Unit,
    onSelectFile: (String) -> Unit,
    onEditorText: (String) -> Unit,
    onSaveFile: () -> Unit,
    saveFeedback: String,
    onCreateFile: (String) -> Unit,
    onCreateFolder: (String) -> Unit,
    onConfigure: () -> Unit,
    onDoctor: () -> Unit,
    onFullClean: () -> Unit,
    onReconfigure: () -> Unit,
    onRefreshFiles: () -> Unit,
    onExportLog: () -> Unit
) {
    Scaffold(
        topBar = { TopAppBar(title = { Column { Text("ESP32 Flashing App"); Text(project.name + " • " + SupportedTargets.label(project.target), style = MaterialTheme.typography.labelMedium) } }) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected=section==AppSection.HARDWARE,onClick={onSection(AppSection.HARDWARE)},icon={},label={Text("Hardware")})
                NavigationBarItem(selected=section==AppSection.PROJECT,onClick={onSection(AppSection.PROJECT)},icon={},label={Text("Project")})
                NavigationBarItem(selected=section==AppSection.SETTINGS,onClick={onSection(AppSection.SETTINGS)},icon={},label={Text("Settings")})
            }
        }
    ) { padding ->
        when(section) {
            AppSection.HARDWARE -> HardwareScreen(state, project, connected, onTarget, onConnect, onDisconnect, onBootloader, onReset, onBuild, onFlash, onBuildFlash, onStartMonitor, onStopMonitor, onClearSerial, Modifier.padding(padding))
            AppSection.PROJECT -> ProjectScreen(projectIds, selectedProjectId, onProjectSelect, onNewProject, files, selectedFile, editorText, onSelectFile, onEditorText, onSaveFile, saveFeedback, onCreateFile, onCreateFolder, onRefreshFiles, Modifier.padding(padding))
            AppSection.SETTINGS -> SettingsScreen(project, onConfigure, onDoctor, onFullClean, onReconfigure, onExportLog, Modifier.padding(padding))
        }
    }
}

@Composable private fun HardwareScreen(
    state:AppUiState, project:ProjectConfig, connected:Boolean, onTarget:(String)->Unit,
    onConnect:()->Unit,onDisconnect:()->Unit,onBootloader:()->Unit,onReset:()->Unit,
    onBuild:()->Unit,onFlash:()->Unit,onBuildFlash:()->Unit,onStartMonitor:()->Unit,onStopMonitor:()->Unit,onClearSerial:()->Unit,
    modifier:Modifier
) {
    var targetMenu by remember { mutableStateOf(false) }
    val serialScroll=rememberScrollState()
    var autoScroll by remember { mutableStateOf(true) }
    LaunchedEffect(state.serialText,autoScroll){if(autoScroll) serialScroll.animateScrollTo(serialScroll.maxValue)}
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
            Text("Hardware",style=MaterialTheme.typography.titleLarge)
            Text(state.deviceLabel)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                Box{OutlinedButton(onClick={targetMenu=true}){Text("Target: "+SupportedTargets.label(project.target))}
                    DropdownMenu(expanded=targetMenu,onDismissRequest={targetMenu=false}){SupportedTargets.values.forEach{target->DropdownMenuItem(text={Text(SupportedTargets.label(target))},onClick={targetMenu=false;onTarget(target)})}}}
            }
            Text("State: "+state.operation.name,style=MaterialTheme.typography.labelMedium);Text(state.detail)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick=onConnect,modifier=Modifier.weight(1f)){Text("Connect")};OutlinedButton(onClick=onDisconnect,modifier=Modifier.weight(1f)){Text("Disconnect")}}
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton(enabled=connected,onClick=onBootloader,modifier=Modifier.weight(1f)){Text("Bootloader")};OutlinedButton(enabled=connected,onClick=onReset,modifier=Modifier.weight(1f)){Text("Reset")}}
        }}
        Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text("Firmware",style=MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick=onBuild,modifier=Modifier.weight(1f)){Text("Build")};Button(enabled=connected,onClick=onFlash,modifier=Modifier.weight(1f)){Text("Flash")}}
            Button(enabled=connected,onClick=onBuildFlash,modifier=Modifier.fillMaxWidth()){Text("Build & Flash")}
        }}
        Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text("Serial Monitor",style=MaterialTheme.typography.titleMedium);Text("115200 baud • RX "+state.rxBytes+" bytes")
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(enabled=connected&&state.operation!=OperationState.MONITORING,onClick=onStartMonitor,modifier=Modifier.weight(1f)){Text("Start")};OutlinedButton(enabled=connected&&state.operation==OperationState.MONITORING,onClick=onStopMonitor,modifier=Modifier.weight(1f)){Text("Stop")}}
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Row{Checkbox(autoScroll,{autoScroll=it});Text("Auto-scroll",Modifier.padding(top=12.dp))};TextButton(onClick=onClearSerial){Text("Clear")}}
            Surface(Modifier.fillMaxWidth().height(280.dp),tonalElevation=1.dp){Text(if(state.serialText.isBlank())"Waiting for serial data…" else state.serialText,Modifier.padding(10.dp).verticalScroll(serialScroll),fontFamily=FontFamily.Monospace,style=MaterialTheme.typography.bodySmall)}
        }}
    }
}

@Composable private fun ProjectScreen(projectIds:List<String>,selectedProjectId:String,onProjectSelect:(String)->Unit,onNewProject:(String,String)->Unit,files:List<String>,selectedFile:String,editorText:String,onSelectFile:(String)->Unit,onEditorText:(String)->Unit,onSaveFile:()->Unit,saveFeedback:String,onCreateFile:(String)->Unit,onCreateFolder:(String)->Unit,onRefreshFiles:()->Unit,modifier:Modifier){
    var fullEditor by remember { mutableStateOf(false) }; var showGenerated by remember { mutableStateOf(false) }; var newKind by remember { mutableStateOf<String?>(null) };var newPath by remember { mutableStateOf("") };var projectMenu by remember { mutableStateOf(false) }; var showNewProject by remember { mutableStateOf(false) }; var newProjectName by remember { mutableStateOf("") }; var newProjectTarget by remember { mutableStateOf("esp32s3") }
    if(showNewProject) AlertDialog(onDismissRequest={showNewProject=false},title={Text("New ESP-IDF Project")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){OutlinedTextField(newProjectName,{newProjectName=it},label={Text("Project name")},singleLine=true);Text("Target");Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){SupportedTargets.values.forEach{t->FilterChip(selected=newProjectTarget==t,onClick={newProjectTarget=t},label={Text(SupportedTargets.label(t))})}}}},confirmButton={Button(enabled=newProjectName.isNotBlank(),onClick={onNewProject(newProjectName,newProjectTarget);showNewProject=false;newProjectName=""}){Text("Create")}},dismissButton={TextButton(onClick={showNewProject=false}){Text("Cancel")}})
    if(newKind!=null) AlertDialog(onDismissRequest={newKind=null},title={Text("New "+newKind)},text={OutlinedTextField(newPath,{newPath=it},label={Text("Path inside project")},singleLine=true)},confirmButton={Button(onClick={if(newKind=="file")onCreateFile(newPath) else onCreateFolder(newPath);newPath="";newKind=null}){Text("Create")}},dismissButton={TextButton(onClick={newKind=null}){Text("Cancel")}})
    if(fullEditor) androidx.compose.ui.window.Dialog(onDismissRequest={fullEditor=false},properties=androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    Text(selectedFile,modifier=Modifier.weight(1f),style=MaterialTheme.typography.titleSmall,maxLines=2)
                    TextButton(onClick={fullEditor=false}) { Text("Close") }
                }
                OutlinedTextField(editorText,onEditorText,modifier=Modifier.fillMaxWidth().weight(1f),textStyle=LocalTextStyle.current.copy(fontFamily=FontFamily.Monospace),singleLine=false)
                Button(onClick=onSaveFile,modifier=Modifier.fillMaxWidth()) { Text("Save") }
                if(saveFeedback.isNotBlank()) Text(saveFeedback,style=MaterialTheme.typography.bodySmall)
            }
        }
    }
    Column(modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text("Project",style=MaterialTheme.typography.titleLarge)
            Text("ESP-IDF project files",style=MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                TextButton(onClick={showNewProject=true},modifier=Modifier.weight(1f),contentPadding=PaddingValues(horizontal=2.dp)) { Text("+ Project",maxLines=1,softWrap=false) }
                TextButton(onClick={newKind="file"},modifier=Modifier.weight(1f),contentPadding=PaddingValues(horizontal=2.dp)) { Text("+ File",maxLines=1,softWrap=false) }
                TextButton(onClick={newKind="folder"},modifier=Modifier.weight(1f),contentPadding=PaddingValues(horizontal=2.dp)) { Text("+ Folder",maxLines=1,softWrap=false) }
                IconButton(onClick=onRefreshFiles) { Text("↻") }
            }
        }
        Box {
            OutlinedButton(onClick={projectMenu=true}) { Text("Project: " + selectedProjectId + " ▼") }
            DropdownMenu(expanded=projectMenu,onDismissRequest={projectMenu=false}) {
                projectIds.forEach { id ->
                    DropdownMenuItem(text={Text(id)},onClick={projectMenu=false;onProjectSelect(id)})
                }
            }
        }
        val visibleFiles = if(showGenerated) files else files.filterNot { it.startsWith("managed_components/") || it.startsWith("build/") || it.startsWith(".git/") || it.startsWith("components/esp-idf/") }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Text("Files (${visibleFiles.size})",style=MaterialTheme.typography.titleSmall)
            TextButton(onClick={showGenerated=!showGenerated}) { Text(if(showGenerated) "Hide generated" else "Show generated") }
        }
        Surface(Modifier.fillMaxWidth().heightIn(max=120.dp),tonalElevation=1.dp){Column(Modifier.verticalScroll(rememberScrollState()).padding(8.dp)){visibleFiles.forEach{path->TextButton(onClick={onSelectFile(path)},modifier=Modifier.fillMaxWidth()){Text(if(path==selectedFile)"●  "+path else "   "+path,modifier=Modifier.fillMaxWidth(),maxLines=2)}}}}
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Text(selectedFile.ifBlank{"Select a file"},modifier=Modifier.weight(1f),style=MaterialTheme.typography.titleMedium,maxLines=2)
            TextButton(enabled=selectedFile.isNotBlank(),onClick={fullEditor=true}) { Text("Expand editor") }
        }
        OutlinedTextField(editorText,onEditorText,enabled=selectedFile.isNotBlank(),modifier=Modifier.fillMaxWidth().weight(1f),textStyle=LocalTextStyle.current.copy(fontFamily=FontFamily.Monospace),label={Text("Editor")})
        Button(enabled=selectedFile.isNotBlank(),onClick=onSaveFile,modifier=Modifier.fillMaxWidth()){Text("Save")}
        if(saveFeedback.isNotBlank()) Text(saveFeedback,style=MaterialTheme.typography.bodySmall)
    }
}

@Composable private fun SettingsScreen(project:ProjectConfig,onConfigure:()->Unit,onDoctor:()->Unit,onFullClean:()->Unit,onReconfigure:()->Unit,onExportLog:()->Unit,modifier:Modifier){
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        Text("Settings",style=MaterialTheme.typography.headlineSmall)
        Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){Text("ESP-IDF Environment",style=MaterialTheme.typography.titleMedium);Text("ESP-IDF 5.5 • "+SupportedTargets.label(project.target));Button(onClick=onConfigure,modifier=Modifier.fillMaxWidth()){Text("Configure ESP-IDF")};OutlinedButton(onClick=onDoctor,modifier=Modifier.fillMaxWidth()){Text("Doctor")};OutlinedButton(onClick=onReconfigure,modifier=Modifier.fillMaxWidth()){Text("Reconfigure")};OutlinedButton(onClick=onFullClean,modifier=Modifier.fillMaxWidth()){Text("Full Clean")};Text("Full Clean removes only ESP-IDF build output. Project source files are preserved.",style=MaterialTheme.typography.bodySmall)}}
        Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){Text("Diagnostics",style=MaterialTheme.typography.titleMedium);OutlinedButton(onClick=onExportLog,modifier=Modifier.fillMaxWidth()){Text("Export Diagnostic Log")};Text("Saves the persistent diagnostic log without loading it into the editor.",style=MaterialTheme.typography.bodySmall)}}
    }
}
