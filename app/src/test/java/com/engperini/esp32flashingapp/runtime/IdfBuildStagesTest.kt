package com.engperini.esp32flashingapp.runtime

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class IdfBuildStagesTest {
    @Test fun provisioningHasCommonThenTargetStages() {
        val stages = IdfBuildStages.stages("esp32s3")
        assertEquals(listOf("common", "target"), stages.map { it.name })
        assertEquals("common-v5.5", stages[0].marker)
        assertEquals("target-v5.5-esp32s3", stages[1].marker)
    }

    @Test fun completionIsPersistedOutsideActivityState() {
        val root = createTempDir()
        try {
            val stage = IdfBuildStages.stages("esp32s3").first()
            assertFalse(IdfBuildStages.isComplete(root, stage))
            File(root, "opt/esp/.app-state").mkdirs()
            File(root, "opt/esp/.app-state/${stage.marker}").writeText("ok")
            assertTrue(IdfBuildStages.isComplete(root, stage))
        } finally { root.deleteRecursively() }
    }
}
