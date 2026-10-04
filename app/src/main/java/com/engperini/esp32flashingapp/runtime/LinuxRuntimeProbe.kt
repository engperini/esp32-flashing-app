package com.engperini.esp32flashingapp.runtime

import android.content.Context
import id.or.oo.pr.engine.ProotHost
import id.or.oo.pr.engine.ProotLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class LinuxRuntimeProbe(private val context: Context) {
    private val host = object : ProotHost {
        override val prefixDir = File(context.filesDir, "runtime")
        override val homeDir = File(prefixDir, "home")
        override val packageName = context.packageName
        override val cacheDir = context.cacheDir
    }

    suspend fun probe(): String = withContext(Dispatchers.IO) {
        host.prefixDir.mkdirs(); host.homeDir.mkdirs()
        val nativeDir = context.applicationInfo.nativeLibraryDir
        val busybox = File(nativeDir, "libbusybox.so")
        require(busybox.exists()) { "Embedded BusyBox not found: $busybox" }
        val session = ProotLauncher(host).startCustomSession(
            listOf(busybox.absolutePath, "sh", "-c",
                "echo __APP_LINUX_RUNTIME_OK__; uname -m; echo uid=$(id -u); echo native=$0")
        ) ?: error("Unable to start embedded runtime process")
        val output = StringBuilder()
        val buffer = ByteArray(4096)
        try {
            while (true) {
                val n = session.read(buffer)
                if (n <= 0) break
                output.append(String(buffer, 0, n))
                if (output.contains("__APP_LINUX_RUNTIME_OK__")) {
                    // PTY may remain open briefly; marker proves execution and stdout handoff.
                    break
                }
            }
        } finally { session.close() }
        output.toString().trim()
    }
}
