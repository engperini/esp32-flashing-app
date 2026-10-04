package com.engperini.esp32flashingapp.flash

import org.junit.Assert.assertEquals
import org.junit.Test

class FlashArgsParserTest {
    @Test fun parsesEspIdfAddressFilePairs() {
        val images = FlashArgsParser.parse(listOf(
            "--flash_mode", "dio", "--flash_freq", "80m", "--flash_size", "2MB",
            "0x0", "bootloader/bootloader.bin",
            "0x8000", "partition_table/partition-table.bin",
            "0x10000", "esp32-flashing-test.bin"
        ))
        assertEquals(3, images.size)
        assertEquals(0x0, images[0].address)
        assertEquals("bootloader/bootloader.bin", images[0].path)
        assertEquals(0x8000, images[1].address)
        assertEquals(0x10000, images[2].address)
    }

    @Test fun parsesMultilineFlashArgsAndQuotedPath() {
        val images = FlashArgsParser.parse(
            """--flash_mode dio --flash_freq 80m --flash_size 2MB
               0x0 bootloader/bootloader.bin
               0x8000 partition_table/partition-table.bin
               0x10000 "firmware with spaces.bin"
            """.trimIndent()
        )
        assertEquals(listOf(
            FlashImage(0x0, "bootloader/bootloader.bin"),
            FlashImage(0x8000, "partition_table/partition-table.bin"),
            FlashImage(0x10000, "firmware with spaces.bin")
        ), images)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnterminatedQuote() {
        FlashArgsParser.parse("""0x10000 "broken.bin""")
    }
}
