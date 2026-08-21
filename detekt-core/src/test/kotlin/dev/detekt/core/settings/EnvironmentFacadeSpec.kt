package dev.detekt.core.settings

import dev.detekt.core.createProcessingSettings
import dev.detekt.test.utils.StringPrintStream
import dev.detekt.test.utils.resourceAsPath
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class EnvironmentFacadeSpec {

    @Test
    fun `prints the compiler configuration in debug mode`() {
        val output = StringPrintStream()

        createProcessingSettings(
            inputPath = resourceAsPath("cases"),
            outputChannel = output,
        ) {
            compiler {
                languageVersion = "2.0"
                apiVersion = "1.9"
            }
        }.use { /* no-op */ }

        assertThat(output.toString())
            .contains("Compiler configuration:")
            .contains("LANGUAGE_VERSION_SETTINGS:")
            .contains("Language = 2.0")
            .contains("API = 1.9")
    }

    @Test
    fun `does not print the compiler configuration when debug mode is disabled`() {
        val output = StringPrintStream()

        createProcessingSettings(
            inputPath = resourceAsPath("cases"),
            outputChannel = output,
        ) {
            logging {
                debug = false
                outputChannel = output
            }
        }.use { /* no-op */ }

        assertThat(output.toString()).doesNotContain("Compiler configuration:")
    }
}
