package io.gitlab.arturbosch.detekt.rules

import org.jetbrains.kotlin.com.intellij.openapi.util.Key
import org.jetbrains.kotlin.com.intellij.psi.PsiComment
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid

fun PsiElement.hasCommentInside(): Boolean {
    val commentKey = Key<Boolean>("comment")
    this.acceptChildren(object : KtTreeVisitorVoid() {
        override fun visitComment(comment: PsiComment) {
            putUserData(commentKey, true)
        }
    })
    return getUserData(commentKey) == true
}

fun getIntValueForPsiElement(element: PsiElement): Int? {
    return (element as? KtConstantExpression)?.text?.toIntOrNull()
}
