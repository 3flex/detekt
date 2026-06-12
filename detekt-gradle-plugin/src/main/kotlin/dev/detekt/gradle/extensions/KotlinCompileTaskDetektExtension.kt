package dev.detekt.gradle.extensions

import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty

/**
 * The `detekt { }` extension added to each Kotlin compile task when the detekt compiler plugin is applied.
 *
 * It controls how the detekt Kotlin compiler plugin behaves for that specific compilation. The values
 * default to the corresponding settings of the project-wide [DetektExtension].
 */
open class KotlinCompileTaskDetektExtension(project: Project) {
    /** The reports produced by the compiler plugin for this compilation, keyed by format name. */
    val reports: NamedDomainObjectContainer<DetektCompilerPluginReport> =
        project.container(DetektCompilerPluginReport::class.java)

    init {
        reports.create("checkstyle")
        reports.create("html")
        reports.create("sarif")
    }

    private val objects: ObjectFactory = project.objects

    /** Whether detekt runs during this compilation. */
    val isEnabled: Property<Boolean> = objects.property(Boolean::class.java)

    /** Whether to print debug output while detekt runs. */
    val debug: Property<Boolean> = objects.property(Boolean::class.java)

    /** Whether to apply the [config] files on top of detekt's default configuration. */
    val buildUponDefaultConfig: Property<Boolean> = objects.property(Boolean::class.java)

    /** Whether to enable all available rules, including those disabled by default. */
    val allRules: Property<Boolean> = objects.property(Boolean::class.java)

    /** Whether to disable all default detekt rule sets and only run custom rules. */
    val disableDefaultRuleSets: Property<Boolean> = objects.property(Boolean::class.java)

    /** Whether to build the abstract syntax tree in parallel. */
    val parallel: Property<Boolean> = objects.property(Boolean::class.java)

    /** The baseline file containing findings that should be suppressed during this compilation. */
    val baseline: RegularFileProperty = objects.fileProperty()

    /** The detekt configuration file(s) used for this compilation. */
    val config: ConfigurableFileCollection = objects.fileCollection()

    /** Glob patterns of files that should be excluded from the analysis. */
    val excludes: SetProperty<String> = objects.setProperty(String::class.java)

    /** Returns the configuration for the Checkstyle XML report. */
    fun getCheckstyle(): DetektCompilerPluginReport = reports.getByName("checkstyle")

    /** Returns the configuration for the HTML report. */
    fun getHtml(): DetektCompilerPluginReport = reports.getByName("html")

    /** Returns the configuration for the SARIF report. */
    fun getSarif(): DetektCompilerPluginReport = reports.getByName("sarif")
}
