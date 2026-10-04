package com.engperini.esp32flashingapp
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.engperini.esp32flashingapp.core.AppState
class MainActivity:ComponentActivity(){override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContent{MaterialTheme{val state by AppState.state.collectAsStateWithLifecycle();Scaffold{padding->Column(Modifier.fillMaxSize().padding(padding).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){Text("ESP32 Flashing App",style=MaterialTheme.typography.headlineMedium);Text(state.deviceLabel);Text("ESP-IDF 5.5 • Target: esp32s3");Text(state.operation.name);Text(state.detail);Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick={}){Text("Build")};Button(onClick={}){Text("Flash")};Button(onClick={}){Text("Build & Flash")}};OutlinedButton(onClick={}){Text("Monitor")};HorizontalDivider();Text("Foundation initialized. Device/Flash engines are separated from Build and UI.")}}}}}}
