package io.gitlab.arturbosch.detekt.core

import com.intellij.core.CoreApplicationEnvironment
import com.intellij.core.CoreModuleManager
import com.intellij.mock.MockComponentManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderEnumerationHandler
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.analyzer.AnalysisResult
import org.jetbrains.kotlin.analyzer.AnalyzerFacade
import org.jetbrains.kotlin.analyzer.ModuleContent
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.analyzer.ResolverForModule
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.TopDownAnalyzerFacadeForJVM
import org.jetbrains.kotlin.cli.jvm.config.JavaSourceRoot
import org.jetbrains.kotlin.cli.jvm.config.addJavaSourceRoot
import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoot
import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoots
import org.jetbrains.kotlin.cli.jvm.config.jvmClasspathRoots
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.ContentRoot
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.config.KotlinSourceRoot
import org.jetbrains.kotlin.config.addKotlinSourceRoot
import org.jetbrains.kotlin.container.getService
import org.jetbrains.kotlin.context.ProjectContext
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.idea.resolve.ResolutionFacade
import org.jetbrains.kotlin.load.java.structure.impl.JavaClassImpl
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.platform.JvmBuiltIns
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.CompilerEnvironment
import org.jetbrains.kotlin.resolve.MultiTargetPlatform
import org.jetbrains.kotlin.resolve.jvm.JvmAnalyzerFacade
import org.jetbrains.kotlin.resolve.jvm.JvmPlatformParameters
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.resolve.lazy.ResolveSession
import java.io.File

/**
 * Kotlin as a service entry point
 *
 * Configures environment, analyses files and provides facilities to perform code processing without emitting bytecode
 *
 * $messageCollector: required by compiler infrastructure and will receive all compiler messages
 * $body: optional and can be used to configure environment without creating local variable
 */
class AnalysisEnvironment(val messageCollector: MessageCollector) : Disposable {
	val configuration = CompilerConfiguration()

	init {
		configuration.put(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, messageCollector)
	}

	fun createCoreEnvironment(): KotlinCoreEnvironment {
		System.setProperty("idea.io.use.fallback", "true") //otherwise will complain bin/idea.properties is missing
		val environment = KotlinCoreEnvironment.createForProduction(this, configuration, EnvironmentConfigFiles.JVM_CONFIG_FILES)
		val projectComponentManager = environment.project as MockComponentManager

		val projectFileIndex = CoreProjectFileIndex(environment.project,
				environment.configuration.getList(JVMConfigurationKeys.CONTENT_ROOTS))

		val moduleManager = object : CoreModuleManager(environment.project, this) {
			override fun getModules(): Array<out Module> = arrayOf(projectFileIndex.module)
		}

		CoreApplicationEnvironment.registerComponentInstance(projectComponentManager.picoContainer,
				ModuleManager::class.java, moduleManager)

		Extensions.registerAreaClass("IDEA_MODULE", null)
		CoreApplicationEnvironment.registerExtensionPoint(Extensions.getRootArea(),
				OrderEnumerationHandler.EP_NAME, OrderEnumerationHandler.Factory::class.java)

		projectComponentManager.registerService(ProjectFileIndex::class.java,
				projectFileIndex)
		projectComponentManager.registerService(ProjectRootManager::class.java,
				CoreProjectRootManager(projectFileIndex))
		return environment
	}

	fun createSourceModuleSearchScope(project: Project, sourceFiles: List<KtFile>): GlobalSearchScope {
		// TODO: Fix when going to implement dokka for JS
		return TopDownAnalyzerFacadeForJVM.newModuleSearchScope(project, sourceFiles)
	}


	fun createResolutionFacade(environment: KotlinCoreEnvironment): DokkaResolutionFacade {

		val projectContext = ProjectContext(environment.project)
		val sourceFiles = environment.getSourceFiles()


		val library = object : ModuleInfo {
			override val name: Name = Name.special("<library>")
			override fun dependencies(): List<ModuleInfo> = listOf(this)
		}
		val module = object : ModuleInfo {
			override val name: Name = Name.special("<module>")
			override fun dependencies(): List<ModuleInfo> = listOf(this, library)
		}

		val sourcesScope = createSourceModuleSearchScope(environment.project, sourceFiles)

		val builtIns = JvmBuiltIns(projectContext.storageManager)

		val resolverForProject = AnalyzerFacade.setupResolverForProject(
				"Dokka",
				projectContext,
				listOf(library, module),
				{ JvmAnalyzerFacade },
				{
					when (it) {
						library -> ModuleContent(emptyList(), GlobalSearchScope.notScope(sourcesScope))
						module -> ModuleContent(sourceFiles, sourcesScope)
						else -> throw IllegalArgumentException("Unexpected module info")
					}
				},
				JvmPlatformParameters {
					val file = (it as JavaClassImpl).psi.containingFile.virtualFile
					if (file in sourcesScope)
						module
					else
						library
				},
				CompilerEnvironment,
				packagePartProviderFactory = {
					info, content -> environment.createPackagePartProvider(content.moduleContentScope)
				},
				builtIns = builtIns,
				modulePlatforms = { MultiTargetPlatform.Specific("JVM") }
		)

		resolverForProject.resolverForModule(library) // Required before module to initialize library properly
		val resolverForModule = resolverForProject.resolverForModule(module)
		val moduleDescriptor = resolverForProject.descriptorForModule(module)
		builtIns.initialize(moduleDescriptor, true)
		return DokkaResolutionFacade(environment.project, moduleDescriptor, resolverForModule)
	}

	/**
	 * Classpath for this environment.
	 */
	val classpath: List<File>
		get() = configuration.jvmClasspathRoots

	/**
	 * Adds list of paths to classpath.
	 * $paths: collection of files to add
	 */
	fun addClasspath(paths: List<File>) {
		configuration.addJvmClasspathRoots(paths)
	}

	/**
	 * Adds path to classpath.
	 * $path: path to add
	 */
	fun addClasspath(path: File) {
		configuration.addJvmClasspathRoot(path)
	}

	/**
	 * List of source roots for this environment.
	 */
	val sources: List<String>
		get() = configuration.get(JVMConfigurationKeys.CONTENT_ROOTS)
				?.filterIsInstance<KotlinSourceRoot>()
				?.map { it.path } ?: emptyList()

	/**
	 * Adds list of paths to source roots.
	 * $list: collection of files to add
	 */
	fun addSources(list: List<String>) {
		list.forEach {
			configuration.addKotlinSourceRoot(it)
			val file = File(it)
			if (file.isDirectory || file.extension == ".java") {
				configuration.addJavaSourceRoot(file)
			}
		}
	}

	fun addRoots(list: List<ContentRoot>) {
		configuration.addAll(JVMConfigurationKeys.CONTENT_ROOTS, list)
	}

	/**
	 * Disposes the environment and frees all associated resources.
	 */
	override fun dispose() {
		Disposer.dispose(this)
	}
}

fun contentRootFromPath(path: String): ContentRoot {
	val file = File(path)
	return if (file.extension == "java") JavaSourceRoot(file, null) else KotlinSourceRoot(path)
}


class DokkaResolutionFacade(override val project: Project,
							override val moduleDescriptor: ModuleDescriptor,
							val resolverForModule: ResolverForModule) : ResolutionFacade {
	override fun <T : Any> tryGetFrontendService(element: PsiElement, serviceClass: Class<T>): T? {
		throw UnsupportedOperationException()
	}

	override fun resolveToDescriptor(declaration: KtDeclaration, bodyResolveMode: BodyResolveMode): DeclarationDescriptor {
		return resolveSession.resolveToDescriptor(declaration)
	}

	override fun analyze(elements: Collection<KtElement>, bodyResolveMode: BodyResolveMode): BindingContext {
		throw UnsupportedOperationException()
	}

	val resolveSession: ResolveSession get() = getFrontendService(ResolveSession::class.java)

	override fun analyze(element: KtElement, bodyResolveMode: BodyResolveMode): BindingContext {
		throw UnsupportedOperationException()
	}

	override fun analyzeFullyAndGetResult(elements: Collection<KtElement>): AnalysisResult {
		throw UnsupportedOperationException()
	}

	override fun <T : Any> getFrontendService(element: PsiElement, serviceClass: Class<T>): T {
		throw UnsupportedOperationException()
	}

	override fun <T : Any> getFrontendService(serviceClass: Class<T>): T {
		return resolverForModule.componentProvider.getService(serviceClass)
	}

	override fun <T : Any> getFrontendService(moduleDescriptor: ModuleDescriptor, serviceClass: Class<T>): T {
		return resolverForModule.componentProvider.getService(serviceClass)
	}

	override fun <T : Any> getIdeService(serviceClass: Class<T>): T {
		throw UnsupportedOperationException()
	}

}
