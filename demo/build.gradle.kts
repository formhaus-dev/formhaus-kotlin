plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

android {
    namespace = "dev.formhaus.demo"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.formhaus.demo"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.2.0"
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
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    implementation(project(":formhaus-compose"))
    implementation(project(":formhaus-research"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    debugImplementation(libs.compose.ui.test.manifest)
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.compose.ui.test.junit4)
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
