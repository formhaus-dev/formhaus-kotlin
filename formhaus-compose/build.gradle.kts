plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "dev.formhaus.compose"
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
    explicitApi()
    compilerOptions {
        allWarningsAsErrors.set(true)
        optIn.add("dev.formhaus.core.renderer.RendererApi")
    }
}

dependencies {
    api(project(":formhaus-core"))
    api(platform(libs.compose.bom))
    api(libs.compose.runtime)
    api(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.kotlinx.coroutines.android)
    debugImplementation(libs.compose.ui.test.manifest)
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
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
