package dev.formhaus.compose.model

import dev.formhaus.core.engine.FormEngineOptions
import dev.formhaus.core.engine.SubmitHandler
import dev.formhaus.core.json.JsonValue
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HandlerTest {
    @get:Rule
    val main = MainDispatcherRule()

    @Test
    fun updatedHandlersReplaceTheHooksAndTheSubmitHandler() =
        runTest {
            val log = mutableListOf<String>()
            val first: SubmitHandler = { _, _ ->
                log += "first"
                null
            }
            val second: SubmitHandler = { _, _ ->
                log += "second"
                null
            }
            val form = model(signupDefinition, """{"email": "a@b.co", "agree": true}""", onSubmit = first)
            form.updateHandlers(
                engineOptions =
                    FormEngineOptions(onBeforeSubmit = {
                        log += "before"
                        null
                    }),
                onSubmit = second,
            )
            form.submit()
            assertEquals(listOf("before", "second"), log)
        }

    @Test
    fun closingStopsFollowingTheEngine() {
        val form = model(signupDefinition)
        form.close()
        form.engine.setValue("name", JsonValue.String("Ada"))
        assertEquals(0, form.version)
    }
}
