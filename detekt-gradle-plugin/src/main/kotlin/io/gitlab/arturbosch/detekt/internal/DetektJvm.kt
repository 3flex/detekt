package io.gitlab.arturbosch.detekt.internal

import io.gitlab.arturbosch.detekt.DetektPlugin
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinWithJavaCompilation
import java.io.File

internal class DetektJvm(private val project: Project) {
    fun registerTasks(extension: DetektExtension) {
        project.afterEvaluate {
            (project.extensions.getByName("kotlin") as KotlinJvmProjectExtension).let { kotlinExtension ->
                kotlinExtension.sourceSets.all { kotlinSourceSet ->
                    val compilation = kotlinExtension.target.compilations.getByName(kotlinSourceSet.name)
                    project.registerJvmDetektTask(extension, kotlinSourceSet, compilation)
                    project.registerJvmCreateBaselineTask(extension, kotlinSourceSet, compilation)
                }
            }
        }
    }

    private fun Project.registerJvmDetektTask(
        extension: DetektExtension,
        kotlinSourceSet: KotlinSourceSet,
        compilation: KotlinWithJavaCompilation<KotlinJvmOptions>
    ) {
        registerDetektTask(DetektPlugin.DETEKT_TASK_NAME + kotlinSourceSet.name.capitalize(), extension) {
            setSource(kotlinSourceSet.kotlin.files)
            classpath.setFrom(compilation.compileDependencyFiles)
            jvmTarget = compilation.kotlinOptions.jvmTarget
            // If a baseline file is configured as input file, it must exist to be configured, otherwise the task fails.
            // We try to find the configured baseline or alternatively a specific variant matching this task.
            extension.baseline?.existingVariantOrBaseFile(kotlinSourceSet.name)?.let { baselineFile ->
                baseline.set(layout.file(project.provider { baselineFile }))
            }
            reports = extension.reports
            reports.xml.setDefaultIfUnset(File(extension.reportsDir, kotlinSourceSet.name + ".xml"))
            reports.html.setDefaultIfUnset(File(extension.reportsDir, kotlinSourceSet.name + ".html"))
            reports.txt.setDefaultIfUnset(File(extension.reportsDir, kotlinSourceSet.name + ".txt"))
            reports.sarif.setDefaultIfUnset(File(extension.reportsDir, kotlinSourceSet.name + ".sarif"))
            description = "EXPERIMENTAL: Run detekt analysis for ${kotlinSourceSet.name} classes with type resolution"
        }
    }

    private fun Project.registerJvmCreateBaselineTask(
        extension: DetektExtension,
        kotlinSourceSet: KotlinSourceSet,
        compilation: KotlinWithJavaCompilation<KotlinJvmOptions>
    ) {
        registerCreateBaselineTask(DetektPlugin.BASELINE_TASK_NAME + kotlinSourceSet.name.capitalize(), extension) {
            setSource(kotlinSourceSet.kotlin.files)
            classpath.setFrom(compilation.compileDependencyFiles)
            jvmTarget = compilation.kotlinOptions.jvmTarget
            val variantBaselineFile = extension.baseline?.addVariantName(kotlinSourceSet.name)
            baseline.set(project.layout.file(project.provider { variantBaselineFile }))
            description =
                "EXPERIMENTAL: Creates detekt baseline for ${kotlinSourceSet.name} classes with type resolution"
        }
    }
}
