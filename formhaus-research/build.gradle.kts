plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
    alias(libs.plugins.animalsniffer)
    id("formhaus.publishing")
}

description = "Option order for randomized Formhaus fields."

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
    api(project(":formhaus-core"))
    testImplementation(testFixtures(project(":formhaus-core")))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
    signature(variantOf(libs.android.signature) { artifactType("signature") })
}

sourceSets.test {
    resources.srcDir(rootProject.file("formhaus-core/src/test/resources"))
}

tasks.test {
    useJUnitPlatform()
    maxHeapSize = "512m"
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

publishing {
    publications {
        register<MavenPublication>("release") {
            from(components["java"])
        }
    }
}
