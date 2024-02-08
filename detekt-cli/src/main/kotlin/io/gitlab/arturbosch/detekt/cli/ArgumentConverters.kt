package io.gitlab.arturbosch.detekt.cli

import com.beust.jcommander.IStringConverter
import com.beust.jcommander.ParameterException
import org.jetbrains.kotlin.config.LanguageVersion
import java.net.URL
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.notExists

class ExistingPathConverter : IStringConverter<Path> {
    override fun convert(value: String): Path {
        require(value.isNotBlank()) { "Provided path '$value' is empty." }
        val config = Path(value)
        if (config.notExists()) {
            throw ParameterException("Provided path '$value' does not exist!")
        }
        return config
    }
}

class LanguageVersionConverter : IStringConverter<LanguageVersion> {
    override fun convert(value: String): LanguageVersion {
        return requireNotNull(LanguageVersion.fromFullVersionString(value)) {
            val validValues = LanguageVersion.entries.joinToString { it.toString() }
            "\"$value\" passed to --language-version, expected one of [$validValues]"
        }
    }
}

class ClasspathResourceConverter : IStringConverter<URL> {
    override fun convert(resource: String): URL {
        val relativeResource = if (resource.startsWith("/")) resource else "/$resource"
        return javaClass.getResource(relativeResource)
            ?: throw ParameterException("Classpath resource '$resource' does not exist!")
    }
}

class FailureSeverityConverter : IStringConverter<FailureSeverity> {
    override fun convert(value: String): FailureSeverity {
        return FailureSeverity.fromString(value)
    }
}
