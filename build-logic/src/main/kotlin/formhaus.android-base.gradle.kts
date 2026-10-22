import com.android.build.api.dsl.CommonExtension
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.HasHostTestsBuilder
import com.android.build.api.variant.HostTestBuilder

plugins {
    id("org.jetbrains.kotlin.plugin.compose")
    id("formhaus.lint")
}

extensions.configure<CommonExtension> {
    compileSdk = 37
    defaultConfig.minSdk = 26
    compileOptions.sourceCompatibility = JavaVersion.VERSION_17
    compileOptions.targetCompatibility = JavaVersion.VERSION_17
    buildFeatures.compose = true
    testOptions.unitTests.isIncludeAndroidResources = true
    testOptions.unitTests.all { it.maxHeapSize = "1536m" }
}

extensions.getByType(AndroidComponentsExtension::class.java).run {
    beforeVariants(selector().withBuildType("release")) { variant ->
        (variant as HasHostTestsBuilder).hostTests[HostTestBuilder.UNIT_TEST_TYPE]?.enable = false
    }
}

configureKotlin()

ktlint {
    android.set(true)
}
