package com.engperini.esp32flashingapp.flash

import java.io.File

object FlashPlanLoader {
    fun load(projectDir: File, chip: String = "esp32s3", baudRate: Int = 115200): FlashPlan {
        val buildDir = File(projectDir, "build").canonicalFile
        require(buildDir.isDirectory) { "Build directory not found" }
        val args = listOf(File(buildDir, "flash_args"), File(buildDir, "flash_args.txt"))
            .firstOrNull { it.isFile } ?: error("ESP-IDF flash_args not found")
        val images = FlashArgsParser.parse(args.readText()).map { image ->
            val file = File(buildDir, image.path).canonicalFile
            require(file.path.startsWith(buildDir.path + File.separator)) { "Flash image escapes build directory: undefined" }
            require(file.isFile) { "Flash image not found: undefined" }
            FlashImage(image.address, file.path)
        }
        require(images.isNotEmpty()) { "No flash images found in ESP-IDF flash_args" }
        return FlashPlan(chip, baudRate, images)
    }
}
