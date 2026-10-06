package com.engperini.esp32flashingapp.project

import android.content.Context
import org.json.JSONObject
import java.io.File

class ProjectManager(context: Context) {
    private val projectsRoot = File(context.filesDir, "projects")
    private val root = File(projectsRoot, "example")
    private val configFile = File(root, ".esp32-flashing-app.json")
    val mainFile = File(root, "main/main.c")
    val projectDir: File get() = root

    fun ensureExampleProject(): File {
        File(root, "main").mkdirs()
        createIfMissing(File(root, "CMakeLists.txt"), "cmake_minimum_required(VERSION 3.16)\ninclude(\u0024ENV{IDF_PATH}/tools/cmake/project.cmake)\nproject(esp32_flashing_app_example)\n")
        createIfMissing(File(root, "main/CMakeLists.txt"), "idf_component_register(SRCS \"main.c\" INCLUDE_DIRS \".\")\n")
        createIfMissing(File(root, "sdkconfig.defaults"), "CONFIG_ESP_CONSOLE_USB_SERIAL_JTAG=y\nCONFIG_ESP_CONSOLE_SECONDARY_NONE=y\n")
        createIfMissing(mainFile, ExampleFirmware.mainC)
        if (!configFile.exists()) saveConfig(ProjectConfig("Example", "esp32s3"))
        return root
    }

    fun config(): ProjectConfig {
        ensureExampleProject()
        return runCatching {
            val json = JSONObject(configFile.readText())
            ProjectConfig(json.optString("name", "Example"), json.optString("target", "esp32s3"))
        }.getOrElse { ProjectConfig("Example", "esp32s3") }
    }

    fun setTarget(target: String) {
        require(target in SupportedTargets.values) { "Unsupported target: \u0024target" }
        saveConfig(config().copy(target = target))
    }

    fun listFiles(): List<File> {
        ensureExampleProject()
        return root.walkTopDown().filter { file ->\n            val relative = file.relativeTo(root).path\n            file.isFile && file != configFile &&\n                !relative.startsWith("build" + File.separator) &&\n                file.name != "esp32-flashing-app.log" &&\n                file.name != "esp32-flashing-app.previous.log"\n        }.toList()
    }

    fun relativePath(file: File): String = file.relativeTo(root).path.replace(File.separatorChar, '/')
    fun read(relativePath: String): String = safeFile(relativePath).readText()
    fun save(relativePath: String, text: String) { safeFile(relativePath).apply { parentFile?.mkdirs(); writeText(text) } }
    fun createFile(relativePath: String) { val f=safeFile(relativePath); f.parentFile?.mkdirs(); require(!f.exists()){"File already exists"}; f.writeText("") }
    fun createDirectory(relativePath: String) { val f=safeFile(relativePath); require(!f.exists()){"Directory already exists"}; require(f.mkdirs()){"Unable to create directory"} }
    fun loadMain(): String { ensureExampleProject(); return mainFile.readText() }
    fun saveMain(text: String) { save("main/main.c", text) }

    private fun createIfMissing(file: File, content: String) { if (!file.exists()) { file.parentFile?.mkdirs(); file.writeText(content) } }
    private fun saveConfig(config: ProjectConfig) {
        root.mkdirs()
        configFile.writeText(JSONObject().put("name", config.name).put("target", config.target).toString(2))
    }
    private fun safeFile(relativePath: String): File {
        val candidate = File(root, relativePath).canonicalFile
        require(candidate.path.startsWith(root.canonicalFile.path + File.separator)) { "Path escapes project" }
        return candidate
    }
}
