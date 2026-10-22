plugins {
    id("formhaus.coverage")
}

dependencies {
    kover(project(":formhaus-core"))
    kover(project(":formhaus-research"))
    kover(project(":formhaus-compose"))
}
