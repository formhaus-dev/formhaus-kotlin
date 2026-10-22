plugins {
    id("formhaus.android-app")
}

android {
    namespace = "dev.formhaus.demo"

    defaultConfig {
        applicationId = "dev.formhaus.demo"
        versionCode = 1
        versionName = "0.2.0"
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
