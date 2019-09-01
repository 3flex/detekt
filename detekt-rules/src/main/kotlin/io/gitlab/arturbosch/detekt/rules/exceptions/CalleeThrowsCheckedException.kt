package io.gitlab.arturbosch.detekt.rules.exceptions

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import io.gitlab.arturbosch.detekt.api.SplitPattern
import io.gitlab.arturbosch.detekt.rules.collectByType
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtThrowExpression

// TODO: fix this description and compliant/non-compliant code examples
/**
 * This rule allows to define functions which should never throw an exception. If a function exists that does throw
 * an exception it will be reported. By default this rule is checking for `toString`, `hashCode`, `equals` and
 * `finalize`. This rule is configurable via the `methodNames` configuration to change the list of functions which
 * should not throw any exceptions.
 *
 * <noncompliant>
 * class Foo {
 *
 *     override fun toString(): String {
 *         throw IllegalStateException() // exception should not be thrown here
 *     }
 * }
 * </noncompliant>
 *
 * @configuration methodNames - methods which should not throw exceptions
 * (default: `'toString,hashCode,equals,finalize'`)
 */
class CalleeThrowsCheckedException : Rule() {

    // TODO fix issue description (Debt won't be 20 mins... also, what is the debt? This isn't something that can or
    //  should be fixed, it's just identifying info for the user...
    override val issue = Issue("CalleeThrowsCheckedException", Severity.CodeSmell,
            "This method is not expected to throw exceptions. This can cause weird behavior.",
            Debt.TWENTY_MINS)

    private val methods = SplitPattern(valueOrDefault(METHOD_NAMES, ""))

    override fun visitNamedFunction(function: KtNamedFunction) {
        if (isPotentialMethod(function) && hasThrowExpression(function.bodyExpression)) {
            report(CodeSmell(issue, Entity.from(function), issue.description))
        }
    }

    private fun isPotentialMethod(function: KtNamedFunction) = methods.any(function.name)

    private fun hasThrowExpression(declaration: KtExpression?) =
            declaration?.collectByType<KtThrowExpression>()?.any() == true

    companion object {
        const val METHOD_NAMES = "methodNames"
    }
}
