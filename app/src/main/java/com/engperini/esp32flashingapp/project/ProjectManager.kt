package com.engperini.esp32flashingapp.project
import android.content.Context
import java.io.File

class ProjectManager(context: Context) {
 private val root = File(context.filesDir, "projects/example")
 val mainFile = File(root, "main/main.c")
 fun ensureExampleProject(): File {
  File(root,"main").mkdirs()
  File(root,"CMakeLists.txt").writeText("""cmake_minimum_required(VERSION 3.16)
include(\$ENV{IDF_PATH}/tools/cmake/project.cmake)
project(esp32_flashing_app_example)
""")
  File(root,"main/CMakeLists.txt").writeText("""idf_component_register(SRCS "main.c" INCLUDE_DIRS ".")
""")
  if(!mainFile.exists()) mainFile.writeText(ExampleFirmware.mainC)
  return root
 }
 fun loadMain():String { ensureExampleProject(); return mainFile.readText() }
 fun saveMain(text:String){ensureExampleProject();mainFile.writeText(text)}
}
