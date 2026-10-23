plugins {
    id("com.vanniktech.maven.publish")
}

val onJitPack = System.getenv("JITPACK") == "true"

group = if (onJitPack) "${System.getenv("GROUP")}.${System.getenv("ARTIFACT")}" else "dev.formhaus"
version = if (onJitPack) System.getenv("VERSION") else "0.3.0"

mavenPublishing {
    if (!onJitPack) {
        publishToMavenCentral(automaticRelease = true)
        if (providers.gradleProperty("signingInMemoryKey").isPresent) signAllPublications()
    }
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
                id.set("formhaus")
                name.set("Formhaus contributors")
                url.set("https://formhaus.dev")
            }
        }
        scm {
            url.set("https://github.com/formhaus-dev/formhaus-kotlin")
            connection.set("scm:git:https://github.com/formhaus-dev/formhaus-kotlin.git")
            developerConnection.set("scm:git:ssh://git@github.com/formhaus-dev/formhaus-kotlin.git")
        }
    }
}
