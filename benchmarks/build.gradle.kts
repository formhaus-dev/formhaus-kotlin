plugins {
    id("formhaus.kotlin-jvm")
    id("org.jetbrains.kotlin.plugin.allopen")
    id("org.jetbrains.kotlinx.benchmark")
}

description = "Benchmarks for the Formhaus Kotlin engine. Not published."

kotlin {
    compilerOptions {
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
