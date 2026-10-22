plugins {
    id("formhaus.android-library")
}

description = "Render benchmarks and recomposition checks for the Formhaus Compose renderer. Not published."

android {
    namespace = "dev.formhaus.benchmarks.compose"

    testOptions {
        unitTests.all {
            it.inputs.property("renderBenchmarks", providers.environmentVariable("FORMHAUS_RENDER_BENCHMARKS").orElse(""))
        }
    }
}

kotlin {
    compilerOptions {
        optIn.add("androidx.compose.runtime.InternalComposeTracingApi")
    }
}

dependencies {
    testImplementation(project(":formhaus-compose"))
    testImplementation(project(":benchmarks"))
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.foundation)
    testImplementation(libs.activity.compose)
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(platform(libs.compose.bom))
    debugImplementation(libs.compose.ui.test.manifest)
}
