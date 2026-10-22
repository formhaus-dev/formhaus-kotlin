plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.allopen)
    alias(libs.plugins.kotlinx.benchmark)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

description = "Benchmarks for the Formhaus Kotlin engine. Not published."

kotlin {
    jvmToolchain(17)
    compilerOptions {
        allWarningsAsErrors.set(true)
        optIn.add("dev.formhaus.core.renderer.RendererApi")
    }
}

allOpen {
    annotation("org.openjdk.jmh.annotations.State")
}

dependencies {
    implementation(project(":formhaus-core"))
    implementation(libs.kotlinx.benchmark.runtime)
    implementation(libs.jmh.core)
}

benchmark {
    targets {
        register("main")
    }
    configurations {
        named("main") {
            reportFormat = "json"
        }
        register("smoke") {
            warmups = 1
            iterations = 2
            iterationTime = 100
            iterationTimeUnit = "ms"
            reportFormat = "json"
        }
    }
}

tasks.register<JavaExec>("benchmarkReport") {
    group = "benchmark"
    description = "Prints the benchmark tables from a JMH report and an optional V8 report."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("dev.formhaus.benchmarks.ReportKt")
    maxHeapSize = "1g"
}

tasks.named("check") {
    dependsOn("mainBenchmarkJar")
}

ktlint {
    version.set(
        libs.versions.ktlint.engine
            .get(),
    )
}

detekt {
    config.setFrom(rootProject.file("config/detekt.yml"))
    buildUponDefaultConfig = true
    allRules = false
    parallel = false
}
