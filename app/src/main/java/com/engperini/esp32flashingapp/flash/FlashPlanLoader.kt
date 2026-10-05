package com.engperini.esp32flashingapp.flash

import org.json.JSONObject
import java.io.File

object FlashPlanLoader {
    fun load(projectDir: File, baudRate: Int = 115200): FlashPlan {
        val buildDir = File(projectDir, "build").canonicalFile
        require(buildDir.isDirectory) { "Build directory not found" }
        val configFile = File(buildDir, "flasher_args.json")
        require(configFile.isFile) { "ESP-IDF flasher_args.json not found" }
        return parse(buildDir, configFile.readText(), baudRate)
    }

    internal fun parse(buildDir: File, json: String, baudRate: Int = 115200): FlashPlan {
        val root = JSONObject(json)
        val extra = root.getJSONObject("extra_esptool_args")
        val chip = extra.getString("chip")
        require(chip == "esp32s3") { "Built firmware target is $chip, expected esp32s3" }
        val flashFiles = root.getJSONObject("flash_files")
        val images = flashFiles.keys().asSequence().map { address ->
            val relativePath = flashFiles.getString(address)
            val file = File(buildDir, relativePath).canonicalFile
            require(file.path.startsWith(buildDir.canonicalPath + File.separator)) { "Flash image escapes build directory: $relativePath" }
            require(file.isFile) { "Flash image not found: $relativePath" }
            FlashImage(address.removePrefix("0x").toLong(16), file.path)
        }.sortedBy { it.address }.toList()
        require(images.isNotEmpty()) { "No flash images found in ESP-IDF flasher_args.json" }
        return FlashPlan(chip, baudRate, images)
    }
}
