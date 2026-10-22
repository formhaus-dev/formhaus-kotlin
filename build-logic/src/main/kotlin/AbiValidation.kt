import org.gradle.api.Project
import org.gradle.api.tasks.Copy
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

internal fun Project.configureAbiValidation(compileTask: String) {
    val tools =
        configurations.create("kotlinAbiTools") {
            isCanBeConsumed = false
            dependencies.add(project.dependencies.create(library("kotlin-build-tools-impl").get()))
        }
    val dumpFile = "$name.api"
    val dump =
        tasks.register<AbiDumpTask>("dumpKotlinAbi") {
            toolsClasspath.from(tools)
            classes.from(tasks.withType<KotlinJvmCompile>().matching { it.name == compileTask })
            dump.set(layout.buildDirectory.file("kotlin/abi/$dumpFile"))
        }
    val check =
        tasks.register<AbiCheckTask>("checkKotlinAbi") {
            group = "verification"
            description = "Checks the public API against the committed dump in api/."
            toolsClasspath.from(tools)
            reference.set(layout.projectDirectory.file("api/$dumpFile"))
            actual.set(dump.flatMap { it.dump })
        }
    tasks.register<Copy>("updateKotlinAbi") {
        group = "verification"
        description = "Writes the public API dump to api/."
        from(dump)
        into(layout.projectDirectory.dir("api"))
    }
    tasks.named("check") {
        dependsOn(check)
    }
}
