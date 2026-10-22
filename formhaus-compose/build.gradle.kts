plugins {
    id("formhaus.android-library")
    id("formhaus.public-library")
    id("formhaus.screenshots")
}

description = "Jetpack Compose renderer for Formhaus JSON form definitions."

android {
    namespace = "dev.formhaus.compose"
}

kotlin {
    compilerOptions {
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
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.viewmodel.savedstate)
    implementation(libs.kotlinx.coroutines.android)
    debugImplementation(libs.compose.ui.test.manifest)
    testImplementation(project(":formhaus-research"))
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
}
