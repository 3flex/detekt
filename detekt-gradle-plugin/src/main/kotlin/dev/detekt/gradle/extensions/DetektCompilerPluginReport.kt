package dev.detekt.gradle.extensions

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

/**
 * Configuration for a report produced by the detekt Kotlin compiler plugin.
 *
 * @property name the format of this report, for example `html`, `sarif` or `checkstyle`
 */
open class DetektCompilerPluginReport @Inject constructor(val name: String, objects: ObjectFactory) {
    /** Whether this report should be generated. */
    val enabled: Property<Boolean> = objects.property(Boolean::class.java)

    /** The file the report is written to. */
    val destination: RegularFileProperty = objects.fileProperty()
}
