package dev.formhaus.benchmarks

import java.io.File

private class Row(
    val label: String,
    val key: String,
)

private fun table(
    header: List<String>,
    rows: List<List<String>>,
): String {
    val align = header.mapIndexed { index, _ -> if (index == 0) "---" else "---:" }
    return (listOf(header, align) + rows).joinToString("\n") { cells -> cells.joinToString(" | ", "| ", " |") { it.replace("|", "\\|") } }
}

private fun regexRows(): List<Row> {
    val typical = RegexCases.typical
    val matches =
        typical.flatMap { case ->
            listOf("short" to "matchShort", "long" to "matchLong").map { (size, method) ->
                Row("match ${case.label}, $size", "$method:${case.id}")
            }
        }
    val pathological = RegexCases.pathological.map { Row(it.label, "match:${it.id}") }
    val compiles = typical.map { Row("compile ${it.label}", "compile:${it.id}") }
    return matches + pathological + compiles
}

private fun regexTable(
    jmh: JmhResults,
    v8: V8Results,
    sampling: Sampling,
): String {
    val capped = RegexCases.pathological.associate { "match:${it.id}" to cappedJavaRegex(it, sampling) }
    val rows =
        regexRows().map { row ->
            val formhaus = jmh["${if (row.key.startsWith("match:")) "PathologicalRegexBenchmark" else "FormhausRegexBenchmark"}.${row.key}"]
            val java = capped[row.key] ?: jmh["JavaRegexBenchmark.${row.key}"]?.let(::Measured) ?: Measured("n/a")
            listOf(
                row.label,
                v8[row.key],
                java.text,
                formhaus?.let(::formatNanos) ?: "n/a",
                if (java.nanos != null && formhaus != null) formatRatio(java.nanos, formhaus) else "",
            )
        }
    return table(listOf("Benchmark", "V8", "java.util.regex", "formhaus", "formhaus vs java.util.regex"), rows)
}

private fun singleTable(
    jmh: JmhResults,
    rows: List<Row>,
): String = table(listOf("Benchmark", "formhaus"), rows.map { listOf(it.label, jmh[it.key]?.let(::formatNanos) ?: "n/a") })

private val recallRows =
    listOf(
        Row("recall text without tokens", "RecallBenchmark.textWithoutTokens"),
        Row("recall four answers as stored", "RecallBenchmark.fourAnswersAsStored"),
        Row("recall four answers formatted", "RecallBenchmark.fourAnswersFormatted"),
    )

private val engineRows =
    listOf(
        Row("create a FormEngine for a 4-step form", "EngineBenchmark.create"),
        Row("setValue on a 4-step form", "EngineBenchmark.setValue"),
        Row("validateStep on a 4-field step", "EngineBenchmark.validateStep"),
    )

private fun option(
    args: Array<String>,
    name: String,
): String? = args.indexOf("--$name").takeIf { it >= 0 }?.let { args.getOrNull(it + 1) }

fun main(args: Array<String>) {
    val jmh =
        JmhResults(
            File(requireNotNull(option(args, "jmh")) { "usage: --jmh <file> [--v8 <file>] [--warmups n] [--runs n] [--cap seconds]" }),
        )
    val v8 = V8Results(option(args, "v8")?.let(::File))
    val sampling =
        Sampling(
            warmups = option(args, "warmups")?.toInt() ?: 2,
            runs = option(args, "runs")?.toInt() ?: 10,
            capSeconds = option(args, "cap")?.toLong() ?: 5,
        )
    println("Regex: one validateFieldValue call with a pattern rule, median\n")
    println(regexTable(jmh, v8, sampling))
    println(
        "\njava.util.regex runs pathological patterns outside JMH: the median of ${sampling.runs} calls " +
            "after ${sampling.warmups} warmups, each call capped at ${sampling.capSeconds} s. " +
            "It reads `[^]` as `[\\s\\S]` and `[]` as `[^\\s\\S]`.",
    )
    println("\nRecall: median\n")
    println(singleTable(jmh, recallRows))
    println("\nEngine: median\n")
    println(singleTable(jmh, engineRows))
}
