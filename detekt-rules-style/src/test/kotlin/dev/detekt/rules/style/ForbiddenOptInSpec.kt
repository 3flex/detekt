package dev.detekt.rules.style

import dev.detekt.api.ValueWithReason
import dev.detekt.test.TestConfig
import dev.detekt.test.assertj.assertThat
import dev.detekt.test.junit.KotlinCoreEnvironmentTest
import dev.detekt.test.lintWithContext
import dev.detekt.test.toConfig
import dev.detekt.test.utils.KotlinEnvironmentContainer
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test

@Language("kotlin")
private const val ANNOTAION_DECLARATIONS = """
    package annotations

    @RequiresOptIn(message = "This API is forbidden.")
    @Retention(AnnotationRetention.BINARY)
    @Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
    annotation class ForbiddenApi

    @RequiresOptIn(message = "This API is not allowed.")
    @Retention(AnnotationRetention.BINARY)
    @Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
    annotation class DoNotUseApi

    @RequiresOptIn(message = "This API is ok to be used.")
    @Retention(AnnotationRetention.BINARY)
    @Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
    annotation class AllowedApi
"""

@KotlinCoreEnvironmentTest
class ForbiddenOptInSpec(val env: KotlinEnvironmentContainer) {
    private val optInConfig = TestConfig(
        "markerClasses" to listOf(
            ValueWithReason("ForbiddenApi").toConfig(),
            ValueWithReason("DoNotUseApi", "Do not use!").toConfig(),
        )
    )

    @Test
    fun `should not report violation if no forbidden opt-ins are used`() {
        val code = """
            import annotations.*

            @OptIn(AllowedApi::class)
            fun main() {}
        """.trimIndent()
        val findings = ForbiddenOptIn(optInConfig).lintWithContext(env, code, ANNOTAION_DECLARATIONS)

        assertThat(findings).isEmpty()
    }

    @Test
    fun `should report single forbidden opt-in without a stating a reason`() {
        val code = """
            import annotations.*

            @OptIn(ForbiddenApi::class)
            fun main() {}
        """.trimIndent()
        val findings = ForbiddenOptIn(optInConfig).lintWithContext(env, code, ANNOTAION_DECLARATIONS)

        assertThat(findings).singleElement()
            .hasStartSourceLocation(3, 1)
            .hasMessage("The opt-in `ForbiddenApi` has been forbidden in the detekt config.")
    }

    @Test
    fun `should report single forbidden opt-in showing the reason`() {
        val code = """
            import annotations.*

            @OptIn(DoNotUseApi::class)
            fun main() {}
        """.trimIndent()
        val findings = ForbiddenOptIn(optInConfig).lintWithContext(env, code, ANNOTAION_DECLARATIONS)

        assertThat(findings).singleElement()
            .hasStartSourceLocation(3, 1)
            .hasMessage("The opt-in `DoNotUseApi` has been forbidden: Do not use!")
    }

    @Test
    fun `should report multiple forbidden opt-ins`() {
        val code = """
            import annotations.*

            @OptIn(DoNotUseApi::class, AllowedApi::class, ForbiddenApi::class)
            fun main() {}
        """.trimIndent()
        val findings = ForbiddenOptIn(optInConfig).lintWithContext(env, code, ANNOTAION_DECLARATIONS)

        assertThat(findings)
            .extracting("message")
            .containsExactlyInAnyOrder(
                "The opt-in `DoNotUseApi` has been forbidden: Do not use!",
                "The opt-in `ForbiddenApi` has been forbidden in the detekt config."
            )
    }

    @Test
    fun `should report forbidden opt-in at file level`() {
        val code = """
            @file:OptIn(DoNotUseApi::class)

            import annotations.*

            fun main() {}
        """.trimIndent()
        val findings = ForbiddenOptIn(optInConfig).lintWithContext(env, code, ANNOTAION_DECLARATIONS)

        assertThat(findings).singleElement()
            .hasStartSourceLocation(1, 1)
    }

    @Test
    fun `should report forbidden opt-in referenced by fully qualified name`() {
        val code = """
            @OptIn(annotations.ForbiddenApi::class)
            fun main() {}
        """.trimIndent()
        val findings = ForbiddenOptIn(optInConfig).lintWithContext(env, code, ANNOTAION_DECLARATIONS)

        assertThat(findings).singleElement()
            .hasStartSourceLocation(1, 1)
            .hasMessage("The opt-in `ForbiddenApi` has been forbidden in the detekt config.")
    }

    @Test
    fun `should report forbidden opt-in at file level when referenced by fully qualified name`() {
        val code = """
            @file:OptIn(annotations.DoNotUseApi::class)

            fun main() {}
        """.trimIndent()
        val findings = ForbiddenOptIn(optInConfig).lintWithContext(env, code, ANNOTAION_DECLARATIONS)

        assertThat(findings).singleElement()
            .hasStartSourceLocation(1, 1)
            .hasMessage("The opt-in `DoNotUseApi` has been forbidden: Do not use!")
    }

    @Test
    fun `should report forbidden opt-ins when mixing simple and fully qualified names`() {
        val code = """
            import annotations.AllowedApi
            import annotations.DoNotUseApi

            @OptIn(annotations.ForbiddenApi::class, AllowedApi::class, DoNotUseApi::class)
            fun main() {}
        """.trimIndent()
        val findings = ForbiddenOptIn(optInConfig).lintWithContext(env, code, ANNOTAION_DECLARATIONS)

        assertThat(findings)
            .extracting("message")
            .containsExactlyInAnyOrder(
                "The opt-in `DoNotUseApi` has been forbidden: Do not use!",
                "The opt-in `ForbiddenApi` has been forbidden in the detekt config."
            )
    }

    @Test
    fun `should report when fully qualified marker is configured and source uses simple name`() {
        val fqOptInConfig = TestConfig(
            "markerClasses" to listOf(
                ValueWithReason("annotations.ForbiddenApi").toConfig(),
            )
        )
        val code = """
            import annotations.*

            @OptIn(ForbiddenApi::class)
            fun main() {}
        """.trimIndent()
        val findings = ForbiddenOptIn(fqOptInConfig).lintWithContext(env, code, ANNOTAION_DECLARATIONS)

        assertThat(findings).singleElement()
            .hasStartSourceLocation(3, 1)
            .hasMessage("The opt-in `annotations.ForbiddenApi` has been forbidden in the detekt config.")
    }

    @Test
    fun `should report when fully qualified marker is configured and source uses fully qualified name`() {
        val fqOptInConfig = TestConfig(
            "markerClasses" to listOf(
                ValueWithReason("annotations.ForbiddenApi", "Use a different API.").toConfig(),
            )
        )
        val code = """
            @OptIn(annotations.ForbiddenApi::class)
            fun main() {}
        """.trimIndent()
        val findings = ForbiddenOptIn(fqOptInConfig).lintWithContext(env, code, ANNOTAION_DECLARATIONS)

        assertThat(findings).singleElement()
            .hasStartSourceLocation(1, 1)
            .hasMessage("The opt-in `annotations.ForbiddenApi` has been forbidden: Use a different API.")
    }
}
