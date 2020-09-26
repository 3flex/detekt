package io.github.detekt.custom

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.callUtil.getResolvedCall

/**
 * // TODO
 *
 * <noncompliant>
 * // TODO
 * </noncompliant>
 *
 * <compliant>
 * // TODO
 * </compliant>
 */
class NotNullIfParsed(config: Config = Config.empty) : Rule(config) {

    override val issue = Issue(
        javaClass.simpleName,
        Severity.Performance, // TODO
        "Use `checkNullability` function to make this variable value non-null",
        Debt.TEN_MINS
    )

    override fun visitReferenceExpression(expression: KtReferenceExpression) {
        super.visitReferenceExpression(expression)
        if (expression is KtCallExpression) return
        if (bindingContext == BindingContext.EMPTY) return

        if (expression.hasIfNotParsedAnnotation()) {
            report(CodeSmell(issue, Entity.from(expression), "This expression is guaranteed to not be null."))
        }
    }

    private fun KtReferenceExpression.hasIfNotParsedAnnotation() =
        getResolvedCall(bindingContext)?.run {
            resultingDescriptor
                .annotations
                .any { it.fqName == FqName("org.jetbrains.kotlin.psi.IfNotParsed") }
        } ?: false
}
