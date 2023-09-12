package io.gitlab.arturbosch.detekt.internal

import io.gitlab.arturbosch.detekt.DetektPlugin
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetContainer

internal class DetektSourceSet(private val project: Project) {
    fun registerTasks(extension: DetektExtension) {
        project.extensions.getByType(KotlinSourceSetContainer::class.java).sourceSets.all { sourceSet ->
            project.registerSourceSetDetektTask(sourceSet, extension)
            project.registerSourceSetCreateBaselineTask(sourceSet, extension)
        }
    }

    private fun Project.registerSourceSetDetektTask(
        sourceSet: KotlinSourceSet,
        extension: DetektExtension,
    ) {
        registerDetektTask(DetektPlugin.DETEKT_TASK_NAME + sourceSet.name.capitalize() + "SourceSet", extension) {
            source = sourceSet.kotlin
            // If a baseline file is configured as input file, it must exist to be configured, otherwise the task fails.
            // We try to find the configured baseline or alternatively a specific variant matching this task.
            extension.baseline?.existingVariantOrBaseFile(sourceSet.name)?.let { baselineFile ->
                baseline.convention(layout.file(provider { baselineFile }))
            }
            setReportOutputConventions(reports, extension, "${sourceSet.name}SourceSet")
            description = "Run detekt analysis for ${sourceSet.name} source set"
        }
    }

    private fun Project.registerSourceSetCreateBaselineTask(
        sourceSet: KotlinSourceSet,
        extension: DetektExtension,
    ) {
        registerCreateBaselineTask(DetektPlugin.BASELINE_TASK_NAME + sourceSet.name.capitalize() + "SourceSet", extension) {
            source = sourceSet.kotlin
            val variantBaselineFile = extension.baseline?.addVariantName("${sourceSet.name}SourceSet")
            baseline.convention(layout.file(provider { variantBaselineFile }))
            description = "Creates detekt baseline for ${sourceSet.name} source set"
        }
    }
}
