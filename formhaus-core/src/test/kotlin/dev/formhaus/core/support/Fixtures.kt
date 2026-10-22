package dev.formhaus.core.support

import dev.formhaus.core.json.JsonParseException
import dev.formhaus.core.json.JsonValue
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readBytes
import kotlin.io.path.readLines

object Fixtures {
    val root: Path = Path.of(requireNotNull(Fixtures::class.java.getResource("/fixtures")).toURI())
    val conformance: Path = root.resolve("conformance")
    val scenarios: Path = conformance.resolve("scenarios")
    val regex: Path = root.resolve("regex")

    val version: Map<String, String> by lazy {
        root.resolve("VERSION").readLines().filter { ':' in it }.associate { line ->
            line.substringBefore(':').trim() to line.substringAfter(':').trim()
        }
    }

    val conformanceNames: List<String> by lazy { jsonFiles(conformance) }

    val scenarioNames: List<String> by lazy { jsonFiles(scenarios).filter { it != "scenario.schema" } }

    fun jsonFiles(directory: Path): List<String> =
        directory.listDirectoryEntries().filter { Files.isRegularFile(it) && it.extension == "json" }
            .map { it.nameWithoutExtension }.sorted()

    fun load(path: Path): JsonValue {
        val bytes = path.readBytes()
        return try {
            JsonValue.parse(bytes)
        } catch (error: JsonParseException) {
            if (error.reason != "lone surrogate in string") throw error
            RawReader.parse(bytes)?.value(dropped = "orders") ?: throw error
        }
    }

    fun conformanceFile(name: String): JsonValue = load(conformance.resolve("$name.json"))

    fun scenarioFile(name: String): JsonValue = load(scenarios.resolve("$name.json"))

    fun orders(name: String): List<RawJson> {
        val raw = requireNotNull(RawReader.parse(conformance.resolve("$name.json").readBytes())) { "cannot read $name.json" }
        return raw["orders"]?.items.orEmpty()
    }
}

object Load {
    val heavy: Boolean = System.getenv("FORMHAUS_HEAVY_TESTS") == "1"

    fun <Size> sizes(fast: Size, heavy: Size): List<Size> = if (this.heavy) listOf(fast, heavy) else listOf(fast)
}
