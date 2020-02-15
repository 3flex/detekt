package io.gitlab.arturbosch.detekt

import com.android.build.gradle.AppExtension
import com.android.build.gradle.FeatureExtension
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.TestExtension
import com.android.build.gradle.TestedExtension
import com.android.build.gradle.api.BaseVariant
import com.android.builder.core.BuilderConstants
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import io.gitlab.arturbosch.detekt.internal.ConfigurationExtractor
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
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinSingleTargetExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.tasks.AbstractKotlinCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.File

class DetektPlugin : Plugin<Project> {

    private lateinit var project: Project

    override fun apply(project: Project) {
        this.project = project
        project.pluginManager.apply(ReportingBasePlugin::class.java)
        val extension = project.extensions.create(DETEKT_TASK_NAME, DetektExtension::class.java, project)
        extension.reportsDir = project.extensions.getByType(ReportingExtension::class.java).file("detekt")

        val defaultConfigFile =
            project.file("${project.rootProject.layout.projectDirectory.dir(CONFIG_DIR_NAME)}/$CONFIG_FILE")
        if (defaultConfigFile.exists()) {
            extension.config = project.files(defaultConfigFile)
        }

        configurePluginDependencies(project, extension)
        setTaskDefaults(project)

        registerOldDetektTask(project, extension)
        registerDetektTasks(project, extension)
        registerCreateBaselineTask(project, extension)
        registerGenerateConfigTask(project)

        registerIdeaTasks(project, extension)
    }

    private fun registerDetektTasks(project: Project, extension: DetektExtension) {
        // Kotlin JVM plugin
        project.plugins.withId("org.jetbrains.kotlin.jvm") {
            project.afterEvaluate {
                project.convention.getPlugin(JavaPluginConvention::class.java).sourceSets.all { sourceSet ->
                    registerDetektTask(project, extension, sourceSet)
                }
            }
        }

        // Kotlin Android plugin
        project.plugins.withId("org.jetbrains.kotlin.android") {
            val appExtension = project.extensions.findByType(AppExtension::class.java)
            val libraryExtension = project.extensions.findByType(LibraryExtension::class.java)
            val kotlinSingleTargetExtension = project.extensions.findByType(KotlinSingleTargetExtension::class.java)

            val sourcesets = kotlinSingleTargetExtension?.sourceSets
            sourcesets?.all { project.logger.warn("Sourceset: ${it.name}") }

            project.logger.warn(sourcesets?.getByName("main")?.kotlin?.sourceDirectories?.asPath)

            kotlinSingleTargetExtension?.target?.compilations?.all {
                val allSource = sourcesets?.getByName(it.name)?.kotlin?.sourceDirectories?.plus(sourcesets.getByName("main").kotlin.sourceDirectories)
                project.logger.warn("Kotlin task source: ${allSource?.asPath}")
                project.logger.warn("Compilation: ${it.name}")
            }
            project.logger.warn((kotlinSingleTargetExtension?.target?.compilations?.findByName("release")?.compileKotlinTask as? KotlinCompile)?.classpath?.asPath)
//            project.logger.warn(kotlinSingleTargetExtension?.target?.compilations?.named("debug")?.

            kotlinSingleTargetExtension?.target?.compilations?.all { compilation ->
                val allSource = sourcesets?.getByName(compilation.name)?.kotlin?.sourceDirectories?.plus(sourcesets.getByName("main").kotlin.sourceDirectories)

//                val ex = ConfigurationExtractor(project)
//                val data = ex.extractFromSinglePlatform()
//                project.logger.warn(data?.classpath.toString())

                project.tasks.register(DETEKT_TASK_NAME + compilation.name.capitalize(), Detekt::class.java) {
                    it.setDependsOn(listOf(compilation.compileKotlinTask))
                    it.setSource(allSource)
	                it.classpath.setFrom({ getClasspath(kotlinSingleTargetExtension.target, "release") })
//                    it.classpath.setFrom({ (compilation.compileKotlinTask as KotlinCompile).classpath }) // deprecated
//                    it.classpath.setFrom({ project.configurations.getByName("kotlinCompilerClasspath") }) // use this instead of what's deprecated
//                    it.classpath.setFrom({ project.configurations.getByName("kotlinCompilerClasspath").plus((compilation.compileKotlinTask as KotlinCompile).compilerClasspath.orEmpty()) })

                    it.debugProp.set(project.provider { extension.debug })
                    it.parallelProp.set(project.provider { extension.parallel })
                    it.disableDefaultRuleSetsProp.set(project.provider { extension.disableDefaultRuleSets })
                    it.buildUponDefaultConfigProp.set(project.provider { extension.buildUponDefaultConfig })
                    it.failFastProp.set(project.provider { extension.failFast })
                    it.autoCorrectProp.set(project.provider { extension.autoCorrect })
                    it.config.setFrom(project.provider { extension.config })
                    it.baseline.set(project.layout.file(project.provider { extension.baseline }))
                    it.setSource(existingInputDirectoriesProvider(project, extension))
                    it.setIncludes(defaultIncludes)
                    it.setExcludes(defaultExcludes)
                    it.reportsDir.set(project.provider { extension.customReportsDir })
                    it.reports = extension.reports
                    it.ignoreFailuresProp.set(project.provider { extension.ignoreFailures })

//                    println(it.classpath.asPath)
//                    sourcesets?.forEach(::println)
//                    it.source(sourcesets!!.findByName("release")?.kotlin?.sourceDirectories)
//                    it.source(compilation.allKotlinSourceSets.map { it.kotlin.sourceDirectories.asPath })
                    compilation
                        .allKotlinSourceSets
                        .flatMap { it.kotlin.sourceDirectories }
//                        .filter { it.exists() }
                        .also(::println)
                }
            }

//            val multiplatformExtension = project.extensions.findByType(KotlinMultiplatformExtension::class.java)

//            multiplatformExtension?.targets?.all {
//                project.logger.warn(it.name)
//            }

//            appExtension?.applicationVariants?.all { applicationVariant ->
////                registerAndroidDetektTaskOld(project, extension, applicationVariant)
//                registerAndroidDetektTask(project, extension, applicationVariant)
//            }

//            libraryExtension?.libraryVariants?.all { libraryVariant ->
//                registerAndroidDetektTaskOld(project, extension, libraryVariant)
//            }
        }
    }

    private fun registerOldDetektTask(project: Project, extension: DetektExtension) {
        val detektTaskProvider = project.tasks.register(DETEKT_TASK_NAME, Detekt::class.java) {
            it.debugProp.set(project.provider { extension.debug })
            it.parallelProp.set(project.provider { extension.parallel })
            it.disableDefaultRuleSetsProp.set(project.provider { extension.disableDefaultRuleSets })
            it.buildUponDefaultConfigProp.set(project.provider { extension.buildUponDefaultConfig })
            it.failFastProp.set(project.provider { extension.failFast })
            it.autoCorrectProp.set(project.provider { extension.autoCorrect })
            it.config.setFrom(project.provider { extension.config })
            it.baseline.set(project.layout.file(project.provider { extension.baseline }))
            it.setSource(existingInputDirectoriesProvider(project, extension))
            it.setIncludes(defaultIncludes)
            it.setExcludes(defaultExcludes)
            it.reportsDir.set(project.provider { extension.customReportsDir })
            it.reports = extension.reports
            it.ignoreFailuresProp.set(project.provider { extension.ignoreFailures })
        }

        project.tasks.matching { it.name == LifecycleBasePlugin.CHECK_TASK_NAME }.configureEach {
            it.dependsOn(detektTaskProvider)
        }
    }

    private fun registerDetektTask(project: Project, extension: DetektExtension, sourceSet: SourceSet) {
        val kotlinSourceSet = (sourceSet as HasConvention).convention.plugins["kotlin"] as? KotlinSourceSet
            ?: throw GradleException("Kotlin source set not found. Please report on detekt's issue tracker")
        project.tasks.register(DETEKT_TASK_NAME + sourceSet.name.capitalize(), Detekt::class.java) {
            it.debugProp.set(project.provider { extension.debug })
            it.parallelProp.set(project.provider { extension.parallel })
            it.disableDefaultRuleSetsProp.set(project.provider { extension.disableDefaultRuleSets })
            it.buildUponDefaultConfigProp.set(project.provider { extension.buildUponDefaultConfig })
            it.failFastProp.set(project.provider { extension.failFast })
            it.autoCorrectProp.set(project.provider { extension.autoCorrect })
            it.config.setFrom(project.provider { extension.config })
            it.baseline.set(project.layout.file(project.provider { extension.baseline }))
            it.setSource(kotlinSourceSet.kotlin.files)
            it.classpath.setFrom(sourceSet.compileClasspath, sourceSet.output.classesDirs)
            it.reports.xml.destination = File(extension.reportsDir, sourceSet.name + ".xml")
            it.reports.html.destination = File(extension.reportsDir, sourceSet.name + ".html")
            it.reports.txt.destination = File(extension.reportsDir, sourceSet.name + ".txt")
            it.ignoreFailuresProp.set(project.provider { extension.ignoreFailures })
            it.description =
                "EXPERIMENTAL & SLOW: Run detekt analysis for ${sourceSet.name} classes with type resolution"
        }
    }

    private fun registerAndroidDetektTask(project: Project, extension: DetektExtension, variant: BaseVariant) {
        val variantName = variant.name.capitalize()

        val target = project.extensions.getByType(KotlinSingleTargetExtension::class.java).target
        if (target.platformType == KotlinPlatformType.androidJvm) {

            project.logger.warn(target.compilations.joinToString("\n"))
            project.logger.warn(target.compilations.joinToString("\n") { it.compileKotlinTaskName })

        project.tasks.register(DETEKT_TASK_NAME + variantName, Detekt::class.java) {
            val files = variant.sourceSets
                .flatMap { it.javaDirectories }
                .flatMap { it.walk().asIterable() }
                .filter { it.isFile }

//            val kotlinBuildTask =
//                project.tasks.getByName("compile${variantName}KotlinAndroid") as? KotlinCompile
            project.tasks.getByName("compile${variantName}Kotlin")

//            project.logger.error()
//            if (kotlinBuildTask != null) {
//            checkNotNull(kotlinBuildTask) { "KotlinCompile task \"compile${variantName}Kotlin\" for build variant $variantName not found." }
            it.debugProp.set(project.provider { extension.debug })
            it.parallelProp.set(project.provider { extension.parallel })
            it.disableDefaultRuleSetsProp.set(project.provider { extension.disableDefaultRuleSets })
            it.buildUponDefaultConfigProp.set(project.provider { extension.buildUponDefaultConfig })
            it.failFastProp.set(project.provider { extension.failFast })
            it.config.setFrom(project.provider { extension.config })
            it.baseline.set(project.layout.file(project.provider { extension.baseline }))
            it.classpath.setFrom(variant.getCompileClasspath(null))
//                it.source(variant.sourceSets)
//                it.source(files, variant.getSourceFolders(SourceKind.JAVA))
            it.source(files)
//                it.classpath.setFrom(kotlinBuildTask.classpath)
            it.reports.xml.destination = File(extension.reportsDir, variant.name + ".xml")
            it.reports.html.destination = File(extension.reportsDir, variant.name + ".html")
            it.description =
                "EXPERIMENTAL & SLOW: Run detekt analysis for ${variant.name} classes with type resolution"
            }
        }
    }

    private fun registerAndroidDetektTaskOld(project: Project, extension: DetektExtension, variant: BaseVariant) {
        val variantName = variant.name.capitalize()

        project.tasks.register(DETEKT_TASK_NAME + variantName, Detekt::class.java) {
            val files = variant.sourceSets
                .flatMap { it.javaDirectories }
                .flatMap { it.walk().asIterable() }
                .filter { it.isFile }

            val kotlinBuildTask =
                project.tasks.getByName("compile${variantName}Kotlin") as? KotlinCompile
//            if (kotlinBuildTask != null) {
            checkNotNull(kotlinBuildTask) { "KotlinCompile task \"compile${variantName}Kotlin\" for build variant $variantName not found." }
                it.debugProp.set(project.provider { extension.debug })
                it.parallelProp.set(project.provider { extension.parallel })
                it.disableDefaultRuleSetsProp.set(project.provider { extension.disableDefaultRuleSets })
                it.buildUponDefaultConfigProp.set(project.provider { extension.buildUponDefaultConfig })
                it.failFastProp.set(project.provider { extension.failFast })
                it.config.setFrom(project.provider { extension.config })
                it.baseline.set(project.layout.file(project.provider { extension.baseline }))
                it.classpath.setFrom(variant.getCompileClasspath(null))
//                it.source(variant.sourceSets)
//                it.source(files, variant.getSourceFolders(SourceKind.JAVA))
                it.source(files)
//                it.classpath.setFrom(kotlinBuildTask.classpath)
                it.reports.xml.destination = File(extension.reportsDir, variant.name + ".xml")
                it.reports.html.destination = File(extension.reportsDir, variant.name + ".html")
                it.description =
                    "EXPERIMENTAL & SLOW: Run detekt analysis for ${variant.name} classes with type resolution"
////            }
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
            it.autoCorrect.set(project.provider { extension.autoCorrect })
            it.setSource(existingInputDirectoriesProvider(project, extension))
            it.setIncludes(defaultIncludes)
            it.setExcludes(defaultExcludes)
        }

    private fun registerGenerateConfigTask(project: Project) =
        project.tasks.register(GENERATE_CONFIG, DetektGenerateConfigTask::class.java)

    private fun registerIdeaTasks(project: Project, extension: DetektExtension) {
        project.tasks.register(IDEA_FORMAT, DetektIdeaFormatTask::class.java) {
            it.setSource(existingInputDirectoriesProvider(project, extension))
            it.setIncludes(defaultIncludes)
            it.setExcludes(defaultExcludes)
            it.ideaExtension = extension.idea
        }

        project.tasks.register(IDEA_INSPECT, DetektIdeaInspectionTask::class.java) {
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
                val version = extension.toolVersion ?: DEFAULT_DETEKT_VERSION
                dependencySet.add(project.dependencies.create("io.gitlab.arturbosch.detekt:detekt-cli:$version"))
            }
        }
    }

    private fun setTaskDefaults(project: Project) {
        project.tasks.withType(Detekt::class.java) {
            it.detektClasspath.setFrom(project.configurations.getAt(CONFIGURATION_DETEKT))
            it.pluginClasspath.setFrom(project.configurations.getAt(CONFIGURATION_DETEKT_PLUGINS))
        }

        project.tasks.withType(DetektCreateBaselineTask::class.java) {
            it.detektClasspath.setFrom(project.configurations.getAt(CONFIGURATION_DETEKT))
            it.pluginClasspath.setFrom(project.configurations.getAt(CONFIGURATION_DETEKT_PLUGINS))
        }

        project.tasks.withType(DetektGenerateConfigTask::class.java) {
            it.detektClasspath.setFrom(project.configurations.getAt(CONFIGURATION_DETEKT))
        }
    }

    private fun getClasspath(target: KotlinTarget, variantName: String? = null): List<File> = if (target.platformType == KotlinPlatformType.androidJvm) {
        if(variantName != null)
            getClasspathFromAndroidTask(getCompilation(target, variantName))
        else
            getClasspathFromAndroidTask(getMainCompilation(target))
    } else {
        getClasspath(getMainCompilation(target))
    }

    private fun getCompilation(target: KotlinTarget, name: String) =
        target.compilations.getByName(name)

    private fun getMainCompilation(target: KotlinTarget) =
        getCompilation(target, getMainCompilationName(target))

    private fun getMainCompilationName(target: KotlinTarget) = if (target.platformType == KotlinPlatformType.androidJvm)
        getVariants(project).filter { it.buildType.name == BuilderConstants.RELEASE }.map { it.name }.first()
    else
        KotlinCompilation.MAIN_COMPILATION_NAME

    private fun getVariants(project: Project): Set<BaseVariant> {
        val androidExtension = project.extensions.getByName("android")
        val baseVariants =  when (androidExtension) {
            is AppExtension -> androidExtension.applicationVariants.toSet()
            is LibraryExtension -> {
                androidExtension.libraryVariants.toSet() +
                        if (androidExtension is FeatureExtension) {
                            androidExtension.featureVariants.toSet()
                        } else {
                            emptySet<BaseVariant>()
                        }
            }
            is TestExtension -> androidExtension.applicationVariants.toSet()
            else -> emptySet()
        }
        val testVariants = if (androidExtension is TestedExtension) {
            androidExtension.testVariants.toSet() + androidExtension.unitTestVariants.toSet()
        } else {
            emptySet<BaseVariant>()
        }

        return baseVariants + testVariants
    }

    private fun getSourceSet(compilation: KotlinCompilation<*>?): List<File> = compilation
        ?.allKotlinSourceSets
        ?.flatMap { it.kotlin.sourceDirectories }
        ?.filter { it.exists() }
        .orEmpty()

    private fun getClasspath(compilation: KotlinCompilation<*>?): List<File> = compilation
        ?.compileDependencyFiles
        ?.files
        ?.toList()
        ?.filter { it.exists() }
        .orEmpty()

    // This is a workaround for KT-33893
    private fun getClasspathFromAndroidTask(compilation: KotlinCompilation<*>): List<File> = (compilation
        .compileKotlinTask as? KotlinCompile)
        ?.classpath?.files?.toList() ?: getClasspath(compilation)

    companion object {
        private const val DETEKT_TASK_NAME = "detekt"
        private const val IDEA_FORMAT = "detektIdeaFormat"
        private const val IDEA_INSPECT = "detektIdeaInspect"
        private const val GENERATE_CONFIG = "detektGenerateConfig"
        private const val BASELINE = "detektBaseline"
        private val defaultExcludes = listOf("build/")
        private val defaultIncludes = listOf("**/*.kt", "**/*.kts")
        internal const val CONFIG_DIR_NAME = "config/detekt"
        internal const val CONFIG_FILE = "detekt.yml"
    }
}

const val CONFIGURATION_DETEKT = "detekt"
const val CONFIGURATION_DETEKT_PLUGINS = "detektPlugins"
