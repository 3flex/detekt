package dev.detekt.gradle.extensions

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

open class DetektCompilerPluginReport @Inject constructor(val name: String, objects: ObjectFactory) {
    val required: Property<Boolean> = objects.property(Boolean::class.java)
    val outputLocation: RegularFileProperty = objects.fileProperty()
}
