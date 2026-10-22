import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlin.buildtools.api.BuildOperation
import org.jetbrains.kotlin.buildtools.api.KotlinToolchains
import org.jetbrains.kotlin.buildtools.api.abi.AbiValidationToolchain

internal abstract class AbiToolsTask : DefaultTask() {
    @get:Classpath
    abstract val toolsClasspath: ConfigurableFileCollection

    protected fun abiText(operation: AbiValidationToolchain.(StringBuilder) -> BuildOperation<*>): String {
        val toolchains = KotlinToolchains.loadImplementation(toolsClasspath.map { it.toPath() })
        val text = StringBuilder()
        toolchains.createBuildSession().use {
            it.executeOperation(
                toolchains.getToolchain(AbiValidationToolchain::class.java).operation(text),
            )
        }
        return text.toString()
    }
}

@CacheableTask
internal abstract class AbiDumpTask : AbiToolsTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val classes: ConfigurableFileCollection

    @get:OutputFile
    abstract val dump: RegularFileProperty

    @TaskAction
    fun write() {
        val classFiles = classes.asFileTree.filter { it.extension == "class" }.map { it.toPath() }
        dump.get().asFile.writeText(abiText { dumpJvmAbiToStringOperationBuilder(it, classFiles).build() })
    }
}

internal abstract class AbiCheckTask : AbiToolsTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val reference: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val actual: RegularFileProperty

    @TaskAction
    fun compare() {
        val expected = reference.get().asFile
        if (expected.readText() == actual.get().asFile.readText()) return
        val diff = abiText { compareAbiTextFilesOperationBuilder(it, expected.toPath(), actual.get().asFile.toPath()).build() }
        throw GradleException("${expected.name} is out of date. Run ./gradlew updateKotlinAbi and commit the dump.\n$diff")
    }
}
