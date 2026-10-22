package dev.formhaus.core.engine

import dev.formhaus.core.json.FieldErrors
import dev.formhaus.core.json.MutableStringMap
import dev.formhaus.core.support.jsonObject

object Forms {
    const val STEPS = """{"id":"m","title":"M","submit":{"label":"S"},"steps":[
        {"id":"one","title":"1","fields":[{"key":"a","type":"text","label":"A"}]},
        {"id":"two","title":"2","fields":[{"key":"c","type":"text","label":"C"}]}]}"""

    const val FLAT = """{"id":"f","title":"F","submit":{"label":"S"},"fields":[{"key":"a","type":"text","label":"A"}]}"""
}

fun errorsOf(text: String): FieldErrors =
    MutableStringMap<String>().also { map -> for ((key, value) in jsonObject(text)) map[key] = value.stringValue.orEmpty() }
