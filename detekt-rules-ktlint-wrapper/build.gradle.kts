plugins {
    id("module")
    id("com.gradleup.shadow") version "9.4.1"
}

val extraDepsToPackage by configurations.registering

dependencies {
    compileOnly(projects.detektApi)
    compileOnly(projects.detektPsiUtils)
    implementation(projects.detektRulesKtlintWrapper.ktlintRepackage) {
        attributes {
            attribute(Bundling.BUNDLING_ATTRIBUTE, named(Bundling.SHADOWED))
        }
    }

    runtimeOnly(libs.slf4j.api)

    testImplementation(libs.kotlin.compiler)
    testImplementation(projects.detektApi)
    testRuntimeOnly(projects.detektPsiUtils)
    testImplementation(projects.detektTest)
    testImplementation(projects.detektTestAssertj)
    testImplementation(projects.detektTestUtils)
    testImplementation(libs.assertj.core)
    testImplementation(libs.classgraph)

    testRuntimeOnly(libs.slf4j.nop)
    extraDepsToPackage(libs.slf4j.nop)
}

consumeGeneratedConfig(
    fromProject = projects.detektGenerator,
    fromConfiguration = "generatedKtlintWrapperConfig",
    forTask = tasks.sourcesJar
)
consumeGeneratedConfig(
    fromProject = projects.detektGenerator,
    fromConfiguration = "generatedKtlintWrapperConfig",
    forTask = tasks.processResources
)

shadow {
    addShadowVariantIntoJavaComponent = false
}

publishing {
    publications.named<MavenPublication>(DETEKT_PUBLICATION) {
        artifact(tasks.shadowJar)
    }
}

tasks.shadowJar {
    configurations = listOf(
        project.configurations.runtimeClasspath.get(),
        extraDepsToPackage.get()
    )
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    mergeServiceFiles()
}
