package com.engperini.esp32flashingapp.project

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

class ProjectManager(private val context: Context) {
    private val projectsRoot = File(context.filesDir, "projects")
    private val prefs = context.getSharedPreferences("project_selection", Context.MODE_PRIVATE)
    private var pinnedProject: String? = null
    private val root: File get() = File(projectsRoot, pinnedProject ?: selectedProjectId())
    private val configFile: File get() = File(root, ".esp32-flashing-app.json")
    val mainFile: File get() = File(root, "main/main.c")
    val projectDir: File get() = root

    fun selectedProjectId(): String = pinnedProject ?: prefs.getString("selected", "example") ?: "example"

    fun projectIds(): List<String> {
        ensureExampleProject()
        return projectsRoot.listFiles()?.filter { it.isDirectory && File(it, ".esp32-flashing-app.json").isFile }
            ?.map { it.name }?.sorted() ?: emptyList()
    }

    fun selectProject(id: String) {
        require(id.matches(Regex("[a-zA-Z0-9_-]+"))) { "Invalid project identifier" }
        require(File(projectsRoot, "$id/.esp32-flashing-app.json").isFile) { "Project not found: $id" }
        prefs.edit().putString("selected", id).apply()
    }

    fun pinned(id: String): ProjectManager {
        require(id.matches(Regex("[a-zA-Z0-9_-]+"))) { "Invalid project identifier" }
        require(File(projectsRoot, "$id/.esp32-flashing-app.json").isFile) { "Project not found: $id" }
        return ProjectManager(context).also { it.pinnedProject = id }
    }

    fun ensureExampleProject(): File {
        File(root, "main").mkdirs()
        createIfMissing(File(root, "CMakeLists.txt"), "cmake_minimum_required(VERSION 3.16)\ninclude(\u0024ENV{IDF_PATH}/tools/cmake/project.cmake)\nproject(esp32_flashing_app_example)\n")
        createIfMissing(File(root, "main/CMakeLists.txt"), "idf_component_register(SRCS \"main.c\" PRIV_REQUIRES spi_flash INCLUDE_DIRS \".\")\n")
        createIfMissing(File(root, "main/idf_component.yml"), ExampleFirmware.componentManifest)
        createIfMissing(File(root, "sdkconfig.defaults"), "# Use ESP-IDF target defaults for console and USB; board-specific settings are optional.\n")
        createIfMissing(mainFile, ExampleFirmware.mainC)
        if (!configFile.exists()) saveConfig(ProjectConfig(if (root.name == "example") "Existing project" else root.name, "esp32s3"))
        ensureTemplate("hello-world", "Hello World", ExampleFirmware.mainC, "esp32s3", false)
        migrateHelloWorldTemplate()
        migrateCameraWebTemplate()
        ensureBuiltIn("camera-webserver", "Camera WebServer", CameraWebFirmware.mainC,
            "esp32s3", "esp_wifi esp_event esp_netif nvs_flash esp_http_server",
            "dependencies:\n  espressif/esp32-camera: \"^2.0.0\"\n",
            "CONFIG_SPIRAM=y\nCONFIG_SPIRAM_MODE_OCT=y\nCONFIG_SPIRAM_SPEED_80M=y\nCONFIG_SPIRAM_USE_MALLOC=y\n")
        ensureBuiltIn("bme280", "BME280 Sensor", Bme280Firmware.mainC,
            "esp32s3", "driver", "", "")
        return root
    }

    private fun ensureTemplate(id: String, name: String, main: String, target: String, servo: Boolean) {
        val dir = File(projectsRoot, id)
        if (dir.exists()) return // Never touch a pre-existing project.
        require(dir.mkdirs()) { "Unable to create project: $id" }
        File(dir, "main").mkdirs()
        File(dir, "CMakeLists.txt").writeText("cmake_minimum_required(VERSION 3.16)\ninclude(\u0024ENV{IDF_PATH}/tools/cmake/project.cmake)\nproject(" + id.replace('-', '_') + ")\n")
        File(dir, "main/CMakeLists.txt").writeText(
            if (servo) "idf_component_register(SRCS \"main.c\" REQUIRES driver INCLUDE_DIRS \".\")\n"
            else "idf_component_register(SRCS \"main.c\" PRIV_REQUIRES spi_flash INCLUDE_DIRS \".\")\n"
        )
        File(dir, "main/idf_component.yml").writeText(ExampleFirmware.componentManifest)
        File(dir, "sdkconfig.defaults").writeText("CONFIG_ESP_CONSOLE_USB_SERIAL_JTAG=y\nCONFIG_ESP_CONSOLE_SECONDARY_NONE=y\n")
        File(dir, "main/main.c").writeText(main)
        File(dir, ".esp32-flashing-app.json").writeText(JSONObject().put("name", name).put("target", target).toString(2))
    }

    /** Only migrate the untouched original camera example; preserve user-modified files. */
    private fun migrateCameraWebTemplate() {
        val dir = File(projectsRoot, "camera-webserver")
        val main = File(dir, "main/main.c")
        if (!main.isFile) return
        val current = main.readText()
        if (current == CameraWebFirmware.previousMainC ||
            current == CameraWebFirmware.mainC.replace("HTTPD_500_INTERNAL_SERVER_ERROR", "HTTPD_503_SERVICE_UNAVAILABLE")) {
            main.writeText(CameraWebFirmware.mainC)
        }
    }

    /** Built-ins are created once; existing projects and user edits are never overwritten. */
    private fun ensureBuiltIn(id: String, name: String, source: String, target: String,
                              requirements: String, manifest: String, extraDefaults: String) {
        val dir = File(projectsRoot, id)
        if (dir.exists()) return
        require(dir.mkdirs()) { "Cannot create project: $id" }
        File(dir, "main").mkdirs()
        File(dir, "CMakeLists.txt").writeText("cmake_minimum_required(VERSION 3.16)\ninclude(\u0024ENV{IDF_PATH}/tools/cmake/project.cmake)\nproject(" + id.replace('-', '_') + ")\n")
        File(dir, "main/CMakeLists.txt").writeText("idf_component_register(SRCS \"main.c\" PRIV_REQUIRES $requirements INCLUDE_DIRS \".\")\n")
        if (manifest.isNotEmpty()) File(dir, "main/idf_component.yml").writeText(manifest)
        File(dir, "sdkconfig.defaults").writeText("CONFIG_ESP_CONSOLE_USB_SERIAL_JTAG=y\nCONFIG_ESP_CONSOLE_SECONDARY_NONE=y\n" + extraDefaults)
        File(dir, "main/main.c").writeText(source)
        File(dir, ".esp32-flashing-app.json").writeText(JSONObject().put("name", name).put("target", target).toString(2))
    }

    fun createProject(name: String, target: String): String {
        require(target in SupportedTargets.values) { "Unsupported target" }
        val cleanName = name.trim()
        require(cleanName.isNotEmpty() && cleanName.length <= 64) { "Enter a project name (1-64 characters)" }
        val id = cleanName.lowercase(java.util.Locale.ROOT).replace(Regex("[^a-z0-9_-]+"), "-").trim('-')
        require(id.isNotEmpty() && id != "example") { "Invalid project name" }
        require(!File(projectsRoot, id).exists()) { "Project already exists: $id" }
        val main = "#include <stdio.h>\n#include \"freertos/FreeRTOS.h\"\n#include \"freertos/task.h\"\nvoid app_main(void) { while (1) { printf(\"Hello from $id!\\n\"); vTaskDelay(pdMS_TO_TICKS(5000)); } }\n"
        ensureBuiltIn(id, cleanName, main, target, "", "", "")
        return id
    }

    /** Update only the untouched, generated Hello World template; never modify user edits. */
    private fun migrateHelloWorldTemplate() {
        val dir = File(projectsRoot, "hello-world")
        if (!dir.isDirectory) return
        val main = File(dir, "main/main.c")
        val previousTemplate = ExampleFirmware.mainC.replace("\\n", "\\\\n")
        if (main.isFile && main.readText() == previousTemplate) {
            main.writeText(ExampleFirmware.mainC)
        }
        val defaults = File(dir, "sdkconfig.defaults")
        if (defaults.isFile && defaults.readText() == "# Target defaults\n") {
            defaults.writeText("CONFIG_ESP_CONSOLE_USB_SERIAL_JTAG=y\nCONFIG_ESP_CONSOLE_SECONDARY_NONE=y\n")
        }
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
        return root.walkTopDown().filter { file ->
            val relative = file.relativeTo(root).path
            file.isFile && file != configFile && file != manifestSnapshotFile &&
                !relative.startsWith("build" + File.separator) &&
                file.name != "esp32-flashing-app.log" &&
                file.name != "esp32-flashing-app.previous.log"
        }.toList()
    }

    private val manifestSnapshotFile get() = File(root, ".idf-component-manifests.sha256")

    private fun manifestDigest(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        root.walkTopDown().filter { it.isFile && it.name == "idf_component.yml" &&
            !it.relativeTo(root).invariantSeparatorsPath.startsWith("build/") &&
            !it.relativeTo(root).invariantSeparatorsPath.startsWith("managed_components/") }.sortedBy { it.relativeTo(root).invariantSeparatorsPath }.forEach {
            digest.update(it.relativeTo(root).invariantSeparatorsPath.toByteArray(Charsets.UTF_8))
            digest.update(0.toByte())
            digest.update(it.readBytes())
            digest.update(0.toByte())
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun configuredIdfTarget(): String? {
        val sdkconfig = File(root, "sdkconfig")
        if (!sdkconfig.isFile) return null
        return sdkconfig.useLines { lines ->
            lines.firstOrNull { it.startsWith("CONFIG_IDF_TARGET=") }
                ?.substringAfter('=')
                ?.trim()
                ?.removeSurrounding("\"")
        }
    }

    fun needsTargetSwitch(target: String): Boolean {
        require(target in SupportedTargets.values) { "Unsupported target: $target" }
        return configuredIdfTarget() != target
    }

    fun manifestsChanged(): Boolean {
        ensureExampleProject()
        return !manifestSnapshotFile.exists() || manifestSnapshotFile.readText() != manifestDigest()
    }

    fun recordManifestSnapshot() {
        manifestSnapshotFile.writeText(manifestDigest())
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
