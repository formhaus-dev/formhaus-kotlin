plugins {
    id("io.github.takahirom.roborazzi")
}

roborazzi {
    outputDir.set(file("src/test/screenshots"))
}

tasks.named("check") {
    dependsOn("verifyRoborazziDebug")
}
