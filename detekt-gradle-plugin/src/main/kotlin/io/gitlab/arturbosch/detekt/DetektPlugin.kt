package io.gitlab.arturbosch.detekt

import com.android.build.gradle.AppExtension
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.api.SourceKind
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.HasConvention
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.plugins.ReportingBasePlugin
import org.gradle.api.provider.Provider
import org.gradle.api.reporting.ReportingExtension
import org.gradle.api.tasks.SourceSet
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.File

/**
 * @author Marvin Ramin
 * @author Markus Schwarz
 * @author Artem Zinnatullin
 * @author Niklas Baudy
 * @author Matthew Haughton
 */
class DetektPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        project.pluginManager.apply(ReportingBasePlugin::class.java)
        val extension = project.extensions.create(DETEKT, DetektExtension::class.java, project)
        extension.reportsDir = project.extensions.getByType(ReportingExtension::class.java).file("detekt")

        configurePluginDependencies(project, extension)
        setTaskDefaults(project)

        registerOldDetektTask(project, extension)
        registerDetektTasks(project, extension)
        registerCreateBaselineTask(project, extension)
        registerGenerateConfigTask(project, extension)

        registerIdeaTasks(project, extension)
    }

    private fun registerDetektTasks(project: Project, extension: DetektExtension) {
        // Kotlin JVM plugin
        project.plugins.withId("org.jetbrains.kotlin.jvm") {
            project.convention.getPlugin(JavaPluginConvention::class.java).sourceSets.all { sourceSet ->
                registerDetektTask(project, extension, sourceSet)
            }
        }

        // Kotlin Android plugin
        project.plugins.withId("org.jetbrains.kotlin.android") {
            val appExtension = project.extensions.findByType(AppExtension::class.java)
            val libraryExtension = project.extensions.findByType(LibraryExtension::class.java)

            appExtension?.applicationVariants?.all { applicationVariant ->
                registerAndroidDetektTask(project, extension, applicationVariant)
            }

            libraryExtension?.libraryVariants?.all { libraryVariant ->
                registerAndroidDetektTask(project, extension, libraryVariant)
            }
        }
    }

    private fun registerOldDetektTask(project: Project, extension: DetektExtension) {
        val detektTaskProvider = project.tasks.register(DETEKT, Detekt::class.java) {
            it.debugProp.set(project.provider { extension.debug })
            it.parallelProp.set(project.provider { extension.parallel })
            it.disableDefaultRuleSetsProp.set(project.provider { extension.disableDefaultRuleSets })
            it.buildUponDefaultConfigProp.set(project.provider { extension.buildUponDefaultConfig })
            it.failFastProp.set(project.provider { extension.failFast })
            it.config.setFrom(project.provider { extension.config })
            it.baseline.set(project.layout.file(project.provider { extension.baseline }))
            it.plugins.set(project.provider { extension.plugins })
            it.setSource(existingInputDirectoriesProvider(project, extension))
            it.setIncludes(defaultIncludes)
            it.setExcludes(defaultExcludes)
            it.reportsDir.set(project.provider { extension.customReportsDir })
            it.reports = extension.reports

            project.subprojects.forEach { subProject ->
                subProject.tasks.firstOrNull { t -> t is Detekt }?.let { subprojectTask ->
                    it.dependsOn(subprojectTask)
                }
            }
        }

        project.tasks.matching { it.name == LifecycleBasePlugin.CHECK_TASK_NAME }.configureEach {
            it.dependsOn(detektTaskProvider)
        }
    }

    private fun registerDetektTask(project: Project, extension: DetektExtension, sourceSet: SourceSet) {
        val kotlinSourceSet = (sourceSet as HasConvention).convention.plugins["kotlin"] as? KotlinSourceSet
            ?: throw GradleException("Kotlin source set not found. Please report on detekt's issue tracker")
        project.tasks.register(DETEKT + sourceSet.name.capitalize(), Detekt::class.java) {
            it.debugProp.set(project.provider { extension.debug })
            it.parallelProp.set(project.provider { extension.parallel })
            it.disableDefaultRuleSetsProp.set(project.provider { extension.disableDefaultRuleSets })
            it.buildUponDefaultConfigProp.set(project.provider { extension.buildUponDefaultConfig })
            it.failFastProp.set(project.provider { extension.failFast })
            it.config.setFrom(project.provider { extension.config })
            it.baseline.set(project.layout.file(project.provider { extension.baseline }))
            it.plugins.set(project.provider { extension.plugins })
            it.setSource(kotlinSourceSet.kotlin.files)
            it.classpath.setFrom(sourceSet.compileClasspath, sourceSet.output.classesDirs)
            it.reports.xml.destination = File(extension.reportsDir, sourceSet.name + ".xml")
            it.reports.html.destination = File(extension.reportsDir, sourceSet.name + ".html")
            it.description =
                "EXPERIMENTAL & SLOW: Run detekt analysis for ${sourceSet.name} classes with type resolution"
        }
    }

    private fun registerAndroidDetektTask(
        project: Project,
        extension: DetektExtension,
        variant: BaseVariant
    ) {
        val variantName = variant.name.capitalize()

        project.tasks.register(DETEKT + variantName, Detekt::class.java) {
            val files = variant.sourceSets.asSequence()
                .flatMap { it.javaDirectories.asSequence() }
                .flatMap { it.walk() }
                .filter { it.isFile }
                .asIterable()
            val kotlinBuildTask =
                project.tasks.getByName("compile${variantName}Kotlin") as? KotlinCompile
            checkNotNull(kotlinBuildTask) { "KotlinCompile task for build variant ${variantName} not found." }
            it.debugProp.set(project.provider { extension.debug })
            it.parallelProp.set(project.provider { extension.parallel })
            it.disableDefaultRuleSetsProp.set(project.provider { extension.disableDefaultRuleSets })
            it.buildUponDefaultConfigProp.set(project.provider { extension.buildUponDefaultConfig })
            it.failFastProp.set(project.provider { extension.failFast })
            it.config.setFrom(project.provider { extension.config })
            it.baseline.set(project.layout.file(project.provider { extension.baseline }))
            it.plugins.set(project.provider { extension.plugins })
            it.source(files, variant.getSourceFolders(SourceKind.JAVA))
            it.classpath.setFrom(kotlinBuildTask.classpath)
            it.reports.xml.destination = File(extension.reportsDir, variant.name + ".xml")
            it.reports.html.destination = File(extension.reportsDir, variant.name + ".html")
            it.description =
                "EXPERIMENTAL & SLOW: Run detekt analysis for ${variant.name} classes with type resolution"
        }
    }

    private fun registerCreateBaselineTask(project: Project, extension: DetektExtension) =
        project.tasks.register(BASELINE, DetektCreateBaselineTask::class.java) {
            it.baseline.set(project.layout.file(project.provider { extension.baseline }))
            it.config.setFrom(project.provider { extension.config })
            it.debug.set(project.provider { extension.debug })
            it.parallel.set(project.provider { extension.parallel })
            it.disableDefaultRuleSets.set(project.provider { extension.disableDefaultRuleSets })
            it.buildUponDefaultConfig.set(project.provider { extension.buildUponDefaultConfig })
            it.failFast.set(project.provider { extension.failFast })
            it.plugins.set(project.provider { extension.plugins })
            it.setSource(existingInputDirectoriesProvider(project, extension))
            it.setIncludes(defaultIncludes)
            it.setExcludes(defaultExcludes)
        }

    private fun registerGenerateConfigTask(project: Project, extension: DetektExtension) =
        project.tasks.register(GENERATE_CONFIG, DetektGenerateConfigTask::class.java) {
            it.setSource(existingInputDirectoriesProvider(project, extension))
            it.setIncludes(listOf("**/*.kt", "**/*.kts"))
            it.setExcludes(listOf("build/"))
        }

    private fun registerIdeaTasks(project: Project, extension: DetektExtension) {
        project.tasks.register(IDEA_FORMAT, DetektIdeaFormatTask::class.java) {
            it.debug.set(project.provider { extension.debug })
            it.setSource(existingInputDirectoriesProvider(project, extension))
            it.setIncludes(defaultIncludes)
            it.setExcludes(defaultExcludes)
            it.ideaExtension = extension.idea
        }

        project.tasks.register(IDEA_INSPECT, DetektIdeaInspectionTask::class.java) {
            it.debug.set(project.provider { extension.debug })
            it.setSource(existingInputDirectoriesProvider(project, extension))
            it.setIncludes(defaultIncludes)
            it.setExcludes(defaultExcludes)
            it.ideaExtension = extension.idea
        }
    }

    private fun existingInputDirectoriesProvider(
        project: Project,
        extension: DetektExtension
    ): Provider<FileCollection> = project.provider { extension.input.filter { it.exists() } }

    private fun configurePluginDependencies(project: Project, extension: DetektExtension) {
        project.configurations.create(CONFIGURATION_DETEKT_PLUGINS) { configuration ->
            configuration.isVisible = false
            configuration.isTransitive = true
            configuration.description = "The $CONFIGURATION_DETEKT_PLUGINS libraries to be used for this project."
        }

        project.configurations.create(CONFIGURATION_DETEKT) { configuration ->
            configuration.isVisible = false
            configuration.isTransitive = true
            configuration.description = "The $CONFIGURATION_DETEKT dependencies to be used for this project."

            configuration.defaultDependencies { dependencySet ->
                @Suppress("USELESS_ELVIS")
                val version = extension.toolVersion ?: DEFAULT_DETEKT_VERSION
                dependencySet.add(project.dependencies.create("io.gitlab.arturbosch.detekt:detekt-cli:$version"))
            }
        }
    }

    private fun setTaskDefaults(project: Project) {
        project.tasks.withType(Detekt::class.java).configureEach {
            it.detektClasspath.setFrom(project.configurations.getAt(CONFIGURATION_DETEKT))
            it.pluginClasspath.setFrom(project.configurations.getAt(CONFIGURATION_DETEKT_PLUGINS))
        }

        project.tasks.withType(DetektCreateBaselineTask::class.java).configureEach {
            it.detektClasspath.setFrom(project.configurations.getAt(CONFIGURATION_DETEKT))
            it.pluginClasspath.setFrom(project.configurations.getAt(CONFIGURATION_DETEKT_PLUGINS))
        }

        project.tasks.withType(DetektGenerateConfigTask::class.java).configureEach {
            it.detektClasspath.setFrom(project.configurations.getAt(CONFIGURATION_DETEKT))
        }
    }

    companion object {
        private const val DETEKT = "detekt"
        private const val IDEA_FORMAT = "detektIdeaFormat"
        private const val IDEA_INSPECT = "detektIdeaInspect"
        private const val GENERATE_CONFIG = "detektGenerateConfig"
        private const val BASELINE = "detektBaseline"
        private val defaultExcludes = listOf("build/")
        private val defaultIncludes = listOf("**/*.kt", "**/*.kts")
    }
}

const val CONFIGURATION_DETEKT = "detekt"
const val CONFIGURATION_DETEKT_PLUGINS = "detektPlugins"
