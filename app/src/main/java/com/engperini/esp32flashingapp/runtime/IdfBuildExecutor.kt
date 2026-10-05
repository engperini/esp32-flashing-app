package com.engperini.esp32flashingapp.runtime

import android.content.Context
import android.system.Os
import id.or.oo.pr.engine.ProotHost
import id.or.oo.pr.engine.ProotLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class IdfBuildExecutor(private val context: Context) {
    companion object {
        private const val LOG_TAIL_CHARS = 65536
        private const val UI_TAIL_CHARS = 8192
    }

    private val host = object : ProotHost {
        override val prefixDir = File(context.filesDir, "runtime")
        override val homeDir = File(prefixDir, "home")
        override val packageName = context.packageName
        override val cacheDir = context.cacheDir
    }

    suspend fun prepare(target: String, onProgress: (SetupProgress) -> Unit = {}): String = withContext(Dispatchers.IO) {
        requireTarget(target)
        val launcher = ProotLauncher(host)
        val cli = prepareLauncher()
        val rootfs = File(host.prefixDir, "var/lib/pr/containers/" + IdfRuntimePlan.GUEST_ALIAS + "/rootfs")
        val osRelease = File(rootfs, "etc/os-release")
        if (!osRelease.exists()) {
            onProgress(SetupProgress(SetupStep.RUNTIME, "Downloading Debian 12 ARM64…"))
            val output = executeRaw(launcher, listOf(cli.absolutePath, "install", "docker.io/library/debian:bookworm-slim", "--override-alias", IdfRuntimePlan.GUEST_ALIAS)) {
                onProgress(SetupProgress(SetupStep.RUNTIME, "Installing Debian 12 ARM64…", it))
            }
            require(osRelease.exists()) { "Debian installation did not produce a usable rootfs: " + output.takeLast(3000) }
            onProgress(SetupProgress(SetupStep.RUNTIME, "Debian runtime ready", output.takeLast(2000), 1f, true))
        } else {
            onProgress(SetupProgress(SetupStep.RUNTIME, "Debian runtime ready", completed = true, fraction = 1f))
        }

        val log = TailBuffer(LOG_TAIL_CHARS)
        IdfBuildStages.stages(target).forEach { stage ->
            if (IdfBuildStages.isComplete(rootfs, stage)) {
                log.append(stage.name + ": ready\n")
            } else {
                val markerDir = File(rootfs, "opt/esp/.app-state").apply { mkdirs() }
                val success = "__APP_STAGE_" + stage.name.uppercase() + "_OK__"
                val step = if (stage.name == "common") SetupStep.ESP_IDF else SetupStep.TOOLCHAIN
                val status = if (stage.name == "common") "Installing dependencies and ESP-IDF 5.5…" else "Installing ESP32-S3 toolchain…"
                onProgress(SetupProgress(step, status))
                val output = executeStage(launcher, cli, stage.command + " && echo " + success, success, {
                    onProgress(SetupProgress(step, status, it))
                })
                File(markerDir, stage.marker).writeText("ok")
                log.append(stage.name + ": completed\n")
                log.append(output.takeLast(2000))
                onProgress(SetupProgress(step, stage.name + ": ready", output.takeLast(2000), 1f, true))
            }
        }
        onProgress(SetupProgress(SetupStep.READY, "ESP-IDF 5.5 / " + target + " ready", log.value(), 1f, true))
        log.value()
    }

    suspend fun doctor(target: String, onOutput: (String) -> Unit = {}): String = withContext(Dispatchers.IO) {
        requireTarget(target)
        requireRuntimeReady()
        val launcher = ProotLauncher(host)
        val cli = prepareLauncher()
        val success = "__APP_IDF_DOCTOR_OK__"
        val command = "export IDF_TOOLS_PATH=" + shQuote(IdfRuntimePlan.IDF_TOOLS_PATH) + " IDF_PATH=" + shQuote(IdfRuntimePlan.IDF_PATH) + " && " +
            "cd " + shQuote(IdfRuntimePlan.IDF_PATH) + " && . ./export.sh && " +
            "echo '--- ESP-IDF ---' && idf.py --version && echo '--- Python ---' && python3 --version && " +
            "echo '--- CMake ---' && cmake --version | head -n 1 && echo '--- Ninja ---' && ninja --version && " +
            "echo '--- Target toolchain ---' && case " + shQuote(target) + " in esp32s3) xtensa-esp32s3-elf-gcc --version | head -n 1 ;; *) exit 2 ;; esac && echo " + success
        executeStage(launcher, cli, command, success, onOutput, "ESP-IDF Doctor")
    }

    suspend fun buildPrepared(project: File, target: String, onOutput: (String) -> Unit = {}): String = withContext(Dispatchers.IO) {
        requireTarget(target)
        require(project.isDirectory) { "Project directory not found: " + project }
        val rootfs = requireRuntimeReady()
        IdfBuildStages.stages(target).forEach { require(IdfBuildStages.isComplete(rootfs, it)) { "Environment is not ready: " + it.name } }

        val launcher = ProotLauncher(host)
        val cli = prepareLauncher()
        val success = "__APP_IDF_BUILD_OK__"
        val exitPrefix = "__APP_IDF_BUILD_EXIT__"
        val qTarget = shQuote(target)

        // Keep ESP-IDF's native incremental CMake/Ninja/ccache behavior.
        // Never call set-target here: ESP-IDF 5.5 documents that it performs a fullclean.
        val command = "export IDF_TOOLS_PATH=" + shQuote(IdfRuntimePlan.IDF_TOOLS_PATH) +
            " IDF_PATH=" + shQuote(IdfRuntimePlan.IDF_PATH) +
            " IDF_CCACHE_ENABLE=1 && " +
            "cd " + shQuote(IdfRuntimePlan.IDF_PATH) + " && . ./export.sh >/dev/null && " +
            "cd " + shQuote(project.canonicalPath) + " && " +
            "if [ -f sdkconfig ]; then " +
            "configured=\$(sed -n 's/^CONFIG_IDF_TARGET=\"\\([^\"]*\\)\"/\\1/p' sdkconfig | head -n 1); " +
            "if [ -n \"\$configured\" ] && [ \"\$configured\" != " + qTarget + " ]; then " +
            "echo \"ERROR: Project target is \$configured, expected " + target + ". Target change must be explicit.\"; exit 64; fi; " +
            "else export IDF_TARGET=" + qTarget + "; fi; " +
            "idf.py build; rc=\$?; echo " + exitPrefix + "\$rc; " +
            "if [ \$rc -eq 0 ]; then echo " + success + "; fi; exit \$rc"
        executeStage(launcher, cli, command, success, onOutput, "Build")
    }

    private fun requireRuntimeReady(): File {
        val rootfs = File(host.prefixDir, "var/lib/pr/containers/" + IdfRuntimePlan.GUEST_ALIAS + "/rootfs")
        require(File(rootfs, "etc/os-release").exists()) { "Debian runtime is not installed" }
        return rootfs
    }

    private fun requireTarget(target: String) {
        require(target.matches(Regex("[a-z0-9]+"))) { "Invalid ESP target" }
    }

    private fun prepareLauncher(): File {
        host.prefixDir.mkdirs()
        host.homeDir.mkdirs()
        val nativeDir = File(context.applicationInfo.nativeLibraryDir)
        val binDir = File(host.prefixDir, "bin").apply { mkdirs() }
        mapOf("busybox" to "libbusybox.so", "proot" to "libproot.so", "pr-cli" to "libpr-cli.so").forEach { (name, lib) ->
            val target = File(nativeDir, lib)
            require(target.exists()) { "Embedded runtime file missing: " + lib }
            val dest = File(binDir, name)
            if (dest.exists() || runCatching { dest.canonicalPath != dest.absolutePath }.getOrDefault(false)) dest.delete()
            Os.symlink(target.absolutePath, dest.absolutePath)
        }
        return File(nativeDir, "libpr-cli.so")
    }

    private fun executeStage(launcher: ProotLauncher, cli: File, command: String, success: String, onOutput: (String) -> Unit = {}, operation: String = "Provisioning stage"): String {
        val session = launcher.startCustomSession(listOf(cli.absolutePath, "login", IdfRuntimePlan.GUEST_ALIAS, "--", command))
            ?: error("Unable to start " + operation)
        val output = TailBuffer(LOG_TAIL_CHARS)
        val buffer = ByteArray(8192)
        try {
            while (true) {
                val count = session.read(buffer)
                if (count <= 0) break
                output.append(String(buffer, 0, count))
                onOutput(output.value().takeLast(UI_TAIL_CHARS))
            }
        } finally {
            session.close()
        }

        val tail = output.value()
        if (!tail.contains(success)) {
            val exitCode = Regex("__APP_IDF_BUILD_EXIT__(\\d+)").find(tail)?.groupValues?.getOrNull(1)
            val diagnostic = tail.lines().filter { line ->
                val s = line.lowercase()
                s.contains("error:") || s.contains("fatal:") || s.contains("failed:") || s.startsWith("failed") ||
                    s.contains("ninja:") || s.contains("killed") || s.contains("no space left") ||
                    s.contains("cannot allocate memory") || s.contains("out of memory")
            }.takeLast(60).joinToString("\n")
            val cause = when {
                diagnostic.isNotBlank() -> diagnostic
                exitCode != null -> operation + " process exited with code " + exitCode + ".\n" + tail.takeLast(6000)
                else -> operation + " process ended without an exit marker. Android/runtime may have terminated it.\n" + tail.takeLast(6000)
            }
            error(operation + " failed:\n" + cause)
        }
        return tail
    }

    private fun executeRaw(launcher: ProotLauncher, args: List<String>, onOutput: (String) -> Unit): String {
        val session = launcher.startCustomSession(args) ?: error("Unable to start runtime setup")
        val output = TailBuffer(LOG_TAIL_CHARS)
        val buffer = ByteArray(8192)
        try {
            while (true) {
                val count = session.read(buffer)
                if (count <= 0) break
                output.append(String(buffer, 0, count))
                onOutput(output.value().takeLast(UI_TAIL_CHARS))
            }
        } finally {
            session.close()
        }
        return output.value()
    }

    private fun shQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    private class TailBuffer(private val maxChars: Int) {
        private val data = StringBuilder()
        fun append(text: String) {
            data.append(text)
            if (data.length > maxChars) data.delete(0, data.length - maxChars)
        }
        fun value(): String = data.toString()
    }
}
