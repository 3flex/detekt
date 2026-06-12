package dev.detekt.gradle.extensions

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

/**
 * The `detekt { }` project extension used to configure the detekt Gradle plugin.
 *
 * The values configured here are used as conventions for the properties of the generated
 * detekt tasks (such as `detekt`, `detektMain` and `detektBaseline`).
 */
@Suppress("ComplexInterface")
interface DetektExtension {

    /**
     * Version of detekt that will be used to run the analysis. When unspecified, the version that the
     * Gradle plugin was built against is used. Override this to pin the analysis to a specific version.
     */
    val toolVersion: Property<String>

    /**
     * When set to `true` the build does not fail even if detekt reports issues. Defaults to `false`.
     */
    val ignoreFailures: Property<Boolean>

    /**
     * The build fails when there is at least one issue with this severity or above. If set to
     * [FailOnSeverity.Never] the task never fails regardless of the issues found. When [ignoreFailures]
     * is `true` this property is ignored. Defaults to [FailOnSeverity.Error].
     */
    val failOnSeverity: Property<FailOnSeverity>

    /**
     * The directory where detekt writes its reports. Defaults to `build/reports/detekt`.
     */
    val reportsDir: DirectoryProperty

    /**
     * The directories where detekt looks for source files to analyze. Defaults to
     * `src/main/java`, `src/test/java`, `src/main/kotlin` and `src/test/kotlin`.
     */
    val source: ConfigurableFileCollection

    /**
     * The baseline file containing findings that should be suppressed in subsequent detekt runs.
     */
    val baseline: RegularFileProperty

    /**
     * The base path used to relativize the file paths reported in the formatted reports. If not set,
     * absolute file paths are reported.
     */
    val basePath: DirectoryProperty

    /**
     * Whether the detekt Kotlin compiler plugin should run during compilation when the compiler plugin
     * is applied to the project. This flag has no effect unless the compiler plugin is applied.
     */
    val enableCompilerPlugin: Property<Boolean>

    /**
     * The detekt configuration file(s) used for the analysis. Defaults to detekt's bundled default
     * configuration.
     */
    val config: ConfigurableFileCollection

    /**
     * Whether to print debug output during task execution. Defaults to `false`.
     */
    val debug: Property<Boolean>

    /**
     * Whether to build the abstract syntax tree in parallel. Can lead to speedups on larger projects.
     * Defaults to `false`.
     */
    val parallel: Property<Boolean>

    /**
     * Whether to enable all available rules, including those disabled by default. Defaults to `false`.
     */
    val allRules: Property<Boolean>

    /**
     * Whether to apply the configured [config] files on top of detekt's default configuration instead of
     * replacing it. Defaults to `false`.
     */
    val buildUponDefaultConfig: Property<Boolean>

    /**
     * Whether to disable all default detekt rule sets so that only custom rules supplied through the
     * `detektPlugins` configuration are run. Defaults to `false`.
     */
    val disableDefaultRuleSets: Property<Boolean>

    /**
     * Whether rules that support auto correction are allowed to modify the analyzed source files.
     * Defaults to `false`.
     */
    val autoCorrect: Property<Boolean>

    /**
     * List of Android build variants for which no detekt task should be created.
     *
     * This is a combination of build types and flavors, such as fooDebug or barRelease.
     */
    val ignoredVariants: ListProperty<String>

    /**
     * List of Android build types for which no detekt task should be created.
     */
    val ignoredBuildTypes: ListProperty<String>

    /**
     * List of Android build flavors for which no detekt task should be created
     */
    val ignoredFlavors: ListProperty<String>
}
