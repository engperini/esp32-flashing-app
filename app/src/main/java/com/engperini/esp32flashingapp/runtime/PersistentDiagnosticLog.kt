package com.engperini.esp32flashingapp.runtime

import android.content.Context
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

    private fun rotateIfNeeded(file: File) {
        if (!file.exists() || file.length() < MAX_BYTES) return
        val previous = File(file.parentFile, "esp32-flashing-app.previous.log")
        if (previous.exists()) previous.delete()
        file.renameTo(previous)
    }
}
