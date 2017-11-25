package io.gitlab.arturbosch.detekt.compilerplugin

import com.intellij.mock.MockProject
import io.gitlab.arturbosch.detekt.compilerplugin.DetektConfigurationKeys.DETEKT
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CliOptionProcessingException
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey
import org.jetbrains.kotlin.resolve.jvm.extensions.AnalysisHandlerExtension

object DetektConfigurationKeys {
	val DETEKT: CompilerConfigurationKey<String> =
			CompilerConfigurationKey.create("Options to pass to detekt CLI")
}

class DetektCommandLineProcessor : CommandLineProcessor {
	companion object {
		val PLUGIN_ID = "io.gitlab.arturbosch.detekt"
		val DETEKT_OPTION = CliOption("detekt", "<detekt_options>", "Options to pass to detekt CLI", required = true)
	}

	override val pluginId = PLUGIN_ID
	override val pluginOptions = listOf(DETEKT_OPTION)

	override fun processOption(option: CliOption, value: String, configuration: CompilerConfiguration) = when (option) {
		DETEKT_OPTION -> configuration.put(DETEKT, value)
		else -> throw CliOptionProcessingException("Unknown option: ${option.name}")
	}
}

class DetektComponentRegistrar : ComponentRegistrar {
	override fun registerProjectComponents(project: MockProject, configuration: CompilerConfiguration) {
		val detektOptions = configuration.get(DETEKT)?.split(" ") ?: emptyList()
		AnalysisHandlerExtension.registerExtension(project, ExtensionHandler(detektOptions))
	}
}
