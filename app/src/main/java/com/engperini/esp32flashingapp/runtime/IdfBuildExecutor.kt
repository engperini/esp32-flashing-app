package com.engperini.esp32flashingapp.runtime

import android.app.ActivityManager
import android.content.Context
import android.os.PowerManager
import android.system.Os
import id.or.oo.pr.engine.ProotHost
import id.or.oo.pr.engine.ProotLauncher
import id.or.oo.pr.engine.PtyNative
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class IdfBuildExecutor(private val context: Context) {
    class BuildCancelledException : RuntimeException("Build cancelled")
    class SessionEndedWithoutMarkerException(operation: String, val outputTail: String) : RuntimeException(operation + " session ended unexpectedly")
    private val cancelled = AtomicBoolean(false)
    private val activeCancel = AtomicReference<(() -> Unit)?>(null)

    fun cancelCurrentBuild() {
        PersistentDiagnosticLog.append(context.applicationContext, "BUILD_CANCEL_REQUEST", "caller=cancelCurrentBuild " + androidDiagnostics().replace('\n', ' '))
        cancelled.set(true)
        runCatching { activeCancel.getAndSet(null)?.invoke() }
    }
    companion object {
        private const val LOG_TAIL_CHARS = 65536
        private const val UI_TAIL_CHARS = 8192
        // Android 12+ limits app child/phantom processes while backgrounded.
        // ESP-IDF/Ninja fan-out can cross that boundary even while our foreground service survives.
        private const val BUILD_JOBS = 4
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
                val status = if (stage.name == "common") "Installing dependencies and ESP-IDF 5.5…" else "Installing $target toolchain…"
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
            "echo '--- Target toolchain ---' && case " + shQuote(target) + " in esp32s3) xtensa-esp32s3-elf-gcc --version | head -n 1 ;; esp32) xtensa-esp32-elf-gcc --version | head -n 1 ;; *) exit 2 ;; esac && echo " + success
        executeStage(launcher, cli, command, success, onOutput, "ESP-IDF Doctor")
    }

    suspend fun setProjectTarget(project: File, target: String, onOutput: (String) -> Unit = {}): String = withContext(Dispatchers.IO) {
        requireTarget(target)
        require(project.isDirectory) { "Project directory not found: $project" }
        requireRuntimeReady()
        val rootfs = File(host.prefixDir, "var/lib/pr/containers/${IdfRuntimePlan.GUEST_ALIAS}/rootfs")
        IdfBuildStages.stages(target).forEach { require(IdfBuildStages.isComplete(rootfs, it)) { "Environment is not ready: ${it.name}" } }
        val launcher = ProotLauncher(host)
        val cli = prepareLauncher()
        val success = "__APP_IDF_SET_TARGET_OK__"
        val command = "export IDF_TOOLS_PATH=" + shQuote(IdfRuntimePlan.IDF_TOOLS_PATH) + " IDF_PATH=" + shQuote(IdfRuntimePlan.IDF_PATH) + " && " +
            "cd " + shQuote(IdfRuntimePlan.IDF_PATH) + " && . ./export.sh >/dev/null && " +
            "cd " + shQuote(project.absolutePath) + " && idf.py set-target " + shQuote(target) + " && echo " + success
        executeStage(launcher, cli, command, success, onOutput, "Set Target")
    }

    suspend fun fullClean(project: File, target: String, onOutput: (String) -> Unit = {}): String = withContext(Dispatchers.IO) {
        requireTarget(target)
        require(project.isDirectory) { "Project directory not found: " + project }
        requireRuntimeReady()
        val launcher = ProotLauncher(host)
        val cli = prepareLauncher()
        val success = "__APP_IDF_FULLCLEAN_OK__"
        val guestProject = "/workspace/project"
        val command = "export IDF_TOOLS_PATH=" + shQuote(IdfRuntimePlan.IDF_TOOLS_PATH) +
            " IDF_PATH=" + shQuote(IdfRuntimePlan.IDF_PATH) + " && " +
            "cd " + shQuote(IdfRuntimePlan.IDF_PATH) + " && . ./export.sh >/dev/null && " +
            "cd " + shQuote(guestProject) + " || { echo \"ERROR: Project workspace is not accessible inside Linux runtime.\"; exit 66; }; " +
            "idf.py fullclean && echo " + success
        executeStage(launcher, cli, command, success, onOutput, "Full Clean", listOf(project.canonicalPath + ":" + guestProject))
    }

    suspend fun buildPrepared(project: File, target: String, onOutput: (String) -> Unit = {}): String = withContext(Dispatchers.IO) {
        cancelled.set(false)
        require(project.isDirectory) { "Project directory not found: $project" }
        val rootfs = File(host.prefixDir, "var/lib/pr/containers/${IdfRuntimePlan.GUEST_ALIAS}/rootfs")
        IdfBuildStages.stages(target).forEach { require(IdfBuildStages.isComplete(rootfs, it)) { "Environment is not ready: ${it.name}" } }
        val launcher = ProotLauncher(host)
        val cli = prepareLauncher()
        val guestProject = project.absolutePath
        val success = "__APP_IDF_BUILD_OK__"
        val command = "export IDF_TOOLS_PATH='${IdfRuntimePlan.IDF_TOOLS_PATH}' IDF_PATH='${IdfRuntimePlan.IDF_PATH}' && " +
            "cd '${IdfRuntimePlan.IDF_PATH}' && . ./export.sh >/dev/null && " +
            "cd '$guestProject' && IDF_PY_BUILD_JOBS=$BUILD_JOBS idf.py build && echo $success"
        executeStage(launcher, cli, command, success, onOutput, "Build")
    }

    private fun requireTarget(target: String) {
        require(target.matches(Regex("[a-z0-9]+"))) { "Invalid ESP target" }
    }

    private fun requireRuntimeReady() {
        val rootfs = File(host.prefixDir, "var/lib/pr/containers/" + IdfRuntimePlan.GUEST_ALIAS + "/rootfs")
        require(File(rootfs, "etc/os-release").exists()) { "Debian runtime is not installed" }
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

    private fun executeStage(launcher: ProotLauncher, cli: File, command: String, success: String, onOutput: (String) -> Unit = {}, operation: String = "Provisioning stage", customBinds: List<String> = emptyList()): String {
        val loginArgs = mutableListOf(cli.absolutePath, "login", IdfRuntimePlan.GUEST_ALIAS)
        customBinds.forEach { bind ->
            loginArgs += "--custom-bind"
            loginArgs += bind
        }
        loginArgs += "--"
        loginArgs += command
        val session = launcher.startCustomSession(loginArgs)
            ?: error("Unable to start " + operation)
        val sessionPid = PtyNative.getPid()
        activeCancel.set { session.close() }
        if (cancelled.get()) { activeCancel.getAndSet(null)?.invoke(); throw BuildCancelledException() }
        val output = TailBuffer(LOG_TAIL_CHARS)
        val buffer = ByteArray(8192)
        try {
            while (true) {
                val count = session.read(buffer)
                if (count <= 0) break
                output.append(String(buffer, 0, count))
                onOutput(output.value().takeLast(UI_TAIL_CHARS))
                if (output.value().contains(success)) break
            }
        } finally {
            activeCancel.set(null)
            session.close()
        }

        if (cancelled.get()) throw BuildCancelledException()
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
                exitCode != null -> operation + " process exited with code " + exitCode + ".\n" + if (diagnostic.isNotBlank()) diagnostic else tail.takeLast(6000)
                else -> {
                    val waitStatus = runCatching { PtyNative.waitPid(sessionPid) }.getOrDefault(-3)
                    val signal = if (waitStatus <= -129) -waitStatus - 128 else null
                    val signalName = signal?.let(::signalName)
                    PersistentDiagnosticLog.append(
                        context.applicationContext,
                        "PTY_EXIT",
                        "operation=$operation pid=$sessionPid waitStatus=$waitStatus signal=${signal ?: "unknown"} signalName=${signalName ?: "unknown"} cancelled=${cancelled.get()}"
                    )
                    throw SessionEndedWithoutMarkerException(
                        "$operation (PTY pid=$sessionPid waitStatus=$waitStatus" +
                            (if (signal != null) " signal=$signalName($signal)" else "") + ")",
                        tail.takeLast(6000) + "\n\n--- PTY diagnostic ---\npid=$sessionPid\nwaitStatus=$waitStatus\n" +
                            (if (signal != null) "signal=$signalName($signal)\n" else "signal=unknown\n") +
                            "cancelled=${cancelled.get()}\n\n--- Android diagnostic ---\n" + androidDiagnostics()
                    )
                }
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

    private fun androidDiagnostics(): String {
        val power = context.getSystemService(PowerManager::class.java)
        val activity = context.getSystemService(ActivityManager::class.java)
        val memory = ActivityManager.MemoryInfo().also { activity.getMemoryInfo(it) }
        val importance = ActivityManager.RunningAppProcessInfo().also {
            ActivityManager.getMyMemoryState(it)
        }.importance
        val runtime = Runtime.getRuntime()
        return buildString {
            append("interactive=").append(power.isInteractive).append('\n')
            append("powerSave=").append(power.isPowerSaveMode).append('\n')
            append("processImportance=").append(importance).append('\n')
            append("systemLowMemory=").append(memory.lowMemory).append('\n')
            append("systemAvailMemMB=").append(memory.availMem / (1024 * 1024)).append('\n')
            append("appHeapUsedMB=").append((runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)).append('\n')
            append("appHeapMaxMB=").append(runtime.maxMemory() / (1024 * 1024))
        }
    }

    private fun signalName(signal: Int): String = when (signal) {
        1 -> "SIGHUP"
        2 -> "SIGINT"
        3 -> "SIGQUIT"
        6 -> "SIGABRT"
        9 -> "SIGKILL"
        11 -> "SIGSEGV"
        13 -> "SIGPIPE"
        15 -> "SIGTERM"
        else -> "SIG$signal"
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
