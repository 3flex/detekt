package io.gitlab.arturbosch.detekt.formatting

import io.gitlab.arturbosch.detekt.formatting.wrappers.TrailingCommaOnDeclarationSite
import io.gitlab.arturbosch.detekt.test.TestConfig
import io.gitlab.arturbosch.detekt.test.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Some test cases were used directly from KtLint to verify the wrapper rule:
 *
 * https://github.com/pinterest/ktlint/blob/master/ktlint-ruleset-experimental/src/test/kotlin/com/pinterest/ktlint/ruleset/experimental/TrailingCommaRuleTest.kt
 */
class TrailingCommaOnDeclarationSiteSpec {

    @Nested
    inner class `missing comma` {

        @Test
        fun `reports missing comma on field definition`() {
            val code = """
                data class Foo1(val bar: Int)
                data class Foo2(
                   val bar: Int
                )
            """.trimIndent()
            val findings = TrailingCommaOnDeclarationSite(TestConfig(mapOf(ALLOW_TRAILING_COMMA to true))).lint(code)
            assertThat(findings).hasSize(1)
        }
    }
}

private const val ALLOW_TRAILING_COMMA = "allowTrailingComma"
