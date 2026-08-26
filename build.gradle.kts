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

// Reports to merge from all projects: detektMain detektTest detektFunctionalTest detektTestFixtures detektFunctionalTestMinSupportedGradle

//val artifactType = Attribute.of("dev.detekt.report.type", String::class.java)

val detektAggregation = configurations.dependencyScope("detektAggregation") {
    description = "Collects project dependencies for purposes of detekt report aggregation"
}
val dependenciesThingClasspath = configurations.resolvable("aggregateCodeCoverageReportResults") {
    description = "Resolvable configuration used to gather files for the detekt report aggregation via ArtifactViews"
    extendsFrom(detektAggregation)
    attributes {
        attribute(
            Category.CATEGORY_ATTRIBUTE,
            attributes.named(Category::class.java, Category.VERIFICATION)
        )
        attribute(
            VerificationType.VERIFICATION_TYPE_ATTRIBUTE,
            attributes.named(VerificationType::class.java, "detekt-report")
        )
    }
}

dependencies {
    detektAggregation("dev.detekt:detekt-gradle-plugin")
    detektAggregation(projects.detektApi)
    detektAggregation(projects.detektCli)
    detektAggregation(projects.detektCore)
    detektAggregation(projects.detektGenerator)
    detektAggregation(projects.detektMetrics)
    detektAggregation(projects.detektParser)
    detektAggregation(projects.detektPsiUtils)
    detektAggregation(projects.detektReportCheckstyle)
    detektAggregation(projects.detektReportComplexity)
    detektAggregation(projects.detektReportHtml)
    detektAggregation(projects.detektReportMarkdown)
    detektAggregation(projects.detektReportSarif)
    detektAggregation(projects.detektReportStatistics)
    detektAggregation(projects.detektRulesComments)
    detektAggregation(projects.detektRulesComplexity)
    detektAggregation(projects.detektRulesCoroutines)
    detektAggregation(projects.detektRulesEmptyBlocks)
    detektAggregation(projects.detektRulesExceptions)
    detektAggregation(projects.detektRulesKtlintWrapper)
    detektAggregation(projects.detektRulesLibraries)
    detektAggregation(projects.detektRulesNaming)
    detektAggregation(projects.detektRulesPerformance)
    detektAggregation(projects.detektRulesPotentialBugs)
    detektAggregation(projects.detektRulesRuleauthors)
    detektAggregation(projects.detektRulesStandardLibrary)
    detektAggregation(projects.detektRulesStyle)
    detektAggregation(projects.detektTestUtils)
    detektAggregation(projects.detektTooling)
    detektAggregation(projects.detektUtils)
}

tasks.register("useIt2") {
    inputs.files(dependenciesThingClasspath)
    doLast {
        inputs.files.forEach {
            logger.warn(it.absolutePath)
        }
    }
}

tasks.register("useIt") {
//    val files = dependenciesThingClasspath.get().incoming.files   // lazy FileCollection
//    inputs.files(dependenciesThingClasspath)
    val files = dependenciesThingClasspath.map { it ->
        it.incoming.artifactView {
            withVariantReselection()
            componentFilter { it is ProjectComponentIdentifier }
            attributes {
//                attribute(artifactType, "my-thing")
                attribute(
                    Usage.USAGE_ATTRIBUTE,
                    attributes.named(Usage::class.java, "detekt-report-aggregation")
                )
                attribute(
                    Category.CATEGORY_ATTRIBUTE,
                    attributes.named(Category::class.java, Category.VERIFICATION)
                )
                attribute(
                    VerificationType.VERIFICATION_TYPE_ATTRIBUTE,
                    attributes.named(VerificationType::class.java, "detekt-report")
                )
            }
        }.files
    }
    inputs.files(files)
    doLast {
        files.get().files.forEach {
            logger.warn(it.absolutePath)
        }
    }
}

detektReportMergeSarif {
    input.from(dependenciesThingClasspath)
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
