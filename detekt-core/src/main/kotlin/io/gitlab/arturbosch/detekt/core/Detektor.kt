package io.gitlab.arturbosch.detekt.core

import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
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
import org.jetbrains.kotlin.cli.jvm.config.JavaSourceRoot
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.idea.resolve.ResolutionFacade
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.LazyTopDownAnalyzer
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
		resolver.generate()

		processors.forEach { it.onStart(ktFiles) }
		val futures = ktFiles.map { file ->
			runAsync {
				processors.forEach { it.onProcess(file) }
				file.analyze().apply {
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

	private fun KtFile.analyze(): List<Pair<String, List<Finding>>> = providers
			.mapNotNull { it.buildRuleset(config) }
			.sortedBy { it.id }
			.distinctBy { it.id }
			.map { rule -> rule.id to rule.accept(this) }
}

class DokkaGenerator(val classpath: List<String>,
					 val sources: List<SourceRoot>) {

	fun generate() {
		appendSourceModule(sources)
	}

	private fun appendSourceModule(sourceRoots: List<SourceRoot>) {
		val sourcePaths = sourceRoots.map { it.path }
		val environment = createAnalysisEnvironment(sourcePaths)

		println("Sources: ${sourcePaths.joinToString()}")
		println("Classpath: ${environment.classpath.joinToString()}")

		println("Analysing sources and libraries... ")

		val coreEnvironment: KotlinCoreEnvironment = environment.createCoreEnvironment()
		val dokkaResolutionFacade = environment.createResolutionFacade(coreEnvironment)
		buildDocumentationModule(coreEnvironment = coreEnvironment, resolutionFacade = dokkaResolutionFacade)

		Disposer.dispose(environment)
	}

	fun createAnalysisEnvironment(sourcePaths: List<String>): AnalysisEnvironment {
		val environment = AnalysisEnvironment(DokkaMessageCollector())

		environment.apply {
			addClasspath(PathUtil.getJdkClassesRootsFromCurrentJre())
//			addClasspath(PathUtil.getKotlinPathsForCompiler().stdlibPath)
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

fun buildDocumentationModule(filesToDocumentFilter: (PsiFile) -> Boolean = { file -> true },
							 coreEnvironment: KotlinCoreEnvironment,
							 resolutionFacade: ResolutionFacade) {

	val fragmentFiles = coreEnvironment.getSourceFiles().filter(filesToDocumentFilter)
	val analyzer = resolutionFacade.getFrontendService(LazyTopDownAnalyzer::class.java)
//	analyzer.analyzeDeclarations()
//	val declarations1 = analyzer.analyze
	analyzer.analyzeDeclarations(TopDownAnalysisMode.TopLevelDeclarations, fragmentFiles)
//	println("declared classes: " + declarations.declaredClasses)

//	val fragments = fragmentFiles
//			.map { resolutionFacade.resolveSession.getPackageFragment(it.packageFqName) }
//			.filterNotNull()
//			.distinct()

//	val packageDocs = injector.getInstance(PackageDocs::class.java)
//	for (include in includes) {
//		packageDocs.parse(include, fragments)
//	}
//	if (documentationModule.content.isEmpty()) {
//		documentationModule.updateContent {
//			for (node in packageDocs.moduleContent.children) {
//				append(node)
//			}
//		}
//	}

//	with(injector.getInstance(DocumentationBuilder::class.java)) {
//		documentationModule.appendFragments(fragments, packageDocs.packageContent,
//				injector.getInstance(PackageDocumentationBuilder::class.java))
//	}

//	val javaFiles = coreEnvironment.getJavaSourceFiles().filter(filesToDocumentFilter)
//	with(injector.getInstance(JavaDocumentationBuilder::class.java)) {
//		javaFiles.map { appendFile(it, documentationModule, packageDocs.packageContent) }
//	}
}

//fun buildDocumentationModule(filesToDocumentFilter: (PsiFile) -> Boolean = { file -> true }) {
//
//	val fragmentFiles = coreEnvironment.getSourceFiles().filter(filesToDocumentFilter)
//
//	val resolutionFacade = injector.getInstance(DokkaResolutionFacade::class.java)
//	val analyzer = resolutionFacade.getFrontendService(LazyTopDownAnalyzer::class.java)
//	analyzer.analyzeDeclarations(TopDownAnalysisMode.TopLevelDeclarations, fragmentFiles)
//
//	val javaFiles = coreEnvironment.getJavaSourceFiles().filter(filesToDocumentFilter)
//}


fun KotlinCoreEnvironment.getJavaSourceFiles(): List<PsiJavaFile> {
	val sourceRoots = configuration.get(JVMConfigurationKeys.CONTENT_ROOTS)
			?.filterIsInstance<JavaSourceRoot>()
			?.map { it.file }
			?: listOf()

	val result = arrayListOf<PsiJavaFile>()
	val localFileSystem = VirtualFileManager.getInstance().getFileSystem("file")
	sourceRoots.forEach { sourceRoot ->
		sourceRoot.absoluteFile.walkTopDown().forEach {
			val vFile = localFileSystem.findFileByPath(it.path)
			if (vFile != null) {
				val psiFile = PsiManager.getInstance(project).findFile(vFile)
				if (psiFile is PsiJavaFile) {
					result.add(psiFile)
				}
			}
		}
	}
	return result
}

class SourceRoot(path: String) {
	val path: String = File(path).absolutePath

	companion object {
		fun parseSourceRoot(sourceRoot: String): SourceRoot {
			val components = sourceRoot.split("::", limit = 2)
			return SourceRoot(components.last())
		}
	}
}
