package io.gitlab.arturbosch.detekt.rules.style

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFunctionType
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.types.typeUtil.isUnit

/**
 * This rule detects and reports usages of magic numbers in the code. Prefer defining constants with clear names
 * describing what the magic number means.
 *
 * <noncompliant>
 * class User {
 *
 *     fun checkName(name: String) {
 *         if (name.length > 42) {
 *             throw IllegalArgumentException("username is too long")
 *         }
 *         // ...
 *     }
 * }
 * </noncompliant>
 *
 * <compliant>
 *
 * class User {
 *
 *     fun checkName(name: String) {
 *         if (name.length > MAX_USERNAME_SIZE) {
 *             throw IllegalArgumentException("username is too long")
 *         }
 *         // ...
 *     }
 *
 *     companion object {
 *         private const val MAX_USERNAME_SIZE = 42
 *     }
 * }
 * </compliant>
 *
 * @active since v1.0.0
 */
class UnnecessaryTypeArgument : Rule() {
//    * Derived from https://github.com/JetBrains/kotlin/blob/62a1ea643abb36d4f5a8dd8cd4f47cf8d9fbd312/idea/src/org/jetbrains/kotlin/idea/intentions/RemoveExplicitTypeIntention.kt
//    * Copyright 2010-2015 JetBrains s.r.o. used under terms of the Apache License, version 2.0

    // TODO: add description
    override val issue = Issue(
        "UnnecessaryTypeArgument", Severity.Style,
        "TODO",
        Debt.FIVE_MINS
    )

    override fun visitParameter(parameter: KtParameter) {
        super.visitParameter(parameter)

        val typeReference = parameter.typeReference ?: return
        if (typeReference.annotationEntries.isNotEmpty()) return

        if (parameter.isLoopParameter) report(CodeSmell(issue, Entity.from(parameter), "loop parameter"))
        if (parameter.isSetterParameter) report(CodeSmell(issue, Entity.from(parameter), "setter parameter"))
    }

    override fun visitProperty(property: KtProperty) {
        super.visitProperty(property)

        val typeReference = property.typeReference ?: return
        if (typeReference.annotationEntries.isNotEmpty()) return

        if (property.getter != null) {
            report(CodeSmell(issue, Entity.from(property), "unnecessary type argument on property"))
        }
    }

    override fun visitNamedFunction(function: KtNamedFunction) {
        super.visitNamedFunction(function)
        if (bindingContext == BindingContext.EMPTY) return
        val typeReference = function.typeReference ?: return
        if (typeReference.annotationEntries.isNotEmpty()) return
        val descriptor = bindingContext[BindingContext.DECLARATION_TO_DESCRIPTOR, function] as? FunctionDescriptor ?: return
        if (function.hasBlockBody() && descriptor.returnType?.isUnit()?.not() != false) return
        if (!redundantTypeSpecification(function, function.initializer)) return

        report(CodeSmell(issue, Entity.from(function), "Unnecessary type argument on function"))
    }

    private fun redundantTypeSpecification(element: KtCallableDeclaration, initializer: KtExpression?): Boolean {
        if (initializer == null) return true
        if (initializer !is KtLambdaExpression && initializer !is KtNamedFunction) return true
        val functionType = element.typeReference?.typeElement as? KtFunctionType ?: return true
        if (functionType.receiver != null) return false
        if (functionType.parameters.isEmpty()) return true
        val valueParameters = when (initializer) {
            is KtLambdaExpression -> initializer.valueParameters
            is KtNamedFunction -> initializer.valueParameters
            else -> emptyList()
        }
        return valueParameters.isNotEmpty() && valueParameters.none { it.typeReference == null }
    }

    private val KtParameter.isSetterParameter get() = (parent.parent as? KtPropertyAccessor)?.isSetter ?: false
}
