plugins {
    id("formhaus.android-app")
}

android {
    namespace = "dev.formhaus.demo"

    defaultConfig {
        applicationId = "dev.formhaus.demo"
        versionCode = 1
        versionName = "0.6.0"
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
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.compose.ui.test.junit4)
}

val readmeImages = "dev.formhaus.demo.readme.ReadmeImagesTest"
val unitTestTask = "testDebugUnitTest"

tasks.withType<Test>().matching { it.name == unitTestTask }.configureEach {
    filter.excludeTestsMatching(readmeImages)
}

tasks.register<Test>("recordReadmeImages") {
    description = "Records the README images in .github/assets."
    group = "verification"
    val source = tasks.named<Test>(unitTestTask).get()
    testClassesDirs = source.testClassesDirs
    classpath = source.classpath
    javaLauncher.set(source.javaLauncher)
    maxHeapSize = source.maxHeapSize
    jvmArgumentProviders.addAll(source.jvmArgumentProviders)
    systemProperties(source.systemProperties)
    filter.includeTestsMatching(readmeImages)
    systemProperty("roborazzi.test.record", "true")
    systemProperty("formhaus.readme.assets", rootProject.file(".github/assets").path)
    outputs.upToDateWhen { false }
}
