package dev.smallproject.ide

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProtocolTest {
    @Test fun jsonEscapesPhpNamespaces() {
        val payload = SemIdeClient.encodeRequest(QueryContext("field", "App\\EntityManager\\UserManager", "user"))
        assertTrue(payload.contains("App\\\\EntityManager\\\\UserManager"))
    }
    @Test fun parseCompletion() {
        assertEquals(listOf("id", "name"), SemIdeClient.parseLabels("""[{"label":"id","kind":"field"},{"label":"name","kind":"field"}]"""))
        assertEquals(listOf("App\\EntityManager\\UserManager"), SemIdeClient.parseLabels("""[{"label":"App\\EntityManager\\UserManager"}]"""))
        assertEquals(emptyList(), SemIdeClient.parseLabels("{}"))
    }
    @Test fun parameterRequest() {
        assertTrue(SemIdeClient.encodeRequest(QueryContext("parameter", parameters = listOf("enabled"))).contains("\"parameters\":[\"enabled\"]"))
    }
}
