package com.engperini.esp32flashingapp.build

import org.junit.Assert.*
import org.junit.Test

class EnvironmentPlannerTest {
    @Test fun cleanAndroidRequiresCommonRuntimeAndSelectedTargetToolchain() {
        val required = EnvironmentPlanner.requiredFor("esp32s3")
        assertFalse(EnvironmentPlanner.isReady(emptyList(), "esp32s3"))
        assertEquals(required, EnvironmentPlanner.missing(emptyList(), "esp32s3"))
        assertEquals(EnvironmentComponent.TOOLCHAIN, required.last())
    }

    @Test fun commonRuntimeDoesNotPretendSelectedTargetIsReady() {
        val commonReady = EnvironmentPlanner.commonRequired.map { EnvironmentCheck(it, true) }
        assertFalse(EnvironmentPlanner.isReady(commonReady, "esp32s3"))
        assertEquals(listOf(EnvironmentComponent.TOOLCHAIN),
            EnvironmentPlanner.missing(commonReady, "esp32s3"))
    }

    @Test fun selectedTargetBecomesReadyAfterItsToolchainIsPresent() {
        val checks = EnvironmentPlanner.requiredFor("esp32s3").map { EnvironmentCheck(it, true) }
        assertTrue(EnvironmentPlanner.isReady(checks, "esp32s3"))
        assertTrue(EnvironmentPlanner.missing(checks, "esp32s3").isEmpty())
    }
}
