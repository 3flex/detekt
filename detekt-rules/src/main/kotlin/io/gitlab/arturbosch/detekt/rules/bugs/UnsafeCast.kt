package io.gitlab.arturbosch.detekt.rules.bugs

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.DetektVisitor
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.descriptors.runtime.structure.parameterizedTypeArguments
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.DiagnosticFactoryWithPsiElement
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.diagnostics.rendering.DefaultErrorMessages
import org.jetbrains.kotlin.psi.KtAnnotatedExpression
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtBinaryExpressionWithTypeRHS
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtDestructuringDeclarationEntry
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtExpressionWithLabel
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtModifierList
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtNullableType
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtParameterList
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtTypeParameter
import org.jetbrains.kotlin.psi.KtTypeProjection
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtWhenEntry
import org.jetbrains.kotlin.psi.KtWhenExpression
import org.jetbrains.kotlin.resolve.BindingContext

/**
 * Reports casts that will never succeed.
 *
 * <noncompliant>
 * fun foo(s: String) {
 *     println(s as Int)
 * }
 *
 * fun bar(s: String) {
 *     println(s as? Int)
 * }
 * </noncompliant>
 *
 * <compliant>
 * fun foo(s: Any) {
 *     println(s as Int)
 * }
 * </compliant>
 */
class UnsafeCast(config: Config = Config.empty) : Rule(config) {

    override val defaultRuleIdAliases: Set<String> = setOf("UNCHECKED_CAST")

    override val issue: Issue = Issue("UnsafeCast",
            Severity.Defect,
            "Cast operator throws an exception if the cast is not possible.",
            Debt.TWENTY_MINS)

    override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
        val field = Errors::class.java.getDeclaredField("CAST_NEVER_SUCCEEDS").get(null) as DiagnosticFactoryWithPsiElement<*, *>

//        val visitor = SimpleNameExpressionVisitor(bindingContext, field.get(null) as DiagnosticFactoryWithPsiElement<*, *>)
        val diagnostic = expression.check(bindingContext, field)

//        when (field.genericType.parameterizedTypeArguments.first().typeName) {
//            "org.jetbrains.kotlin.psi.KtElement" -> visitor.visitKtElement(expression),
//            "org.jetbrains.kotlin.psi.KtDeclaration" -> visitor.visitDeclaration(expression),
//            "org.jetbrains.kotlin.psi.KtExpression" -> visitor.visit(expression),
//            "org.jetbrains.kotlin.psi.KtNullableType" -> visitor.visit(expression),
//            "org.jetbrains.kotlin.psi.KtTypeProjection" -> visitor.visit(expression),
//            "org.jetbrains.kotlin.psi.KtClassOrObject" -> visitor.visit(expression),
//            "org.jetbrains.kotlin.psi.KtAnnotationEntry" -> visitor.visit(expression),
//            "org.jetbrains.kotlin.psi.KtAnnotatedExpression" -> visitor.visit(expression),
//            "org.jetbrains.kotlin.psi.KtModifierListOwner" -> visitor.visit(expression),
//            "org.jetbrains.kotlin.psi.KtTypeParameterList" -> visitor.visit(expression),
//            "org.jetbrains.kotlin.psi.KtTypeReference" -> visitor.visit(expression),
//            "org.jetbrains.kotlin.psi.KtTypeParameter" -> visitor.visit(expression),
//            "org.jetbrains.kotlin.psi.KtNamedDeclaration" -> visitor.visit(expression),
//            "org.jetbrains.kotlin.psi.KtProperty" -> visitor.visit(expression),
//            "org.jetbrains.kotlin.psi.KtNamedFunction" -> visitor.visit(expression),
//            "org.jetbrains.kotlin.psi.KtParameter" -> visitor.visit(expression),
//            "org.jetbrains.kotlin.psi.KtBinaryExpression" -> visitor.visit(expression),
//            "org.jetbrains.kotlin.psi.KtSimpleNameExpression" -> visitor.visitSimpleNameExpression(expression)
//            "org.jetbrains.kotlin.psi.KtExpressionWithLabel" -> visitor.visit(expression),
//            "org.jetbrains.kotlin.psi.KtDestructuringDeclarationEntry" -> visitor.visit(expression),
//            "org.jetbrains.kotlin.psi.KtLambdaExpression" -> visitor.visit(expression),
//            "org.jetbrains.kotlin.psi.KtConstantExpression" -> visitor.visit(expression),
//            "org.jetbrains.kotlin.psi.KtBinaryExpressionWithTypeRHS" -> visitor.visit(expression),
//            "org.jetbrains.kotlin.psi.KtWhenEntry" -> visitor.visit(expression),
//            "org.jetbrains.kotlin.psi.KtWhenExpression" -> visitor.visit(expression)
//            else -> error("failure")
//        }

        diagnostic?.let {
            report(CodeSmell(issue, Entity.from(diagnostic.psiElement), DefaultErrorMessages.render(diagnostic)))
        }

        super.visitSimpleNameExpression(expression)
    }
}

fun PsiElement.check(bindingContext: BindingContext, error: DiagnosticFactoryWithPsiElement<*, *>): Diagnostic? {
    return bindingContext.diagnostics.forElement(this).firstOrNull { it.factory == error }
}

class SimpleNameExpressionVisitor(val bindingContext: BindingContext, val error: DiagnosticFactoryWithPsiElement<*, *>) : DetektVisitor() {
    val possibleReports = mutableListOf<Diagnostic>()

    fun check(element: PsiElement) {
        bindingContext.diagnostics.forElement(element).firstOrNull { it.factory == error }?.let(possibleReports::add)
    }
//
//    override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
//        check(expression)
//        super.visitSimpleNameExpression(expression)
//    }
//
//    override fun visitKtElement(element: KtElement) {
//        check(element)
//        super.visitKtElement(element)
//    }
//
//    override fun visitDeclaration(dcl: KtDeclaration) {
//        check(dcl)
//        super.visitDeclaration(dcl)
//    }
//
//    override fun visitExpression(expression: KtExpression) {
//        check(expression)
//        super.visitExpression(expression)
//    }
//
//    override fun visitNullableType(nullableType: KtNullableType) {
//        check(nullableType)
//        super.visitNullableType(nullableType)
//    }
//
//    override fun visitTypeProjection(typeProjection: KtTypeProjection) {
//        check(typeProjection)
//        super.visitTypeProjection(typeProjection)
//    }
//
//    override fun visitClassOrObject(classOrObject: KtClassOrObject) {
//        check(classOrObject)
//        super.visitClassOrObject(classOrObject)
//    }
//
//    override fun visitAnnotationEntry(annotationEntry: KtAnnotationEntry) {
//        check(annotationEntry)
//        super.visitAnnotationEntry(annotationEntry)
//    }
//
//    override fun visitAnnotatedExpression(expression: KtAnnotatedExpression) {
//        check(expression)
//        super.visitAnnotatedExpression(expression)
//    }
//
//    override fun visitModifierList(list: KtModifierList) {
//        check(list)
//        super.visitModifierList(list)
//    }
//
//    override fun visitParameterList(list: KtParameterList) {
//        check(list)
//        super.visitParameterList(list)
//    }
//
//    override fun visitTypeReference(typeReference: KtTypeReference) {
//        check(typeReference)
//        super.visitTypeReference(typeReference)
//    }
//
//    override fun visitTypeParameter(parameter: KtTypeParameter) {
//        check(parameter)
//        super.visitTypeParameter(parameter)
//    }
//
//    override fun visitNamedDeclaration(declaration: KtNamedDeclaration) {
//        check(declaration)
//        super.visitNamedDeclaration(declaration)
//    }
//
//    override fun visitProperty(property: KtProperty) {
//        check(property)
//        super.visitProperty(property)
//    }
//
//    override fun visitNamedFunction(function: KtNamedFunction) {
//        check(function)
//        super.visitNamedFunction(function)
//    }
//
//    override fun visitParameter(parameter: KtParameter) {
//        check(parameter)
//        super.visitParameter(parameter)
//    }
//
//    override fun visitBinaryExpression(expression: KtBinaryExpression) {
//        check(expression)
//        super.visitBinaryExpression(expression)
//    }
//
//    override fun visitExpressionWithLabel(expression: KtExpressionWithLabel) {
//        check(expression)
//        super.visitExpressionWithLabel(expression)
//    }
//
//    override fun visitDestructuringDeclarationEntry(multiDeclarationEntry: KtDestructuringDeclarationEntry) {
//        check(multiDeclarationEntry)
//        super.visitDestructuringDeclarationEntry(multiDeclarationEntry)
//    }
//
//    override fun visitLambdaExpression(lambdaExpression: KtLambdaExpression) {
//        check(lambdaExpression)
//        super.visitLambdaExpression(lambdaExpression)
//    }
//
//    override fun visitConstantExpression(expression: KtConstantExpression) {
//        check(expression)
//        super.visitConstantExpression(expression)
//    }
//
//    override fun visitBinaryWithTypeRHSExpression(expression: KtBinaryExpressionWithTypeRHS) {
//        check(expression)
//        super.visitBinaryWithTypeRHSExpression(expression)
//    }
//
//    override fun visitWhenEntry(jetWhenEntry: KtWhenEntry) {
//        check(jetWhenEntry)
//        super.visitWhenEntry(jetWhenEntry)
//    }
//
//    override fun visitWhenExpression(expression: KtWhenExpression) {
//        check(expression)
//        super.visitWhenExpression(expression)
//    }
}
