package dev.formhaus.core.server

import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.regex.CallBudget
import dev.formhaus.core.regex.StepBudget
import dev.formhaus.core.support.Load
import dev.formhaus.core.support.definition
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SubmissionBudgetTest {
    private val heavy = "^[a-h]{0,10000}[a-h]{0,10000}$"
    private val heavyText = "a".repeat(9990) + "!"

    @Test
    fun `a submission fails closed on every field once its budget runs out`() {
        val pattern = JsonValue.String(heavy).toString()
        val fields =
            (0 until 3).joinToString(",") {
                """{"key":"f$it","type":"text","label":"F","validation":{"pattern":$pattern}}"""
            }
        val form = definition("""{"id":"p","submit":{"label":"S"},"fields":[$fields]}""")
        val values = JsonValue.parse("""{"f0":"$heavyText","f1":"abc","f2":"abc"}""")
        val result =
            StepBudget.backtrackingLimit.withValue(if (Load.heavy) StepBudget.backtrackingLimit.value else 4_000_000) {
                validateSubmission(form, values)
            }
        assertNotNull(result.errors["f0"])
        assertNull(result.errors["f1"])
        val exhausted = CallBudget.current.withValue(CallBudget(0)) { validateSubmission(form, values) }
        assertNotNull(exhausted.errors["f1"])
    }
}
