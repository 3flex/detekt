package io.gitlab.arturbosch.detekt.internal

import com.android.build.gradle.AppExtension
import com.android.build.gradle.FeatureExtension
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.TestExtension
import com.android.build.gradle.TestedExtension
import com.android.build.gradle.api.BaseVariant
import com.android.builder.core.BuilderConstants
import org.gradle.api.NamedDomainObjectCollection
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.UnknownDomainObjectException
import org.gradle.api.artifacts.ResolveException
import org.gradle.api.file.FileCollection
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.SourceSet
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinSingleTargetExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.File
import java.io.Serializable

class ConfigurationExtractor(private val project: Project) {

	fun extractFromSinglePlatform(variantName: String? = null): PlatformData? {
		val target: KotlinTarget
		try {
			target = project.extensions.getByType(KotlinSingleTargetExtension::class.java).target
		} catch (e: Throwable) {
			when (e) {
				is UnknownDomainObjectException, is NoClassDefFoundError, is ClassNotFoundException ->
					return null
				else -> throw e
			}
		}

		return try {
			PlatformData(
				null,
				getClasspath(target, variantName),
				getSourceSet(target, variantName),
				getPlatformName(target.platformType)
			)
		} catch (e: NoSuchMethodError) {
			null
		}
	}

	fun extractFromJavaPlugin(): PlatformData? =
		project.convention.findPlugin(JavaPluginConvention::class.java)
			?.run { sourceSets.findByName(SourceSet.MAIN_SOURCE_SET_NAME)?.allSource?.srcDirs }
			?.let { PlatformData(null, emptyList(), it.toList(), "") }

	fun extractFromKotlinTasks(kotlinTasks: List<Task>): PlatformData? =
		try {
			kotlinTasks.map { extractFromKotlinTask(it) }.let { platformDataList ->
				PlatformData(
					null,
					platformDataList.flatMap { it.classpath },
					platformDataList.flatMap { it.sourceRoots },
					""
				)
			}
		} catch (e: Throwable) {
			when (e) {
				is UnknownDomainObjectException, is NoClassDefFoundError, is ClassNotFoundException ->
					extractFromKotlinTasksTheHardWay(kotlinTasks)
				else -> throw e
			}
		}

	private fun extractFromKotlinTask(task: Task): PlatformData =
		project.extensions.getByType(KotlinSingleTargetExtension::class.java).target
			.compilations
			.find { it.compileKotlinTask == task }
			.let { PlatformData(task.name, getClasspath(it), getSourceSet(it), it?.platformType?.toString() ?: "") }

	private fun extractFromKotlinTasksTheHardWay(kotlinTasks: List<Task>): PlatformData? {
		val allClasspath = mutableSetOf<File>()
		var allClasspathFileCollection: FileCollection = project.files()
		val allSourceRoots = mutableSetOf<File>()

		val classpath: MutableList<File> = try {
			allClasspathFileCollection.toMutableList()
		} catch (e: ResolveException) {
			mutableListOf()
		}
		classpath.addAll(project.files(allClasspath).toList())

		return PlatformData(null, classpath, allSourceRoots.toList(), "")
	}

	private fun getSourceSet(target: KotlinTarget, variantName: String? = null): List<File> =
		if (variantName != null)
			getSourceSet(getCompilation(target, variantName))
		else
			getSourceSet(getMainCompilation(target))

	private fun getClasspath(target: KotlinTarget, variantName: String? = null): List<File> =
		if (target.platformType == KotlinPlatformType.androidJvm) {
			if (variantName != null)
				getClasspathFromAndroidTask(getCompilation(target, variantName))
			else
				getClasspathFromAndroidTask(getMainCompilation(target))
		} else {
			getClasspath(getMainCompilation(target))
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

	private fun getMainCompilation(target: KotlinTarget) =
		getCompilation(target, getMainCompilationName(target))

	private fun getCompilation(target: KotlinTarget, name: String) =
		target.compilations.getByName(name)

	private fun getMainCompilationName(target: KotlinTarget) = if (target.platformType == KotlinPlatformType.androidJvm)
		getVariants(project).filter { it.buildType.name == BuilderConstants.RELEASE }.map { it.name }.first()
	else
		KotlinCompilation.MAIN_COMPILATION_NAME

	private fun getVariants(project: Project): Set<BaseVariant> {
		val androidExtension = project.extensions.getByName("android")
		val baseVariants = when (androidExtension) {
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

	private fun getPlatformName(platform: KotlinPlatformType): String =
		if (platform == KotlinPlatformType.androidJvm) KotlinPlatformType.jvm.toString() else platform.toString()

	data class PlatformData(
		val name: String?,
		val classpath: List<File>,
		val sourceRoots: List<File>,
		val platform: String
	) : Serializable
}