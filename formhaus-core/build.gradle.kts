plugins {
    id("formhaus.kotlin-library")
    `java-test-fixtures`
}

description = "Kotlin engine for Formhaus JSON form definitions."

kotlin {
    compilerOptions {
        optIn.add("dev.formhaus.core.renderer.RendererApi")
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(project(":formhaus-research"))
}

tasks.test {
    maxHeapSize = "1g"
}

afterEvaluate {
    configurations.filter { it.name.startsWith("testFixtures") && it.name.endsWith("Elements") }.forEach {
        (components["java"] as AdhocComponentWithVariants).withVariantsFromConfiguration(it) { skip() }
    }
}
