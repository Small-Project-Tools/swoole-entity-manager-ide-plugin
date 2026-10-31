package dev.smallproject.ide

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Regression coverage for the old CLI dependency: the PhpStorm indexer must
 * operate on PHP source without installing or invoking PHP, Composer or sem-ide.
 */
class BundledCliTest {
    @Test
    fun nativeIndexerWorksWithoutAnyPhpBinary() {
        val root = Files.createTempDirectory("small-ide-php-free").toFile()
        try {
            createProject(root)
            val index = NativeProjectScanner.scan(root)
            assertEquals("Demo\\Entity\\Person", index.managers["Demo\\EntityManager\\PersonManager"])
            assertEquals(setOf("name"), index.entities.getValue("Demo\\Entity\\Person").fields)
            assertEquals(listOf("name"), index.complete(
                QueryContext(kind = "field", manager = "Demo\\EntityManager\\PersonManager", rootAlias = "person"),
            ))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun nativeIndexerNeverInvokesLegacyCliAndSkipsVendor() {
        val root = Files.createTempDirectory("small-ide-php-free").toFile()
        try {
            createProject(root)
            val marker = File(root, "marker")
            for (name in listOf("bin/sem-ide", "vendor/bin/sem-ide")) {
                val file = File(root, name)
                file.parentFile.mkdirs()
                file.writeText("#!/bin/sh\nprintf executed > '" + marker.absolutePath + "'\n")
                file.setExecutable(true)
            }
            val vendorEntity = File(root, "vendor/foreign/Person.php")
            vendorEntity.parentFile.mkdirs()
            vendorEntity.writeText(
                """
                <?php
                namespace Demo\Entity;
                use Small\SwooleEntityManager\Entity\Attribute\{OrmEntity, Field};
                #[OrmEntity]
                class Person { #[Field] private int PLACEHOLDERvendorOnly; }
                """.trimIndent().replace("PLACEHOLDER", "$"),
            )

            val index = NativeProjectScanner.scan(root)
            assertFalse(marker.exists(), "Native indexing must not launch any CLI")
            assertEquals(setOf("name"), index.entities.getValue("Demo\\Entity\\Person").fields)
            assertTrue("vendorOnly" !in index.complete(
                QueryContext(kind = "field", manager = "Demo\\EntityManager\\PersonManager", rootAlias = "person"),
            ))
        } finally {
            root.deleteRecursively()
        }
    }

    private fun createProject(root: File) {
        val entity = File(root, "src/Entity/Person.php")
        entity.parentFile.mkdirs()
        entity.writeText(
            """
            <?php
            namespace Demo\Entity;
            use Small\SwooleEntityManager\Entity\Attribute\{OrmEntity, Field};
            #[OrmEntity]
            class Person { #[Field] private string PLACEHOLDERname; }
            """.trimIndent().replace("PLACEHOLDER", "$"),
        )
        val manager = File(root, "src/EntityManager/PersonManager.php")
        manager.parentFile.mkdirs()
        manager.writeText(
            """
            <?php
            namespace Demo\EntityManager;
            use Demo\Entity\Person;
            use Small\SwooleEntityManager\EntityManager\Attribute\Entity;
            #[Entity(Person::class)]
            class PersonManager {}
            """.trimIndent(),
        )
    }
}
