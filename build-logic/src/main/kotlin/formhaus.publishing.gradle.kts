import com.android.build.api.dsl.LibraryExtension

plugins {
    `maven-publish`
}

val onJitPack = System.getenv("JITPACK") == "true"

group = if (onJitPack) "${System.getenv("GROUP")}.${System.getenv("ARTIFACT")}" else "dev.formhaus"
version = if (onJitPack) System.getenv("VERSION") else "0.2.0"

pluginManager.withPlugin("java") {
    extensions.configure<JavaPluginExtension> {
        withSourcesJar()
        withJavadocJar()
    }
}

pluginManager.withPlugin("com.android.library") {
    extensions.configure<LibraryExtension> {
        publishing {
            singleVariant("release") {
                withSourcesJar()
                withJavadocJar()
            }
        }
    }
}

publishing {
    publications.register<MavenPublication>("release") {
        pluginManager.withPlugin("java") { from(components["java"]) }
        pluginManager.withPlugin("com.android.library") { afterEvaluate { from(components["release"]) } }
    }
    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set(project.name)
            description.set(project.provider { project.description })
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
