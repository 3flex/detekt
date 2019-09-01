package io.gitlab.arturbosch.detekt.rules.exceptions

import io.gitlab.arturbosch.detekt.test.TestConfig
import io.gitlab.arturbosch.detekt.test.compileAndLint
import org.assertj.core.api.Assertions.assertThat
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class CalleeThrowsCheckedExceptionSpec : Spek({
    val subject by memoized { CalleeThrowsCheckedException() }

    describe("ExceptionRaisedInUnexpectedLocation rule") {

        it("reports methods raising an unexpected exception") {
            val code = """
                import java.io.FileInputStream

                val something = FileInputStream("string")
            """
            assertThat(subject.compileAndLint(code)).hasSize(5)
        }

        it("reports the configured method") {
            val config = TestConfig(mapOf(ExceptionRaisedInUnexpectedLocation.METHOD_NAMES to "toDo,todo2"))
            val findings = ExceptionRaisedInUnexpectedLocation(config).compileAndLint("""
			fun toDo() {
				throw IllegalStateException()
			}""")
            assertThat(findings).hasSize(1)
        }
    }
})
