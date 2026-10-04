package com.engperini.esp32flashingapp.runtime

import android.content.Context
import android.system.Os
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
        val nativeDir = File(context.applicationInfo.nativeLibraryDir)
        val busybox = File(nativeDir, "libbusybox.so")
        val proot = File(nativeDir, "libproot.so")
        val loader = File(nativeDir, "libproot-loader.so")
        val cli = File(nativeDir, "libpr-cli.so")
        listOf(busybox, proot, loader, cli).forEach { require(it.exists()) { "Embedded runtime file not found: $it" } }

        val binDir = File(host.prefixDir, "bin").apply { mkdirs() }
        fun link(name: String, target: File) {
            val dest = File(binDir, name)
            if (!dest.exists()) Os.symlink(target.absolutePath, dest.absolutePath)
        }
        link("busybox", busybox); link("proot", proot); link("pr-cli", cli)

        val launcher = ProotLauncher(host)
        val version = capture(launcher, listOf(proot.absolutePath, "--version"), "PRoot")
        val rootfs = File(host.prefixDir, "var/lib/pr/containers/alpine/rootfs")
        val installOutput = if (!File(rootfs, "etc/os-release").exists()) {
            capture(
                launcher,
                listOf(cli.absolutePath, "install", "docker.io/library/alpine:3.21", "--override-alias", "alpine"),
                "installation completed"
            )
        } else "Alpine rootfs already provisioned"

        val guest = capture(
            launcher,
            listOf(cli.absolutePath, "login", "alpine", "--",
                "printf", "__APP_LINUX_GUEST_OK__\\n", "&&", "cat", "/etc/os-release", "&&", "uname", "-m"),
            "__APP_LINUX_GUEST_OK__"
        )
        version.trim() + "\n" + installOutput.trim() + "\n" + guest.trim()
    }

    private fun capture(
        launcher: ProotLauncher,
        args: List<String>,
        successMarker: String,
    ): String {
        val session = launcher.startCustomSession(args) ?: error("Unable to execute: ${args.firstOrNull()}")
        val output = StringBuilder()
        val buffer = ByteArray(8192)
        try {
            while (true) {
                val n = session.read(buffer)
                if (n <= 0) break
                output.append(String(buffer, 0, n))
                if (output.contains(successMarker, ignoreCase = true)) break
            }
        } finally { session.close() }
        require(output.contains(successMarker, ignoreCase = true)) {
            "Runtime command did not reach '$successMarker': $output"
        }
        return output.toString()
    }
}
