package dev.smallproject.ide

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Only generic PHP fixtures are used in this plugin project. */
class QueryContextRegressionTest {
    private val prefix = """<?php
        namespace Sample;
        use Sample\Manager\PersonManager;
        /** @var PersonManager ${'$'}manager */
        ${'$'}manager = ${'$'}this->factory->get(managerClass: PersonManager::class);
    """.trimIndent()

    @Test fun namedFactoryAndFluentJoin() {
        val context = QueryContextResolver.resolve(prefix + """
            ${'$'}manager->createQueryBuilder(alias: 'person')
                ->leftJoin(fromAlias: 'person', relationAlias: 'owner')->endJoin()
                ->where()->firstCondition(var1: ${'$'}query->getFieldForCondition(fieldName: '
        """.trimIndent())
        assertEquals("field", context?.kind)
        assertEquals("Sample\\Manager\\PersonManager", context?.manager)
        assertEquals("person", context?.rootAlias)
        assertEquals("owner", context?.joins?.single()?.get("relation"))
    }

    @Test fun namedJoinRelation() {
        val context = QueryContextResolver.resolve(prefix + """
            ${'$'}manager->createQueryBuilder(alias: 'person')
                ->leftJoin(fromAlias: 'person', relationAlias: 'ow
        """.trimIndent())
        assertEquals("relation", context?.kind)
        assertEquals("person", context?.alias)
        assertEquals("ow", context?.typedPrefix)
    }

    @Test fun joinsInSeparateStatements() {
        val context = QueryContextResolver.resolve(prefix + """
            ${'$'}query = ${'$'}manager->createQueryBuilder('person');
            ${'$'}query->leftJoin('person', 'owner', 'owner')->endJoin();
            ${'$'}query->getFieldForCondition('
        """.trimIndent())
        assertEquals("field", context?.kind)
        assertEquals("owner", context?.joins?.single()?.get("relation"))
    }

    @Test fun namedFieldAndProjectionSignature() {
        val context = QueryContextResolver.resolve(prefix + """
            ${'$'}query = ${'$'}manager->createQueryBuilder(alias: 'person');
            ${'$'}query->addOrderBy(fieldName: 'created
        """.trimIndent())
        assertEquals("created", context?.typedPrefix)
        assertEquals("field", context?.kind)
        assertEquals("projection", QueryContextResolver.methodArgumentRole("select", "fields", 0))
    }

    @Test fun invalidConditionDoesNotClaimFieldCompletion() {
        assertNull(QueryContextResolver.resolve(prefix + """
            ${'$'}manager->createQueryBuilder(alias: 'person')->where()
                ->firstCondition(var1: 'person.deletedAt IS NULL
        """.trimIndent()))
        assertEquals("condition-field", QueryContextResolver.methodArgumentRole("firstCondition", "var1", 0))
    }

    @Test fun fluentJoinsMadeDuringBuilderAssignmentAreAvailableLater() {
        val source = """
            <?php
            namespace Sample;
            use Demo\EntityManager\UserManager;
            ${'$'}manager = ${'$'}factory->get(UserManager::class);
            ${'$'}query = ${'$'}manager->createQueryBuilder(alias: 'user')
                ->leftJoin(fromAlias: 'user', relationAlias: 'company', alias: 'owner')->endJoin();
            ${'$'}query->where()->firstCondition(${'$'}query->getFieldForCondition('
        """.trimIndent()
        val context = QueryContextResolver.resolve(source)
        assertEquals("field", context?.kind)
        assertEquals("Demo\\EntityManager\\UserManager", context?.manager)
        assertEquals("user", context?.rootAlias)
        assertEquals("company", context?.joins?.single()?.get("relation"))
        assertEquals("owner", context?.joins?.single()?.get("alias"))
    }


    @Test fun emptyUnquotedFieldArgumentInsideConditionIsCompletable() {
        val context = QueryContextResolver.resolve(prefix + """
            ${'$'}query = ${'$'}manager->createQueryBuilder('person')
                ->leftJoin('person', 'owner', 'owner')->endJoin();
            ${'$'}query->where()->firstCondition(${'$'}query->getFieldForCondition(
        """.trimIndent())
        assertEquals("field", context?.kind)
        assertEquals("Sample\\Manager\\PersonManager", context?.manager)
        assertEquals("person", context?.rootAlias)
        assertEquals("owner", context?.joins?.single()?.get("alias"))
        assertEquals("", context?.typedPrefix)
    }

    @Test fun positionalFieldCompletionWorksBeforeTypingQuotes() {
        val context = QueryContextResolver.resolve(prefix + """
            ${'$'}query = ${'$'}manager->createQueryBuilder('person');
            ${'$'}query->getFieldForCondition(
        """.trimIndent())
        assertEquals("field", context?.kind)
        assertEquals("", context?.typedPrefix)
    }

    @Test fun namedAndPartiallyTypedUnquotedFieldCompletes() {
        val context = QueryContextResolver.resolve(prefix + """
            ${'$'}query = ${'$'}manager->createQueryBuilder('person');
            ${'$'}query->getFieldForCondition(fieldName: deleted
        """.trimIndent())
        assertEquals("field", context?.kind)
        assertEquals("deleted", context?.typedPrefix)
    }

    @Test fun dynamicUnquotedPhpExpressionsRemainUnclaimed() {
        assertNull(QueryContextResolver.resolve(prefix + """
            ${'$'}query = ${'$'}manager->createQueryBuilder('person');
            ${'$'}query->getFieldForCondition(${'$'}dynamic
        """.trimIndent()))
    }

    @Test fun groupedAliasedManagerImportResolvesToIndexedManager() {
        val source = listOf(
            "<?php",
            "namespace App;",
            "use Demo\\EntityManager\\{UserManager as AccountManager, CompanyManager};",
            "\$manager = \$factory->get(AccountManager::class);",
            "\$query = \$manager->createQueryBuilder('user');",
            "\$query->getFieldForCondition(",
        ).joinToString("\n")
        val context = QueryContextResolver.resolve(source)
        assertEquals("field", context?.kind)
        assertEquals("Demo\\EntityManager\\UserManager", context?.manager)
        assertEquals("user", context?.rootAlias)
    }
    @Test fun managerAliasWithoutManagerSuffixResolves() {
        val source = listOf(
            "<?php",
            "namespace App;",
            "use Demo\\EntityManager\\UserManager as Customer;",
            "function apply(Customer " + 36.toChar() + "repository): void {",
            "    " + 36.toChar() + "query = " + 36.toChar() + "repository->createQueryBuilder('u');",
            "    " + 36.toChar() + "query->getFieldForCondition(",
        ).joinToString("\n")
        val context = QueryContextResolver.resolve(source)
        assertEquals("Demo\\EntityManager\\UserManager", context?.manager)
        assertEquals("u", context?.rootAlias)
    }

    @Test fun nullableManagerPropertyResolvesThroughAlias() {
        val source = listOf(
            "<?php",
            "namespace App;",
            "use Demo\\EntityManager\\UserManager as Customer;",
            "class Example {",
            "    private ?Customer " + 36.toChar() + "repository = null;",
            "    public function apply(): void {",
            "        " + 36.toChar() + "query = " + 36.toChar() + "this->repository->createQueryBuilder('u');",
            "        " + 36.toChar() + "query->getFieldForCondition(",
        ).joinToString("\n")
        assertEquals("Demo\\EntityManager\\UserManager", QueryContextResolver.resolve(source)?.manager)
    }
    @Test fun groupedManagerImportIsRespectedInsideFactoryQuery() {
        val source = "<?php\n" +
            "namespace Demo;\n" +
            "use Vendor\\EntityManager\\{UserManager as AccountManager, CompanyManager};\n" +
            "\$manager = \$factory->get(manager: AccountManager::class);\n" +
            "\$query = \$manager->createQueryBuilder('u');\n" +
            "\$query->getFieldForCondition("
        val context = QueryContextResolver.resolve(source)
        assertEquals("Vendor\\EntityManager\\UserManager", context?.manager)
        assertEquals("u", context?.rootAlias)
    }

    @Test fun groupedAliasedOperatorImportUsesVisibleName() {
        val source = "<?php\n" +
            "use Demo\\EntityManager\\UserManager;\n" +
            "use Small\\SwooleEntityManager\\QueryBuilder\\RelationalQueryBuilder\\Enum\\{ConditionOperatorType as Op};\n" +
            "\$manager = \$factory->get(UserManager::class);\n" +
            "\$query = \$manager->createQueryBuilder('user');\n" +
            "\$query->where()->firstCondition(\$query->getFieldForCondition('id'), "
        val context = QueryContextResolver.resolve(source)
        assertEquals("condition-operator", context?.kind)
        assertEquals("Op", context?.operatorReference)
    }

    @Test fun builderFromPreviousMethodDoesNotLeakIntoCurrentMethod() {
        val source = "<?php\n" +
            "use Demo\\EntityManager\\UserManager;\n" +
            "function first(UserManager \$manager): void {\n" +
            "    \$query = \$manager->createQueryBuilder('user');\n" +
            "}\n" +
            "function second(): void {\n" +
            "    \$query->getFieldForCondition("
        assertNull(QueryContextResolver.resolve(source))
    }

    @Test fun managerTypesAreResolvedFromCurrentMethodOnly() {
        val source = "<?php\n" +
            "use Demo\\EntityManager\\{UserManager, CompanyManager};\n" +
            "function first(UserManager \$manager): void {\n" +
            "    \$query = \$manager->createQueryBuilder('user');\n" +
            "}\n" +
            "function second(CompanyManager \$manager): void {\n" +
            "    \$query = \$manager->createQueryBuilder('company');\n" +
            "    \$query->getFieldForCondition("
        val context = QueryContextResolver.resolve(source)
        assertEquals("Demo\\EntityManager\\CompanyManager", context?.manager)
        assertEquals("company", context?.rootAlias)
    }

    @Test fun priorManagerClassDoesNotLeakIntoAnUnrelatedClass() {
        val source = "<?php\nnamespace Demo;\n" +
            "class UserManager {\n" +
            "    function build(): void { \$query = \$this->createQueryBuilder('user'); }\n" +
            "}\n" +
            "class OtherService {\n" +
            "    function build(): void {\n" +
            "        \$query = \$this->createQueryBuilder('user');\n" +
            "        \$query->getFieldForCondition("
        assertNull(QueryContextResolver.resolve(source))
    }

    @Test fun priorManagerPropertyDoesNotLeakIntoAnotherClass() {
        val source = "<?php\n" +
            "use Demo\\EntityManager\\UserManager;\n" +
            "class One { private UserManager \$manager; }\n" +
            "class Two {\n" +
            "    public function run(): void {\n" +
            "        \$query = \$this->manager->createQueryBuilder('user');\n" +
            "        \$query->getFieldForCondition("
        assertNull(QueryContextResolver.resolve(source))
    }

}
