package dev.detekt.gradle

import dev.detekt.gradle.extensions.DetektExtension
import dev.detekt.gradle.extensions.DetektReportType
import dev.detekt.gradle.extensions.FailOnSeverity
import dev.detekt.gradle.plugin.DetektPlugin
import dev.detekt.gradle.testkit.DslGradleRunner
import dev.detekt.gradle.testkit.ProjectLayout
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.repositories
import org.gradle.kotlin.dsl.the
import org.jetbrains.kotlin.gradle.plugin.KotlinPluginWrapper
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * Verifies that plugin/extension configuration translates into the correct detekt CLI arguments.
 *
 * These tests build an in-memory project with Gradle's [org.gradle.testfixtures.ProjectBuilder] (via
 * [DslGradleRunner.buildProject]) and inspect [Detekt.arguments] directly, rather than executing the task with the
 * Gradle TestKit "dry run". This is significantly faster and contributes to code coverage. See detekt/detekt#3778.
 */
class DetektArgumentsSpec {

    private fun runner(
        projectLayout: ProjectLayout = ProjectLayout(numberOfSourceFilesInRootPerSourceDir = 1),
        baselineFiles: List<String> = emptyList(),
        projectConfig: Project.() -> Unit = {},
    ): DslGradleRunner =
        DslGradleRunner(
            projectLayout = projectLayout,
            buildFileName = "build.gradle.kts",
            baselineFiles = baselineFiles,
            projectScript = {
                apply<KotlinPluginWrapper>()
                apply<DetektPlugin>()
                repositories {
                    mavenCentral()
                }
                projectConfig()
            },
        ).also { it.setupProject() }

    private fun DslGradleRunner.detektArguments(taskPath: String = "detekt"): String {
        val project = buildProject()
        val detektTask = project.tasks.getByPath(taskPath) as Detekt
        return detektTask.arguments.joinToString(" ")
    }

    private fun Project.detektExtension(): DetektExtension = the<DetektExtension>()

    private fun Project.regularFile(path: String) = layout.projectDirectory.file(path)

    private fun Project.configureDetektTask(configure: (Detekt) -> Unit) {
        tasks.named("detekt", Detekt::class.java).configure { configure(it) }
    }

    @Nested
    inner class `without detekt config` {
        private val gradleRunner = runner()

        @Test
        fun `enables checkstyle report to default location`() {
            assertThat(gradleRunner.detektArguments())
                .containsPattern("""--report checkstyle:\S*[/\\]build[/\\]reports[/\\]detekt[/\\]detekt.xml""")
        }

        @Test
        fun `enables html report to default location`() {
            assertThat(gradleRunner.detektArguments())
                .containsPattern("""--report html:\S*[/\\]build[/\\]reports[/\\]detekt[/\\]detekt.html""")
        }

        @Test
        fun `enables sarif report to default location`() {
            assertThat(gradleRunner.detektArguments())
                .containsPattern("""--report sarif:\S*[/\\]build[/\\]reports[/\\]detekt[/\\]detekt.sarif""")
        }

        @Test
        fun `sets as input all the kotlin files in src-main and src-test`() {
            val arguments = gradleRunner.detektArguments()
            assertThat(arguments).containsPattern("""--input \S*[/\\]src[/\\]main[/\\]java[/\\]My0Root0Class.kt""")
            assertThat(arguments).containsPattern("""[/\\]src[/\\]test[/\\]java[/\\]My1Root0Class.kt""")
            assertThat(arguments).containsPattern("""[/\\]src[/\\]main[/\\]kotlin[/\\]My2Root0Class.kt""")
            assertThat(arguments).containsPattern("""[/\\]src[/\\]test[/\\]kotlin[/\\]My3Root0Class.kt""")
        }
    }

    @Nested
    inner class `with multiple detekt configs` {
        private val gradleRunner = runner {
            detektExtension().config.setFrom("firstConfig.yml", "secondConfig.yml")
        }

        @Test
        fun `passes absolute filename of both config files to detekt cli`() {
            val arguments = gradleRunner.detektArguments()
            assertThat(arguments).contains("--config ")
            assertThat(arguments).containsPattern("""[/\\]firstConfig.yml""")
            assertThat(arguments).containsPattern("""[/\\]secondConfig.yml""")
        }
    }

    @Nested
    inner class `with custom baseline file` {
        private val baselineFilename = "custom-baseline.xml"
        private val gradleRunner = runner(baselineFiles = listOf(baselineFilename)) {
            detektExtension().baseline.set(layout.projectDirectory.file(baselineFilename))
        }

        @Test
        fun `sets baseline parameter with absolute filename`() {
            assertThat(gradleRunner.detektArguments())
                .containsPattern("""--baseline \S*[/\\]custom-baseline.xml""")
        }
    }

    @Nested
    inner class `with custom baseline file that doesn't exist` {
        private val gradleRunner = runner {
            detektExtension().baseline.set(layout.projectDirectory.file("detekt-baseline-no-exist.xml"))
        }

        @Test
        fun `doesn't set the baseline parameter`() {
            assertThat(gradleRunner.detektArguments()).doesNotContain("--baseline")
        }
    }

    @Nested
    inner class `with custom input directories` {
        private val customSrc1 = "gensrc/kotlin"
        private val customSrc2 = "src/main/kotlin"
        private val gradleRunner = runner(
            projectLayout = ProjectLayout(1, srcDirs = listOf(customSrc1, customSrc2)),
        ) {
            detektExtension().source.setFrom(customSrc1, customSrc2, "folder_that_does_not_exist")
        }

        @Test
        fun `sets input parameter to absolute filenames of all source files`() {
            val arguments = gradleRunner.detektArguments()
            assertThat(arguments)
                .containsPattern("""--input \S*[/\\]gensrc[/\\]kotlin[/\\]My0Root0Class.kt""")
            assertThat(arguments)
                .containsPattern("""[/\\]src[/\\]main[/\\]kotlin[/\\]My1Root0Class.kt""")
        }

        @Test
        fun `ignores input directories that do not exist`() {
            assertThat(gradleRunner.detektArguments()).doesNotContain("folder_that_does_not_exist")
        }
    }

    @Nested
    inner class `with custom reports dir` {
        private val gradleRunner = runner {
            detektExtension().reportsDir.set(layout.projectDirectory.dir("build/detekt-reports"))
        }

        @Test
        fun `configures checkstyle report to custom directory`() {
            assertThat(gradleRunner.detektArguments())
                .containsPattern("""--report checkstyle:\S*[/\\]build[/\\]detekt-reports[/\\]detekt.xml""")
        }

        @Test
        fun `configures html report to custom directory`() {
            assertThat(gradleRunner.detektArguments())
                .containsPattern("""--report html:\S*[/\\]build[/\\]detekt-reports[/\\]detekt.html""")
        }

        @Test
        fun `configures sarif report to custom directory`() {
            assertThat(gradleRunner.detektArguments())
                .containsPattern("""--report sarif:\S*[/\\]build[/\\]detekt-reports[/\\]detekt.sarif""")
        }
    }

    @Nested
    inner class `with custom reports dir and custom report filename` {
        private val gradleRunner = runner {
            detektExtension().reportsDir.set(layout.projectDirectory.dir("build/detekt-reports"))
            configureDetektTask { task ->
                task.reports { reports ->
                    reports.checkstyle.outputLocation.set(regularFile("build/xml-reports/custom-detekt.xml"))
                }
            }
        }

        @Test
        fun `configures checkstyle report to specific absolute filename`() {
            assertThat(gradleRunner.detektArguments())
                .containsPattern("""--report checkstyle:\S*[/\\]build[/\\]xml-reports[/\\]custom-detekt.xml""")
        }

        @Test
        fun `configures html report to default name in custom directory`() {
            assertThat(gradleRunner.detektArguments())
                .containsPattern("""--report html:\S*[/\\]build[/\\]detekt-reports[/\\]detekt.html""")
        }
    }

    @Nested
    inner class `with disabled reports` {
        private val gradleRunner = runner {
            configureDetektTask { task ->
                task.reports { reports ->
                    reports.checkstyle.required.set(false)
                    reports.html.required.set(false)
                    reports.sarif.required.set(false)
                    reports.markdown.required.set(false)
                }
            }
        }

        @Test
        fun `no report param is set`() {
            assertThat(gradleRunner.detektArguments()).doesNotContain("--report")
        }
    }

    @Nested
    inner class `with custom report types` {
        @Nested
        inner class `configured correctly` {
            private val gradleRunner = runner {
                configureDetektTask { task ->
                    task.reports { reports ->
                        reports.custom { report ->
                            report.reportId = "customXml"
                            report.outputLocation.set(layout.projectDirectory.file("build/reports/custom.xml"))
                        }
                        reports.custom { report ->
                            report.reportId = "customJson"
                            report.outputLocation.set(layout.projectDirectory.file("build/reports/custom.json"))
                        }
                    }
                }
            }

            @Test
            fun `configures custom xml report to absolute filename`() {
                assertThat(gradleRunner.detektArguments())
                    .containsPattern("""--report customXml:\S*[/\\]build[/\\]reports[/\\]custom.xml""")
            }

            @Test
            fun `configures custom json report to absolute filename`() {
                assertThat(gradleRunner.detektArguments())
                    .containsPattern("""--report customJson:\S*[/\\]build[/\\]reports[/\\]custom.json""")
            }
        }

        @Nested
        inner class `report id is missing` {
            private val gradleRunner = runner {
                configureDetektTask { task ->
                    task.reports { reports ->
                        reports.custom { report ->
                            report.outputLocation.set(layout.projectDirectory.file("build/reports/custom.xml"))
                        }
                    }
                }
            }

            @Test
            fun `fails to build arguments`() {
                assertThatThrownBy { gradleRunner.detektArguments() }
                    .hasMessageContaining("If a custom report is specified, the reportId must be present")
            }
        }

        @Nested
        inner class `report filename is missing` {
            private val gradleRunner = runner {
                configureDetektTask { task ->
                    task.reports { reports ->
                        reports.custom { report ->
                            report.reportId = "customJson"
                        }
                    }
                }
            }

            @Test
            fun `fails to build arguments`() {
                assertThatThrownBy { gradleRunner.detektArguments() }
                    .hasMessageContaining("If a custom report is specified, the destination must be present")
            }
        }

        @Nested
        inner class `report filename is a directory` {
            private val gradleRunner = runner {
                configureDetektTask { task ->
                    task.reports { reports ->
                        reports.custom { report ->
                            report.reportId = "foo"
                            report.outputLocation.set(layout.projectDirectory.file("src"))
                        }
                    }
                }
            }

            @Test
            fun `fails to build arguments`() {
                assertThatThrownBy { gradleRunner.detektArguments() }
                    .hasMessageContaining("If a custom report is specified, the destination must be not a directory")
            }
        }

        @Nested
        inner class `using the report id of a well known type` {
            @ParameterizedTest
            @EnumSource(DetektReportType::class)
            fun `fails to build arguments`(wellKnownType: DetektReportType) {
                val gradleRunner = runner {
                    configureDetektTask { task ->
                        task.reports { reports ->
                            reports.custom { report ->
                                report.reportId = wellKnownType.reportId
                                report.outputLocation.set(layout.projectDirectory.file("build/reports/custom.xml"))
                            }
                        }
                    }
                }

                assertThatThrownBy { gradleRunner.detektArguments() }
                    .hasMessageContaining("The custom report reportId may not be same as one of the default reports")
            }
        }
    }

    @Nested
    inner class `with flags` {
        private val gradleRunner = runner {
            with(detektExtension()) {
                debug.set(true)
                parallel.set(true)
                disableDefaultRuleSets.set(true)
                allRules.set(true)
                autoCorrect.set(true)
                buildUponDefaultConfig.set(true)
                ignoreFailures.set(true)
            }
        }

        @Test
        fun `enables debug mode`() {
            assertThat(gradleRunner.detektArguments()).contains("--debug")
        }

        @Test
        fun `enables parallel processing`() {
            assertThat(gradleRunner.detektArguments()).contains("--parallel")
        }

        @Test
        fun `disables default ruleset`() {
            assertThat(gradleRunner.detektArguments()).contains("--disable-default-rulesets")
        }

        @Test
        fun `enables all rules`() {
            assertThat(gradleRunner.detektArguments()).contains("--all-rules")
        }

        @Test
        fun `enables auto correcting`() {
            assertThat(gradleRunner.detektArguments()).contains("--auto-correct")
        }

        @Test
        fun `enables using default config as baseline`() {
            assertThat(gradleRunner.detektArguments()).contains("--build-upon-default-config")
        }

        @Test
        fun `ignores failures by never failing on any severity`() {
            assertThat(gradleRunner.detektArguments()).contains("--fail-on-severity never")
        }
    }

    @Nested
    inner class FailureSeverity {
        @Test
        fun `is set to error by default`() {
            assertThat(runner().detektArguments()).contains("--fail-on-severity error")
        }

        @Test
        fun `can be configured`() {
            val gradleRunner = runner {
                detektExtension().failOnSeverity.set(FailOnSeverity.Never)
            }
            assertThat(gradleRunner.detektArguments()).contains("--fail-on-severity never")
        }
    }

    @Nested
    inner class `and creating a custom task` {
        private val gradleRunner = runner {
            tasks.register("myDetekt", Detekt::class.java) { task ->
                task.description = "Runs a custom detekt build."
                task.setSource(files(projectDir))
                task.setIncludes(listOf("**/*.kt", "**/*.kts"))
                task.setExcludes(listOf("build/"))
                task.config.setFrom("config.yml")
                task.debug.set(true)
                task.parallel.set(true)
                task.disableDefaultRuleSets.set(true)
                task.buildUponDefaultConfig.set(true)
                task.allRules.set(false)
                task.ignoreFailures.set(false)
                task.failOnSeverity.set(FailOnSeverity.Error)
                task.autoCorrect.set(false)
                task.reports { reports ->
                    reports.checkstyle { report ->
                        report.required.set(true)
                        report.outputLocation.set(layout.projectDirectory.file("build/reports/mydetekt.xml"))
                    }
                    reports.html.outputLocation.set(layout.projectDirectory.file("build/reports/mydetekt.html"))
                    reports.sarif { report ->
                        report.required.set(true)
                        report.outputLocation.set(layout.projectDirectory.file("build/reports/mydetekt.sarif"))
                    }
                }
                task.basePath.set(projectDir.toString())
            }
        }.also { it.writeProjectFile("config.yml", "") }

        @Test
        fun `enables checkstyle report to specified location`() {
            assertThat(gradleRunner.detektArguments("myDetekt"))
                .containsPattern("""--report checkstyle:\S*[/\\]build[/\\]reports[/\\]mydetekt.xml""")
        }

        @Test
        fun `enables html report to specified location`() {
            assertThat(gradleRunner.detektArguments("myDetekt"))
                .containsPattern("""--report html:\S*[/\\]build[/\\]reports[/\\]mydetekt.html""")
        }

        @Test
        fun `enables sarif report to specified location`() {
            assertThat(gradleRunner.detektArguments("myDetekt"))
                .containsPattern("""--report sarif:\S*[/\\]build[/\\]reports[/\\]mydetekt.sarif""")
        }

        @Test
        fun `sets base path`() {
            assertThat(gradleRunner.detektArguments("myDetekt")).contains("--base-path")
        }

        @Test
        fun `sets absolute filename of config file to detekt cli`() {
            val arguments = gradleRunner.detektArguments("myDetekt")
            assertThat(arguments).contains("--config ")
            assertThat(arguments).containsPattern("""[/\\]config.yml""")
        }

        @Test
        fun `enables debug mode`() {
            assertThat(gradleRunner.detektArguments("myDetekt")).contains("--debug")
        }

        @Test
        fun `enables parallel processing`() {
            assertThat(gradleRunner.detektArguments("myDetekt")).contains("--parallel")
        }

        @Test
        fun `disables the default ruleset`() {
            assertThat(gradleRunner.detektArguments("myDetekt")).contains("--disable-default-rulesets")
        }
    }
}
