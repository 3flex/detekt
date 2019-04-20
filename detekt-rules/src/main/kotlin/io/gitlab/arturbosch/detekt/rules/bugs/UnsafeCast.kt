package io.gitlab.arturbosch.detekt.rules.bugs

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.builtins.jvm.JavaToKotlinClassMap
import org.jetbrains.kotlin.psi.KtBinaryExpressionWithTypeRHS
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.bindingContextUtil.getAbbreviatedTypeOrType
import org.jetbrains.kotlin.resolve.calls.callUtil.getType
import org.jetbrains.kotlin.types.CastDiagnosticsUtil
import org.jetbrains.kotlin.types.checker.KotlinTypeChecker

/**
 * Reports casts which are unsafe. In case the cast is not possible it will throw an exception.
 *
 * <noncompliant>
 * fun foo(s: Any) {
 *     println(s as Int)
 * }
 * </noncompliant>
 *
 * <compliant>
 * fun foo(s: Any) {
 *     println((s as? Int) ?: 0)
 * }
 * </compliant>
 *
 * @author Ivan Balaksha
 * @author Marvin Ramin
 */
class UnsafeCast(config: Config = Config.empty) : Rule(config) {

    override val defaultRuleIdAliases: Set<String> = setOf("UNCHECKED_CAST")

    override val issue: Issue = Issue("UnsafeCast",
            Severity.Defect,
            "Cast operator throws an exception if the cast is not possible.",
            Debt.TWENTY_MINS)

    override fun visitBinaryWithTypeRHSExpression(expression: KtBinaryExpressionWithTypeRHS) {
        super.visitBinaryWithTypeRHSExpression(expression)
        if (bindingContext == BindingContext.EMPTY) return

        val actualType = expression.left.getType(bindingContext) ?: return
        val targetType = expression.right?.typeElement?.getAbbreviatedTypeOrType(bindingContext)

        if (targetType != null && !CastDiagnosticsUtil.isCastPossible(actualType, targetType, JavaToKotlinClassMap)) {
            report(
                CodeSmell(
                    issue, Entity.from(expression),
                    "Cast from ${expression.left.text} to ${expression.right?.text ?: ""} will never succeed."
                )
            )
        }
        if (targetType != null && CastDiagnosticsUtil.isCastErased(actualType, targetType, KotlinTypeChecker.DEFAULT)) {
            report(
                CodeSmell(
                    issue, Entity.from(expression),
//                    "Unchecked cast: `Map<String, *>` to `Map<String, Int>`"
                    "Unchecked cast: `${actualType}` to `${targetType}`."
                )
            )
        }

    }
}
