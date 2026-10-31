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
}
