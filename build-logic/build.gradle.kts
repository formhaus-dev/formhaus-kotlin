plugins {
    `kotlin-dsl`
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

fun Provider<PluginDependency>.artifact() = map { "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version}" }

dependencies {
    with(libs.plugins) {
        listOf(
            kotlin.jvm,
            kotlin.compose,
            kotlin.allopen,
            kotlinx.benchmark,
            android.library,
            ktlint,
            detekt,
            animalsniffer,
            roborazzi,
            kover,
        ).forEach { implementation(it.artifact()) }
    }
    implementation(libs.kotlin.build.tools.api)
}

kotlin {
    compilerOptions {
        optIn.add("org.jetbrains.kotlin.buildtools.api.ExperimentalBuildToolsApi")
    }
}

ktlint {
    version.set(libs.versions.ktlint.engine)
    filter {
        exclude { "generated-sources" in it.file.path }
    }
}

detekt {
    config.setFrom(file("../config/detekt.yml"))
    buildUponDefaultConfig = true
    parallel = false
}
