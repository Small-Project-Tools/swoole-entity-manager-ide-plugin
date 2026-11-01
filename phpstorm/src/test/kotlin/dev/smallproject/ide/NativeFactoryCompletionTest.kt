package dev.smallproject.ide

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NativeFactoryCompletionTest {
    @Test fun classNameInFactoryCallIsCompletionTarget() {
        val source = "<?php\n" + "\$manager = \$factory->get(Us"
        val context = QueryContextResolver.resolve(source)
        assertEquals("manager", context?.kind)
        assertEquals("Us", context?.typedPrefix)
    }

    @Test fun namedFactoryParameter() {
        val source = "<?php\n" + "\$manager = \$entityManagerFactory->get(managerClass: Us"
        val context = QueryContextResolver.resolve(source)
        assertEquals("manager", context?.kind)
        assertEquals("Us", context?.typedPrefix)
    }

    @Test fun unrelatedGetIsNotHijacked() {
        assertNull(QueryContextResolver.resolve("<?php\n" + "\$cache->get(Us"))
    }
    @Test fun coreFactoryNamedManagerIsCompletable() {
        val source = "<?php\n" + "\$manager = \$factory->get(manager: Us"
        val context = QueryContextResolver.resolve(source)
        assertEquals("manager", context?.kind)
        assertEquals("Us", context?.typedPrefix)
    }

    @Test fun typedBundleFactoryAliasOffersManagerCandidates() {
        val source = "<?php\n" +
            "use Small\\SwooleEntityManagerBundle\\Contract\\EntityManagerFactoryInterface;\n" +
            "function run(EntityManagerFactoryInterface \$locator): void {\n" +
            "    \$manager = \$locator->get(managerClass: Us"
        val context = QueryContextResolver.resolve(source)
        assertEquals("manager", context?.kind)
        assertEquals("Us", context?.typedPrefix)
    }

    @Test fun unrelatedNamedFactoryIsNotClaimed() {
        assertNull(QueryContextResolver.resolve("<?php\n" + "\$cacheFactory->get(Us"))
    }

}
