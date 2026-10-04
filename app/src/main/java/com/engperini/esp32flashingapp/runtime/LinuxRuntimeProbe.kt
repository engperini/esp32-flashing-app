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
        val proot = File(nativeDir, "libproot.so")
        val loader = File(nativeDir, "libproot-loader.so")
        require(busybox.exists()) { "Embedded BusyBox not found: $busybox" }
        require(proot.exists()) { "Embedded PRoot not found: $proot" }
        require(loader.exists()) { "Embedded PRoot loader not found: $loader" }
        // Execute the packaged PRoot itself first. This specifically validates the
        // Android W^X-safe native payload, not merely the JNI PTY/BusyBox path.
        val prootSession = ProotLauncher(host).startCustomSession(listOf(proot.absolutePath, "--version"))
            ?: error("Unable to execute embedded PRoot")
        val prootOut = StringBuilder()
        val probeBuf = ByteArray(4096)
        try {
            while (true) {
                val n = prootSession.read(probeBuf)
                if (n <= 0) break
                prootOut.append(String(probeBuf, 0, n))
                if (prootOut.contains("PRoot", ignoreCase = true)) break
            }
        } finally { prootSession.close() }
        require(prootOut.contains("PRoot", ignoreCase = true)) { "Embedded PRoot did not identify itself: $prootOut" }

        val binDir = File(host.prefixDir, "bin").apply { mkdirs() }
        val busyboxLink = File(binDir, "busybox")
        if (!busyboxLink.exists()) {
            android.system.Os.symlink(busybox.absolutePath, busyboxLink.absolutePath)
        }
        val session = ProotLauncher(host).startCustomSession(
            listOf(busyboxLink.absolutePath, "sh", "-c",
                "echo __APP_LINUX_RUNTIME_OK__; uname -m; echo uid=$(id -u); echo proot=embedded; echo loader=embedded")
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
        (prootOut.toString().trim() + "\n" + output.toString().trim())
    }
}
