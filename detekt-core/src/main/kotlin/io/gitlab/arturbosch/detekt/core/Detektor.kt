package io.gitlab.arturbosch.detekt.core

import com.intellij.openapi.util.Disposer
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Detektion
import io.gitlab.arturbosch.detekt.api.FileProcessListener
import io.gitlab.arturbosch.detekt.api.Finding
import io.gitlab.arturbosch.detekt.api.FindingsForFile
import io.gitlab.arturbosch.detekt.api.Notification
import io.gitlab.arturbosch.detekt.api.RuleSetProvider
import io.gitlab.arturbosch.detekt.api.toMergedMap
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocation
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.idea.resolve.ResolutionFacade
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.LazyTopDownAnalyzer
import org.jetbrains.kotlin.resolve.TopDownAnalysisContext
import org.jetbrains.kotlin.resolve.TopDownAnalysisMode
import org.jetbrains.kotlin.utils.PathUtil
import java.io.File

/**
 * @author Artur Bosch
 */
class Detektor(private val settings: ProcessingSettings,
			   private val providers: List<RuleSetProvider>,
			   private val processors: List<FileProcessListener> = emptyList()) {

	private val config: Config = settings.config
	private val modifier: KtFileModifier = KtFileModifier(settings.project)
	private val notifications: MutableList<Notification> = mutableListOf()

	fun run(compiler: KtTreeCompiler = KtTreeCompiler.instance(settings)): Detektion = run(compiler.compile())

	fun run(ktFiles: List<KtFile>): Detektion = withExecutor {

		val paths = ktFiles.map { SourceRoot(it.getUserData(KtCompiler.RELATIVE_PATH)!!) }
		println(paths)

		//TODO: call the resolver
		val resolver = DokkaGenerator(
				settings.classpath,
				paths)
				//TODO: get sources (the actual files themselves)
		val context = resolver.generate()

		processors.forEach { it.onStart(ktFiles) }
		val futures = ktFiles.map { file ->
			runAsync {
				processors.forEach { it.onProcess(file) }
				file.analyze(context).apply {
					processors.forEach { it.onProcessComplete(file, FindingsForFile(this)) }
				}
			}
		}
		val findings = awaitAll(futures).flatMap { it }.toMergedMap()

		if (config.valueOrDefault("autoCorrect", false)) {
			modifier.saveModifiedFiles(ktFiles) {
				notifications.add(it)
			}
		}

		DetektResult(findings.toSortedMap(), notifications).apply {
			processors.forEach { it.onFinish(ktFiles, this) }
		}
	}

	private fun KtFile.analyze(context: TopDownAnalysisContext): List<Pair<String, List<Finding>>> = providers
			.mapNotNull { it.buildRuleset(config) }
			.sortedBy { it.id }
			.distinctBy { it.id }
			.map { rule -> rule.id to rule.accept(this, context) }
}

class DokkaGenerator(val classpath: List<String>,
					 val sources: List<SourceRoot>) {

	var analysisContext: TopDownAnalysisContext? = null

	fun generate(): TopDownAnalysisContext {
		appendSourceModule(sources)
		return analysisContext!!
	}

	private fun appendSourceModule(sourceRoots: List<SourceRoot>) {
		val sourcePaths = sourceRoots.map { it.path }
		val environment = createAnalysisEnvironment(sourcePaths)

		println("Sources: ${sourcePaths.joinToString()}")
		println("Classpath: ${environment.classpath.joinToString()}")

		println("Analysing sources and libraries... ")

		val coreEnvironment: KotlinCoreEnvironment = environment.createCoreEnvironment()
		val dokkaResolutionFacade = environment.createResolutionFacade(coreEnvironment)
		analysisContext = buildDocumentationModule(coreEnvironment = coreEnvironment, resolutionFacade =
		dokkaResolutionFacade)

		Disposer.dispose(environment)
	}

	fun createAnalysisEnvironment(sourcePaths: List<String>): AnalysisEnvironment {
		val environment = AnalysisEnvironment(DokkaMessageCollector())

		environment.apply {
			addClasspath(PathUtil.getJdkClassesRootsFromCurrentJre())
			for (element in this@DokkaGenerator.classpath) {
				addClasspath(File(element))
			}

			addSources(sourcePaths)
		}

		return environment
	}
}

class DokkaMessageCollector : MessageCollector {
	override fun clear() {
		seenErrors = false
	}

	private var seenErrors = false

	override fun report(severity: CompilerMessageSeverity, message: String, location: CompilerMessageLocation?) {
		if (severity == CompilerMessageSeverity.ERROR) {
			seenErrors = true
		}
	}

	override fun hasErrors() = seenErrors
}

fun buildDocumentationModule(coreEnvironment: KotlinCoreEnvironment,
							 resolutionFacade: ResolutionFacade): TopDownAnalysisContext {

	val fragmentFiles = coreEnvironment.getSourceFiles()
	val analyzer = resolutionFacade.getFrontendService(LazyTopDownAnalyzer::class.java)

	return analyzer.analyzeDeclarations(TopDownAnalysisMode.TopLevelDeclarations, fragmentFiles)
//	declarations.secondaryConstructors

//	return declarations
}

class SourceRoot(path: String) {
	val path: String = File(path).absolutePath
}
