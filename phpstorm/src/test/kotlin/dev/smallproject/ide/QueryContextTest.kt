package dev.smallproject.ide

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class QueryContextTest {
    @Test fun fieldOfManager() {
        val ctx = QueryContextResolver.resolve("""
            <?php
            ${'$'}manager = ${'$'}factory->get(UserManager::class);
            ${'$'}query = ${'$'}manager->createQueryBuilder('user');
            ${'$'}query->getFieldForCondition('
        """.trimIndent())
        assertEquals("field", ctx?.kind)
        assertEquals("UserManager", ctx?.manager)
        assertEquals("user", ctx?.rootAlias)
    }
    @Test fun joins() {
        val ctx = QueryContextResolver.resolve("""
           ${'$'}manager = ${'$'}factory->get(UserManager::class);
           ${'$'}query = ${'$'}manager->createQueryBuilder('user');
           ${'$'}query->leftJoin('user', 'company', 'company');
           ${'$'}query->getFieldForCondition('
        """.trimIndent())
        assertEquals(1, ctx?.joins?.size)
        assertEquals("company", ctx?.joins?.first()?.get("relation"))
    }
    @Test fun relation() {
        val ctx = QueryContextResolver.resolve("""
           ${'$'}manager = ${'$'}factory->get(UserManager::class);
           ${'$'}query = ${'$'}manager->createQueryBuilder('user');
           ${'$'}query->leftJoin('user', '
        """.trimIndent())
        assertEquals("relation", ctx?.kind)
    }
    @Test fun parameters() {
        val context = QueryContextResolver.resolve("""
            ${'$'}manager = ${'$'}factory->get(UserManager::class);
            ${'$'}query = ${'$'}manager->createQueryBuilder('user');
            ${'$'}query->where()->firstCondition('enabled', '=', ':enabled');
            ${'$'}query->setParameter('
        """.trimIndent())
        assertEquals("parameter", context?.kind)
        assertEquals(listOf("enabled"), context?.parameters)
    }
    @Test fun unknown() { assertNull(QueryContextResolver.resolve("unknown('")) }
}
