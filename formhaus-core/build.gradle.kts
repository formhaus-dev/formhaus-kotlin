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

listOf("testFixturesApiElements", "testFixturesRuntimeElements").forEach { name ->
    (components["java"] as AdhocComponentWithVariants).withVariantsFromConfiguration(configurations[name]) { skip() }
}
