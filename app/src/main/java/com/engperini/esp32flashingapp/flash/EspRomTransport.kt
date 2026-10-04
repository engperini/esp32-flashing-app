package com.engperini.esp32flashingapp.flash

import com.engperini.esp32flashingapp.device.UsbDeviceEngine
import kotlinx.coroutines.delay
import java.io.ByteArrayOutputStream

class EspRomTransport(private val device: UsbDeviceEngine) {
    companion object {
        private const val ESP_SYNC = 0x08
        private const val SLIP_END = 0xC0
        private const val SLIP_ESC = 0xDB
        private const val SLIP_ESC_END = 0xDC
        private const val SLIP_ESC_ESC = 0xDD
    }

    suspend fun sync(): Boolean {
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
        device.write(slipEncode(packet), 1500)
        val buffer = ByteArray(1024)
        repeat(8) {
            val n = runCatching { device.read(buffer, 500) }.getOrDefault(0)
            if (n > 0 && containsSyncResponse(buffer, n)) return true
            delay(50)
        }
        return false
    }

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
