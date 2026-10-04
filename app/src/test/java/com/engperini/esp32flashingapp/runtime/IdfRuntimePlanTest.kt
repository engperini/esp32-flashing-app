package com.engperini.esp32flashingapp.runtime

import org.junit.Assert.*
import org.junit.Test

class IdfRuntimePlanTest {
    @Test fun esp32s3ProvisioningIsTargetScopedAndVersionPinned() {
        val command = IdfRuntimePlan.targetInstallCommand("esp32s3")
        assertTrue(command.contains("./install.sh 'esp32s3'"))
        assertTrue(command.contains("/opt/esp/tools/v5.5"))
        assertEquals("v5.5", IdfRuntimePlan.ESP_IDF_VERSION)
    }

    @Test fun rejectsUnexpectedTargetShellInput() {
        assertThrows(IllegalArgumentException::class.java) {
            IdfRuntimePlan.targetInstallCommand("esp32s3; rm -rf /")
        }
    }

    @Test fun commonSetupDoesNotInstallTargetToolchain() {
        val command = IdfRuntimePlan.commonSetupCommand()
        assertTrue(command.contains("git clone --branch 'v5.5'"))
        assertFalse(command.contains("./install.sh"))
    }
}
