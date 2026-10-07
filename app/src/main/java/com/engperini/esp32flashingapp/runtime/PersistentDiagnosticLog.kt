package com.engperini.esp32flashingapp.runtime

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
import android.os.BatteryManager
import android.os.PowerManager
import android.os.Process
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PersistentDiagnosticLog {
    private const val MAX_BYTES = 2 * 1024 * 1024
    private val lock = Any()
    private val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    fun file(context: Context): File =
        File(context.filesDir, "projects/example/esp32-flashing-app.log")

    fun append(context: Context, event: String, detail: String = "") {
        runCatching {
            synchronized(lock) {
                val file = file(context)
                file.parentFile?.mkdirs()
                rotateIfNeeded(file)
                val line = buildString {
                    append(format.format(Date()))
                    append(" pid=").append(Process.myPid())
                    append(" ").append(event)
                    if (detail.isNotBlank()) append(" | ").append(detail.replace("\n", " \\n "))
                    append('\n')
                }
                file.appendText(line)
            }
        }
    }

    private var lastBuildSnapshotAt = 0L

    fun appendBuildOutput(context: Context, output: String) {
        val now = System.currentTimeMillis()
        synchronized(lock) {
            if (now - lastBuildSnapshotAt < 15_000L) return
            lastBuildSnapshotAt = now
        }
        append(context, "BUILD_OUTPUT", output.takeLast(2048))
    }

    fun deviceState(context: Context, intent: Intent? = null): String {
        val battery = context.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val plugged = battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percent = if (level >= 0 && scale > 0) level * 100 / scale else -1
        val power = context.getSystemService(PowerManager::class.java)
        val usb = context.getSystemService(UsbManager::class.java)
        val memory = ActivityManager.MemoryInfo().also {
            context.getSystemService(ActivityManager::class.java).getMemoryInfo(it)
        }
        val usbEventDevice = intent?.getParcelableExtra<android.hardware.usb.UsbDevice>(UsbManager.EXTRA_DEVICE)
        return buildString {
            append("battery=").append(percent).append('%')
            append(" plugged=").append(plugged)
            append(" status=").append(status)
            append(" interactive=").append(power.isInteractive)
            append(" powerSave=").append(power.isPowerSaveMode)
            append(" deviceIdle=").append(power.isDeviceIdleMode)
            append(" usbDevices=").append(usb.deviceList.size)
            if (usbEventDevice != null) {
                append(" usbEventVid=").append(usbEventDevice.vendorId)
                append(" usbEventPid=").append(usbEventDevice.productId)
                append(" usbEventName=").append(usbEventDevice.deviceName)
            }
            append(" availMemMB=").append(memory.availMem / (1024 * 1024))
            append(" lowMemory=").append(memory.lowMemory)
            append(" thresholdMB=").append(memory.threshold / (1024 * 1024))
            append(" appImportance=").append(ActivityManager.RunningAppProcessInfo().also { ActivityManager.getMyMemoryState(it) }.importance)
        }
    }

    private fun rotateIfNeeded(file: File) {
        if (!file.exists() || file.length() < MAX_BYTES) return
        val previous = File(file.parentFile, "esp32-flashing-app.previous.log")
        if (previous.exists()) previous.delete()
        file.renameTo(previous)
    }
}
