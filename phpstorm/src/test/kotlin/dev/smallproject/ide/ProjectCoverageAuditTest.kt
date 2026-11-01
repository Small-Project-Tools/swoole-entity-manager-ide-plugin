package dev.smallproject.ide

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Validates the generic, source-free external audit CLI on generated PHP files. */
class ProjectCoverageAuditTest {
    @Test fun synthesizedProjectIsFullyAuditedWithoutExposingSource() {
        val root = Files.createTempDirectory("small-ide-audit")
        try {
            val src = Files.createDirectories(root.resolve("src"))
            val vendor = Files.createDirectories(root.resolve("vendor"))
            Files.writeString(src.resolve("Subject.php"), """
                <?php
                namespace AuditFixtures;
                use Small\SwooleEntityManager\Entity\Attribute\Field;
                class Subject { #[Field] private string ${'$'}secretField; }
            """.trimIndent())
            Files.writeString(src.resolve("SubjectManager.php"), """
                <?php
                namespace AuditFixtures;
                use Small\SwooleEntityManager\EntityManager\Attribute\Entity;
                #[Entity(Subject::class)]
                class SubjectManager {
                    public function test() {
                        ${'$'}query = ${'$'}this->createQueryBuilder('subject');
                        // Ignored by the audit: ${'$'}query->addOrderBy('not-a-field');
                    /* Also ignored: ${'$'}query->firstCondition('invalid', Op::equal); */
                    ${'$'}query->addOrderBy('secretField');
                        ${'$'}query->where()->firstCondition(':secret', Operator::equal, ':identifier');
                        ${'$'}query->setParameter('identifier', 42);
                    }
                }
            """.trimIndent())
            Files.writeString(vendor.resolve("Ignore.php"), """
                <?php class Ignore { function test() { ${'$'}foo->addOrderBy('x'); } }
            """.trimIndent())
            val report = ProjectCoverageAudit.audit(root.toFile())
            assertEquals(2, report.files)
            assertEquals(1, report.managers)
            assertEquals(1, report.entities)
            assertEquals(1, report.fields)
            assertEquals(3, report.calls)
            assertEquals(3, report.resolved)
            assertEquals(3, report.suggested)
            assertEquals(0, report.errors)
            assertFalse(report.summary().contains("secretField"))
            assertFalse(report.summary().contains(root.toString()))
            assertTrue(report.summary().contains("aggregate only"))
        } finally { root.toFile().deleteRecursively() }
    }

    @Test fun auditRequiresExistingLocalDirectory() {
        assertFailsWith<IllegalArgumentException> {
            ProjectCoverageAudit.audit(File("/unavailable/small-ide-rc-audit"))
        }
    }
}
