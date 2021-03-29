package io.gitlab.arturbosch.detekt.rules.performance

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import io.gitlab.arturbosch.detekt.rules.isConstant
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

/**
 * Using the forEach method on ranges has a heavy performance cost. Prefer using simple for loops.
 *
 * Benchmarks have shown that using forEach on a range can have a huge performance cost in comparison to
 * simple for loops. Hence in most contexts a simple for loop should be used instead.
 * See more details here: https://sites.google.com/a/athaydes.com/renato-athaydes/posts/kotlinshiddencosts-benchmarks
 * To solve this CodeSmell, the forEach usage should be replaced by a for loop.
 *
 * <noncompliant>
 * (1..10).forEach {
 *     println(it)
 * }
 * (1 until 10).forEach {
 *     println(it)
 * }
 * (10 downTo 1).forEach {
 *     println(it)
 * }
 * </noncompliant>
 *
 * <compliant>
 * for (i in 1..10) {
 *     println(i)
 * }
 * </compliant>
 */
class ConstantBackingField(config: Config = Config.empty) : Rule(config) {
    override val issue = Issue(
        javaClass.simpleName,
        Severity.Performance,
        "Properties with constant values do not benefit from a backing field and can save memory by avoiding it.",
        Debt.FIVE_MINS
    )

    override fun visitProperty(property: KtProperty) {
        super.visitProperty(property)

        if (property.isConstant()) return
        if (property.valOrVarKeyword.textMatches("var")) return
        if (property.isLocal) return

        if ((property.initializer as? KtStringTemplateExpression)?.hasInterpolation() == false) {
            report(CodeSmell(issue, Entity.from(property), issue.description))
        }

        if (property.initializer is KtConstantExpression) {
            report(CodeSmell(issue, Entity.from(property), issue.description))
        }
    }
}
