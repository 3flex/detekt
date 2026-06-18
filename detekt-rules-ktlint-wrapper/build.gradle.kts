import dev.detekt.buildlogic.RelocateJarTransform

plugins {
    id("module")
}

// Custom attribute marking whether a jar's packages have been relocated via the ASM transform.
val relocated = Attribute.of("dev.detekt.relocated", Boolean::class.javaObjectType)

dependencies {
    attributesSchema {
        attribute(relocated)
    }
    // Plain (non-relocated) jars are the default input to the transform.
    artifactTypes.named("jar") {
        attributes.attribute(relocated, false)
    }
    registerTransform(RelocateJarTransform::class) {
        from.attribute(relocated, false)
        to.attribute(relocated, true)
        parameters {
            relocations.put("org.jetbrains.kotlin.com.intellij", "com.intellij")
        }
    }
}

// Declares the ktlint ruleset to relocate; the resolvable view requests the relocated variant,
// which triggers the transform. Only the ktlint graph is ever transformed (not detekt-api etc.).
val ktlintToRelocate = configurations.dependencyScope("ktlintToRelocate")
val ktlintRelocated = configurations.resolvable("ktlintRelocated") {
    extendsFrom(ktlintToRelocate.get())
    attributes {
        attribute(relocated, true)
    }
}

// Extra non-ktlint deps bundled verbatim into the jar (slf4j-nop), no relocation needed.
val extraDepsToPackage = configurations.dependencyScope("extraDepsToPackage")
val extraDepsToPackageClasspath = configurations.resolvable("extraDepsToPackageClasspath") {
    extendsFrom(extraDepsToPackage.get())
}

// Resolves the ktlint dependencies through the relocating transform; mapping to `.files` ensures
// the configuration's `relocated` attribute (and thus the transform) is applied during resolution.
val relocatedKtlintFiles = files(ktlintRelocated.map { it.files })

dependencies {
    compileOnly(projects.detektApi)
    compileOnly(projects.detektPsiUtils)
    // Relocated ktlint on the compile classpath; files(...) keeps it out of the published POM.
    compileOnly(relocatedKtlintFiles)

    ktlintToRelocate(libs.ktlint.rulesetStandard) {
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-compiler-embeddable")
    }

    runtimeOnly(libs.slf4j.api)

    testImplementation(relocatedKtlintFiles)
    testImplementation(libs.kotlin.compiler)
    testImplementation(projects.detektApi)
    testRuntimeOnly(projects.detektPsiUtils)
    testImplementation(projects.detektTest)
    testImplementation(projects.detektTestAssertj)
    testImplementation(projects.detektTestUtils)
    testImplementation(libs.assertj.core)
    testImplementation(libs.classgraph)

    testRuntimeOnly(libs.slf4j.nop)
    testCompileOnly(libs.jetbrains.annotations)

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

tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE // allow duplicates
    from(
        ktlintRelocated.get().map { zipTree(it) },
        extraDepsToPackageClasspath.get().map { zipTree(it) },
    )
}
