package com.engperini.esp32flashingapp.build
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.engperini.esp32flashingapp.core.AppState
import com.engperini.esp32flashingapp.core.OperationState

class BuildResultReceiver:BroadcastReceiver(){
 override fun onReceive(context:Context,intent:Intent){
  val b=intent.getBundleExtra("result")
  val exit=b?.getInt("exitCode",-1)?:-1
  val out=b?.getString("stdout").orEmpty()
  val err=b?.getString("stderr").orEmpty()
  if(exit==0 && out.contains("__ESP32_APP_BUILD_OK__")) AppState.operation(OperationState.BUILD_SUCCESS,"ESP-IDF build succeeded")
  else AppState.operation(OperationState.BUILD_ERROR,if(err.isNotBlank()) err.takeLast(1500) else out.takeLast(1500))
  BuildResultBus.handler?.invoke(exit,out,err)
 }
}
