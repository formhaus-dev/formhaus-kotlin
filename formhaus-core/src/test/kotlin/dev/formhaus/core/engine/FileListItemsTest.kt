package dev.formhaus.core.engine

import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.server.validateSubmission
import dev.formhaus.core.support.engine
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FileListItemsTest {
    private val definition =
        """{"formatVersion":"1.7","id":"f","submit":{"label":"S"},"fields":[
        {"key":"d","type":"file","label":"D","multiple":true,"validation":{"required":true,"maxLength":1}}]}"""

    private fun client(values: String): String? = engine(definition, values).validate()["d"]

    private fun server(values: String) = validateSubmission(JsonValue.parse(definition), JsonValue.parse(values))

    @Test
    fun `empty strings and nulls are not items`() {
        assertNull(client("""{"d":["a","",null]}"""))
        assertEquals("This field is required", client("""{"d":["",null]}"""))
        val result = server("""{"d":["a","",null]}""")
        assertTrue(result.errors.isEmpty())
        assertEquals(JsonValue.parse("""["a"]"""), result.values["d"])
    }

    @Test
    fun `numbers and objects without a string name fail as file values`() {
        for (item in listOf("7", """{"size":1}""", """{"name":3}""", "true")) {
            assertEquals("Must be a file reference", client("""{"d":[$item]}"""), item)
            val result = server("""{"d":[$item]}""")
            assertEquals("Must be a file reference", result.errors["d"], item)
            assertFalse("d" in result.values, item)
        }
        assertNull(client("""{"d":[{"name":"scan.pdf"}]}"""))
        assertEquals("Must be a file reference", server("""{"d":[{"name":"scan.pdf"}]}""").errors["d"])
        assertNull(client("""{"d":"upload-1"}"""))
        assertEquals("Must be a file reference", client("""{"d":7}"""))
    }
}
