plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
    alias(libs.plugins.animalsniffer)
    `java-test-fixtures`
    id("formhaus.publishing")
}

java {
    withSourcesJar()
    withJavadocJar()
}

kotlin {
    jvmToolchain(17)
    explicitApi()
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(project(":formhaus-research"))
    testRuntimeOnly(libs.junit.launcher)
    signature(variantOf(libs.android.signature) { artifactType("signature") })
}

tasks.test {
    useJUnitPlatform()
    maxHeapSize = "1g"
}

animalsniffer {
    sourceSets = listOf(project.sourceSets.main.get())
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

description = "Kotlin engine for Formhaus JSON form definitions."

publishing {
    publications {
        register<MavenPublication>("release") {
            from(components["java"])
        }
    }
}

listOf("testFixturesApiElements", "testFixturesRuntimeElements").forEach { name ->
    (components["java"] as AdhocComponentWithVariants).withVariantsFromConfiguration(configurations[name]) { skip() }
}
