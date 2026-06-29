// Gradle test suite limitations mean we have to reference "functionalTest" in many places.
// https://github.com/gradle/gradle/issues/21285
@file:Suppress("StringLiteralDuplication")

import dev.detekt.buildlogic.osDependent
import dev.detekt.gradle.Detekt
import dev.detekt.gradle.DetektCreateBaselineTask
import org.gradle.plugin.compatibility.compatibility
import org.jetbrains.kotlin.buildtools.api.ExperimentalBuildToolsApi
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    id("module")
    id("java-gradle-plugin")
    id("java-test-fixtures")
    id("idea")
    id("com.gradle.plugin-publish") version "2.1.1"
    // We use this published version of the detekt plugin to self analyse this project.
    id("dev.detekt") version "2.0.0-alpha.3"
    id("org.jetbrains.kotlinx.binary-compatibility-validator") version "0.18.1"
    id("org.jetbrains.dokka") version "2.2.0"
    id("com.github.gmazzo.buildconfig") version "6.0.10"
}

group = "dev.detekt"
version = Versions.currentOrSnapshot()

buildConfig {
    buildConfigField("DETEKT_VERSION", project.version.toString())
    buildConfigField("DETEKT_COMPILER_PLUGIN_VERSION", project.version.toString())
    buildConfigField("KOTLIN_IMPLEMENTATION_VERSION", libs.versions.kotlin.get())
}

detekt {
    source.from("src/functionalTest/kotlin")
    buildUponDefaultConfig = true
    baseline = file("config/gradle-plugin-baseline.xml")
    config.setFrom("config/gradle-plugin-detekt.yml")
}

dokka {
    dokkaPublications.configureEach {
        failOnWarning = true
    }

    dokkaSourceSets.configureEach {
        apiVersion = "1.4"

        externalDocumentationLinks {
            create("gradle") {
                url("https://docs.gradle.org/current/javadoc/")
                packageListUrl("https://docs.gradle.org/current/javadoc/element-list")
            }
        }
    }

    dokkaPublications.html {
        suppressInheritedMembers = true
    }
}

testing {
    suites {
        getByName("test", JvmTestSuite::class) {
            dependencies {
                implementation(libs.assertj.core)
                implementation(libs.kotlin.gradle.plugin)
                runtimeOnly(gradleKotlinDsl())
            }
        }
        register<JvmTestSuite>("functionalTest") {
            dependencies {
                implementation(libs.assertj.core)
                implementation(project())
                implementation(testFixtures(project()))
            }

            targets {
                all {
                    testTask.configure {
                        osDependent()
                        // If `androidSdkInstalled` is false, skip running DetektAndroidSpec
                        val isAndroidSdkInstalled = providers.environmentVariable("ANDROID_SDK_ROOT").isPresent ||
                            providers.environmentVariable("ANDROID_HOME").isPresent
                        inputs.property("isAndroidSdkInstalled", isAndroidSdkInstalled).optional(true)
                    }
                }
            }
        }
        register<JvmTestSuite>("functionalTestMinSupportedGradle") {
            dependencies {
                implementation(libs.assertj.core)
                implementation(testFixtures(project()))
            }
            targets {
                all {
                    testTask {
                        dependsOn("gradleMinVersionPluginUnderTestMetadata")
                    }
                }
            }
        }
    }
}

kotlin {
    @OptIn(ExperimentalBuildToolsApi::class, ExperimentalKotlinGradlePluginApi::class)
    compilerVersion = "2.1.21"

    compilerOptions {
        // The apiVersion Gradle property cannot be used here, so set api version using free compiler args.
        // https://youtrack.jetbrains.com/issue/KT-72247/KGP-Cannot-use-unsupported-API-version-with-compilerVersion-that-supports-it#focus=Comments-27-11050897.0-0
        freeCompilerArgs.addAll("-language-version", "1.8")
        freeCompilerArgs.addAll("-api-version", "1.7")
        // Suppress warning about deprecated API version. When DGP compiles with Kotlin 2.4 change this to suppress DEPRECATED_LANGUAGE_VERSION diagnostic (see KT-83765)
        freeCompilerArgs.add("-Xsuppress-version-warnings")
    }

    // Some functional tests reference internal functions in the Gradle plugin. This should become unnecessary as further
    // updates are made to the functional test suite.
    target.compilations.getByName("functionalTest") {
        associateWith(target.compilations.getByName("main"))
    }
}

val testKitRuntimeOnly = configurations.register("testKitRuntimeOnly")
val testKitGradleMinVersionRuntimeOnly = configurations.register("testKitGradleMinVersionRuntimeOnly")

// Resolves the JaCoCo agent runtime jar so it can be injected into the TestKit-spawned Gradle JVMs.
// See the `jacoco.agent.jar` system property wiring on the functional test tasks below.
val jacocoAgentRuntime = configurations.register("jacocoAgentRuntime")

dependencies {
    compileOnly(libs.android.gradleApi)
    compileOnly(libs.kotlin.gradlePluginApi)

    // gradle-public-api is consumed compile-only across every source set: production code,
    // tests, fixtures, and functional tests all rely on Gradle types provided by the runtime
    // (the user's Gradle distribution or the embedded TestKit). compileOnly keeps it out of
    // the published POM (compileOnlyApi would re-add it as a compile-scope dep). See #9396.
    listOf(
        "compileOnly",
        "testCompileOnly",
        "testFixturesCompileOnly",
        "functionalTestCompileOnly",
        "functionalTestMinSupportedGradleCompileOnly",
    ).forEach { configurationName ->
        configurationName(libs.gradle.publicApi) {
            capabilities {
                // https://github.com/gradle/gradle/issues/29483#issuecomment-2791668178
                requireCapability("org.gradle.experimental:gradle-public-api-internal")
            }
        }
    }

    implementation(libs.sarif4k)

    testKitRuntimeOnly(libs.kotlin.gradle.plugin)
    testKitRuntimeOnly(libs.android.gradle.plugin)
    testKitRuntimeOnly(libs.android.gradle.builtInKotlin.plugin)
    testKitGradleMinVersionRuntimeOnly(libs.kotlin.gradle.plugin) {
        attributes {
            // Set this value to the minimum Gradle version tested in testKitGradleMinVersionRuntimeOnly source set
            attribute(GradlePluginApiVersion.GRADLE_PLUGIN_API_VERSION_ATTRIBUTE, named("7.6.3"))
        }
    }
    jacocoAgentRuntime("org.jacoco:org.jacoco.agent:${libs.versions.jacoco.get()}:runtime")

    // We use this published version of the detekt-rules-ktlint-wrapper to self analyse this project.
    detektPlugins("dev.detekt:detekt-rules-ktlint-wrapper:2.0.0-alpha.3")
}

gradlePlugin {
    website = "https://detekt.dev"
    vcsUrl = "https://github.com/detekt/detekt"
    plugins {
        register("dev.detekt.gradle.base") {
            displayName = "Static code analysis for Kotlin - Base Plugin"
            description = "Static code analysis for Kotlin - Base Plugin"
            implementationClass = "dev.detekt.gradle.plugin.DetektBasePlugin"
            compatibility {
                features.configurationCache = true
            }
        }
        register("dev.detekt") {
            displayName = "Static code analysis for Kotlin"
            description = "Static code analysis for Kotlin"
            implementationClass = "dev.detekt.gradle.plugin.DetektPlugin"
            compatibility {
                features.configurationCache = true
            }
        }
        register("dev.detekt.gradle.compiler-plugin") {
            displayName = "Static code analysis for Kotlin - Compiler Plugin"
            description = "Static code analysis for Kotlin - Compiler Plugin"
            implementationClass = "dev.detekt.gradle.plugin.DetektKotlinCompilerPlugin"
        }
        configureEach {
            tags = listOf("kotlin", "detekt", "code-analysis", "linter", "codesmells", "android")
        }
    }
    // Source sets that require the Gradle TestKit dependency
    testSourceSets(
        sourceSets["testFixtures"],
        sourceSets["functionalTest"],
        sourceSets["functionalTestMinSupportedGradle"],
    )
}

tasks {
    /*
     * Ignore metadata version checks for tests. Gradle API is on the classpath which includes stdlib and reflect libs
     * that have Kotlin metadata too new for the Kotlin compiler version used in this build to read. This affects test
     * compilations only. The suppress-gradle-api system property set for this build does not affect test compilations
     * at this stage, so replacing the Gradle API with the separately published Gradle API is not currently possible.
     */
    named<KotlinJvmCompile>("compileFunctionalTestKotlin") {
        compilerOptions {
            freeCompilerArgs.add("-Xskip-metadata-version-check")
        }
    }

    named<KotlinJvmCompile>("compileTestFixturesKotlin") {
        compilerOptions {
            freeCompilerArgs.add("-Xskip-metadata-version-check")
        }
    }

    named<KotlinJvmCompile>("compileFunctionalTestMinSupportedGradleKotlin") {
        compilerOptions {
            freeCompilerArgs.add("-Xskip-metadata-version-check")
        }
    }

    // Manually inject dependency to gradle-testkit since the default injected plugin classpath is from `main.runtime`.
    pluginUnderTestMetadata {
        pluginClasspath.from(testKitRuntimeOnly)
    }

    validatePlugins {
        enableStricterValidation = true
    }

    register<PluginUnderTestMetadata>("gradleMinVersionPluginUnderTestMetadata") {
        pluginClasspath.setFrom(sourceSets.main.get().output, testKitGradleMinVersionRuntimeOnly)
        outputDirectory = layout.buildDirectory.dir(name)
    }

    withType<Detekt>().configureEach {
        exclude("dev/detekt/detekt_gradle_plugin/BuildConfig.kt")
    }
    withType<DetektCreateBaselineTask>().configureEach {
        exclude("dev/detekt/detekt_gradle_plugin/BuildConfig.kt")
    }

    withType<Test>().configureEach {
        develocity {
            testRetry {
                @Suppress("MagicNumber")
                if (providers.environmentVariable("CI").isPresent) {
                    maxRetries = 2
                    maxFailures = 20
                }
            }
        }
    }

    // JaCoCo does not instrument the Gradle JVMs that TestKit spawns to run the build under test, so
    // functional tests would otherwise contribute no coverage of the plugin. Expose the agent jar and
    // a per-suite destination directory to the test JVM; DslGradleRunner reads these system properties
    // and injects the agent into the spawned JVMs via -Dorg.gradle.jvmargs.
    val agentJarPath = jacocoAgentRuntime.map { it.singleFile.absolutePath }
    listOf("functionalTest", "functionalTestMinSupportedGradle").forEach { suiteName ->
        named<Test>(suiteName) {
            val execDir = layout.buildDirectory.dir("jacoco/testkit/$suiteName")
            inputs.files(jacocoAgentRuntime)
            outputs.dir(execDir)
            doFirst {
                val dir = execDir.get().asFile
                dir.mkdirs()
                systemProperty("jacoco.agent.jar", agentJarPath.get())
                systemProperty("jacoco.testkit.destdir", dir.absolutePath)
            }
        }
    }

    // Aggregation in `code-coverage-report` cannot reach this included build, so produce the plugin's
    // own merged report here, combining unit-test coverage with the TestKit exec files.
    register<JacocoReport>("jacocoTestKitReport") {
        dependsOn("test", "functionalTest", "functionalTestMinSupportedGradle")
        executionData(
            fileTree(layout.buildDirectory) {
                // *.exec: coverage from the test JVMs (also catches plugin code applied in-process via
                // ProjectBuilder). testkit/**: coverage from the TestKit-spawned Gradle JVMs.
                include("jacoco/*.exec", "jacoco/testkit/**/*.exec")
            }
        )
        sourceDirectories.from(sourceSets["main"].allSource.srcDirs)
        classDirectories.from(
            files(sourceSets["main"].output.classesDirs).asFileTree.matching {
                // Generated build metadata, also excluded from detekt self-analysis above.
                exclude("dev/detekt/detekt_gradle_plugin/BuildConfig*")
            }
        )
        reports {
            xml.required = true
            html.required = false
        }
    }

    check {
        dependsOn(
            testing.suites.named("functionalTest"),
            testing.suites.named("functionalTestMinSupportedGradle"),
        )
    }
}

// Skip publishing of test fixture API & runtime variants
with(components["java"] as AdhocComponentWithVariants) {
    withVariantsFromConfiguration(configurations["testFixturesApiElements"]) { skip() }
    withVariantsFromConfiguration(configurations["testFixturesRuntimeElements"]) { skip() }
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
