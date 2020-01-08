configurations.testImplementation.get().extendsFrom(configurations.kotlinTest.get())

val spekVersion: String by project
val reflectionsVersion: String by project

dependencies {
    api(project(":detekt-api"))

    testImplementation(project(":detekt-rules"))
    testImplementation(project(":detekt-test"))
    testImplementation("org.reflections:reflections:$reflectionsVersion")
    testRuntimeOnly("org.spekframework.spek2:spek-runner-junit5:$spekVersion")
}
