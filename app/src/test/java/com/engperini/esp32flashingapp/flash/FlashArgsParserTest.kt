package com.engperini.esp32flashingapp.flash

import org.junit.Assert.assertEquals
import org.junit.Test

class FlashArgsParserTest {
    @Test fun parsesEspIdfAddressFilePairs() {
        val images = FlashArgsParser.parse(listOf(
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
}
