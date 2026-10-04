package com.engperini.esp32flashingapp.build

import org.junit.Assert.*
import org.junit.Test

class EnvironmentPlannerTest {
    @Test fun cleanAndroidRequiresProvisioning() {
        assertFalse(EnvironmentPlanner.isReady(emptyList()))
        assertEquals(EnvironmentPlanner.required, EnvironmentPlanner.missing(emptyList()))
    }

    @Test fun fullyPreparedEnvironmentIsReady() {
        val checks = EnvironmentPlanner.required.map { EnvironmentCheck(it, true) }
        assertTrue(EnvironmentPlanner.isReady(checks))
        assertTrue(EnvironmentPlanner.missing(checks).isEmpty())
    }

    @Test fun reportsOnlyMissingComponents() {
        val checks = listOf(
            EnvironmentCheck(EnvironmentComponent.STORAGE, true),
            EnvironmentCheck(EnvironmentComponent.TOOLCHAIN, true),
            EnvironmentCheck(EnvironmentComponent.ESP_IDF, false)
        )
        val missing = EnvironmentPlanner.missing(checks)
        assertFalse(EnvironmentComponent.STORAGE in missing)
        assertFalse(EnvironmentComponent.TOOLCHAIN in missing)
        assertTrue(EnvironmentComponent.ESP_IDF in missing)
        assertTrue(EnvironmentComponent.PYTHON in missing)
    }
}
