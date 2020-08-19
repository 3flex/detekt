import org.jetbrains.dokka.gradle.DokkaTask

plugins {
    id("org.jetbrains.dokka")
    id("info.solidsoft.pitest") version "1.4.6"
}

val yamlVersion: String by project
val junitPlatformVersion: String by project
val spekVersion: String by project

dependencies {
    implementation("org.yaml:snakeyaml:$yamlVersion")
    api(kotlin("compiler-embeddable"))
    implementation(kotlin("reflect"))
//    compileOnly("org.pitest:pitest-junit5-plugin:0.12")

    testImplementation(project(":detekt-test"))
}

pitest {
    useClasspathFile.set(true)
    testPlugin.set("junit5")
    pitestVersion.set("1.5.0")
    verbose.set(true)
    avoidCallsTo.add("kotlin.jvm.internal")
    mainProcessJvmArgs.add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005")
}

tasks.withType<DokkaTask> {
    outputFormat = "jekyll"
    outputDirectory = "$rootDir/docs/pages/kdoc"
    configuration {
        // suppresses undocumented classes but not dokka warnings https://github.com/Kotlin/dokka/issues/90
        reportUndocumented = false
        @Suppress("MagicNumber")
        jdkVersion = 8
    }
}

tasks.withType<Test> {
    systemProperty("kotlinVersion", embeddedKotlinVersion)

    doFirst {
        systemProperty("testClasspath", classpath.joinToString(";"))
    }
}
