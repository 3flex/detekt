// This package can be retired once this is closed: https://youtrack.jetbrains.com/issue/KT-56203/AA-Publish-analysis-api-standalone-and-dependencies-to-Maven-Central

plugins {
    id("packaging")
    id("com.gradleup.shadow") version "9.6.1"
}

val aaDependency = configurations.dependencyScope("aaDependency")
val aaDependencies = configurations.resolvable("aaDependencies") {
    extendsFrom(aaDependency)
}
val decompilerDependency = configurations.dependencyScope("decompilerDependency")
val decompilerDependencies = configurations.resolvable("decompilerDependencies") {
    extendsFrom(decompilerDependency)
}

dependencies {
    // Exclude transitive dependencies due to https://youtrack.jetbrains.com/issue/KT-61639
    aaDependency(libs.kotlin.analysisApiStandalone) { isTransitive = false }
    decompilerDependency(libs.kotlin.compilerCommonForIde) { isTransitive = false }
}

val defaultJarClassifier = "default-jar"

tasks.jar {
    archiveClassifier = defaultJarClassifier
}

configurations.runtimeElements {
    outgoing.artifacts.removeIf { it.classifier == defaultJarClassifier && it.extension == "jar" }
    outgoing.artifact(tasks.shadowJar)
}

configurations.apiElements {
    outgoing.variants.removeIf { it.name == "classes" }

    outgoing.artifacts.removeIf { it.classifier == defaultJarClassifier && it.extension == "jar" }
    outgoing.artifact(tasks.shadowJar)
}

tasks.shadowJar {
    archiveClassifier = ""
    configurations = aaDependencies.map { listOf(it) }
    // The standalone Analysis API session requires the class file decompiler, which is no longer part of the
    // kotlin-compiler artifact since Kotlin 2.4.20. It is only published in kotlin-compiler-common-for-ide, which
    // otherwise overlaps kotlin-compiler, so only the decompiler classes are repackaged here.
    from(decompilerDependencies.map { classpath -> classpath.incoming.files.map { jar -> zipTree(jar) } }) {
        include("org/jetbrains/kotlin/analysis/decompiled/**")
        include("org/jetbrains/kotlin/analysis/decompiler/**")
        include("META-INF/org.jetbrains.kotlin_decompiler-*.kotlin_module")
        include("META-INF/org.jetbrains.kotlin_light-classes-for-decompiled.kotlin_module")
    }
}

val sourcesJar = tasks.register<Jar>("sourcesJar") {
    archiveClassifier = "sources"

    from(
        aaDependencies.map {
            it.incoming.artifactView {
                withVariantReselection()
                attributes {
                    attribute(Category.CATEGORY_ATTRIBUTE, named(Category.DOCUMENTATION))
                    attribute(DocsType.DOCS_TYPE_ATTRIBUTE, named(DocsType.SOURCES))
                }
            }.files.map { jar -> zipTree(jar) }
        }
    )
}

java {
    targetCompatibility = JavaVersion.VERSION_1_8
    withSourcesJar()
}

shadow {
    addShadowVariantIntoJavaComponent = false
}
