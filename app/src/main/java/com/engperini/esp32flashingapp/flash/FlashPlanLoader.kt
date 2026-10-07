package com.engperini.esp32flashingapp.flash

import com.engperini.esp32flashingapp.project.SupportedTargets
import org.json.JSONObject
import java.io.File

object FlashPlanLoader {
    fun load(projectDir: File, baudRate: Int = 115200): FlashPlan {
        val lastGood = File(projectDir, ".last-good-flash")
        val buildDir = (if (lastGood.isDirectory) lastGood else File(projectDir, "build")).canonicalFile
        require(buildDir.isDirectory) { "Build directory not found" }
        val configFile = File(buildDir, "flasher_args.json")
        require(configFile.isFile) { "ESP-IDF flasher_args.json not found" }
        return parse(buildDir, configFile.readText(), baudRate)
    }

    fun promoteLastGood(projectDir: File, expectedTarget: String? = null) {
        val source = File(projectDir, "build").canonicalFile
        val plan = loadFromBuild(source, expectedTarget = expectedTarget)
        val destination = File(projectDir, ".last-good-flash")
        val staging = File(projectDir, ".last-good-flash.tmp")
        staging.deleteRecursively(); staging.mkdirs()
        val root = JSONObject(File(source, "flasher_args.json").readText())
        File(staging, "flasher_args.json").writeText(root.toString(2))
        val flashFiles = root.getJSONObject("flash_files")
        flashFiles.keys().forEach { address ->
            val relative = flashFiles.getString(address)
            val src = File(source, relative).canonicalFile
            val dst = File(staging, relative)
            dst.parentFile?.mkdirs(); src.copyTo(dst, overwrite = true)
        }
        // Validate the complete staged set before replacing the previous known-good set.
        parse(staging.canonicalFile, File(staging, "flasher_args.json").readText(), plan.baudRate)
        destination.deleteRecursively()
        require(staging.renameTo(destination)) { "Unable to promote last valid firmware artifacts" }
    }

    private fun loadFromBuild(buildDir: File, baudRate: Int = 115200, expectedTarget: String? = null): FlashPlan {
        require(buildDir.isDirectory) { "Build directory not found" }
        val configFile = File(buildDir, "flasher_args.json")
        require(configFile.isFile) { "ESP-IDF flasher_args.json not found" }
        return parse(buildDir, configFile.readText(), baudRate, expectedTarget)
    }

    internal fun parse(buildDir: File, json: String, baudRate: Int = 115200, expectedTarget: String? = null): FlashPlan {
        val root = JSONObject(json)
        val extra = root.getJSONObject("extra_esptool_args")
        val chip = extra.getString("chip")
        require(chip in SupportedTargets.values) { "Built firmware target is unsupported: $chip" }
        if (expectedTarget != null) {
            require(chip == expectedTarget) { "Built firmware target is $chip, expected $expectedTarget" }
        }
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
