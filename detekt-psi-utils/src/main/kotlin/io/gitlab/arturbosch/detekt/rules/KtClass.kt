package io.gitlab.arturbosch.detekt.rules

import org.jetbrains.kotlin.psi.KtClass

fun KtClass.companionObject() = this.companionObjects.singleOrNull { it.isCompanion() }
