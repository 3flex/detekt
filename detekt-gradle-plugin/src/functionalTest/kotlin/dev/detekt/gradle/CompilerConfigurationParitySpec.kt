package dev.detekt.gradle

import dev.detekt.gradle.testkit.DslTestBuilder
import dev.detekt.gradle.testkit.joinGradleBlocks
import org.assertj.core.api.Assertions.assertThat
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class CompilerConfigurationParitySpec {

    @Nested
    inner class `detekt task mirrors the compiler configuration of the companion compilation task` {

        @Test
        fun `with compiler options configured on the kotlin extension`() {
            @Language("gradle.kts")
            val kotlinConfig = """
                kotlin {
                    compilerOptions {
                        apiVersion = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_0
                        languageVersion = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_1
                        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
                        progressiveMode = true
                        optIn.add("kotlin.RequiresOptIn")
                        freeCompilerArgs.add("-Xstring-concat=inline")
                    }
                }
            """.trimIndent()

            val gradleRunner = DslTestBuilder.kotlin()
                .withDetektConfig(joinGradleBlocks(kotlinConfig, verifyParityTask))
                .build()

            gradleRunner.runTasks(VERIFY_TASK_NAME)
        }

        @Test
        fun `with default compiler options`() {
            val gradleRunner = DslTestBuilder.kotlin()
                .withDetektConfig(verifyParityTask)
                .build()

            gradleRunner.runTasks(VERIFY_TASK_NAME)
        }

        @Test
        fun `with compiler options configured on the compilation task`() {
            @Language("gradle.kts")
            val compileTaskConfig = """
                tasks.named(
                    "compileKotlin",
                    org.jetbrains.kotlin.gradle.tasks.KotlinCompile::class.java,
                ) {
                    compilerOptions.progressiveMode = true
                    compilerOptions.optIn.add("kotlin.RequiresOptIn")
                }
            """.trimIndent()

            val gradleRunner = DslTestBuilder.kotlin()
                .withDetektConfig(joinGradleBlocks(compileTaskConfig, verifyParityTask))
                .build()

            gradleRunner.runTasks(VERIFY_TASK_NAME)
        }
    }

    @Test
    fun `renders the compiler options of the compilation task as detekt CLI arguments`() {
        @Language("gradle.kts")
        val kotlinConfig = """
            kotlin {
                compilerOptions {
                    apiVersion = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_0
                    languageVersion = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_1
                    progressiveMode = true
                    optIn.add("kotlin.RequiresOptIn")
                }
            }
        """.trimIndent()

        val gradleRunner = DslTestBuilder.kotlin()
            .dryRun()
            .withDetektConfig(kotlinConfig)
            .build()

        gradleRunner.runTasksAndCheckResult("detektMain") { result ->
            assertThat(result.output).contains("--api-version 2.0")
            assertThat(result.output).contains("--language-version 2.1")
            assertThat(result.output).contains("-progressive")
            assertThat(result.output).contains("-opt-in kotlin.RequiresOptIn")
        }
    }

    @Test
    fun `prints the compiler configuration when running with debug enabled`() {
        @Language("gradle.kts")
        val config = """
            detekt {
                debug = true
                ignoreFailures = true
            }

            kotlin {
                compilerOptions {
                    apiVersion = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_0
                    languageVersion = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_1
                }
            }
        """.trimIndent()

        val gradleRunner = DslTestBuilder.kotlin()
            .withDetektConfig(config)
            .build()

        gradleRunner.runTasksAndCheckResult("detektMain") { result ->
            assertThat(result.output).contains("Compiler configuration:")
            assertThat(result.output).contains("LANGUAGE_VERSION_SETTINGS:")
            assertThat(result.output).contains("Language = 2.1")
            assertThat(result.output).contains("API = 2.0")
        }
    }

    companion object {
        private const val VERIFY_TASK_NAME = "verifyDetektConfigParity"

        /**
         * Registers a task comparing the compiler configuration of the `detektMain` task with the
         * `compileKotlin` task it is derived from. Only the task properties are captured at
         * configuration time so that the verification stays compatible with the configuration cache.
         */
        @Language("gradle.kts")
        private val verifyParityTask = """
            tasks.register("$VERIFY_TASK_NAME") {
                // All state is evaluated into plain values at configuration time so that the doLast
                // action captures neither the build script object nor Kotlin Gradle plugin types,
                // which cannot be serialized by the configuration cache. Both tasks are fully
                // configured at this point as the values are read when the task graph is built.
                val compileTask = project.tasks
                    .named("compileKotlin", org.jetbrains.kotlin.gradle.tasks.KotlinCompile::class.java)
                    .get()
                val compileOptions = compileTask.compilerOptions
                val companionDetektTask = project.tasks
                    .named("detektMain", dev.detekt.gradle.Detekt::class.java)
                    .get()
                val expected = mapOf(
                    "apiVersion" to compileOptions.apiVersion.orNull?.version,
                    "languageVersion" to compileOptions.languageVersion.orNull?.version,
                    "jvmTarget" to compileOptions.jvmTarget.orNull?.target,
                    "progressiveMode" to compileOptions.progressiveMode.getOrElse(false).toString(),
                    "optIn" to compileOptions.optIn.getOrElse(emptyList()).toString(),
                    "freeCompilerArgs" to compileOptions.freeCompilerArgs.getOrElse(emptyList()).toString(),
                )
                val actual = mapOf(
                    "apiVersion" to companionDetektTask.apiVersion.orNull,
                    "languageVersion" to companionDetektTask.languageVersion.orNull,
                    "jvmTarget" to companionDetektTask.jvmTarget.orNull,
                    "progressiveMode" to companionDetektTask.progressiveMode.getOrElse(false).toString(),
                    "optIn" to companionDetektTask.optIn.getOrElse(emptyList()).toString(),
                    "freeCompilerArgs" to companionDetektTask.freeCompilerArgs.getOrElse(emptyList()).toString(),
                )
                val compileLibraryFiles = compileTask.libraries.files.map { it.absolutePath }
                val detektClasspathFiles = companionDetektTask.classpath.files.map { it.absolutePath }

                doLast {
                    val mismatches = expected.filter { (key, value) -> actual.getValue(key) != value }
                    check(mismatches.isEmpty()) {
                        "Compiler configuration mismatch between compileKotlin and detektMain:\n" +
                            mismatches.entries.joinToString("\n") { (key, value) ->
                                "  " + key + ": compile=" + value + " detekt=" + actual.getValue(key)
                            }
                    }
                    val missingLibraries = compileLibraryFiles - detektClasspathFiles
                    check(missingLibraries.isEmpty()) {
                        "detektMain classpath misses libraries of compileKotlin: " + missingLibraries
                    }
                }
            }
        """.trimIndent()
    }
}
