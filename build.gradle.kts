import dev.detekt.gradle.report.ReportMergeTask

plugins {
    id("releasing")
    id("dev.detekt")
    id("org.jetbrains.dokka")
}

dependencies {
    dokka(projects.detektApi)
    dokka(projects.detektPsiUtils)
    dokka(projects.detektTest)
    dokka(projects.detektTestAssertj)
    dokka(projects.detektTestUtils)
    dokka(projects.detektTooling)
    dokka("dev.detekt:detekt-gradle-plugin")
}

dokka {
    dokkaPublications.html {
        outputDirectory = layout.projectDirectory.dir("website/static/kdoc")
    }
}

dependencyAnalysis {
    issues {
        all {
            onAny {
                severity("fail")
            }
        }
    }
    structure {
        // Could potentially remove in future if DAGP starts handling this natively https://github.com/autonomousapps/dependency-analysis-gradle-plugin/issues/1269
        bundle("junit-jupiter") {
            includeDependency("org.junit.jupiter:junit-jupiter")
            includeDependency("org.junit.jupiter:junit-jupiter-api")
            includeDependency("org.junit.jupiter:junit-jupiter-params")
        }
    }
}

val detektReportMergeSarif = tasks.register<ReportMergeTask>("detektReportMergeSarif") {
    output = layout.buildDirectory.file("reports/detekt/merge.sarif.json")
}

allprojects {
//    detektReportMergeSarif {
//        input.from(tasks.withType<Detekt>().map { it.reports.sarif.outputLocation })
//    }
}

setOf(
    "detektMain",
    "detektTest",
    "detektFunctionalTest",
    "detektFunctionalTestMinSupportedGradle",
    "detektTestFixtures",
).forEach { taskName ->
    tasks.register(taskName) {
        dependsOn(gradle.includedBuild("detekt-gradle-plugin").task(":$taskName"))
    }
}

tasks.build { dependsOn(gradle.includedBuild("detekt-gradle-plugin").task(":build")) }
