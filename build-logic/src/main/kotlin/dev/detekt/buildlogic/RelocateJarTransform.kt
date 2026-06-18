package dev.detekt.buildlogic

import org.gradle.api.artifacts.transform.CacheableTransform
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.commons.ClassRemapper
import org.objectweb.asm.commons.Remapper
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Artifact transform that relocates packages inside a jar by rewriting its bytecode with ASM.
 *
 * This replaces the use of the Shadow plugin for the sole purpose of package relocation. For every
 * `.class` entry the class is read and re-emitted through a [ClassRemapper], rewriting any reference
 * whose internal name matches a configured relocation prefix. The zip entry path of relocated
 * classes is renamed accordingly, and the string table of Kotlin's `@Metadata` annotation is
 * rewritten as well so the Kotlin compiler resolves the relocated types. Non-class entries are
 * copied verbatim.
 */
@CacheableTransform
abstract class RelocateJarTransform : TransformAction<RelocateJarTransform.Parameters> {

    interface Parameters : TransformParameters {
        /**
         * Relocation prefixes expressed with dot separators, from -> to.
         * Example: `"org.jetbrains.kotlin.com.intellij"` -> `"com.intellij"`.
         */
        @get:Input
        val relocations: MapProperty<String, String>
    }

    @get:InputArtifact
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val inputArtifact: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val input = inputArtifact.get().asFile
        val internalRelocations = parameters.relocations.get()
            .entries
            // Internal names use '/' separators, so convert the dot-separated prefixes.
            .map { (from, to) -> from.replace('.', '/') to to.replace('.', '/') }
            // Longest/most-specific prefix wins.
            .sortedByDescending { it.first.length }
        val remapper = PrefixRemapper(internalRelocations)

        val outputJar = outputs.file("${input.nameWithoutExtension}-relocated.jar")
        ZipFile(input).use { zip ->
            ZipOutputStream(outputJar.outputStream().buffered()).use { out ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.isDirectory) continue
                    val bytes = zip.getInputStream(entry).use { it.readBytes() }
                    if (entry.name.endsWith(".class")) {
                        writeEntry(out, remapper.mapPath(entry.name), relocateClass(bytes, remapper, internalRelocations))
                    } else {
                        writeEntry(out, entry.name, bytes)
                    }
                }
            }
        }
    }

    private fun relocateClass(
        bytes: ByteArray,
        remapper: Remapper,
        internalRelocations: List<Pair<String, String>>,
    ): ByteArray {
        // COMPUTE_FRAMES must not be used: it would require a classloader to resolve common
        // supertypes. Pure reference renaming needs no frame/maxs recomputation.
        val writer = ClassWriter(0)
        val visitor = object : ClassRemapper(Opcodes.ASM9, writer, remapper) {
            override fun visitAnnotation(descriptor: String?, visible: Boolean): AnnotationVisitor? {
                val annotationVisitor = super.visitAnnotation(descriptor, visible)
                return if (descriptor == KOTLIN_METADATA_DESCRIPTOR && annotationVisitor != null) {
                    MetadataAnnotationVisitor(annotationVisitor, internalRelocations)
                } else {
                    annotationVisitor
                }
            }
        }
        ClassReader(bytes).accept(visitor, 0)
        return writer.toByteArray()
    }

    private fun writeEntry(out: ZipOutputStream, name: String, bytes: ByteArray) {
        out.putNextEntry(ZipEntry(name))
        out.write(bytes)
        out.closeEntry()
    }

    private class PrefixRemapper(private val relocations: List<Pair<String, String>>) : Remapper(Opcodes.ASM9) {
        override fun map(internalName: String): String {
            for ((from, to) in relocations) {
                if (internalName == from || internalName.startsWith("$from/")) {
                    return to + internalName.substring(from.length)
                }
            }
            return internalName
        }

        fun mapPath(entryName: String): String = map(entryName.removeSuffix(".class")) + ".class"
    }

    /**
     * Rewrites the `d2` string table of Kotlin's `@Metadata` annotation. `d2` holds the class and
     * member names referenced (by index) from the `d1` protobuf payload; the bytecode [Remapper]
     * does not touch these plain string values, so the Kotlin compiler would otherwise still resolve
     * the original, non-relocated types.
     */
    private class MetadataAnnotationVisitor(
        delegate: AnnotationVisitor,
        private val relocations: List<Pair<String, String>>,
    ) : AnnotationVisitor(Opcodes.ASM9, delegate) {
        override fun visitArray(name: String?): AnnotationVisitor? {
            val arrayVisitor = super.visitArray(name) ?: return null
            return if (name == "d2") {
                object : AnnotationVisitor(Opcodes.ASM9, arrayVisitor) {
                    override fun visit(name: String?, value: Any?) {
                        super.visit(name, if (value is String) relocate(value) else value)
                    }
                }
            } else {
                arrayVisitor
            }
        }

        private fun relocate(value: String): String =
            relocations.fold(value) { acc, (from, to) -> acc.replace(from, to) }
    }

    private companion object {
        const val KOTLIN_METADATA_DESCRIPTOR = "Lkotlin/Metadata;"
    }
}
