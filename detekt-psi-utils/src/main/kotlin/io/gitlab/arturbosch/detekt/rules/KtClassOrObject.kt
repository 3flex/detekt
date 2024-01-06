package io.gitlab.arturbosch.detekt.rules

import org.jetbrains.kotlin.psi.KtClassOrObject

fun KtClassOrObject.hasCommentInside() = this.body?.hasCommentInside() ?: false
