plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

description = "Render benchmarks and recomposition checks for the Formhaus Compose renderer. Not published."

android {
    namespace = "dev.formhaus.benchmarks.compose"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.maxHeapSize = "1536m"
                it.inputs.property("renderBenchmarks", providers.environmentVariable("FORMHAUS_RENDER_BENCHMARKS").orElse(""))
            }
        }
    }
}

androidComponents {
    beforeVariants(selector().withBuildType("release")) { variant ->
        variant.hostTests[com.android.build.api.variant.HostTestBuilder.UNIT_TEST_TYPE]?.enable = false
    }
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        allWarningsAsErrors.set(true)
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

ktlint {
    version.set(
        libs.versions.ktlint.engine
            .get(),
    )
    android.set(true)
}

detekt {
    config.setFrom(rootProject.file("config/detekt.yml"))
    buildUponDefaultConfig = true
    allRules = false
    parallel = false
}
