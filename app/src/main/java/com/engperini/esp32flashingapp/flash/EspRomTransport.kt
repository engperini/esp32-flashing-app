package com.engperini.esp32flashingapp.flash

import com.engperini.esp32flashingapp.device.UsbDeviceEngine
import kotlinx.coroutines.delay
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import kotlin.math.min

class EspRomTransport(private val device: UsbDeviceEngine) {
    companion object {
        private const val ESP_FLASH_BEGIN = 0x02
        private const val ESP_FLASH_DATA = 0x03
        private const val ESP_SYNC = 0x08
        private const val ESP_WRITE_REG = 0x09
        private const val ESP_SPI_SET_PARAMS = 0x0B
        private const val ESP_SPI_ATTACH = 0x0D
        private const val ESP_SPI_FLASH_MD5 = 0x13
        private const val FLASH_WRITE_SIZE = 0x400
        private const val FLASH_SIZE_BYTES = 2 * 1024 * 1024
        private const val CHECKSUM_MAGIC = 0xEF
        private const val SLIP_END = 0xC0
        private const val SLIP_ESC = 0xDB
        private const val SLIP_ESC_END = 0xDC
        private const val SLIP_ESC_ESC = 0xDD
        private const val RTC_CNTL_WDTCONFIG0_REG = 0x60008098
        private const val RTC_CNTL_WDTCONFIG1_REG = 0x6000809C
        private const val RTC_CNTL_WDTWPROTECT_REG = 0x600080B0
        private const val RTC_CNTL_WDT_WKEY = 0x50D83AA1
    }

    suspend fun sync(): Boolean = syncAttempt(1)

    suspend fun syncEsp32(attempts: Int = 7): Boolean {
        repeat(attempts) {
            if (syncAttempt(1)) return true
            delay(100)
        }
        return false
    }

    private suspend fun syncAttempt(attempts: Int): Boolean {
        val payload = ByteArray(36)
        payload[0] = 0x07
        payload[1] = 0x07
        payload[2] = 0x12
        payload[3] = 0x20
        for (i in 4 until payload.size) payload[i] = 0x55
        val packet = ByteArrayOutputStream().apply {
            write(0x00); write(ESP_SYNC)
            write(payload.size and 0xff); write((payload.size ushr 8) and 0xff)
            write(0x00); write(0x00); write(0x00); write(0x00)
            write(payload)
        }.toByteArray()
        repeat(attempts) {
            device.write(slipEncode(packet), 1500)
            val buffer = ByteArray(1024)
            val decoder = SyncResponseDecoder()
            repeat(8) {
                val n = runCatching { device.read(buffer, 500) }.getOrDefault(0)
                if (n > 0 && decoder.accept(buffer, n)) return true
                delay(50)
            }
        }
        return false
    }


    suspend fun flash(plan: FlashPlan, onProgress: (String) -> Unit = {}) {
        command(ESP_SPI_ATTACH, le32(0) + byteArrayOf(0, 0, 0, 0), 3000)
        command(ESP_SPI_SET_PARAMS, le32(0) + le32(FLASH_SIZE_BYTES) + le32(64 * 1024) + le32(4 * 1024) + le32(256) + le32(0xFFFF), 3000)
        val total = plan.images.sumOf { File(it.path).length() }
        var written = 0L
        plan.images.forEachIndexed { index, image ->
            val data = File(image.path).readBytes()
            val blocks = (data.size + FLASH_WRITE_SIZE - 1) / FLASH_WRITE_SIZE
            onProgress("Image " + (index + 1) + "/" + plan.images.size + ": 0x" + image.address.toString(16) + " • " + data.size + " bytes")
            command(ESP_FLASH_BEGIN, le32(data.size) + le32(blocks) + le32(FLASH_WRITE_SIZE) + le32(image.address) + le32(0), eraseTimeout(data.size))
            for (seq in 0 until blocks) {
                val from = seq * FLASH_WRITE_SIZE
                val count = min(FLASH_WRITE_SIZE, data.size - from)
                val block = ByteArray(FLASH_WRITE_SIZE) { 0xFF.toByte() }
                data.copyInto(block, 0, from, from + count)
                command(ESP_FLASH_DATA, le32(block.size) + le32(seq) + le32(0) + le32(0) + block, 5000, checksum(block))
                written += count
                if (seq == blocks - 1 || seq % 16 == 0) onProgress("Writing flash… " + (written * 100 / total) + "%")
            }
            val expected = MessageDigest.getInstance("MD5").digest(data).joinToString("") { "%02x".format(it) }
            val response = command(ESP_SPI_FLASH_MD5, le32(image.address) + le32(data.size) + le32(0) + le32(0), 12000)
            val actual = response.copyOfRange(0, min(32, response.size)).toString(Charsets.US_ASCII).lowercase()
            check(actual == expected) { "MD5 verification failed at 0x" + image.address.toString(16) }
            onProgress("Verified 0x" + image.address.toString(16) + " • MD5 OK")
        }
    }

    suspend fun watchdogReset() {
        writeReg(RTC_CNTL_WDTWPROTECT_REG, RTC_CNTL_WDT_WKEY)
        writeReg(RTC_CNTL_WDTCONFIG1_REG, 2000)
        writeReg(RTC_CNTL_WDTCONFIG0_REG, (1 shl 31) or (5 shl 28) or (1 shl 8) or 2)
        writeReg(RTC_CNTL_WDTWPROTECT_REG, 0, waitResponse = false)
        delay(500)
    }

    private suspend fun writeReg(address: Int, value: Int, mask: Int = -1, waitResponse: Boolean = true) {
        val payload = le32(address) + le32(value) + le32(mask) + le32(0)
        if (waitResponse) command(ESP_WRITE_REG, payload, 3000)
        else {
            val packet = byteArrayOf(0x00, ESP_WRITE_REG.toByte()) + le16(payload.size) + le32(0) + payload
            device.write(slipEncode(packet), 1000)
        }
    }

    private suspend fun command(op: Int, payload: ByteArray, timeoutMs: Int, checksum: Int = 0): ByteArray {
        val packet = byteArrayOf(0x00, op.toByte()) + le16(payload.size) + le32(checksum) + payload
        device.write(slipEncode(packet), 5000)
        val buffer = ByteArray(2048)
        val frame = ByteArrayOutputStream()
        var escaped = false
        repeat(100) {
            val n = runCatching { device.read(buffer, timeoutMs) }.getOrDefault(0)
            for (i in 0 until n) {
                val b = buffer[i].toInt() and 0xff
                if (b == SLIP_END) {
                    val p = frame.toByteArray()
                    frame.reset(); escaped = false
                    if (p.size >= 8 && (p[0].toInt() and 0xff) == 1 && (p[1].toInt() and 0xff) == op) {
                        val len = (p[2].toInt() and 0xff) or ((p[3].toInt() and 0xff) shl 8)
                        if (p.size >= 8 + len) {
                            val data = p.copyOfRange(8, 8 + len)
                            check(data.size >= 4) { "Short ROM status for command 0x" + op.toString(16) }
                            val status = data[data.size - 4].toInt() and 0xff
                            val error = data[data.size - 3].toInt() and 0xff
                            check(status == 0) { "ROM command 0x" + op.toString(16) + " failed: status=" + status + " error=0x" + error.toString(16) }
                            return data.copyOf(data.size - 4)
                        }
                    }
                } else if (escaped) {
                    frame.write(if (b == SLIP_ESC_END) SLIP_END else if (b == SLIP_ESC_ESC) SLIP_ESC else b); escaped = false
                } else if (b == SLIP_ESC) escaped = true else frame.write(b)
            }
            delay(10)
        }
        error("No matching ROM response for command 0x" + op.toString(16))
    }

    private fun checksum(data: ByteArray): Int = data.fold(CHECKSUM_MAGIC) { acc, b -> acc xor (b.toInt() and 0xff) }
    private fun eraseTimeout(size: Int): Int = 5000 + ((size + 1024 * 1024 - 1) / (1024 * 1024)) * 10000
    private fun le16(v: Int) = byteArrayOf(v.toByte(), (v ushr 8).toByte())
    private fun le32(v: Int) = byteArrayOf(v.toByte(), (v ushr 8).toByte(), (v ushr 16).toByte(), (v ushr 24).toByte())
    private fun le32(v: Long) = le32(v.toInt())

    private fun containsSyncResponse(data: ByteArray, length: Int): Boolean {
        val frame = ByteArrayOutputStream()
        var escaped = false
        for (i in 0 until length) {
            val b = data[i].toInt() and 0xff
            if (b == SLIP_END) {
                val p = frame.toByteArray()
                if (p.size >= 2 && (p[0].toInt() and 0xff) == 0x01 && (p[1].toInt() and 0xff) == ESP_SYNC) return true
                frame.reset(); escaped = false
            } else if (escaped) {
                frame.write(if (b == SLIP_ESC_END) SLIP_END else if (b == SLIP_ESC_ESC) SLIP_ESC else b)
                escaped = false
            } else if (b == SLIP_ESC) escaped = true else frame.write(b)
        }
        return false
    }

    private class SyncResponseDecoder {
        private val frame = ByteArrayOutputStream()
        private var escaped = false
        fun accept(data: ByteArray, length: Int): Boolean {
            for (i in 0 until length) {
                val b = data[i].toInt() and 0xff
                if (b == SLIP_END) {
                    val p = frame.toByteArray()
                    if (p.size >= 2 && (p[0].toInt() and 0xff) == 0x01 && (p[1].toInt() and 0xff) == ESP_SYNC) return true
                    frame.reset(); escaped = false
                } else if (escaped) {
                    frame.write(if (b == SLIP_ESC_END) SLIP_END else if (b == SLIP_ESC_ESC) SLIP_ESC else b)
                    escaped = false
                } else if (b == SLIP_ESC) escaped = true else frame.write(b)
            }
            return false
        }
    }

    private fun slipEncode(data: ByteArray): ByteArray = ByteArrayOutputStream().apply {
        write(SLIP_END)
        data.forEach {
            when (val b = it.toInt() and 0xff) {
                SLIP_END -> { write(SLIP_ESC); write(SLIP_ESC_END) }
                SLIP_ESC -> { write(SLIP_ESC); write(SLIP_ESC_ESC) }
                else -> write(b)
            }
        }
        write(SLIP_END)
    }.toByteArray()
}
