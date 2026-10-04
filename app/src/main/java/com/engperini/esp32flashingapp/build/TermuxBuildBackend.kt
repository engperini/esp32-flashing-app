package com.engperini.esp32flashingapp.build

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Base64
import androidx.core.content.ContextCompat
import com.engperini.esp32flashingapp.core.AppState
import com.engperini.esp32flashingapp.core.OperationState

object BuildResultBus { var handler: ((Int,String,String)->Unit)? = null }

class TermuxBuildBackend(private val context:Context) {
 companion object {
  const val ACTION="com.termux.RUN_COMMAND"
  const val PATH="com.termux.RUN_COMMAND_PATH"
  const val STDIN="com.termux.RUN_COMMAND_STDIN"
  const val WORKDIR="com.termux.RUN_COMMAND_WORKDIR"
  const val BACKGROUND="com.termux.RUN_COMMAND_BACKGROUND"
  const val PENDING="pendingIntent"
 }
 fun build(source:String):Boolean {
  val b64=Base64.encodeToString(source.toByteArray(),Base64.NO_WRAP)
  val script="""set -euo pipefail
proot-distro login ubuntu -- bash -lc '
set -euo pipefail
cd /root/esp-idf
. ./export.sh >/dev/null
P=/root/esp32-flashing-app-workspace
mkdir -p "${'/main"
cat >"$P/CMakeLists.txt" <<EOF
cmake_minimum_required(VERSION 3.16)
include(${'{IDF_PATH}/tools/cmake/project.cmake)
project(esp32_flashing_app_example)
EOF
cat >"$P/main/CMakeLists.txt" <<EOF
idf_component_register(SRCS "main.c" INCLUDE_DIRS ".")
EOF
echo "$b64" | base64 -d >"$P/main/main.c"
cd "$P"
idf.py set-target esp32s3 >/dev/null
idf.py build
echo "__ESP32_APP_BUILD_OK__"
echo "__FLASH_ARGS__"
cat build/flash_args
'"""
  val resultIntent=Intent(context,BuildResultReceiver::class.java)
  val pi=PendingIntent.getBroadcast(context,1001,resultIntent,PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE)
  val intent=Intent(ACTION).apply{
   setClassName("com.termux","com.termux.app.RunCommandService")
   putExtra(PATH,"/data/data/com.termux/files/usr/bin/bash")
   putExtra(STDIN,script);putExtra(WORKDIR,"/data/data/com.termux/files/home")
   putExtra(BACKGROUND,true);putExtra(PENDING,pi)
  }
  return runCatching{AppState.operation(OperationState.BUILDING,"ESP-IDF 5.5 build running in backend");ContextCompat.startForegroundService(context,intent);true}
   .getOrElse{AppState.operation(OperationState.BUILD_ERROR,"Could not start build backend: "+it.message);false}
 }
}
}P/main"
cat >"${'/CMakeLists.txt" <<EOF
cmake_minimum_required(VERSION 3.16)
include(\$ENV{IDF_PATH}/tools/cmake/project.cmake)
project(esp32_flashing_app_example)
EOF
cat >"$P/main/CMakeLists.txt" <<EOF
idf_component_register(SRCS "main.c" INCLUDE_DIRS ".")
EOF
echo "$b64" | base64 -d >"$P/main/main.c"
cd "$P"
idf.py set-target esp32s3 >/dev/null
idf.py build
echo "__ESP32_APP_BUILD_OK__"
echo "__FLASH_ARGS__"
cat build/flash_args
'"""
  val resultIntent=Intent(context,BuildResultReceiver::class.java)
  val pi=PendingIntent.getBroadcast(context,1001,resultIntent,PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE)
  val intent=Intent(ACTION).apply{
   setClassName("com.termux","com.termux.app.RunCommandService")
   putExtra(PATH,"/data/data/com.termux/files/usr/bin/bash")
   putExtra(STDIN,script);putExtra(WORKDIR,"/data/data/com.termux/files/home")
   putExtra(BACKGROUND,true);putExtra(PENDING,pi)
  }
  return runCatching{AppState.operation(OperationState.BUILDING,"ESP-IDF 5.5 build running in backend");ContextCompat.startForegroundService(context,intent);true}
   .getOrElse{AppState.operation(OperationState.BUILD_ERROR,"Could not start build backend: "+it.message);false}
 }
}
}P/CMakeLists.txt" <<EOF
cmake_minimum_required(VERSION 3.16)
include(\$ENV{IDF_PATH}/tools/cmake/project.cmake)
project(esp32_flashing_app_example)
EOF
cat >"${'/main/CMakeLists.txt" <<EOF
idf_component_register(SRCS "main.c" INCLUDE_DIRS ".")
EOF
echo "$b64" | base64 -d >"$P/main/main.c"
cd "$P"
idf.py set-target esp32s3 >/dev/null
idf.py build
echo "__ESP32_APP_BUILD_OK__"
echo "__FLASH_ARGS__"
cat build/flash_args
'"""
  val resultIntent=Intent(context,BuildResultReceiver::class.java)
  val pi=PendingIntent.getBroadcast(context,1001,resultIntent,PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE)
  val intent=Intent(ACTION).apply{
   setClassName("com.termux","com.termux.app.RunCommandService")
   putExtra(PATH,"/data/data/com.termux/files/usr/bin/bash")
   putExtra(STDIN,script);putExtra(WORKDIR,"/data/data/com.termux/files/home")
   putExtra(BACKGROUND,true);putExtra(PENDING,pi)
  }
  return runCatching{AppState.operation(OperationState.BUILDING,"ESP-IDF 5.5 build running in backend");ContextCompat.startForegroundService(context,intent);true}
   .getOrElse{AppState.operation(OperationState.BUILD_ERROR,"Could not start build backend: "+it.message);false}
 }
}
}P/main/CMakeLists.txt" <<EOF
idf_component_register(SRCS "main.c" INCLUDE_DIRS ".")
EOF
echo "$b64" | base64 -d >"${'/main/main.c"
cd "$P"
idf.py set-target esp32s3 >/dev/null
idf.py build
echo "__ESP32_APP_BUILD_OK__"
echo "__FLASH_ARGS__"
cat build/flash_args
'"""
  val resultIntent=Intent(context,BuildResultReceiver::class.java)
  val pi=PendingIntent.getBroadcast(context,1001,resultIntent,PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE)
  val intent=Intent(ACTION).apply{
   setClassName("com.termux","com.termux.app.RunCommandService")
   putExtra(PATH,"/data/data/com.termux/files/usr/bin/bash")
   putExtra(STDIN,script);putExtra(WORKDIR,"/data/data/com.termux/files/home")
   putExtra(BACKGROUND,true);putExtra(PENDING,pi)
  }
  return runCatching{AppState.operation(OperationState.BUILDING,"ESP-IDF 5.5 build running in backend");ContextCompat.startForegroundService(context,intent);true}
   .getOrElse{AppState.operation(OperationState.BUILD_ERROR,"Could not start build backend: "+it.message);false}
 }
}
}P/main/main.c"
cd "${'"
idf.py set-target esp32s3 >/dev/null
idf.py build
echo "__ESP32_APP_BUILD_OK__"
echo "__FLASH_ARGS__"
cat build/flash_args
'"""
  val resultIntent=Intent(context,BuildResultReceiver::class.java)
  val pi=PendingIntent.getBroadcast(context,1001,resultIntent,PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE)
  val intent=Intent(ACTION).apply{
   setClassName("com.termux","com.termux.app.RunCommandService")
   putExtra(PATH,"/data/data/com.termux/files/usr/bin/bash")
   putExtra(STDIN,script);putExtra(WORKDIR,"/data/data/com.termux/files/home")
   putExtra(BACKGROUND,true);putExtra(PENDING,pi)
  }
  return runCatching{AppState.operation(OperationState.BUILDING,"ESP-IDF 5.5 build running in backend");ContextCompat.startForegroundService(context,intent);true}
   .getOrElse{AppState.operation(OperationState.BUILD_ERROR,"Could not start build backend: "+it.message);false}
 }
}
}P"
idf.py set-target esp32s3 >/dev/null
idf.py build
echo "__ESP32_APP_BUILD_OK__"
echo "__FLASH_ARGS__"
cat build/flash_args
'"""
  val resultIntent=Intent(context,BuildResultReceiver::class.java)
  val pi=PendingIntent.getBroadcast(context,1001,resultIntent,PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE)
  val intent=Intent(ACTION).apply{
   setClassName("com.termux","com.termux.app.RunCommandService")
   putExtra(PATH,"/data/data/com.termux/files/usr/bin/bash")
   putExtra(STDIN,script);putExtra(WORKDIR,"/data/data/com.termux/files/home")
   putExtra(BACKGROUND,true);putExtra(PENDING,pi)
  }
  return runCatching{AppState.operation(OperationState.BUILDING,"ESP-IDF 5.5 build running in backend");ContextCompat.startForegroundService(context,intent);true}
   .getOrElse{AppState.operation(OperationState.BUILD_ERROR,"Could not start build backend: "+it.message);false}
 }
}
}ENV{IDF_PATH}/tools/cmake/project.cmake)
project(esp32_flashing_app_example)
EOF
cat >"$P/main/CMakeLists.txt" <<EOF
idf_component_register(SRCS "main.c" INCLUDE_DIRS ".")
EOF
echo "$b64" | base64 -d >"$P/main/main.c"
cd "$P"
idf.py set-target esp32s3 >/dev/null
idf.py build
echo "__ESP32_APP_BUILD_OK__"
echo "__FLASH_ARGS__"
cat build/flash_args
'"""
  val resultIntent=Intent(context,BuildResultReceiver::class.java)
  val pi=PendingIntent.getBroadcast(context,1001,resultIntent,PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE)
  val intent=Intent(ACTION).apply{
   setClassName("com.termux","com.termux.app.RunCommandService")
   putExtra(PATH,"/data/data/com.termux/files/usr/bin/bash")
   putExtra(STDIN,script);putExtra(WORKDIR,"/data/data/com.termux/files/home")
   putExtra(BACKGROUND,true);putExtra(PENDING,pi)
  }
  return runCatching{AppState.operation(OperationState.BUILDING,"ESP-IDF 5.5 build running in backend");ContextCompat.startForegroundService(context,intent);true}
   .getOrElse{AppState.operation(OperationState.BUILD_ERROR,"Could not start build backend: "+it.message);false}
 }
}
}P/main"
cat >"${'/CMakeLists.txt" <<EOF
cmake_minimum_required(VERSION 3.16)
include(\$ENV{IDF_PATH}/tools/cmake/project.cmake)
project(esp32_flashing_app_example)
EOF
cat >"$P/main/CMakeLists.txt" <<EOF
idf_component_register(SRCS "main.c" INCLUDE_DIRS ".")
EOF
echo "$b64" | base64 -d >"$P/main/main.c"
cd "$P"
idf.py set-target esp32s3 >/dev/null
idf.py build
echo "__ESP32_APP_BUILD_OK__"
echo "__FLASH_ARGS__"
cat build/flash_args
'"""
  val resultIntent=Intent(context,BuildResultReceiver::class.java)
  val pi=PendingIntent.getBroadcast(context,1001,resultIntent,PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE)
  val intent=Intent(ACTION).apply{
   setClassName("com.termux","com.termux.app.RunCommandService")
   putExtra(PATH,"/data/data/com.termux/files/usr/bin/bash")
   putExtra(STDIN,script);putExtra(WORKDIR,"/data/data/com.termux/files/home")
   putExtra(BACKGROUND,true);putExtra(PENDING,pi)
  }
  return runCatching{AppState.operation(OperationState.BUILDING,"ESP-IDF 5.5 build running in backend");ContextCompat.startForegroundService(context,intent);true}
   .getOrElse{AppState.operation(OperationState.BUILD_ERROR,"Could not start build backend: "+it.message);false}
 }
}
}P/CMakeLists.txt" <<EOF
cmake_minimum_required(VERSION 3.16)
include(\$ENV{IDF_PATH}/tools/cmake/project.cmake)
project(esp32_flashing_app_example)
EOF
cat >"${'/main/CMakeLists.txt" <<EOF
idf_component_register(SRCS "main.c" INCLUDE_DIRS ".")
EOF
echo "$b64" | base64 -d >"$P/main/main.c"
cd "$P"
idf.py set-target esp32s3 >/dev/null
idf.py build
echo "__ESP32_APP_BUILD_OK__"
echo "__FLASH_ARGS__"
cat build/flash_args
'"""
  val resultIntent=Intent(context,BuildResultReceiver::class.java)
  val pi=PendingIntent.getBroadcast(context,1001,resultIntent,PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE)
  val intent=Intent(ACTION).apply{
   setClassName("com.termux","com.termux.app.RunCommandService")
   putExtra(PATH,"/data/data/com.termux/files/usr/bin/bash")
   putExtra(STDIN,script);putExtra(WORKDIR,"/data/data/com.termux/files/home")
   putExtra(BACKGROUND,true);putExtra(PENDING,pi)
  }
  return runCatching{AppState.operation(OperationState.BUILDING,"ESP-IDF 5.5 build running in backend");ContextCompat.startForegroundService(context,intent);true}
   .getOrElse{AppState.operation(OperationState.BUILD_ERROR,"Could not start build backend: "+it.message);false}
 }
}
}P/main/CMakeLists.txt" <<EOF
idf_component_register(SRCS "main.c" INCLUDE_DIRS ".")
EOF
echo "$b64" | base64 -d >"${'/main/main.c"
cd "$P"
idf.py set-target esp32s3 >/dev/null
idf.py build
echo "__ESP32_APP_BUILD_OK__"
echo "__FLASH_ARGS__"
cat build/flash_args
'"""
  val resultIntent=Intent(context,BuildResultReceiver::class.java)
  val pi=PendingIntent.getBroadcast(context,1001,resultIntent,PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE)
  val intent=Intent(ACTION).apply{
   setClassName("com.termux","com.termux.app.RunCommandService")
   putExtra(PATH,"/data/data/com.termux/files/usr/bin/bash")
   putExtra(STDIN,script);putExtra(WORKDIR,"/data/data/com.termux/files/home")
   putExtra(BACKGROUND,true);putExtra(PENDING,pi)
  }
  return runCatching{AppState.operation(OperationState.BUILDING,"ESP-IDF 5.5 build running in backend");ContextCompat.startForegroundService(context,intent);true}
   .getOrElse{AppState.operation(OperationState.BUILD_ERROR,"Could not start build backend: "+it.message);false}
 }
}
}P/main/main.c"
cd "${'"
idf.py set-target esp32s3 >/dev/null
idf.py build
echo "__ESP32_APP_BUILD_OK__"
echo "__FLASH_ARGS__"
cat build/flash_args
'"""
  val resultIntent=Intent(context,BuildResultReceiver::class.java)
  val pi=PendingIntent.getBroadcast(context,1001,resultIntent,PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE)
  val intent=Intent(ACTION).apply{
   setClassName("com.termux","com.termux.app.RunCommandService")
   putExtra(PATH,"/data/data/com.termux/files/usr/bin/bash")
   putExtra(STDIN,script);putExtra(WORKDIR,"/data/data/com.termux/files/home")
   putExtra(BACKGROUND,true);putExtra(PENDING,pi)
  }
  return runCatching{AppState.operation(OperationState.BUILDING,"ESP-IDF 5.5 build running in backend");ContextCompat.startForegroundService(context,intent);true}
   .getOrElse{AppState.operation(OperationState.BUILD_ERROR,"Could not start build backend: "+it.message);false}
 }
}
}P"
idf.py set-target esp32s3 >/dev/null
idf.py build
echo "__ESP32_APP_BUILD_OK__"
echo "__FLASH_ARGS__"
cat build/flash_args
'"""
  val resultIntent=Intent(context,BuildResultReceiver::class.java)
  val pi=PendingIntent.getBroadcast(context,1001,resultIntent,PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE)
  val intent=Intent(ACTION).apply{
   setClassName("com.termux","com.termux.app.RunCommandService")
   putExtra(PATH,"/data/data/com.termux/files/usr/bin/bash")
   putExtra(STDIN,script);putExtra(WORKDIR,"/data/data/com.termux/files/home")
   putExtra(BACKGROUND,true);putExtra(PENDING,pi)
  }
  return runCatching{AppState.operation(OperationState.BUILDING,"ESP-IDF 5.5 build running in backend");ContextCompat.startForegroundService(context,intent);true}
   .getOrElse{AppState.operation(OperationState.BUILD_ERROR,"Could not start build backend: "+it.message);false}
 }
}
