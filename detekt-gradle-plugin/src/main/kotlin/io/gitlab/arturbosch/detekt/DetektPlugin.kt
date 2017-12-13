package io.gitlab.arturbosch.detekt

import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.util.PatternSet


class DetektPlugin : Plugin<Project> {

	override fun apply(project: Project) {
		project.extensions.create("detekt", DetektExtension::class.java)
		project.task(mapOf("type" to DetektCheckTask::class.java), "detektCheck")
		project.task(mapOf("type" to DetektIdeaFormatTask::class.java), "detektIdeaFormat")
		project.task(mapOf("type" to DetektIdeaInspectionTask::class.java), "detektIdeaInspect")
		project.task(mapOf("type" to DetektGenerateConfigTask::class.java), "detektGenerateConfig")
		project.task(mapOf("type" to DetektCreateBaselineTask::class.java), "detektBaseline")
		project.rootProject.buildscript.configurations.maybeCreate("detekt")
		configureDefaultDependencies(project)
		project.plugins.withType(JavaBasePlugin::class.java) {
			val javaConvention: JavaPluginConvention = project.convention.getPlugin(JavaPluginConvention::class.java)

			// create a detektCheck task for every sourceset
			configureForSourceSets(project, javaConvention.sourceSets)
		}
	}

	private fun configureForSourceSets(project: Project, sourceSets: SourceSetContainer) {
		sourceSets.all { sourceSet ->
			val task = project.tasks.create(sourceSet.getTaskName("detekt", null), DetektCheckTask::class.java)
			configureForSourceSet(sourceSet, task)
		}
	}

	private fun configureForSourceSet(sourceSet: SourceSet, task: DetektCheckTask) {
		task.description = "Run detekt analysis for " + sourceSet.name + " classes"
		task.classpath.setFrom(sourceSet.compileClasspath)
		task.source(sourceSet.allSource.matching(PatternSet().include(listOf("**/*.kt", "**/*.kts"))))
	}

	private fun configureDefaultDependencies(project: Project) {
		val configuration = project.rootProject.buildscript.configurations.getAt("detekt")
		configuration.defaultDependencies({ dependencies -> dependencies.add(project.buildscript.dependencies.create
		("io.gitlab.arturbosch.detekt:detekt-cli:1.0.0.RC6-1")) })
	}

}
