import org.jetbrains.kotlin.gradle.dsl.KotlinBaseExtension

plugins {
    id("formhaus.publishing")
    id("org.jetbrains.kotlinx.kover")
}

extensions.configure<KotlinBaseExtension> {
    explicitApi()
}

configureCoverage("jvm", "debug")

pluginManager.withPlugin("java") { configureAbiValidation("compileKotlin") }
pluginManager.withPlugin("com.android.library") { configureAbiValidation("compileReleaseKotlin") }
