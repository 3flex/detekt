package dev.detekt.gradle.plugin.internal

import com.android.build.api.dsl.CommonExtension
import dev.detekt.gradle.Detekt
import dev.detekt.gradle.DetektCreateBaselineTask
import dev.detekt.gradle.extensions.DetektExtension
import dev.detekt.gradle.internal.addVariantName
import dev.detekt.gradle.internal.existingVariantOrBaseFile
import dev.detekt.gradle.plugin.DetektPlugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskProvider

internal object DetektAndroidSourceSets {

    /**
     * Registers detekt source set tasks (e.g. `detektMainSourceSet`) based on Android source sets.
     *
     * AGP 9 enables `android.disallowKotlinSourceSets` by default when its built-in Kotlin support is in use, which
     * means the Kotlin Gradle Plugin no longer mirrors every Android source set into the Kotlin source set container.
     * As a result, [dev.detekt.gradle.plugin.DetektBasePlugin] does not register source set tasks for those Android
     * source sets. This function fills that gap by registering detekt source set tasks directly from Android's source
     * sets, skipping registration when the corresponding task is already registered via the Kotlin source set
     * container.
     */
    fun registerTasks(project: Project, extension: DetektExtension) {
        val androidExtension = project.extensions.findByType(CommonExtension::class.java) ?: return

        androidExtension.sourceSets.configureEach { sourceSet ->
            val sourceSetName = sourceSet.name
            val sources = project.provider { project.files(sourceSet.kotlin.directories) }

            val taskName = "${DetektPlugin.DETEKT_TASK_NAME}${sourceSetName.capitalize()}SourceSet"
            project.registerOrSkipIfExists(taskName, Detekt::class.java) { detektTask ->
                detektTask.setSource(sources)
                detektTask.baseline.convention(
                    project.layout.file(
                        extension.baseline.flatMap {
                            project.providers.provider {
                                it.asFile.existingVariantOrBaseFile("${sourceSetName}SourceSet")
                            }
                        }
                    )
                )
                if (sourceSetName == "main") {
                    detektTask.explicitApi.convention(project.mapExplicitArgMode())
                }
                detektTask.description = "Run detekt analysis for $sourceSetName source set"
            }

            val baselineTaskName = "${DetektPlugin.BASELINE_TASK_NAME}${sourceSetName.capitalize()}SourceSet"
            project.registerOrSkipIfExists(baselineTaskName, DetektCreateBaselineTask::class.java) { task ->
                task.setSource(sources)
                task.baseline.convention(
                    project.layout.file(
                        extension.baseline.flatMap {
                            project.providers.provider { it.asFile.addVariantName("${sourceSetName}SourceSet") }
                        }
                    )
                )
                if (sourceSetName == "main") {
                    task.explicitApi.convention(project.mapExplicitArgMode())
                }
                task.description = "Creates detekt baseline for $sourceSetName source set"
            }
        }
    }

    private fun <T : Task> Project.registerOrSkipIfExists(
        name: String,
        type: Class<T>,
        configure: (T) -> Unit,
    ): TaskProvider<T>? = if (name in tasks.names) null else tasks.register(name, type, configure)
}
