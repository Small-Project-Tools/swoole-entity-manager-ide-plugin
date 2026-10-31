package dev.smallproject.ide

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import java.io.File
import java.nio.file.Files

class BundledCliTest {
    @Test fun prefersProjectInstalledIndexer() {
        val base = Files.createTempDirectory("ide-complete").toFile()
        try {
            val local = File(base, "vendor/bin/sem-ide")
            local.parentFile.mkdirs()
            local.writeText("<?php")
            val plugin = File(base, "plugin")
            val jar = File(plugin, "lib/plugin.jar")
            val embedded = File(plugin, "core/bin/sem-ide")
            embedded.parentFile.mkdirs()
            embedded.writeText("<?php")
            assertEquals(local, SemIdeService.findCli(base, jar))
        } finally { base.deleteRecursively() }
    }

    @Test fun bundledIndexerWorksWithoutComposerDependency() {
        val base = Files.createTempDirectory("ide-complete").toFile()
        try {
            val jar = File(base, "plugin/lib/plugin.jar")
            val embedded = File(base, "plugin/core/bin/sem-ide")
            embedded.parentFile.mkdirs()
            embedded.writeText("<?php")
            assertEquals(embedded, SemIdeService.findCli(base, jar))
            assertNull(SemIdeService.findCli(base, null))
        } finally { base.deleteRecursively() }
    }
}
