package io.gitlab.arturbosch.detekt.api.internal

import io.gitlab.arturbosch.detekt.core.exists
import org.assertj.core.api.Assertions.assertThat
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.nio.file.Path
import java.nio.file.Paths

class KotlinEnvironmentUtilsTest : Spek({

    describe("retrieved kotlin language version") {
        it("should match") {
            val expectedVersionString = System.getProperty("kotlinVersion", "")
                .splitToSequence('.')
                .take(2)
                .joinToString(".")

            val classpathFiles = System.getProperty("testClasspath", "")
                .splitToSequence(';')
                .map { Paths.get(it) }
                .filter(Path::exists)
                .toList()

            val languageVersion = classpathFiles.getKotlinLanguageVersion()
            assertThat(languageVersion).isNotNull
            assertThat(languageVersion?.versionString).isEqualTo(expectedVersionString)
        }
    }
})
