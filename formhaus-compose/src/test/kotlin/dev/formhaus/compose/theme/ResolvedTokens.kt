package dev.formhaus.compose.theme

import dev.formhaus.core.json.JsonObject
import dev.formhaus.core.json.JsonValue
import java.io.File

class ResolvedTokens(
    context: Map<String, String> = emptyMap(),
) {
    private val tokens = mutableMapOf<String, JsonObject>()

    init {
        val resolver = read("formhaus.resolver.json")
        for (step in resolver["resolutionOrder"]!!.arrayValue!!) {
            val (_, kind, name) = step["\$ref"]!!.stringValue!!.split("/")
            val target = resolver[kind]!![name]!!
            val files =
                if (kind == "sets") {
                    target["sources"]
                } else {
                    target["contexts"]!![context[name] ?: target["default"]!!.stringValue!!]
                }
            for (file in files!!.arrayValue!!) flatten(read(file["\$ref"]!!.stringValue!!).objectValue!!, emptyList(), null)
        }
    }

    fun color(path: String): Pair<String, Double> {
        val value = value(path)
        return value["hex"]!!.stringValue!!.lowercase() to (value["alpha"]?.numberValue ?: 1.0)
    }

    fun dimension(path: String): Double = dimension(value(path))

    fun dimension(value: JsonValue): Double {
        val resolved = resolve(value)
        check(resolved["unit"]?.stringValue == "px")
        return resolved["value"]!!.numberValue!!
    }

    fun value(path: String): JsonValue = resolve(requireNotNull(tokens[path]?.get("\$value")) { path })

    fun extensions(path: String): JsonValue = tokens[path]!!["\$extensions"]!!["dev.formhaus"]!!

    private fun resolve(value: JsonValue): JsonValue {
        val text = value.stringValue
        if (text != null && text.startsWith("{") && text.endsWith("}")) return value(text.drop(1).dropLast(1))
        return value
    }

    private fun flatten(
        node: JsonObject,
        path: List<String>,
        type: String?,
    ) {
        val current = node["\$type"]?.stringValue ?: type
        if (node["\$value"] != null) {
            tokens[path.joinToString(".")] = node
            return
        }
        for (key in node.keys.filterNot { it.startsWith("$") }) {
            node[key]?.objectValue?.let { flatten(it, path + key, current) }
        }
    }

    companion object {
        private val directory = File(requireNotNull(ResolvedTokens::class.java.classLoader!!.getResource("fixtures/tokens")).toURI())

        fun read(name: String): JsonValue = JsonValue.parse(File(directory, name).readBytes())
    }
}
