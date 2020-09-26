package io.github.detekt.custom

import io.gitlab.arturbosch.detekt.rules.setupKotlinEnvironment
import io.gitlab.arturbosch.detekt.test.compileAndLintWithContext
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object NotNullIfParsedSpec : Spek({
    setupKotlinEnvironment()

    val subject by memoized { NotNullIfParsed() }
    val env: KotlinCoreEnvironment by memoized()

    describe("NotNullIfParsedSpec") {

        context("// TODO") { // TODO

            it("allows strings, paths and files by default") {
                val code = """
                    package org.jetbrains.kotlin.psi

                    @Retention(AnnotationRetention.RUNTIME)
                    @Target(AnnotationTarget.FUNCTION)
                    annotation class IfNotParsed

                    @IfNotParsed
                    fun getValue(): String? {
                        return "String"
                    }

                    val x: String? = getValue()
                """.trimIndent()

                assertThat(subject.compileAndLintWithContext(env, code)).hasSize(1)
            }

            it("fdsat") {
                val code = """
                    package org.jetbrains.kotlin.psi

                    @Retention(AnnotationRetention.RUNTIME)
                    @Target(AnnotationTarget.FUNCTION)
                    annotation class IfNotParsed

                    @IfNotParsed
                    fun getValue(): String? {
                        return "String"
                    }

                    val x: String = getValue() as? String ?: "something"
                """.trimIndent()

                assertThat(subject.compileAndLintWithContext(env, code)).isEmpty()
            }

        }
    }
})
