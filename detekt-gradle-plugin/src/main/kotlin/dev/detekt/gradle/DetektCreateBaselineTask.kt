package dev.detekt.gradle

import dev.detekt.gradle.invoke.AllRulesArgument
import dev.detekt.gradle.invoke.ApiVersionArgument
import dev.detekt.gradle.invoke.AutoCorrectArgument
import dev.detekt.gradle.invoke.BasePathArgument
import dev.detekt.gradle.invoke.BaselineArgument
import dev.detekt.gradle.invoke.BuildUponDefaultConfigArgument
import dev.detekt.gradle.invoke.ClasspathArgument
import dev.detekt.gradle.invoke.CliArgument
import dev.detekt.gradle.invoke.ConfigArgument
import dev.detekt.gradle.invoke.CreateBaselineArgument
import dev.detekt.gradle.invoke.DebugArgument
import dev.detekt.gradle.invoke.DetektInvoker
import dev.detekt.gradle.invoke.DetektWorkAction
import dev.detekt.gradle.invoke.DisableDefaultRuleSetArgument
import dev.detekt.gradle.invoke.ExplicitApiArgument
import dev.detekt.gradle.invoke.FreeArgs
import dev.detekt.gradle.invoke.FriendPathArgs
import dev.detekt.gradle.invoke.InputArgument
import dev.detekt.gradle.invoke.JdkHomeArgument
import dev.detekt.gradle.invoke.JvmTargetArgument
import dev.detekt.gradle.invoke.LanguageVersionArgument
import dev.detekt.gradle.invoke.MultiPlatformEnabledArgument
import dev.detekt.gradle.invoke.NoJdkArgument
import dev.detekt.gradle.invoke.OptInArguments
import dev.detekt.gradle.invoke.ParallelArgument
import dev.detekt.gradle.plugin.isWorkerApiEnabled
import org.gradle.api.Incubating
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileTree
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Console
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.SourceTask
import org.gradle.api.tasks.TaskAction
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.workers.WorkerExecutor
import javax.inject.Inject

/**
 * Gradle task that runs detekt and writes all current findings into a baseline file.
 *
 * Findings recorded in the baseline are suppressed in subsequent [Detekt] runs, so the task is typically
 * used to adopt detekt on an existing code base. The plugin registers one instance per source set
 * (for example `detektBaselineMain`), configured from the project's
 * [dev.detekt.gradle.extensions.DetektExtension].
 */
@CacheableTask
abstract class DetektCreateBaselineTask @Inject constructor(
    private val workerExecutor: WorkerExecutor,
    private val providers: ProviderFactory,
) : SourceTask() {

    init {
        description = "Creates a detekt baseline on the given --baseline path."
        group = LifecycleBasePlugin.VERIFICATION_GROUP
    }

    /** The baseline file that this task writes the current findings to. */
    @get:OutputFile
    abstract val baseline: RegularFileProperty

    /** The detekt configuration file(s) used for the analysis. */
    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val config: ConfigurableFileCollection

    /** The classpath containing the detekt CLI used to run the analysis. */
    @get:Classpath
    abstract val detektClasspath: ConfigurableFileCollection

    /** The classpath containing additional detekt rule set plugins to load. */
    @get:Classpath
    abstract val pluginClasspath: ConfigurableFileCollection

    /** The classpath of the analyzed sources, used to enable type resolution. */
    @get:Classpath
    @get:Optional
    abstract val classpath: ConfigurableFileCollection

    /** Paths to friend modules that grant access to their `internal` declarations during analysis. */
    @get:Internal
    abstract val friendPaths: ConfigurableFileCollection

    /** Whether to print debug output during task execution. */
    @get:Console
    abstract val debug: Property<Boolean>

    /** Whether to build the abstract syntax tree in parallel. */
    @get:Internal
    abstract val parallel: Property<Boolean>

    /** Whether to disable all default detekt rule sets and only run custom rules. */
    @get:Input
    @get:Optional
    abstract val disableDefaultRuleSets: Property<Boolean>

    /** Whether to apply the [config] files on top of detekt's default configuration. */
    @get:Input
    @get:Optional
    abstract val buildUponDefaultConfig: Property<Boolean>

    /** Whether the build should still succeed when detekt reports issues. */
    @get:Input
    @get:Optional
    abstract val ignoreFailures: Property<Boolean>

    /** Whether to enable all available rules, including those disabled by default. */
    @get:Input
    @get:Optional
    abstract val allRules: Property<Boolean>

    /** The list of opt-in requirement markers to acknowledge during analysis. */
    @get:Input
    abstract val optIn: ListProperty<String>

    /** Whether to exclude the JDK from the analysis classpath. */
    @get:Input
    abstract val noJdk: Property<Boolean>

    /** Whether the analyzed sources are part of a Kotlin Multiplatform project. */
    @get:Input
    abstract val multiPlatformEnabled: Property<Boolean>

    /** Whether rules that support auto correction are allowed to modify the analyzed source files. */
    @get:Input
    @get:Optional
    abstract val autoCorrect: Property<Boolean>

    /**
     * Respect only the file path for incremental build. Using @InputFile respects both file path and content.
     */
    @get:Input
    @get:Optional
    abstract val basePath: Property<String>

    /** The target JVM version of the analyzed sources, for example `1.8` or `17`. */
    @get:Input
    @get:Optional
    abstract val jvmTarget: Property<String>

    /** The Kotlin API version used when analyzing the sources, for example `2.0`. */
    @get:Input
    @get:Optional
    abstract val apiVersion: Property<String>

    /** The Kotlin language version used when analyzing the sources, for example `2.0`. */
    @get:Input
    @get:Optional
    abstract val languageVersion: Property<String>

    /** The JDK home used to resolve the JDK classes referenced by the analyzed sources. */
    @get:Internal
    abstract val jdkHome: DirectoryProperty

    /** Additional compiler arguments passed through to the Kotlin compiler when analyzing the sources. */
    @get:Input
    @get:Incubating
    abstract val freeCompilerArgs: ListProperty<String>

    @get:Input
    @get:Optional
    internal abstract val explicitApi: Property<String>

    @get:Internal
    internal val arguments
        get() = listOf(
            CreateBaselineArgument,
            ClasspathArgument(classpath),
            ApiVersionArgument(apiVersion.orNull),
            LanguageVersionArgument(languageVersion.orNull),
            JvmTargetArgument(jvmTarget.orNull),
            JdkHomeArgument(jdkHome),
            BaselineArgument(baseline.get()),
            InputArgument(source),
            ConfigArgument(config),
            DebugArgument(debug.get()),
            ParallelArgument(parallel.get()),
            BuildUponDefaultConfigArgument(buildUponDefaultConfig.get()),
            AutoCorrectArgument(autoCorrect.get()),
            AllRulesArgument(allRules.get()),
            BasePathArgument(basePath.orNull),
            DisableDefaultRuleSetArgument(disableDefaultRuleSets.get()),
            FreeArgs(freeCompilerArgs.get()),
            OptInArguments(optIn.get()),
            FriendPathArgs(friendPaths),
            NoJdkArgument(noJdk.get()),
            ExplicitApiArgument(explicitApi.orNull),
            MultiPlatformEnabledArgument(multiPlatformEnabled.get()),
        ).flatMap(CliArgument::toArgument)
            .plus("-no-stdlib")
            .plus("-no-reflect")

    /** The Kotlin source files analyzed by this task. */
    @InputFiles
    @SkipWhenEmpty
    @IgnoreEmptyDirectories
    @PathSensitive(PathSensitivity.RELATIVE)
    override fun getSource(): FileTree = super.getSource()

    /** Runs the detekt analysis and writes all current findings into the [baseline] file. */
    @TaskAction
    fun baseline() {
        if (providers.isWorkerApiEnabled()) {
            logger.info("Executing $name using Worker API")
            val workQueue = workerExecutor.processIsolation()

            workQueue.submit(DetektWorkAction::class.java) { workParameters ->
                workParameters.arguments.set(arguments)
                workParameters.classpath.setFrom(detektClasspath, pluginClasspath)
                workParameters.ignoreFailures.set(ignoreFailures)
                workParameters.taskName.set(name)
            }
        } else {
            logger.info("Executing $name using DetektInvoker")
            DetektInvoker.create().invokeCli(
                arguments = arguments,
                ignoreFailures = ignoreFailures.get(),
                classpath = detektClasspath.plus(pluginClasspath).files,
                taskName = name
            )
        }
    }
}
