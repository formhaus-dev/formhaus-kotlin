plugins {
    id("formhaus.kotlin-jvm")
    id("ru.vyarus.animalsniffer")
    id("formhaus.public-library")
}

dependencies {
    testImplementation(platform(library("junit-bom")))
    testImplementation(library("junit-jupiter"))
    testRuntimeOnly(library("junit-launcher"))
    signature(variantOf(library("android-signature")) { artifactType("signature") })
}

tasks.test {
    useJUnitPlatform()
}

animalsniffer {
    sourceSets = listOf(project.sourceSets.main.get())
}
