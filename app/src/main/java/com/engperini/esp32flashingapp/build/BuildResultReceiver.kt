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
  else { val msg=if(err.isNotBlank()) err else out; AppState.operation(OperationState.BUILD_ERROR, if(msg.contains("allow-external-apps",true)) "Termux blocked external apps. Enable allow-external-apps=true in Termux settings; this is a transitional backend requirement." else msg.takeLast(1500)) }
  BuildResultBus.handler?.invoke(exit,out,err)
 }
}
