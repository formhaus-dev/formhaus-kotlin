package dev.formhaus.demo

import dev.formhaus.compose.form.FormComponents
import dev.formhaus.compose.theme.FormhausTheme
import dev.formhaus.demo.kiln.kilnComponents
import dev.formhaus.demo.kiln.kilnTheme

class Look(
    val name: String,
    val components: FormComponents,
    val theme: FormhausTheme = FormhausTheme.standard,
) {
    companion object {
        val formhaus = Look("Formhaus", FormComponents.standard)
        val material = Look("Material 3", FormComponents.system)
        val kiln = Look("Kiln", kilnComponents, kilnTheme)
        val all = listOf(formhaus, material, kiln)
    }
}
