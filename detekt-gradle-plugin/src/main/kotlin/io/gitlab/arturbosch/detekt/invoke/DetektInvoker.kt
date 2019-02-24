package io.gitlab.arturbosch.detekt.invoke

import io.gitlab.arturbosch.detekt.CONFIGURATION_DETEKT
import io.gitlab.arturbosch.detekt.CONFIGURATION_DETEKT_PLUGINS
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.FileCollection

/**
 * @author Marvin Ramin
 */
object DetektInvoker {
    internal fun invokeCli(
        project: Project,
        arguments: List<CliArgument>,
        debug: Boolean = false,
        ignoreFailures: Boolean = false
    ) {
        val cliArguments = arguments.flatMap(CliArgument::toArgument)

        if (debug) println(cliArguments)
        val javaExec = project.javaexec {
            it.main = DETEKT_MAIN
            it.classpath = getConfigurations(project, debug)
            it.args = cliArguments
            // Ignore non-zero exit values as these will be handled by the task
            it.isIgnoreExitValue = true
        }

        if (javaExec.exitValue == 2 && !ignoreFailures) {
            throw GradleException("detekt found some errors")
        } else if (javaExec.exitValue == 2 && ignoreFailures) {
            throw GradleException("There were failures but we don't care")
        } else if (javaExec.exitValue == 1) {
            throw GradleException("There was an error")
        } else {
            throw GradleException("dunno what happened")
        }
    }

    private fun getConfigurations(project: Project, debug: Boolean = false): FileCollection {
        val detektConfigurations = setOf(CONFIGURATION_DETEKT_PLUGINS, CONFIGURATION_DETEKT)
        val configurations = project.configurations.filter { detektConfigurations.contains(it.name) }

        val files = project.files(configurations)
        if (debug) files.forEach { println(it) }
        return files
    }
}

private const val DETEKT_MAIN = "io.gitlab.arturbosch.detekt.cli.Main"
