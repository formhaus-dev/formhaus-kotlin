package dev.formhaus.compose.form

import dev.formhaus.compose.fields.TextInputField
import dev.formhaus.compose.model.FormModel
import dev.formhaus.compose.model.next
import dev.formhaus.core.engine.FormEngineOptions
import dev.formhaus.core.i18n.LocaleCatalog
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RetainedLocaleTest {
    private val seen = mutableListOf<FormModel>()
    private var tag = "de"

    private val capturing: FormComponents =
        FormComponents.standard.field("text") { context ->
            if (seen.lastOrNull() !== context.model) seen += context.model
            TextInputField(context)
        }

    @After
    fun clear() {
        HostActivity.content = {}
    }

    @Test
    fun aNewLocaleOnARetainedModelChangesItsLabelsAndErrorSummary() {
        HostActivity.content = {
            val options = FormEngineOptions(locale = listOfNotNull(LocaleCatalog.bundled(tag)))
            Provided(capturing) { FormhausForm(trip, engineOptions = options) { _, _ -> null } }
        }
        val controller = Robolectric.buildActivity(HostActivity::class.java).setup()
        settle()
        val model = seen.last()
        model.scope.launch { model.next() }
        settle()
        assertEquals("Weiter", model.actions.primaryLabel)
        assertEquals("Who", model.errorGroups.single().title)
        assertEquals("Dieses Feld ist erforderlich", model.field("name").error)
        tag = "fr"
        controller.recreate()
        settle()
        assertSame(model, seen.last())
        assertEquals("Continuer", model.actions.primaryLabel)
        assertEquals("Vérifiez ces réponses", model.label("errorSummary"))
        model.scope.launch { model.next() }
        settle()
        assertEquals("Ce champ est obligatoire", model.field("name").error)
    }
}
