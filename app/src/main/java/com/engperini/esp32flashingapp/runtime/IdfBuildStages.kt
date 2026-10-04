package com.engperini.esp32flashingapp.runtime

import java.io.File

data class RuntimeStage(val name: String, val marker: String, val command: String)

object IdfBuildStages {
    fun stages(target: String): List<RuntimeStage> {
        IdfRuntimePlan.targetInstallCommand(target)
        return listOf(
            RuntimeStage("common", "common-v5.5", IdfRuntimePlan.commonSetupCommand()),
            RuntimeStage("target", "target-v5.5-$target", IdfRuntimePlan.targetInstallCommand(target))
        )
    }

    fun isComplete(rootfs: File, stage: RuntimeStage): Boolean =
        File(rootfs, "opt/esp/.app-state/${stage.marker}").exists()
}
