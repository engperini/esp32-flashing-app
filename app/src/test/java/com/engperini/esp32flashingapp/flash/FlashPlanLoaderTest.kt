package com.engperini.esp32flashingapp.flash

import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Test

class FlashPlanLoaderTest {
    @Test fun parsesOfficialEspIdf55FlasherArgsStructure() {
        val build = createTempDirectory("idf-build").toFile()
        File(build,"bootloader").mkdirs()
        File(build,"partition_table").mkdirs()
        File(build,"bootloader/bootloader.bin").writeBytes(byteArrayOf(1))
        File(build,"partition_table/partition-table.bin").writeBytes(byteArrayOf(2))
        File(build,"app.bin").writeBytes(byteArrayOf(3))
        val json = """{
          "write_flash_args":["--flash_mode","dio","--flash_size","2MB","--flash_freq","80m"],
          "flash_settings":{"flash_mode":"dio","flash_size":"2MB","flash_freq":"80m"},
          "flash_files":{
            "0x0":"bootloader/bootloader.bin",
            "0x8000":"partition_table/partition-table.bin",
            "0x10000":"app.bin"
          },
          "extra_esptool_args":{"after":"hard_reset","before":"default_reset","stub":true,"chip":"esp32s3"}
        }"""
        val plan=FlashPlanLoader.parse(build,json)
        assertEquals("esp32s3",plan.chip)
        assertEquals(115200,plan.baudRate)
        assertEquals(listOf(0L,0x8000L,0x10000L),plan.images.map{it.address})
        assertEquals(listOf("bootloader.bin","partition-table.bin","app.bin"),plan.images.map{File(it.path).name})
    }
    @Test fun parsesOfficialEsp32FlasherArgsStructure() {
        val build = createTempDirectory("idf-build-esp32").toFile()
        File(build,"bootloader").mkdirs()
        File(build,"partition_table").mkdirs()
        File(build,"bootloader/bootloader.bin").writeBytes(byteArrayOf(1))
        File(build,"partition_table/partition-table.bin").writeBytes(byteArrayOf(2))
        File(build,"app.bin").writeBytes(byteArrayOf(3))
        val json = """{
          "flash_files":{
            "0x1000":"bootloader/bootloader.bin",
            "0x8000":"partition_table/partition-table.bin",
            "0x10000":"app.bin"
          },
          "extra_esptool_args":{"chip":"esp32"}
        }"""
        val plan=FlashPlanLoader.parse(build,json,expectedTarget="esp32")
        assertEquals("esp32",plan.chip)
        assertEquals(listOf(0x1000L,0x8000L,0x10000L),plan.images.map{it.address})
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsArtifactTargetDifferentFromRequestedTarget() {
        val build = createTempDirectory("idf-build-mismatch").toFile()
        File(build,"app.bin").writeBytes(byteArrayOf(1))
        val json = """{
          "flash_files":{"0x10000":"app.bin"},
          "extra_esptool_args":{"chip":"esp32"}
        }"""
        FlashPlanLoader.parse(build,json,expectedTarget="esp32s3")
    }

}
