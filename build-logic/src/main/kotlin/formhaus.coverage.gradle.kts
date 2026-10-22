plugins {
    id("org.jetbrains.kotlinx.kover")
}

configureCoverage()

tasks.register("coverage") {
    group = "verification"
    description = "Writes the merged coverage report of the published libraries and prints the totals."
    val variant = COVERAGE_VARIANT.replaceFirstChar(Char::uppercaseChar)
    dependsOn("koverHtmlReport$variant", "koverLog$variant")
}
