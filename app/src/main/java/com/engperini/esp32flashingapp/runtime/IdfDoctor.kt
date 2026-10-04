package com.engperini.esp32flashingapp.runtime

import android.content.Context
import java.io.File

data class DoctorCheck(val name: String, val ok: Boolean, val detail: String)
data class DoctorReport(val checks: List<DoctorCheck>) {
    val healthy: Boolean get() = checks.all { it.ok }
    fun text(): String = buildString {
        appendLine(if (healthy) "Environment healthy" else "Repair needed")
        checks.forEach { appendLine("${if (it.ok) "✓" else "✗"} ${it.name}: ${it.detail}") }
    }
}

object IdfDoctor {
    fun inspect(context: Context, target: String): DoctorReport {
        val prefix = File(context.filesDir, "runtime")
        val rootfs = File(prefix, "var/lib/pr/containers/${IdfRuntimePlan.GUEST_ALIAS}/rootfs")
        val checks = mutableListOf<DoctorCheck>()
        checks += DoctorCheck("Debian ARM64 runtime", File(rootfs, "etc/os-release").exists(), if (File(rootfs, "etc/os-release").exists()) "installed" else "missing")
        checks += DoctorCheck("ESP-IDF ${IdfRuntimePlan.ESP_IDF_VERSION}", File(rootfs, IdfRuntimePlan.IDF_PATH.removePrefix("/") + "/export.sh").exists(), if (File(rootfs, IdfRuntimePlan.IDF_PATH.removePrefix("/") + "/export.sh").exists()) "installed" else "missing")
        IdfBuildStages.stages(target).forEach { stage ->
            checks += DoctorCheck("${stage.name} stage", IdfBuildStages.isComplete(rootfs, stage), if (IdfBuildStages.isComplete(rootfs, stage)) "ready" else "not ready")
        }
        return DoctorReport(checks)
    }
}
