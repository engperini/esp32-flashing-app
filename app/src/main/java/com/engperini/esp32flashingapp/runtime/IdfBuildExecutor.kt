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

    suspend fun prepare(target: String): String = withContext(Dispatchers.IO) {
        val launcher = ProotLauncher(host)
        val cli = prepareLauncher()
        val rootfs = File(host.prefixDir, "var/lib/pr/containers/${IdfRuntimePlan.GUEST_ALIAS}/rootfs")
        require(File(rootfs, "etc/os-release").exists()) { "Debian build runtime is not provisioned" }
        val log = StringBuilder()
        IdfBuildStages.stages(target).forEach { stage ->
            if (IdfBuildStages.isComplete(rootfs, stage)) {
                log.appendLine("${stage.name}: ready")
            } else {
                val markerDir = File(rootfs, "opt/esp/.app-state").apply { mkdirs() }
                val success = "__APP_STAGE_${stage.name.uppercase()}_OK__"
                val output = executeStage(launcher, cli, stage.command + " && echo " + success, success)
                File(markerDir, stage.marker).writeText("ok")
                log.appendLine("${stage.name}: completed")
                log.appendLine(output.takeLast(2000))
            }
        }
        log.toString()
    }

    suspend fun build(project: File, target: String): String = withContext(Dispatchers.IO) {
        require(project.isDirectory) { "Project directory not found: $project" }
        prepare(target)
        val launcher = ProotLauncher(host)
        val cli = prepareLauncher()
        val guestProject = project.absolutePath
        val success = "__APP_IDF_BUILD_OK__"
        val command = "export IDF_TOOLS_PATH='${IdfRuntimePlan.IDF_TOOLS_PATH}' && " +
            ". '${IdfRuntimePlan.IDF_PATH}/export.sh' >/dev/null && " +
            "cd '$guestProject' && idf.py set-target '$target' && idf.py build && echo $success"
        executeStage(launcher, cli, command, success)
    }

    private fun prepareLauncher(): File {
        host.prefixDir.mkdirs(); host.homeDir.mkdirs()
        val nativeDir = File(context.applicationInfo.nativeLibraryDir)
        val binDir = File(host.prefixDir, "bin").apply { mkdirs() }
        mapOf("busybox" to "libbusybox.so", "proot" to "libproot.so", "pr-cli" to "libpr-cli.so").forEach { (name, lib) ->
            val target = File(nativeDir, lib)
            require(target.exists()) { "Embedded runtime file missing: $lib" }
            val dest = File(binDir, name)
            if (!dest.exists()) Os.symlink(target.absolutePath, dest.absolutePath)
        }
        return File(nativeDir, "libpr-cli.so")
    }

    private fun executeStage(launcher: ProotLauncher, cli: File, command: String, success: String): String {
        val session = launcher.startCustomSession(
            listOf(cli.absolutePath, "login", IdfRuntimePlan.GUEST_ALIAS, "--", command)
        ) ?: error("Unable to start provisioning stage")
        val output = StringBuilder()
        val buffer = ByteArray(8192)
        try {
            while (true) {
                val count = session.read(buffer)
                if (count <= 0) break
                output.append(String(buffer, 0, count))
            }
        } finally {
            session.close()
        }
        require(output.contains(success)) { "Provisioning stage failed before $success: ${output.takeLast(4000)}" }
        return output.toString()
    }
}
