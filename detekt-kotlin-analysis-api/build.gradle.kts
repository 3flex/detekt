// This package can be retired once this is closed: https://youtrack.jetbrains.com/issue/KT-56203/AA-Publish-analysis-api-standalone-and-dependencies-to-Maven-Central

plugins {
    id("packaging")
    id("com.gradleup.shadow") version "9.4.1"
}

dependencies {
    // Exclude transitive dependencies due to https://youtrack.jetbrains.com/issue/KT-61639
    api(libs.kotlin.analysisApi) { isTransitive = false }
    api(libs.kotlin.analysisApiK2) { isTransitive = false }

    implementation(libs.kotlin.analysisApiImplBase) { isTransitive = false }
    implementation(libs.kotlin.analysisApiPlatformInterface) { isTransitive = false }
    implementation(libs.kotlin.lowLevelApiFir) { isTransitive = false }
    implementation(libs.kotlin.symbolLightClasses) { isTransitive = false }
    implementation(libs.caffeine) {
        attributes {
            // https://github.com/ben-manes/caffeine/issues/716
            // Remove on upgrade to Caffeine 3.x or if https://youtrack.jetbrains.com/issue/KT-73751 is fixed
            attribute(Bundling.BUNDLING_ATTRIBUTE, named(Bundling.EXTERNAL))
        }
    }
    implementation(libs.kotlinx.serializationCore)
    runtimeOnly(libs.kotlinx.coroutinesCore.intellij)
}

java {
    targetCompatibility = JavaVersion.VERSION_1_8
}

val sourcesJar by tasks.registering(Jar::class) {
    archiveClassifier.set("sources")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(provider {
        configurations.runtimeClasspath.get().incoming.artifactView {
            withVariantReselection()
            attributes {
                attribute(Category.CATEGORY_ATTRIBUTE, objects.named<Category>(Category.DOCUMENTATION))
                attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named<DocsType>(DocsType.SOURCES))
            }
            lenient(true)
        }.files.map { zipTree(it) }
    })
}

publishing {
    publications.named<MavenPublication>(DETEKT_PUBLICATION) {
        artifact(sourcesJar)
    }
}

val javaComponent = components["java"] as AdhocComponentWithVariants
javaComponent.withVariantsFromConfiguration(configurations["apiElements"]) {
    skip()
}
javaComponent.withVariantsFromConfiguration(configurations["runtimeElements"]) {
    skip()
}
