package io.gitlab.arturbosch.detekt.rules.style

import io.gitlab.arturbosch.detekt.test.KtTestCompiler
import io.gitlab.arturbosch.detekt.test.compileAndLint
import io.gitlab.arturbosch.detekt.test.compileAndLintWithContext
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.com.intellij.openapi.Disposable
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object UnnecessaryTypeArgumentSpec : Spek({

    val subject by memoized { UnnecessaryTypeArgument() }

    var environment: KotlinCoreEnvironment? = null
    lateinit var disposable: Disposable

    beforeEachTest {
        val pair = KtTestCompiler.createEnvironment()
        disposable = pair.first
        environment = pair.second
    }

    describe("UnnecessaryTypeArgument rule") {

        context("parameters") {
            it("unnecessary type argument in loop parameter") {
                val code = """
                    fun intLoop() {
                        val ints = listOf(1,2,3)
                        for (item: Int in ints) {
                            println("Did a loop")
                        }
                    }
                """
                assertThat(subject.compileAndLint(code)).hasSize(1)
            }

            it("unnecessary type argument in setter parameter") {
                val code = """
                    var strangeBool
                        get() = true
                        set(value: Boolean) { value }
                """
                assertThat(subject.compileAndLint(code)).hasSize(1)
            }
        }

        context("properties") {
            it("picks up explicit type on property with getter") {
                val code = "val strangeBool: Boolean get() = true"
                assertThat(subject.compileAndLint(code)).hasSize(1)
            }
        }

        context("functions") {
            it("redundant Unit type on function") {
                val code = """
                    fun foo(): Unit = bar()
                """
                assertThat(subject.compileAndLintWithContext(environment!!, code)).hasSize(1)
            }
        }
    }

    afterEachTest {
        Disposer.dispose(disposable)
        environment = null
    }
})
