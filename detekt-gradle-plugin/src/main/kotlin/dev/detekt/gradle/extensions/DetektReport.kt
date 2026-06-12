package dev.detekt.gradle.extensions

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import javax.inject.Inject

/**
 * Configuration for one of detekt's built-in report formats produced by a [dev.detekt.gradle.Detekt] task.
 *
 * @property type the format of this report
 */
abstract class DetektReport @Inject constructor(@get:Internal val type: DetektReportType) {
    /** Whether this report should be generated. */
    @get:Input
    abstract val required: Property<Boolean>

    /** The file the report is written to. */
    @get:OutputFile
    abstract val outputLocation: RegularFileProperty

    override fun toString(): String = "DetektReport(type='$type', required=$required, outputLocation=$outputLocation)"
}
