package dev.formhaus.core.renderer

import dev.formhaus.core.model.FieldOption
import dev.formhaus.core.model.FormField

private fun counts(texts: List<String>): Map<String, Int> = texts.groupingBy { it }.eachCount()

@RendererApi
public fun optionTexts(options: List<FieldOption>): List<String> {
    val labels = counts(options.map { it.label.orEmpty() })
    val texts =
        options.map { option ->
            val label = option.label.orEmpty()
            if ((labels[label] ?: 0) > 1) "$label (${option.value.orEmpty()})" else label
        }
    val repeated = counts(texts)
    val taken = texts.toHashSet()
    val next = HashMap<String, Int>()
    return texts.map { text ->
        if ((repeated[text] ?: 0) <= 1) return@map text
        var index = next[text] ?: 1
        while ("$text ($index)" in taken) index++
        next[text] = index + 1
        taken.add("$text ($index)")
        "$text ($index)"
    }
}

@RendererApi
public fun dividerBefore(
    field: FormField,
    options: List<FieldOption>,
    index: Int,
): Boolean = !field.hasOptionsFrom && index in 1 until options.size && options[index].exclusive && !options[index - 1].exclusive
