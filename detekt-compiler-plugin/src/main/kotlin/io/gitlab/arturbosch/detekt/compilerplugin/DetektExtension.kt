package io.gitlab.arturbosch.detekt.compilerplugin

import com.intellij.openapi.project.Project
import io.gitlab.arturbosch.detekt.cli.Args
import io.gitlab.arturbosch.detekt.cli.CliArgs
import io.gitlab.arturbosch.detekt.cli.OutputFacade
import io.gitlab.arturbosch.detekt.cli.createPathFilters
import io.gitlab.arturbosch.detekt.cli.createPlugins
import io.gitlab.arturbosch.detekt.cli.loadConfiguration
import io.gitlab.arturbosch.detekt.cli.parseArguments
import io.gitlab.arturbosch.detekt.core.DetektFacade
import io.gitlab.arturbosch.detekt.core.ProcessingSettings
import org.jetbrains.kotlin.analyzer.AnalysisResult
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.BindingTrace
import org.jetbrains.kotlin.resolve.jvm.extensions.AnalysisHandlerExtension

class ExtensionHandler(private val detektOptions: List<String>) : AnalysisHandlerExtension {
	override fun analysisCompleted(
			project: Project,
			module: ModuleDescriptor,
			bindingTrace: BindingTrace,
			files: Collection<KtFile>
	): AnalysisResult? {
		val args = parseArguments<CliArgs>(detektOptions.toTypedArray()).first
		val settings = createSettings(args)
		val detektion = DetektFacade.create(settings).run(files.toList(), bindingTrace.bindingContext)
		OutputFacade(args, detektion, settings).run()
		return null
	}

	private fun createSettings(arguments: Args): ProcessingSettings {
		with(arguments) {
			val pathFilters = createPathFilters()
			val plugins = createPlugins()
			val config = loadConfiguration()
			return ProcessingSettings(inputPath, config, pathFilters, parallel, disableDefaultRuleSets, plugins)
		}
	}
}
