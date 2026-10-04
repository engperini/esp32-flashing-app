package com.engperini.esp32flashingapp.runtime

import android.content.Context
import android.system.Os
import id.or.oo.pr.engine.ProotHost
import id.or.oo.pr.engine.ProotLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class IdfBuildExecutor(private val context: Context) {
    private val host = object : ProotHost {
        override val prefixDir = File(context.filesDir, "runtime")
        override val homeDir = File(prefixDir, "home")
        override val packageName = context.packageName
        override val cacheDir = context.cacheDir
    }

    suspend fun prepare(target: String, onProgress: (SetupProgress) -> Unit = {}): String = withContext(Dispatchers.IO) {
        val launcher = ProotLauncher(host)
        val cli = prepareLauncher()
        val rootfs = File(host.prefixDir, "var/lib/pr/containers/${IdfRuntimePlan.GUEST_ALIAS}/rootfs")
        val osRelease = File(rootfs, "etc/os-release")
        if (!osRelease.exists()) {
            onProgress(SetupProgress(SetupStep.RUNTIME, "Downloading Debian 12 ARM64…"))
            val output = executeRaw(launcher, listOf(cli.absolutePath, "install", "docker.io/library/debian:bookworm-slim", "--override-alias", IdfRuntimePlan.GUEST_ALIAS)) {
                onProgress(SetupProgress(SetupStep.RUNTIME, "Installing Debian 12 ARM64…", it))
            }
            require(osRelease.exists()) { "Debian installation did not produce a usable rootfs: ${output.takeLast(3000)}" }
            onProgress(SetupProgress(SetupStep.RUNTIME, "Debian runtime ready", output.takeLast(2000), 1f, true))
        } else onProgress(SetupProgress(SetupStep.RUNTIME, "Debian runtime ready", completed = true, fraction = 1f))
        val log = StringBuilder()
        IdfBuildStages.stages(target).forEach { stage ->
            if (IdfBuildStages.isComplete(rootfs, stage)) {
                log.appendLine("${stage.name}: ready")
            } else {
                val markerDir = File(rootfs, "opt/esp/.app-state").apply { mkdirs() }
                val success = "__APP_STAGE_${stage.name.uppercase()}_OK__"
                onProgress(SetupProgress(if(stage.name == "common") SetupStep.ESP_IDF else SetupStep.TOOLCHAIN, if(stage.name == "common") "Installing dependencies and ESP-IDF 5.5…" else "Installing ESP32-S3 toolchain…"))
                val output = executeStage(launcher, cli, stage.command + " && echo " + success, success, {
                    onProgress(SetupProgress(if(stage.name == "common") SetupStep.ESP_IDF else SetupStep.TOOLCHAIN, if(stage.name == "common") "Installing dependencies and ESP-IDF 5.5…" else "Installing ESP32-S3 toolchain…", it))
                })
                File(markerDir, stage.marker).writeText("ok")
                log.appendLine("${stage.name}: completed")
                log.appendLine(output.takeLast(2000))
                onProgress(SetupProgress(if(stage.name == "common") SetupStep.ESP_IDF else SetupStep.TOOLCHAIN, "${stage.name}: ready", output.takeLast(2000), 1f, true))
            }
        }
        onProgress(SetupProgress(SetupStep.READY, "ESP-IDF 5.5 / $target ready", log.toString(), 1f, true))
        log.toString()
    }

    suspend fun doctor(target: String, onOutput: (String) -> Unit = {}): String = withContext(Dispatchers.IO) {
        require(target.matches(Regex("[a-z0-9]+"))) { "Invalid ESP target" }
        val rootfs = File(host.prefixDir, "var/lib/pr/containers/${IdfRuntimePlan.GUEST_ALIAS}/rootfs")
        require(File(rootfs, "etc/os-release").exists()) { "Debian runtime is not installed" }
        val launcher = ProotLauncher(host)
        val cli = prepareLauncher()
        val success = "__APP_IDF_DOCTOR_OK__"
        val command = "export IDF_TOOLS_PATH='${IdfRuntimePlan.IDF_TOOLS_PATH}' IDF_PATH='${IdfRuntimePlan.IDF_PATH}' && " +
            "cd '${IdfRuntimePlan.IDF_PATH}' && . ./export.sh && " +
            "echo '--- ESP-IDF ---' && idf.py --version && " +
            "echo '--- Python ---' && python3 --version && " +
            "echo '--- CMake ---' && cmake --version | head -n 1 && " +
            "echo '--- Ninja ---' && ninja --version && " +
            "echo '--- Target toolchain ---' && " +
            "case '$target' in esp32s3) xtensa-esp32s3-elf-gcc --version | head -n 1 ;; *) echo 'Target toolchain check not defined: $target'; exit 2 ;; esac && " +
            "echo $success"
        executeStage(launcher, cli, command, success, onOutput, "ESP-IDF Doctor")
    }

    suspend fun buildPrepared(project: File, target: String, onOutput: (String) -> Unit = {}): String = withContext(Dispatchers.IO) {
        require(project.isDirectory) { "Project directory not found: $project" }
        val rootfs = File(host.prefixDir, "var/lib/pr/containers/${IdfRuntimePlan.GUEST_ALIAS}/rootfs")
        IdfBuildStages.stages(target).forEach { require(IdfBuildStages.isComplete(rootfs, it)) { "Environment is not ready: ${it.name}" } }
        val launcher = ProotLauncher(host)
        val cli = prepareLauncher()
        val guestProject = project.absolutePath
        val success = "__APP_IDF_BUILD_OK__"
        val command = "export IDF_TOOLS_PATH='${IdfRuntimePlan.IDF_TOOLS_PATH}' IDF_PATH='${IdfRuntimePlan.IDF_PATH}' && " +
            "cd '${IdfRuntimePlan.IDF_PATH}' && . ./export.sh >/dev/null && " +
            "cd '$guestProject' && idf.py set-target '$target' >/dev/null && idf.py build && echo $success"
        executeStage(launcher, cli, command, success, onOutput, "Build")
    }

    private fun prepareLauncher(): File {
        host.prefixDir.mkdirs(); host.homeDir.mkdirs()
        val nativeDir = File(context.applicationInfo.nativeLibraryDir)
        val binDir = File(host.prefixDir, "bin").apply { mkdirs() }
        mapOf("busybox" to "libbusybox.so", "proot" to "libproot.so", "pr-cli" to "libpr-cli.so").forEach { (name, lib) ->
            val target = File(nativeDir, lib)
            require(target.exists()) { "Embedded runtime file missing: $lib" }
            val dest = File(binDir, name)
            // APK updates move nativeLibraryDir. Always refresh these tiny launcher links.
            if (dest.exists() || runCatching { dest.canonicalPath != dest.absolutePath }.getOrDefault(false)) dest.delete()
            Os.symlink(target.absolutePath, dest.absolutePath)
        }
        return File(nativeDir, "libpr-cli.so")
    }

    private fun executeStage(launcher: ProotLauncher, cli: File, command: String, success: String, onOutput: (String) -> Unit = {}, operation: String = "Provisioning stage"): String {
        val session = launcher.startCustomSession(
            listOf(cli.absolutePath, "login", IdfRuntimePlan.GUEST_ALIAS, "--", command)
        ) ?: error("Unable to start $operation")
        val output = StringBuilder()
        val buffer = ByteArray(8192)
        try {
            while (true) {
                val count = session.read(buffer)
                if (count <= 0) break
                output.append(String(buffer, 0, count))
                onOutput(output.toString().takeLast(6000))
            }
        } finally {
            session.close()
        }
        if (!output.contains(success)) {
            val lines = output.lines()
            val diagnostic = lines.filter { line ->
                val s = line.lowercase()
                s.contains("error:") || s.contains("fatal:") || s.contains("failed:") || s.startsWith("failed") || s.contains("ninja:")
            }.takeLast(40).joinToString("\n").ifBlank { output.takeLast(4000) }
            error("$operation failed:\n$diagnostic")
        }
        return output.toString()
    }

    private fun executeRaw(launcher: ProotLauncher, args: List<String>, onOutput: (String) -> Unit): String {
        val session = launcher.startCustomSession(args) ?: error("Unable to start runtime setup")
        val output = StringBuilder()
        val buffer = ByteArray(8192)
        try {
            while (true) {
                val count = session.read(buffer)
                if (count <= 0) break
                output.append(String(buffer, 0, count))
                onOutput(output.toString().takeLast(6000))
            }
        } finally { session.close() }
        return output.toString()
    }
}
