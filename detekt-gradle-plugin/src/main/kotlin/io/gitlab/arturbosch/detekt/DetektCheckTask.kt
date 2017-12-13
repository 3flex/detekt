package io.gitlab.arturbosch.detekt

import io.gitlab.arturbosch.detekt.extensions.CLASSPATH_PARAMETER
import io.gitlab.arturbosch.detekt.extensions.CONFIG_RESOURCE_PARAMETER
import io.gitlab.arturbosch.detekt.extensions.DEFAULT_DETEKT_CONFIG_RESOURCE
import io.gitlab.arturbosch.detekt.extensions.INPUT_PARAMETER
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.SourceTask
import org.gradle.api.tasks.TaskAction

/**
 * @author Artur Bosch
 */
open class DetektCheckTask : SourceTask() {
	val classpath: ConfigurableFileCollection = project.files()

	init {
		description = "Analyze your kotlin code with detekt."
		group = "verification"
	}

	@TaskAction
	fun check() {
		val configuration = project.rootProject.buildscript.configurations.getAt("detekt")

		project.javaexec {
			it.main = "io.gitlab.arturbosch.detekt.cli.Main"
			it.classpath = configuration
			it.args(CONFIG_RESOURCE_PARAMETER, DEFAULT_DETEKT_CONFIG_RESOURCE,
					INPUT_PARAMETER, getSource().asPath, CLASSPATH_PARAMETER, classpath.asPath)
//			it.args(detektExtension.profileArgumentsOrDefault(project))
		}
	}
}
