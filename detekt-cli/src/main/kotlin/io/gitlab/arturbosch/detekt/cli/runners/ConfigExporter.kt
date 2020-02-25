package io.gitlab.arturbosch.detekt.cli.runners

import io.gitlab.arturbosch.detekt.cli.ClasspathResourceConverter
import io.gitlab.arturbosch.detekt.cli.CliArgs
import io.gitlab.arturbosch.detekt.cli.DEFAULT_CONFIG
import java.nio.file.Files
import java.nio.file.Paths

class ConfigExporter(private val arguments: CliArgs) : Executable {

    override fun execute() {
        val configPath = Paths.get(arguments.config ?: DEFAULT_CONFIG)
        val defaultConfig = Paths.get(ClasspathResourceConverter().convert(DEFAULT_CONFIG).toURI())
        Files.copy(configPath, defaultConfig)
        println("Successfully copied default config to ${configPath.toAbsolutePath()}")
    }
}
