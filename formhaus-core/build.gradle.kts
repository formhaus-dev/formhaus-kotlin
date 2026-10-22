plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
    alias(libs.plugins.animalsniffer)
    `maven-publish`
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

group = "dev.formhaus"
version = "0.1.0"

publishing {
    publications {
        create<MavenPublication>("release") {
            from(components["java"])
            pom {
                name.set("formhaus-core")
                description.set("Kotlin engine for Formhaus JSON form definitions.")
                url.set("https://github.com/formhaus-dev/formhaus-kotlin")
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                developers {
                    developer {
                        name.set("Formhaus contributors")
                        url.set("https://formhaus.dev")
                    }
                }
                scm {
                    url.set("https://github.com/formhaus-dev/formhaus-kotlin")
                    connection.set("scm:git:https://github.com/formhaus-dev/formhaus-kotlin.git")
                }
            }
        }
    }
}
