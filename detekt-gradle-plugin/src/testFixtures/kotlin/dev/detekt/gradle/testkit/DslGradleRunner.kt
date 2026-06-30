package dev.detekt.gradle.testkit

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.intellij.lang.annotations.Language
import java.io.File
import java.nio.file.Files
import java.util.UUID

@Suppress("TooManyFunctions", "ClassOrdering")
class DslGradleRunner
@Suppress("LongParameterList")
constructor(
    val projectLayout: ProjectLayout,
    val buildFileName: String,
    @Language("gradle.kts")
    val mainBuildFileContent: String = "",
    val settingsContent: String = "",
    val configFileOrNone: String? = null,
    val baselineFiles: List<String> = emptyList(),
    val gradleVersionOrNone: String? = null,
    val dryRun: Boolean = false,
    var disableIP: Boolean = false,
    var failOnGradleWarnings: Boolean = true,
    val jvmArgs: String = "-Xmx2g -XX:MaxMetaspaceSize=1g",
    val gradleProperties: Map<String, String> = emptyMap(),
    val customPluginClasspath: List<File> = emptyList(),
    val projectScript: Project.() -> Unit = {},
) {

    private val rootDir: File = Files.createTempDirectory("applyPlugin").toFile().apply { deleteOnExit() }
    private val randomString = UUID.randomUUID().toString()

    @Language("xml")
    private val baselineContent = """
        <some>
            <xml/>
        </some>
    """.trimIndent()

    @Language("yaml")
    private val configFileContent = """
        style:
          MagicNumber:
            active: true
            ignorePropertyDeclaration: false
    """.trimIndent()

    /**
     * Each generated file is different so the artifacts are not cached in between test runs
     */
    @Language("kotlin")
    private fun ktFileContent(className: String, withFinding: Boolean = false): String =
        """
            internal class $className(
                val randomDefaultValue: String = "$randomString"
            ) {
                val smellyConstant: Int = ${if (withFinding) "11" else "0"}
            }
            
        """.trimIndent() // Last line empty to prevent NewLineAtEndOfFile.

    fun setupProject() {
        writeProjectFile(buildFileName, mainBuildFileContent)
        val finalSettingsContent =
            """
                $settingsContent
                rootProject.name = "rootDir-project"
                include(${projectLayout.submodules.joinToString { "\"${it.name}\"" }})
            """.trimIndent()
        writeProjectFile(SETTINGS_FILENAME, finalSettingsContent)
        configFileOrNone?.let { writeProjectFile(it, configFileContent) }
        baselineFiles.forEach { file -> writeProjectFile(file, baselineContent) }
        projectLayout.srcDirs.forEachIndexed { srcDirIdx, sourceDir ->
            repeat(projectLayout.numberOfSourceFilesInRootPerSourceDir) { srcFileIndex ->
                val withFinding =
                    srcDirIdx * projectLayout.numberOfSourceFilesInRootPerSourceDir +
                        srcFileIndex < projectLayout.numberOfFindingsInRootPerSourceDir
                writeKtFile(
                    dir = File(rootDir, sourceDir),
                    className = "My${srcDirIdx}Root${srcFileIndex}Class",
                    withFinding = withFinding
                )
            }
        }

        projectLayout.submodules.forEach { submodule ->
            submodule.writeModuleFile(buildFileName, submodule.buildFileContent.orEmpty())
            submodule.baselineFiles.forEach { file -> submodule.writeModuleFile(file, baselineContent) }
            submodule.srcDirs.forEachIndexed { srcDirIdx, moduleSourceDir ->
                repeat(submodule.numberOfSourceFilesPerSourceDir) {
                    val withFinding =
                        srcDirIdx * submodule.numberOfSourceFilesPerSourceDir + it < submodule.numberOfFindings
                    writeKtFile(
                        dir = File(submodule.moduleRoot, moduleSourceDir),
                        className = "My$srcDirIdx${submodule.name}${it}Class",
                        withFinding = withFinding
                    )
                }
            }
        }
    }

    fun projectFile(path: String): File = File(rootDir, path).canonicalFile

    fun writeProjectFile(filename: String, content: String) {
        File(rootDir, filename)
            .also { it.parentFile.mkdirs() }
            .writeText(content)
    }

    fun writeKtFile(srcDir: String, className: String) {
        writeKtFile(File(rootDir, srcDir), className)
    }

    private fun writeKtFile(dir: File, className: String, withFinding: Boolean = false) {
        dir.mkdirs()
        File(dir, "$className.kt").writeText(ktFileContent(className, withFinding))
    }

    private fun Submodule.writeModuleFile(filename: String, content: String) {
        File(moduleRoot, filename).writeText(content)
    }

    private val Submodule.moduleRoot: File
        get() = File(rootDir, name).apply { mkdirs() }

    fun buildProject(): Project =
        ProjectBuilder.builder()
            .withProjectDir(rootDir)
            .build()
            .apply(projectScript)

    private fun buildGradleRunner(tasks: List<String>): GradleRunner {
        val args = buildList {
            add("--stacktrace")
            add("--info")
            add("--build-cache")
            add("-Dorg.gradle.jvmargs=${listOfNotNull(jvmArgs, jacocoAgentArg()).joinToString(" ")}")
            if (dryRun) {
                add("-Pdetekt-dry-run=true")
            }
            if (!disableIP) {
                add("-Dorg.gradle.unsafe.isolated-projects=true")
            }
            if (failOnGradleWarnings) {
                add("--warning-mode=fail")
            }
            addAll(gradleProperties.toList().map { (key, value) -> "-P$key=$value" })
            addAll(tasks)
        }

        return GradleRunner.create().apply {
            withProjectDir(rootDir)
            if (customPluginClasspath.isNotEmpty()) {
                withPluginClasspath(customPluginClasspath)
            } else {
                withPluginClasspath()
            }
            withArguments(args)
            gradleVersionOrNone?.let(::withGradleVersion)
        }
    }

    /**
     * When the functional test task injects the JaCoCo agent (via the `jacoco.agent.jar` and
     * `jacoco.testkit.destdir` system properties), return a `-javaagent` argument so the
     * TestKit-spawned Gradle JVMs are instrumented. JaCoCo does not instrument these spawned JVMs
     * out of the box, so without this functional tests contribute no coverage of the plugin.
     *
     * The exec file is named after [jacocoJvmId] (stable per test JVM), so every build in a fork
     * appends to the same file. Keeping the name constant across a fork's tests keeps
     * `-Dorg.gradle.jvmargs` constant too, so TestKit reuses its Gradle daemon rather than spawning a
     * fresh one per test. The report task merges the per-fork files. `append=true` accumulates coverage
     * across a fork's sequentially-executed builds; distinct files per fork avoid the data corruption
     * that concurrent writes to one `.exec` would cause under parallel test forks.
     */
    private fun jacocoAgentArg(): String? {
        val agentJar = System.getProperty("jacoco.agent.jar")
        val destDir = System.getProperty("jacoco.testkit.destdir")
        if (agentJar == null || destDir == null) return null
        val destFile = File(destDir, "testkit-$jacocoJvmId.exec").absolutePath
        return "-javaagent:$agentJar=destfile=$destFile,append=true,output=file"
    }

    fun runTasksAndCheckResult(vararg tasks: String, doAssert: DslGradleRunner.(BuildResult) -> Unit) {
        this.doAssert(runTasks(*tasks))
    }

    fun runTasks(vararg tasks: String): BuildResult = buildGradleRunner(tasks.toList()).build()

    fun runTasksAndExpectFailure(vararg tasks: String, doAssert: DslGradleRunner.(BuildResult) -> Unit) {
        val result: BuildResult = buildGradleRunner(tasks.toList()).buildAndFail()
        this.doAssert(result)
    }

    fun runDetektTaskAndCheckResult(doAssert: DslGradleRunner.(BuildResult) -> Unit) {
        runTasksAndCheckResult(DETEKT_TASK) { this.doAssert(it) }
    }

    fun runDetektTask(vararg args: String): BuildResult = runTasks(DETEKT_TASK, *args)

    fun runDetektTaskAndExpectFailure(doAssert: DslGradleRunner.(BuildResult) -> Unit = {}) {
        val result = buildGradleRunner(listOf(DETEKT_TASK)).buildAndFail()
        this.doAssert(result)
    }

    companion object {
        private const val SETTINGS_FILENAME = "settings.gradle"
        private const val DETEKT_TASK = "detekt"

        private val jacocoJvmId: String = UUID.randomUUID().toString()
    }
}
