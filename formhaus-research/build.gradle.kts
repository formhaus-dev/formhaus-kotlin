plugins {
    id("formhaus.kotlin-library")
}

description = "Option order for randomized Formhaus fields."

dependencies {
    api(project(":formhaus-core"))
    testImplementation(testFixtures(project(":formhaus-core")))
}

sourceSets.test {
    resources.srcDir(rootProject.file("formhaus-core/src/test/resources"))
}

tasks.test {
    maxHeapSize = "512m"
}
