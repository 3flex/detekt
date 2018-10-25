package io.gitlab.arturbosch.detekt

import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.HasConvention
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.getPlugin
import org.gradle.kotlin.dsl.withConvention
import org.gradle.kotlin.dsl.withType
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.jetbrains.kotlin.gradle.dsl.KotlinCommonCompile
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompile
import org.jetbrains.kotlin.gradle.plugin.KOTLIN_DSL_NAME
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.tasks.AbstractKotlinCompile
import org.jetbrains.kotlin.gradle.tasks.Kotlin2JsCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.File
import java.lang.IllegalStateException

//import org.jetbrains.kotlin.gradle.plugin.sources.DefaultKotlinSourceSet
//import java.util.UUID

/**
 * @author Marvin Ramin
 */
class DetektPlugin : Plugin<Project> {

	override fun apply(project: Project) {
		val extension = project.extensions.create(DETEKT, DetektExtension::class.java, project)

		configurePluginDependencies(project, extension)

		createDetektTasks(project, extension)
		createAndConfigureCreateBaselineTask(project, extension)
		createAndConfigureGenerateConfigTask(project, extension)

		createAndConfigureIdeaTasks(project, extension)
	}

	private fun createDetektTasks(project: Project, extension: DetektExtension) {
		// this one works for some definitions of "works"
//		project.tasks.withType(KotlinCompile::class) {
//			createAndConfigureDetektTask(project, extension, "$DETEKT${name}", "chenton${description}")
//		}

//		project.afterEvaluate {
//			project.tasks.withType(KotlinCompile::class) {
//				if (classpath != null) {
//					createAndConfigureDetektTask(project, extension, "detekt${name.capitalize()}", "chenton", source, classpath)
//				}
//			}
//		}

		// works for Java
		val javaSourceSets = project.extensions.findByType(SourceSetContainer::class.java)
		javaSourceSets?.map { sourceSet ->
			val kotlinSourceSet = sourceSet.getConvention(KOTLIN_DSL_NAME) as KotlinSourceSet
			createAndConfigureDetektTask(project, extension, "chenton${kotlinSourceSet.kotlin.name}", "chenton", kotlinSourceSet.kotlin)
		}

//			project.sourceSets?.map { sourceSet ->
//				val name = "$DETEKT${sourceSet.name.capitalize()}"
//				val description = "Runs detekt on the ${sourceSet.name} source set."
//				createAndConfigureDetektTask(project, extension, name, description, sourceSet.allSource, sourceSet.compileClasspath)
//			}
//		}
		project.afterEvaluate {
			if (project.plugins.hasPlugin("kotlin-multiplatform")) {
//			project.extensions.getByName("kotlin")
				project.withConvention(KotlinSourceSet::class) { kotlin }
				throw IllegalStateException()
			}
		}

		val detektTask = createAndConfigureDetektTask(project, extension, DETEKT, "Runs the default detekt task.", determineInput(extension))
		project.tasks.findByName(LifecycleBasePlugin.CHECK_TASK_NAME)?.dependsOn(detektTask)
	}

	private fun createAndConfigureDetektTask(project: Project,
											 extension: DetektExtension,
											 name: String,
											 taskDescription: String,
											 inputSources: FileCollection,
											 compileClasspath: FileCollection = project.files()): TaskProvider<Detekt> {
		return project.tasks.register(name, Detekt::class.java) {
			description = taskDescription
			debug = extension.debug
			parallel = extension.parallel
			disableDefaultRuleSets = extension.disableDefaultRuleSets
			filters = extension.filters
			config = extension.config
			baseline = extension.baseline
			plugins = extension.plugins
			input = inputSources.asFileTree
			classpath = compileClasspath
			extension.reports.forEach { extReport ->
				reports.withName(extReport.name) {
					enabled = extReport.enabled
					val fileSuffix = extReport.name
					@Suppress("USELESS_ELVIS")
					val reportsDir = extension.reportsDir ?: extension.defaultReportsDir
					val customDestination = extReport.destination
					destination = customDestination ?: File(reportsDir, "$name.$fileSuffix")
				}
			}
		}
	}

	private fun createAndConfigureCreateBaselineTask(project: Project, extension: DetektExtension) =
			project.tasks.register(BASELINE, DetektCreateBaselineTask::class.java) {
				baseline = extension.baseline
				debugOrDefault = extension.debug
				parallelOrDefault = extension.parallel
				disableDefaultRuleSetsOrDefault = extension.disableDefaultRuleSets
				filters = extension.filters
				config = extension.config
				plugins = extension.plugins
				input = determineInput(extension)
			}

	private fun createAndConfigureGenerateConfigTask(project: Project, extension: DetektExtension) =
			project.tasks.register(GENERATE_CONFIG, DetektGenerateConfigTask::class.java) {
				input = determineInput(extension)
			}

	private fun createAndConfigureIdeaTasks(project: Project, extension: DetektExtension) {
		project.tasks.register(IDEA_FORMAT, DetektIdeaFormatTask::class.java) {
			debugOrDefault = extension.debug
			input = determineInput(extension)
			ideaExtension = extension.idea
		}

		project.tasks.register(IDEA_INSPECT, DetektIdeaInspectionTask::class.java) {
			debugOrDefault = extension.debug
			input = determineInput(extension)
			ideaExtension = extension.idea
		}
	}

	private fun determineInput(extension: DetektExtension) = extension.input.filter { it.exists() }

	private fun configurePluginDependencies(project: Project, extension: DetektExtension) {
		project.configurations.create(CONFIGURATION_DETEKT_PLUGINS) {
			isVisible = false
			isTransitive = true
			description = "The $CONFIGURATION_DETEKT_PLUGINS libraries to be used for this project."
		}

		project.configurations.create(CONFIGURATION_DETEKT) {
			isVisible = false
			isTransitive = true
			description = "The $CONFIGURATION_DETEKT dependencies to be used for this project."

			@Suppress("USELESS_ELVIS")
			val version = extension.toolVersion ?: DEFAULT_DETEKT_VERSION
			defaultDependencies {
				add(project.dependencies.create("io.gitlab.arturbosch.detekt:detekt-cli:$version"))
			}
		}
	}

	private val SourceSet.kotlin: KotlinSourceSet get() = (this as HasConvention).convention.getPlugin<KotlinSourceSet>()
	private val Project.sourceSets: SourceSetContainer? get() = project.extensions.findByType(SourceSetContainer::class.java)
	private fun Any.getConvention(name: String): Any? =
			(this as HasConvention).convention.plugins[name]

	companion object {
		private const val DETEKT = "detekt"
		private const val IDEA_FORMAT = "detektIdeaFormat"
		private const val IDEA_INSPECT = "detektIdeaInspect"
		private const val GENERATE_CONFIG = "detektGenerateConfig"
		private const val BASELINE = "detektBaseline"
	}
}

const val CONFIGURATION_DETEKT = "detekt"
const val CONFIGURATION_DETEKT_PLUGINS = "detektPlugins"
